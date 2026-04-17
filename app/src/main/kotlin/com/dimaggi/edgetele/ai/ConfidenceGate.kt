package com.dimaggi.edgetele.ai

import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel

/**
 * Pure logic — no state, no dependencies, no side effects.
 * Every ClassificationResult must pass through here before reaching the UI.
 */
object ConfidenceGate {

    const val GREEN_THRESHOLD = 0.80f
    const val YELLOW_THRESHOLD = 0.60f

    fun evaluate(confidence: Float): ConfidenceLevel = when {
        confidence >= GREEN_THRESHOLD  -> ConfidenceLevel.GREEN
        confidence >= YELLOW_THRESHOLD -> ConfidenceLevel.YELLOW
        else                           -> ConfidenceLevel.RED
    }

    fun requiresExpertConfirmation(level: ConfidenceLevel): Boolean =
        level == ConfidenceLevel.YELLOW || level == ConfidenceLevel.RED

    fun bannerMessage(level: ConfidenceLevel): String? = when (level) {
        ConfidenceLevel.GREEN  -> null
        ConfidenceLevel.YELLOW -> "Low confidence — seek expert confirmation before acting"
        ConfidenceLevel.RED    -> "Very low confidence — do not act without expert review"
    }
}
