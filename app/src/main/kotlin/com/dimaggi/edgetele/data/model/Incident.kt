package com.dimaggi.edgetele.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.model.enums.Language
import java.util.UUID

@Entity(tableName = "incidents")
data class Incident(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val responderID: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),

    // Location (nullable — GPS not always available)
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Inputs
    val photoPath: String? = null,
    val photoHash: String? = null,          // SHA-256 of photo bytes
    val voiceTranscript: String? = null,
    val textNotes: String? = null,
    val language: Language,

    // AI output
    val category: IncidentCategory,
    val severity: Int,                      // 1–5; 0 if AI failed
    val confidence: Float,                  // 0.0–1.0
    val confidenceLevel: ConfidenceLevel,

    // Stored as JSON string (Room Converter handles serialization)
    val recommendedActionsJson: String = "[]",

    // Full Gemma response + timing (for post-event audit)
    val rawGemmaResponse: String = "",
    val inferenceMs: Long = 0L,

    val appVersion: String,

    // Sync state
    val synced: Boolean = false,
    val syncedAtMs: Long? = null
)
