package com.dimaggi.edgetele.di

import com.dimaggi.edgetele.ai.GemmaInferenceEngine
import com.dimaggi.edgetele.ai.MockGemmaInferenceEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindGemmaEngine(
        impl: MockGemmaInferenceEngineImpl
    ): GemmaInferenceEngine
}
