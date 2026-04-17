package com.dimaggi.edgetele.ui.screens.incident

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dimaggi.edgetele.ai.GemmaInferenceEngine
import com.dimaggi.edgetele.audio.SpeechRecognitionManager
import com.dimaggi.edgetele.audio.SpeechRecognitionManager.SttError
import com.dimaggi.edgetele.audio.SpeechRecognitionManager.SttEvent
import com.dimaggi.edgetele.data.model.ClassificationResult
import com.dimaggi.edgetele.data.model.Incident
import com.dimaggi.edgetele.data.model.enums.Language
import com.dimaggi.edgetele.data.repository.PlaybookRepository
import com.dimaggi.edgetele.sync.SyncPacketGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NewIncidentViewModel @Inject constructor(
    private val gemmaEngine: GemmaInferenceEngine,
    private val playbookRepository: PlaybookRepository,
    private val syncPacketGenerator: SyncPacketGenerator,
    private val speechManager: SpeechRecognitionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewIncidentUiState())
    val uiState: StateFlow<NewIncidentUiState> = _uiState.asStateFlow()

    val isModelReady: StateFlow<Boolean> = gemmaEngine.isReady
    val isInferring: StateFlow<Boolean> = gemmaEngine.isLoading
    val isUsingApiFallback: StateFlow<Boolean> = gemmaEngine.isUsingApiFallback
    val isListening: StateFlow<Boolean> = speechManager.isListening
    val isSttAvailable: StateFlow<Boolean> = speechManager.isAvailable

    init {
        observeSttEvents()
    }

    fun setLanguage(language: Language) {
        _uiState.update { it.copy(language = language) }
    }

    fun setPhoto(file: File, bytes: ByteArray) {
        _uiState.update { it.copy(photoFile = file, photoBytes = bytes, photoError = null) }
    }

    fun setNotes(text: String) {
        _uiState.update { it.copy(notes = text) }
    }

    fun setLocation(location: Location) {
        _uiState.update { it.copy(location = location) }
    }

    fun startListening() {
        speechManager.startListening(_uiState.value.language)
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun classifyIncident(responderID: String) {
        val state = _uiState.value
        if (state.photoBytes == null && state.transcript.isBlank() && state.notes.isBlank()) {
            _uiState.update { it.copy(error = "Add a photo, voice note, or text before classifying.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(error = null, isClassifying = true) }
            try {
                val result = gemmaEngine.classify(
                    imageBytes = state.photoBytes,
                    transcript = state.transcript.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    language = state.language
                )
                val actions = playbookRepository.getActions(
                    category = result.category,
                    severity = result.severity,
                    language = state.language
                )
                val incident = syncPacketGenerator.generate(
                    responderID = responderID,
                    classification = result,
                    actions = actions,
                    photoFile = state.photoFile,
                    transcript = state.transcript.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    language = state.language,
                    location = state.location
                )
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        savedIncidentId = incident.id,
                        classificationResult = result
                    )
                }
            } catch (e: Exception) {
                // Tier 3: save incident with fallback classification — field data is never lost
                try {
                    val fallback = ClassificationResult.fallback()
                    val actions = playbookRepository.getActions(
                        category = fallback.category,
                        severity = fallback.severity,
                        language = state.language
                    )
                    val incident = syncPacketGenerator.generate(
                        responderID = responderID,
                        classification = fallback,
                        actions = actions,
                        photoFile = state.photoFile,
                        transcript = state.transcript.ifBlank { null },
                        notes = state.notes.ifBlank { null },
                        language = state.language,
                        location = state.location
                    )
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            savedIncidentId = incident.id,
                            classificationResult = fallback,
                            error = "AI unavailable — incident saved for manual review"
                        )
                    }
                } catch (saveEx: Exception) {
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            error = "Classification failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearSttError() = _uiState.update { it.copy(sttError = null) }

    private fun observeSttEvents() {
        viewModelScope.launch {
            speechManager.events.collect { event ->
                when (event) {
                    is SttEvent.PartialResult -> _uiState.update {
                        it.copy(transcriptPartial = event.text)
                    }
                    is SttEvent.FinalResult   -> _uiState.update {
                        it.copy(
                            transcript = event.text,
                            transcriptPartial = ""
                        )
                    }
                    is SttEvent.Error         -> _uiState.update {
                        it.copy(
                            sttError = event.error,
                            transcriptPartial = ""
                        )
                    }
                    is SttEvent.VolumeChanged -> { /* handled in UI via isListening */ }
                }
            }
        }
    }
}

data class NewIncidentUiState(
    val language: Language = Language.ENGLISH,
    val photoFile: File? = null,
    val photoBytes: ByteArray? = null,
    val photoError: String? = null,
    val transcript: String = "",
    val transcriptPartial: String = "",
    val notes: String = "",
    val location: Location? = null,
    val isClassifying: Boolean = false,
    val classificationResult: ClassificationResult? = null,
    val savedIncidentId: String? = null,
    val error: String? = null,
    val sttError: SttError? = null
)
