package com.dimaggi.edgetele.ui.screens.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dimaggi.edgetele.data.model.Incident
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class IncidentLogViewModel @Inject constructor(
    repository: IncidentRepository
) : ViewModel() {

    private val _filter = MutableStateFlow<LogFilter>(LogFilter.All)
    val filter: StateFlow<LogFilter> = _filter

    private val allIncidents = repository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incidents: StateFlow<List<Incident>> = combine(allIncidents, _filter) { list, f ->
        when (f) {
            LogFilter.All           -> list
            LogFilter.PendingSync   -> list.filter { !it.synced }
            is LogFilter.ByCategory -> list.filter { it.category == f.category }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = allIncidents
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val unsyncedCount: StateFlow<Int> = allIncidents
        .map { list -> list.count { !it.synced } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setFilter(f: LogFilter) = _filter.update { f }

    sealed class LogFilter {
        object All : LogFilter()
        object PendingSync : LogFilter()
        data class ByCategory(val category: IncidentCategory) : LogFilter()
    }
}
