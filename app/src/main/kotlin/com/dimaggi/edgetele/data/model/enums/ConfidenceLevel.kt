package com.dimaggi.edgetele.data.model.enums

enum class ConfidenceLevel(
    val label: String,
    val colorHex: Long,
    val requiresExpertConfirmation: Boolean
) {
    GREEN(
        label = "High confidence",
        colorHex = 0xFF2E7D32L,
        requiresExpertConfirmation = false
    ),
    YELLOW(
        label = "Low confidence",
        colorHex = 0xFFF57F17L,
        requiresExpertConfirmation = true
    ),
    RED(
        label = "Very low confidence",
        colorHex = 0xFFC62828L,
        requiresExpertConfirmation = true
    )
}
