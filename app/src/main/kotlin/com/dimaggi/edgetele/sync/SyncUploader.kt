package com.dimaggi.edgetele.sync

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.dimaggi.edgetele.EdgeTeleApp
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dimaggi.edgetele.data.repository.IncidentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles opportunistic upload of unsynced [Incident] records.
 *
 * Two upload paths:
 * 1. Automatic: WorkManager fires [SyncWorker] every 15 min when network is available.
 * 2. Manual: [shareAsFile] exports all unsynced incidents as a JSON file
 *    shared via the Android share sheet — works with any messaging app, no backend required.
 *
 * Backend endpoint is configurable. In demo mode, [SYNC_ENDPOINT] can be left empty
 * and only the manual share path is used.
 */
@Singleton
class SyncUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: IncidentRepository,
    private val generator: SyncPacketGenerator
) {
    companion object {
        private const val TAG = "SyncUploader"
        private const val SYNC_WORK_TAG = "edge_tele_sync"
        // HTTP sync endpoint — intentionally empty for the submission.
        // File export via shareAsFile() is the only supported sync path.
        // Set this to a backend URL to enable automatic HTTP upload.
        private const val SYNC_ENDPOINT = ""
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    /** Register WorkManager periodic sync task. Call once at app startup. */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(SYNC_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Periodic sync scheduled")
    }

    /** Trigger an immediate sync attempt. No-op if offline. */
    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext SyncResult.Offline

        val unsynced = repository.getUnsynced()
        if (unsynced.isEmpty()) return@withContext SyncResult.NothingToSync

        if (SYNC_ENDPOINT.isBlank()) {
            Log.d(TAG, "No endpoint configured — skipping HTTP sync")
            return@withContext SyncResult.NoEndpoint
        }

        var successCount = 0
        var failCount = 0

        for (incident in unsynced) {
            try {
                val json = generator.toJson(incident)
                postJson(SYNC_ENDPOINT, json)
                repository.markSynced(incident.id, System.currentTimeMillis())
                successCount++
                Log.d(TAG, "Synced incident ${incident.id}")
            } catch (e: Exception) {
                failCount++
                Log.w(TAG, "Failed to sync incident ${incident.id}: ${e.message}")
            }
        }

        SyncResult.Completed(successCount, failCount)
    }

    /**
     * Export all unsynced incidents as a JSON file and launch the Android share sheet.
     * This is the primary demo path — no backend required.
     */
    suspend fun shareAsFile(): Boolean = withContext(Dispatchers.IO) {
        val unsynced = repository.getUnsynced()
        if (unsynced.isEmpty()) return@withContext false

        val exportDir = File(context.filesDir, "sync_exports").also { it.mkdirs() }
        val fileName = "edge_tele_sync_${System.currentTimeMillis()}.json"
        val file = File(exportDir, fileName)

        file.writeText(generator.toBulkJson(unsynced))

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Edge-Tele Incident Report — ${unsynced.size} records"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share incident records").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        // Records remain unsynced until the user confirms delivery. Re-sharing is safe.
        Log.d(TAG, "Shared ${unsynced.size} incidents — awaiting delivery confirmation")
        true
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun postJson(endpoint: String, json: String) {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(json) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode from sync endpoint")
            }
        } finally {
            conn.disconnect()
        }
    }

    // ---- Sealed result ----

    sealed class SyncResult {
        object Offline : SyncResult()
        object NothingToSync : SyncResult()
        object NoEndpoint : SyncResult()
        data class Completed(val successCount: Int, val failCount: Int) : SyncResult()
    }

    // ---- WorkManager Worker ----

    class SyncWorker(
        appContext: Context,
        params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            Log.d(TAG, "SyncWorker running")
            val uploader = (applicationContext as? EdgeTeleApp)?.syncUploader
                ?: return Result.failure()
            return try {
                uploader.syncNow()
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "SyncWorker failed: ${e.message}")
                Result.retry()
            }
        }
    }
}
