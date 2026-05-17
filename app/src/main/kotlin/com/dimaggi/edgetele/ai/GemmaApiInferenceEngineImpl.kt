package com.dimaggi.edgetele.ai

import android.graphics.BitmapFactory
import android.util.Log
import com.dimaggi.edgetele.BuildConfig
import com.dimaggi.edgetele.data.model.ClassificationResult
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.model.enums.Language
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production inference engine using Gemma 4 via the Gemini API.
 *
 * Tries Gemma 4 models in preference order. If a model is unavailable in the API
 * (wrong name, quota, etc.) it automatically falls back to the next candidate.
 * The first model that responds successfully is used for all subsequent calls.
 *
 * Preference order:
 *   1. gemma-4-26b-a4b-it — Gemma 4 (26B total / 4B active MoE) — primary
 *   2. gemma-4-31b-it     — Gemma 4 31B — secondary
 */
@Singleton
class GemmaApiInferenceEngineImpl @Inject constructor(
    private val gson: Gson
) : GemmaInferenceEngine {

    companion object {
        private const val TAG = "GemmaApi"

        private val MODEL_CANDIDATES = listOf(
            "gemma-4-26b-a4b-it",  // Gemma 4 (26B total / 4B active MoE) — primary
            "gemma-4-31b-it"       // Gemma 4 31B — secondary
        )

        private val SYSTEM_PROMPT = """
            You are an offline disaster response assistant helping trained field responders.
            You classify disaster incidents from photos, voice transcripts, and field notes.
            You are NOT an autonomous decision-maker.
            All your outputs are DECISION SUPPORT for trained human responders.

            Always respond with a single valid JSON object matching this exact schema:
            {
              "category": "<one of: injury, contamination, flood_damage, structural_damage, other>",
              "severity": <integer 1-5>,
              "confidence": <float 0.0-1.0>,
              "reasoning": "<brief 1-sentence explanation>"
            }

            Category definitions — choose the single best match:
            - injury: bodily harm to a person — wounds, broken bones, burns, fractures, sprains, unconscious person, bleeding, medical emergency
            - contamination: a substance (water, food, air, soil) is unsafe — chemical spill, polluted water source, toxic exposure, biological hazard, nuclear or radiological hazard, hazmat incident, radiation leak
            - flood_damage: flooding or water inundation — submerged roads, waterlogged buildings, overflowing rivers
            - structural_damage: physical damage to buildings or infrastructure — collapsed roof, cracked walls, downed bridge, damaged roads
            - other: does not clearly fit any category above

            CRITICAL matching rules:
            - Use injury whenever a person is physically harmed — broken bones, wounds, bleeding, burns always map here.
            - Use contamination for any hazardous substance — nuclear material, radiation, chemicals, biohazards always map here.
            - Use flood_damage ONLY when water flooding or inundation is explicitly described. Do NOT use it as a default.
            - When unsure between two categories, pick the one whose definition contains the most matching words from the input.

            Severity scale:
            1 = Minor, no immediate risk
            2 = Moderate, monitor situation
            3 = Significant, intervention needed soon
            4 = Severe, immediate intervention required
            5 = Critical, life-threatening

            Confidence guidance:
            0.85-1.00 = Input clearly and directly matches the chosen category
            0.65-0.84 = Strong indicators present but some ambiguity
            0.40-0.64 = Mixed signals or incomplete information
            0.00-0.39 = Input insufficient to classify with confidence
            Do NOT inflate confidence. A wrong answer with high confidence is worse than a low-confidence flag.
        """.trimIndent()
    }

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    override val isUsingApiFallback: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    // Starts at index 0 (Gemma 4 27B). Advances when a model returns an API error.
    @Volatile private var candidateIndex = 0
    private var model: GenerativeModel? = null

    override suspend fun preload() {
        if (_isReady.value) return
        model = buildModel(MODEL_CANDIDATES[candidateIndex])
        _isReady.value = true
        Log.d(TAG, "Engine ready — primary candidate: ${MODEL_CANDIDATES[candidateIndex]}")
    }

    override suspend fun classify(
        imageBytes: ByteArray?,
        transcript: String?,
        notes: String?,
        language: Language
    ): ClassificationResult = withContext(Dispatchers.IO) {
        if (!_isReady.value) preload()

        _isLoading.value = true
        val startMs = System.currentTimeMillis()

        return@withContext try {
            val inputContent = content("user") {
                if (imageBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    if (bitmap != null) image(bitmap)
                }
                val lines = buildList {
                    add("Field report language: ${language.gemmaLocaleHint}")
                    if (transcript != null) add("Voice transcript: $transcript")
                    if (notes != null) add("Field notes: $notes")
                    add("\nClassify this incident:")
                }
                text(lines.joinToString("\n"))
            }

            val rawResponse = generateWithFallback(inputContent)
            val elapsed = System.currentTimeMillis() - startMs
            Log.d(TAG, "${MODEL_CANDIDATES[candidateIndex]} — inference in ${elapsed}ms")
            parseResponse(rawResponse, elapsed)
        } catch (e: Exception) {
            Log.e(TAG, "All model candidates exhausted: ${e.message}", e)
            throw e  // Surface to ViewModel so the real error appears in the UI
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Try each candidate in order per call. Resets to the primary on every invocation
     * so a transient error on one call does not permanently skip the primary model.
     */
    private suspend fun generateWithFallback(inputContent: com.google.ai.client.generativeai.type.Content): String {
        for (index in MODEL_CANDIDATES.indices) {
            val name = MODEL_CANDIDATES[index]
            // Reuse the preloaded model for the primary; build fresh only for fallbacks.
            val m = if (index == 0) model ?: buildModel(name) else buildModel(name)
            try {
                val response = m.generateContent(inputContent)
                Log.d(TAG, "$name — response received")
                return response.text ?: ""
            } catch (e: Exception) {
                Log.w(TAG, "$name failed (${e.message})")
                if (index == MODEL_CANDIDATES.lastIndex) throw e
            }
        }
        throw IllegalStateException("No model candidates remaining")
    }

    private fun buildModel(name: String): GenerativeModel = GenerativeModel(
        modelName = name,
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.3f
            maxOutputTokens = 1024
        },
        systemInstruction = content { text(SYSTEM_PROMPT) }
    )

    override fun release() {
        model = null
        _isReady.value = false
    }

    private fun parseResponse(rawResponse: String, processingTimeMs: Long): ClassificationResult {
        return try {
            val parsed = parseProse(rawResponse) ?: parseJson(rawResponse)
                ?: throw IllegalStateException("No parseable classification in response")
            val category = IncidentCategory.fromGemmaLabel(parsed.category)
            val severity = parsed.severity.coerceIn(1, 5)
            val confidence = parsed.confidence.coerceIn(0f, 1f)
            val level = ConfidenceGate.evaluate(confidence)
            ClassificationResult(
                category = category,
                severity = severity,
                confidence = confidence,
                confidenceLevel = level,
                reasoning = parsed.reasoning,
                rawResponse = rawResponse,
                processingTimeMs = processingTimeMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response: $rawResponse", e)
            ClassificationResult.fallback().copy(rawResponse = rawResponse)
        }
    }

    private fun parseJson(text: String): GemmaClassificationResponse? {
        return try {
            val jsonStr = extractJson(text)
            val parsed = gson.fromJson(jsonStr, GemmaClassificationResponse::class.java)
            // Reject the schema example the model sometimes echoes back
            if (parsed?.category != null && !parsed.category.startsWith("<")) parsed else null
        } catch (e: Exception) { null }
    }

    // Fallback: model output bullet-point prose instead of JSON.
    // Uses the LAST occurrence of each key so reasoning sections are skipped.
    private fun parseProse(text: String): GemmaClassificationResponse? {
        val cat = Regex("""(?i)category[`\s]*:\s*[`"]?([a-z][a-z_]*)""")
            .findAll(text).lastOrNull()?.groupValues?.get(1) ?: return null
        val sev = Regex("""(?i)severity[`\s]*:\s*[`"]?(\d)""")
            .findAll(text).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 3
        val conf = Regex("""(?i)confidence[`\s]*:\s*[`"]?([01]\.?[0-9]*)""")
            .findAll(text).lastOrNull()?.groupValues?.get(1)?.toFloatOrNull() ?: 0.5f
        val reason = Regex("""(?i)reasoning[`\s]*:\s*"?([^\n"]+)""")
            .findAll(text).lastOrNull()?.groupValues?.get(1)?.trim() ?: ""
        return GemmaClassificationResponse(cat, sev, conf, reason)
    }

    private fun extractJson(text: String): String {
        val jsonBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = jsonBlockRegex.find(text)
        if (match != null) return match.groupValues[1].trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return text.trim()
    }

    private data class GemmaClassificationResponse(
        @SerializedName("category") val category: String,
        @SerializedName("severity") val severity: Int,
        @SerializedName("confidence") val confidence: Float,
        @SerializedName("reasoning") val reasoning: String
    )
}
