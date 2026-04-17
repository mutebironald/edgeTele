package com.dimaggi.edgetele

import android.app.Application
import android.util.Log
import com.dimaggi.edgetele.ai.GemmaInferenceEngine
import com.dimaggi.edgetele.ai.ModelInstaller
import com.dimaggi.edgetele.sync.SyncUploader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EdgeTeleApp : Application() {

    @Inject lateinit var syncUploader: SyncUploader
    @Inject lateinit var modelInstaller: ModelInstaller
    @Inject lateinit var gemmaEngine: GemmaInferenceEngine

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        syncUploader.schedulePeriodicSync()
        appScope.launch {
            val result = modelInstaller.installFromExternalIfPresent()
            if (result is ModelInstaller.InstallResult.NotFound) {
                Log.i("EdgeTeleApp", "Gemma model not found. Push it with:\n" +
                    "adb push gemma-2b-it-gpu-int4.bin /sdcard/Android/data/${packageName}/files/")
            }
            // Warm up the model immediately so it is ready before the first incident
            gemmaEngine.preload()
        }
    }
}
