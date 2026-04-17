package com.dimaggi.edgetele.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dimaggi.edgetele.ai.GemmaInferenceEngine
import com.dimaggi.edgetele.data.model.Incident
import com.dimaggi.edgetele.data.repository.IncidentRepository
import com.dimaggi.edgetele.sync.SyncUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: IncidentRepository,
    private val gemmaEngine: GemmaInferenceEngine,
    private val syncUploader: SyncUploader
) : ViewModel() {

    val isModelReady: StateFlow<Boolean> = gemmaEngine.isReady

    val recentIncidents: StateFlow<List<Incident>> = repository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unsyncedCount: StateFlow<Int> = repository
        .observeUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun syncNow() {
        viewModelScope.launch {
            _syncStatus.update { SyncStatus.Syncing }
            val result = syncUploader.syncNow()
            _syncStatus.update {
                when (result) {
                    is SyncUploader.SyncResult.Completed ->
                        SyncStatus.Done("Synced ${result.successCount} records")
                    is SyncUploader.SyncResult.Offline ->
                        SyncStatus.Done("Offline — records saved locally")
                    is SyncUploader.SyncResult.NothingToSync ->
                        SyncStatus.Done("Nothing to sync")
                    is SyncUploader.SyncResult.NoEndpoint ->
                        SyncStatus.Done("No endpoint configured — use Share")
                }
            }
        }
    }

    fun shareAsFile() {
        viewModelScope.launch { syncUploader.shareAsFile() }
    }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        data class Done(val message: String) : SyncStatus()
    }
}
