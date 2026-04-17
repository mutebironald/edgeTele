package com.dimaggi.edgetele.di

import android.content.Context
import androidx.room.Room
import com.dimaggi.edgetele.data.db.EdgeTeleDatabase
import com.dimaggi.edgetele.data.db.IncidentDao
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Note: AiModule (GemmaInferenceEngine binding) lives in src/debug/ and src/release/
// so each build type can swap the real vs. mock implementation.

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EdgeTeleDatabase =
        Room.databaseBuilder(
            context,
            EdgeTeleDatabase::class.java,
            EdgeTeleDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideIncidentDao(db: EdgeTeleDatabase): IncidentDao = db.incidentDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .serializeNulls()
        .create()
}
