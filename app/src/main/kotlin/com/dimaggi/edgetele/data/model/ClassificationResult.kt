package com.dimaggi.edgetele.data.model

import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import com.dimaggi.edgetele.data.model.enums.IncidentCategory

data class ClassificationResult(
    val category: IncidentCategory,
    val severity: Int,              // 1–5
    val confidence: Float,          // 0.0–1.0
    val confidenceLevel: ConfidenceLevel,
    val reasoning: String,          // brief model reasoning, stored for audit log
    val rawResponse: String,        // full Gemma JSON output
    val processingTimeMs: Long
) {
    val isHighConfidence: Boolean get() = confidenceLevel == ConfidenceLevel.GREEN
    val requiresExpertConfirmation: Boolean get() = confidenceLevel.requiresExpertConfirmation

    companion object {
        /** Fallback when AI inference itself fails — preserves data collection. */
        fun fallback() = ClassificationResult(
            category = IncidentCategory.OTHER,
            severity = 0,
            confidence = 0f,
            confidenceLevel = ConfidenceLevel.RED,
            reasoning = "AI inference unavailable — manual classification required",
            rawResponse = "",
            processingTimeMs = 0L
        )
    }
}
