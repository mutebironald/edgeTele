package com.dimaggi.edgetele.ai

import com.dimaggi.edgetele.data.model.ClassificationResult
import com.dimaggi.edgetele.data.model.enums.Language
import kotlinx.coroutines.flow.StateFlow

/**
 * The single AI boundary.
 * Every AI call — photo classification, voice analysis, text analysis — goes through here.
 * Implementations must be callable with airplane mode on.
 */
interface GemmaInferenceEngine {

    /** True once the model is loaded and ready to accept inference calls. */
    val isReady: StateFlow<Boolean>

    /** True while an inference call is in progress. */
    val isLoading: StateFlow<Boolean>

    /** True when the on-device model is unavailable and inference is routed to the cloud API. */
    val isUsingApiFallback: StateFlow<Boolean>

    /**
     * Classify an incident from multimodal input.
     *
     * At least one of [imageBytes], [transcript], or [notes] must be non-null.
     *
     * @param imageBytes  JPEG bytes from CameraX (nullable)
     * @param transcript  STT output text (nullable)
     * @param notes       Free-text notes from field worker (nullable)
     * @param language    Input language for prompt injection
     * @return            [ClassificationResult] — always passes through [ConfidenceGate]
     * @throws            [InferenceException] on model errors
     * @throws            [ModelNotReadyException] if called before [isReady] is true
     */
    suspend fun classify(
        imageBytes: ByteArray? = null,
        transcript: String? = null,
        notes: String? = null,
        language: Language = Language.ENGLISH
    ): ClassificationResult

    /**
     * Warms up the model so it is ready before the first [classify] call.
     * Safe to call multiple times — no-ops if already loaded.
     */
    suspend fun preload()

    /** Release model resources. Call when the app goes to background. */
    fun release()

    // ---- Exception types ----

    class ModelNotReadyException : Exception("Gemma model not yet loaded")
    class InferenceException(message: String, cause: Throwable? = null) :
        Exception(message, cause)
    class InsufficientMemoryException : Exception("Insufficient device memory for inference")
}
