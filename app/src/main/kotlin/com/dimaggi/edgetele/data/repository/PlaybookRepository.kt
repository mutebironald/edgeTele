package com.dimaggi.edgetele.data.repository

import android.content.Context
import com.dimaggi.edgetele.data.model.PlaybookAction
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.model.enums.Language
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    // Cached parsed playbooks — loaded once per session.
    private val cache = mutableMapOf<String, PlaybookFile>()

    /**
     * Returns up to [limit] actions for the given [category] and [language],
     * ordered by priority. High-severity incidents (>=4) get priority 1-2 actions first.
     */
    fun getActions(
        category: IncidentCategory,
        severity: Int,
        language: Language,
        limit: Int = 3
    ): List<PlaybookAction> {
        val playbook = loadPlaybook(category) ?: return emptyList()

        return playbook.actions
            .filter { action ->
                // For high severity, prefer actions tagged "immediate"
                if (severity >= 4) true else "immediate" !in action.tags
            }
            .sortedWith(compareBy { it.priority })
            .take(limit)
            .map { raw ->
                PlaybookAction(
                    id = raw.id,
                    action = raw.translations[language.assetKey]
                        ?: raw.translations["en"]
                        ?: raw.id,
                    priority = raw.priority,
                    tags = raw.tags
                )
            }
    }

    private fun loadPlaybook(category: IncidentCategory): PlaybookFile? {
        val key = category.assetKey
        return cache.getOrPut(key) {
            try {
                val json = context.assets
                    .open("playbooks/$key.json")
                    .bufferedReader()
                    .readText()
                gson.fromJson(json, PlaybookFile::class.java)
            } catch (e: Exception) {
                return null
            }
        }
    }

    // ---- JSON schema mirrors ----

    private data class PlaybookFile(
        @SerializedName("category") val category: String,
        @SerializedName("version") val version: String,
        @SerializedName("actions") val actions: List<RawAction>
    )

    private data class RawAction(
        @SerializedName("id") val id: String,
        @SerializedName("priority") val priority: Int,
        @SerializedName("tags") val tags: List<String>,
        @SerializedName("translations") val translations: Map<String, String>
    )
}
