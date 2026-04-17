package com.dimaggi.edgetele.ai

import com.dimaggi.edgetele.data.model.ClassificationResult
import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.model.enums.Language
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake Gemma engine for debug/testing builds.
 * Returns realistic-looking results after a 2-second simulated inference delay.
 * No model file required. Swap back to GemmaInferenceEngineImpl for production.
 */
@Singleton
class MockGemmaInferenceEngineImpl @Inject constructor() : GemmaInferenceEngine {

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    override val isUsingApiFallback: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    // Cycles through different scenarios so you can see the full UI
    private var callCount = 0

    init {
        // Simulate 1-second model load time
        _isReady.value = true
    }

    override suspend fun preload() { /* no-op — mock is always ready */ }

    override suspend fun classify(
        imageBytes: ByteArray?,
        transcript: String?,
        notes: String?,
        language: Language
    ): ClassificationResult {
        _isLoading.value = true
        delay(2000) // Simulate inference time

        // Rotate through scenarios: high confidence → low confidence → very low
        val result = when (callCount % 4) {
            0 -> ClassificationResult(
                category = IncidentCategory.FLOOD_DAMAGE,
                severity = 4,
                confidence = 0.87f,
                confidenceLevel = ConfidenceLevel.GREEN,
                reasoning = "Visible floodwater reaching building foundations. Multiple structures affected.",
                rawResponse = """{"category":"flood_damage","severity":4,"confidence":0.87,"reasoning":"Visible floodwater reaching building foundations."}""",
                processingTimeMs = 2000L
            )
            1 -> ClassificationResult(
                category = IncidentCategory.STRUCTURAL_DAMAGE,
                severity = 3,
                confidence = 0.71f,
                confidenceLevel = ConfidenceLevel.YELLOW,
                reasoning = "Wall cracking visible. Partial roof collapse suspected.",
                rawResponse = """{"category":"structural_damage","severity":3,"confidence":0.71,"reasoning":"Wall cracking visible."}""",
                processingTimeMs = 2000L
            )
            2 -> ClassificationResult(
                category = IncidentCategory.CONTAMINATION,
                severity = 5,
                confidence = 0.92f,
                confidenceLevel = ConfidenceLevel.GREEN,
                reasoning = "Discoloured standing water near water supply. Cholera risk high.",
                rawResponse = """{"category":"contamination","severity":5,"confidence":0.92,"reasoning":"Discoloured standing water near water supply."}""",
                processingTimeMs = 2000L
            )
            else -> ClassificationResult(
                category = IncidentCategory.INJURY,
                severity = 2,
                confidence = 0.54f,
                confidenceLevel = ConfidenceLevel.RED,
                reasoning = "Partial input — unable to confirm injury type without clearer image.",
                rawResponse = """{"category":"injury","severity":2,"confidence":0.54,"reasoning":"Partial input."}""",
                processingTimeMs = 2000L
            )
        }

        callCount++
        _isLoading.value = false
        return result
    }

    override fun release() {
        _isReady.value = false
    }
}
