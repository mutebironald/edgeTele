package com.dimaggi.edgetele.di

import com.dimaggi.edgetele.ai.GemmaInferenceEngine
import com.dimaggi.edgetele.ai.GemmaInferenceEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Release builds use on-device inference (MediaPipe LiteRT) — no network during inference.
// Requires the Gemma model file pushed to device before first launch:
//   adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/com.dimaggi.edgetele/files/
// When the model file is absent, GemmaInferenceEngineImpl falls back to the API
// engine automatically so the app remains functional for demo without the file.
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindGemmaEngine(
        impl: GemmaInferenceEngineImpl
    ): GemmaInferenceEngine
}
