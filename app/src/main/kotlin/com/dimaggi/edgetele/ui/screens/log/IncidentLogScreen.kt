package com.dimaggi.edgetele.ui.screens.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dimaggi.edgetele.data.model.Incident
import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.ui.components.ConfidenceBadge
import com.dimaggi.edgetele.ui.theme.ConfidenceGreen
import com.dimaggi.edgetele.ui.theme.ConfidenceRed
import com.dimaggi.edgetele.ui.theme.ConfidenceYellow
import com.dimaggi.edgetele.ui.theme.EdgeTeleGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentLogScreen(
    onBack: () -> Unit,
    onOpenIncident: (String) -> Unit,
    viewModel: IncidentLogViewModel = hiltViewModel()
) {
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()
    val currentFilter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Incident Log", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "$totalCount total · $unsyncedCount pending sync",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EdgeTeleGreen)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Filter chips
            item {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        LogFilterChip(
                            label = "All",
                            selected = currentFilter is IncidentLogViewModel.LogFilter.All,
                            onClick = { viewModel.setFilter(IncidentLogViewModel.LogFilter.All) }
                        )
                    }
                    item {
                        LogFilterChip(
                            label = "Pending Sync",
                            selected = currentFilter is IncidentLogViewModel.LogFilter.PendingSync,
                            onClick = { viewModel.setFilter(IncidentLogViewModel.LogFilter.PendingSync) }
                        )
                    }
                    items(IncidentCategory.entries) { category ->
                        LogFilterChip(
                            label = category.displayName,
                            selected = currentFilter is IncidentLogViewModel.LogFilter.ByCategory &&
                                    (currentFilter as IncidentLogViewModel.LogFilter.ByCategory).category == category,
                            onClick = {
                                viewModel.setFilter(IncidentLogViewModel.LogFilter.ByCategory(category))
                            }
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            if (incidents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No incidents match this filter",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                items(incidents, key = { it.id }) { incident ->
                    IncidentLogCard(
                        incident = incident,
                        onClick = { onOpenIncident(incident.id) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EdgeTeleGreen.copy(alpha = 0.15f),
            selectedLabelColor = EdgeTeleGreen
        )
    )
}

@Composable
private fun IncidentLogCard(incident: Incident, onClick: () -> Unit) {
    val levelColor = when (incident.confidenceLevel) {
        ConfidenceLevel.GREEN  -> ConfidenceGreen
        ConfidenceLevel.YELLOW -> ConfidenceYellow
        ConfidenceLevel.RED    -> ConfidenceRed
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        incident.category.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        formatTimestamp(incident.timestampEpochMs),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                // Severity pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(levelColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Sev ${incident.severity}/5",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = levelColor
                    )
                }
            }

            ConfidenceBadge(confidence = incident.confidence, level = incident.confidenceLevel)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sync status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (incident.synced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (incident.synced)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            ConfidenceYellow
                    )
                    Text(
                        if (incident.synced) "Synced" else "Pending sync",
                        fontSize = 11.sp,
                        color = if (incident.synced)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            ConfidenceYellow
                    )
                }

                // GPS if available
                if (incident.latitude != null && incident.longitude != null) {
                    Text(
                        "%.4f, %.4f".format(incident.latitude, incident.longitude),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // Language
                Text(
                    incident.language.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val fmt = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
}
