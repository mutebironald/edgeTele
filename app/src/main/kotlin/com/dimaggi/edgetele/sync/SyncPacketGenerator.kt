package com.dimaggi.edgetele.sync

import android.location.Location
import com.dimaggi.edgetele.BuildConfig
import com.dimaggi.edgetele.data.model.ClassificationResult
import com.dimaggi.edgetele.data.model.Incident
import com.dimaggi.edgetele.data.model.PlaybookAction
import com.dimaggi.edgetele.data.model.enums.Language
import com.dimaggi.edgetele.data.repository.IncidentRepository
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPacketGenerator @Inject constructor(
    private val repository: IncidentRepository,
    private val gson: Gson
) {
    /**
     * Assembles a complete [Incident] record and persists it to Room.
     * Returns the saved [Incident] with its generated ID.
     */
    suspend fun generate(
        responderID: String,
        classification: ClassificationResult,
        actions: List<PlaybookAction>,
        photoFile: File? = null,
        transcript: String? = null,
        notes: String? = null,
        language: Language = Language.ENGLISH,
        location: Location? = null
    ): Incident {
        val actionsJson = gson.toJson(actions.map { it.action })
        val photoHash = photoFile?.let { computeSha256(it) }

        val incident = Incident(
            responderID = responderID,
            latitude = location?.latitude,
            longitude = location?.longitude,
            photoPath = photoFile?.absolutePath,
            photoHash = photoHash,
            voiceTranscript = transcript,
            textNotes = notes,
            language = language,
            category = classification.category,
            severity = classification.severity,
            confidence = classification.confidence,
            confidenceLevel = classification.confidenceLevel,
            recommendedActionsJson = actionsJson,
            rawGemmaResponse = classification.rawResponse,
            inferenceMs = classification.processingTimeMs,
            appVersion = BuildConfig.VERSION_NAME
        )

        repository.save(incident)
        return incident
    }

    /**
     * Serialize a single [Incident] wrapped in an integrity envelope.
     * The [packetHash] covers the incident JSON — verifiable by the receiving server.
     */
    fun toJson(incident: Incident): String {
        val incidentJson = gson.toJson(incident)
        val hash = computeSha256String(incidentJson)
        val envelope = mapOf("packetHash" to hash, "incident" to incident)
        return gson.toJson(envelope)
    }

    /**
     * Serialize all provided incidents to a JSON array envelope with per-record hashes.
     * Used for manual export via Android share sheet.
     */
    fun toBulkJson(incidents: List<Incident>): String {
        val packets = incidents.map { incident ->
            val incidentJson = gson.toJson(incident)
            val hash = computeSha256String(incidentJson)
            mapOf("packetHash" to hash, "incident" to incident)
        }
        return gson.toJson(packets)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeSha256String(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(input.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
