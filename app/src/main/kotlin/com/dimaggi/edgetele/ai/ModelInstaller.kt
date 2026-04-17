package com.dimaggi.edgetele.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks for the Gemma model file in the app's external files directory.
 * No permissions required — getExternalFilesDir() is the app's own external dir,
 * accessible without READ_EXTERNAL_STORAGE on all API levels.
 *
 * ADB push command (run once before demo):
 *   Release: adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/com.dimaggi.edgetele/files/
 *   Debug:   adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/com.dimaggi.edgetele.debug/files/
 *
 */
@Singleton
class ModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ModelInstaller"
        const val MODEL_FILENAME = "gemma-4-E4B-it.litertlm"
    }

    val modelFile: File? get() = context.getExternalFilesDir(null)?.let { File(it, MODEL_FILENAME) }

    fun isInstalled(): Boolean = modelFile?.let { it.exists() && it.length() > 0 } ?: false

    suspend fun installFromExternalIfPresent(): InstallResult = withContext(Dispatchers.IO) {
        val file = modelFile
            ?: return@withContext InstallResult.Failed("External storage unavailable")

        if (isInstalled()) {
            Log.d(TAG, "Model already present at ${file.absolutePath}")
            return@withContext InstallResult.AlreadyInstalled
        }

        Log.w(TAG, "Model not found at ${file.absolutePath}")
        return@withContext InstallResult.NotFound(file.absolutePath)
    }

    sealed class InstallResult {
        object AlreadyInstalled : InstallResult()
        object Installed : InstallResult()
        data class NotFound(val searchedPath: String) : InstallResult()
        data class Failed(val reason: String) : InstallResult()
    }
}
