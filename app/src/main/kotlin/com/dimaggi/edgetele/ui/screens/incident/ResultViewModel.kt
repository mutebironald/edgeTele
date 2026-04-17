package com.dimaggi.edgetele.ui.screens.incident

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dimaggi.edgetele.data.model.Incident
import com.dimaggi.edgetele.data.model.PlaybookAction
import com.dimaggi.edgetele.data.repository.IncidentRepository
import com.dimaggi.edgetele.data.repository.PlaybookRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: IncidentRepository,
    private val playbookRepository: PlaybookRepository,
    private val gson: Gson
) : ViewModel() {

    private val incidentId: String = checkNotNull(savedStateHandle["incidentId"])

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        loadIncident()
    }

    private fun loadIncident() {
        viewModelScope.launch {
            val incident = repository.getById(incidentId)
            if (incident == null) {
                _uiState.value = ResultUiState.Error("Incident not found")
                return@launch
            }

            val actionsType = object : TypeToken<List<String>>() {}.type
            val actionStrings: List<String> = gson.fromJson(
                incident.recommendedActionsJson,
                actionsType
            ) ?: emptyList()

            val actions = actionStrings.mapIndexed { idx, actionText ->
                PlaybookAction(
                    id = "action_$idx",
                    action = actionText,
                    priority = idx + 1,
                    tags = emptyList()
                )
            }

            _uiState.value = ResultUiState.Ready(incident, actions)
        }
    }
}

sealed class ResultUiState {
    object Loading : ResultUiState()
    data class Ready(val incident: Incident, val actions: List<PlaybookAction>) : ResultUiState()
    data class Error(val message: String) : ResultUiState()
}
