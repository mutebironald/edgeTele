package com.dimaggi.edgetele.data.model.enums

enum class IncidentCategory(
    val displayName: String,
    val assetKey: String,         // maps to assets/playbooks/{assetKey}.json
    val gemmaLabel: String        // label used in Gemma prompt schema
) {
    FLOOD_DAMAGE(
        displayName = "Flood Damage",
        assetKey = "flood_damage",
        gemmaLabel = "flood_damage"
    ),
    STRUCTURAL_DAMAGE(
        displayName = "Structural Damage",
        assetKey = "structural_damage",
        gemmaLabel = "structural_damage"
    ),
    INJURY(
        displayName = "Injury",
        assetKey = "injury",
        gemmaLabel = "injury"
    ),
    CONTAMINATION(
        displayName = "Contamination",
        assetKey = "contamination",
        gemmaLabel = "contamination"
    ),
    OTHER(
        displayName = "Other",
        assetKey = "other",
        gemmaLabel = "other"
    );

    companion object {
        fun fromGemmaLabel(label: String): IncidentCategory {
            val normalised = label.trim().lowercase().replace(" ", "_")
            return entries.firstOrNull { it.gemmaLabel == normalised } ?: OTHER
        }
    }
}
