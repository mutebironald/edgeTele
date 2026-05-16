package com.dimaggi.edgetele.ai

import android.content.Context
import android.util.Log
import com.dimaggi.edgetele.data.model.ClassificationResult
import com.dimaggi.edgetele.data.model.enums.Language
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Release inference engine — on-device first, API fallback.
 *
 * Attempts to load the Gemma 4 E4B model via MediaPipe LiteRT from the app's
 * external files directory. If the model file is absent, falls back to the API
 * engine ([GemmaApiInferenceEngineImpl]) and surfaces a visible mode indicator.
 *
 * To enable fully offline inference push the model file before first launch:
 *   adb push gemma-4-E4B-it.litertlm \
 *       /sdcard/Android/data/com.dimaggi.edgetele/files/
 */
@Singleton
class GemmaInferenceEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val apiEngine: GemmaApiInferenceEngineImpl
) : GemmaInferenceEngine {

    companion object {
        private const val TAG = "GemmaEngine"
        private const val MODEL_PATH = "gemma-4-E4B-it.litertlm"
        // 2048 gives the 4B model enough KV-cache for prompt + output.
        // The old value of 512 was smaller than the prompt itself, causing truncation.
        private const val MAX_TOKENS = 2048

        // Compact prompt tuned for the 4B on-device model.
        // Long prompts exceed the old 512-token budget before the categories were even reached.
        // Few-shot examples give the small model concrete anchors rather than abstract rules.
        private val SYSTEM_PROMPT = """
            You are a disaster incident classifier. Read the field report and output ONLY a JSON object.

            Categories (pick the single best match):
            injury        – person physically hurt: broken bone, wound, bleeding, burn, fracture, medical emergency
            contamination – hazardous substance: chemical spill, nuclear/radioactive material, toxic exposure, biohazard
            flood_damage  – water flooding: flooded road, submerged building, overflowing river
            structural_damage – building/infrastructure damage: collapsed roof, cracked wall, downed bridge
            other         – does not fit any category above

            Examples:
            "broken leg"          → {"category":"injury","severity":3,"confidence":0.95,"reasoning":"Broken leg is bodily harm."}
            "chemical spill"      → {"category":"contamination","severity":4,"confidence":0.95,"reasoning":"Chemical spill is a hazardous substance."}
            "nuclear material"    → {"category":"contamination","severity":5,"confidence":0.95,"reasoning":"Nuclear material is a radiological hazard."}
            "flooded street"      → {"category":"flood_damage","severity":3,"confidence":0.90,"reasoning":"Flooded street is water inundation."}
            "collapsed wall"      → {"category":"structural_damage","severity":4,"confidence":0.90,"reasoning":"Collapsed wall is structural damage."}

            Severity: 1=minor 2=moderate 3=significant 4=severe 5=critical
            Confidence: 0.85-1.0=clear match  0.60-0.84=probable  0.0-0.59=uncertain

            Respond with ONLY the JSON object, nothing else.
        """.trimIndent()
    }

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isUsingApiFallback = MutableStateFlow(false)
    override val isUsingApiFallback: StateFlow<Boolean> = _isUsingApiFallback.asStateFlow()

    private var llmInference: LlmInference? = null
    private var usingApiEngine = false

    override suspend fun preload() = withContext(Dispatchers.IO) {
        if (_isReady.value) return@withContext

        val externalDir = context.getExternalFilesDir(null)
        val modelFile = if (externalDir != null) File(externalDir, MODEL_PATH) else null
        if (modelFile == null || !modelFile.exists()) {
            Log.w(TAG, "Model file not found at ${modelFile?.absolutePath} — using API fallback")
            apiEngine.preload()
            usingApiEngine = true
            _isUsingApiFallback.value = true
            _isReady.value = true
            return@withContext
        }

        Log.d(TAG, "Loading Gemma model from ${modelFile!!.absolutePath} (GPU)...")
        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile!!.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            _isReady.value = true
            Log.d(TAG, "Gemma loaded on GPU successfully")
        } catch (e: Exception) {
            Log.w(TAG, "GPU load failed (${e.message}), retrying on CPU...")
            try {
                val cpuOptions = LlmInferenceOptions.builder()
                    .setModelPath(modelFile!!.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                llmInference = LlmInference.createFromOptions(context, cpuOptions)
                _isReady.value = true
                Log.d(TAG, "Gemma loaded on CPU successfully")
            } catch (cpuEx: Exception) {
                Log.w(TAG, "On-device load failed: ${cpuEx.message} — falling back to API engine")
                apiEngine.preload()
                usingApiEngine = true
                _isUsingApiFallback.value = true
                _isReady.value = true
            }
        }
    }

    override suspend fun classify(
        imageBytes: ByteArray?,
        transcript: String?,
        notes: String?,
        language: Language
    ): ClassificationResult = withContext(Dispatchers.IO) {
        if (!_isReady.value) preload()

        if (usingApiEngine) {
            return@withContext apiEngine.classify(imageBytes, transcript, notes, language)
        }

        require(imageBytes != null || transcript != null || notes != null) {
            "At least one input (image, transcript, or notes) must be provided"
        }

        _isLoading.value = true
        val startMs = System.currentTimeMillis()

        return@withContext try {
            val prompt = buildPrompt(imageBytes, transcript, notes, language)
            val rawResponse = runInference(prompt)
            val elapsed = System.currentTimeMillis() - startMs
            Log.d(TAG, "On-device inference in ${elapsed}ms")
            parseResponse(rawResponse, elapsed)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during inference", e)
            throw GemmaInferenceEngine.InsufficientMemoryException()
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            ClassificationResult.fallback()
        } finally {
            _isLoading.value = false
        }
    }

    private fun buildPrompt(
        imageBytes: ByteArray?,
        transcript: String?,
        notes: String?,
        language: Language
    ): String {
        val parts = mutableListOf<String>()
        parts.add(SYSTEM_PROMPT)
        parts.add("\n---\n")
        parts.add("Field report language: ${language.gemmaLocaleHint}")
        if (transcript != null) parts.add("Voice transcript: $transcript")
        if (notes != null) parts.add("Field notes: $notes")
        // Photo classification is not supported in on-device mode (LiteRT text-only).
        // Use API mode for multimodal classification.
        parts.add("\nClassify this incident:")
        return parts.joinToString("\n")
    }

    private suspend fun runInference(prompt: String): String =
        suspendCancellableCoroutine { continuation ->
            val engine = llmInference
                ?: throw GemmaInferenceEngine.ModelNotReadyException()
            try {
                val result = engine.generateResponse(prompt)
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(
                    GemmaInferenceEngine.InferenceException("LlmInference failed", e)
                )
            }
        }

    private fun parseResponse(rawResponse: String, processingTimeMs: Long): ClassificationResult {
        return try {
            val parsed = parseProse(rawResponse) ?: parseJson(rawResponse)
                ?: throw IllegalStateException("No parseable classification in response")
            val category = com.dimaggi.edgetele.data.model.enums.IncidentCategory.fromGemmaLabel(parsed.category)
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
            Log.w(TAG, "Failed to parse Gemma response: $rawResponse", e)
            ClassificationResult.fallback().copy(rawResponse = rawResponse)
        }
    }

    private fun parseJson(text: String): GemmaClassificationResponse? {
        return try {
            val jsonStr = extractJson(text)
            val parsed = gson.fromJson(jsonStr, GemmaClassificationResponse::class.java)
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

    override fun release() {
        llmInference?.close()
        llmInference = null
        _isReady.value = false
        _isUsingApiFallback.value = false
        usingApiEngine = false
        Log.d(TAG, "Gemma engine released")
    }

    private data class GemmaClassificationResponse(
        @SerializedName("category") val category: String,
        @SerializedName("severity") val severity: Int,
        @SerializedName("confidence") val confidence: Float,
        @SerializedName("reasoning") val reasoning: String
    )
}
