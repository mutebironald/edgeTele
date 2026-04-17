package com.dimaggi.edgetele.ui.screens.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import com.dimaggi.edgetele.ui.theme.ConfidenceGreen
import com.dimaggi.edgetele.ui.theme.ConfidenceRed
import com.dimaggi.edgetele.ui.theme.ConfidenceYellow
import com.dimaggi.edgetele.ui.theme.EdgeTeleGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    responderID: String,
    onNewIncident: () -> Unit,
    onViewLog: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val isModelReady by viewModel.isModelReady.collectAsStateWithLifecycle()
    val recentIncidents by viewModel.recentIncidents.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Edge-Tele",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Offline incident copilot",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EdgeTeleGreen
                ),
                actions = {
                    // Share unsynced records
                    IconButton(onClick = viewModel::shareAsFile) {
                        Icon(Icons.Default.Share, contentDescription = "Export", tint = Color.White)
                    }
                    // Sync now
                    IconButton(onClick = viewModel::syncNow) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync", tint = Color.White)
                    }
                    // View full log
                    IconButton(onClick = onViewLog) {
                        Icon(Icons.Default.List, contentDescription = "Log", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewIncident,
                containerColor = EdgeTeleGreen
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Incident", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Status bar
            item {
                StatusBar(
                    isModelReady = isModelReady,
                    unsyncedCount = unsyncedCount,
                    syncStatus = syncStatus
                )
            }

            // Model loading indicator
            if (!isModelReady) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Loading Gemma 4 model...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Recent incidents header
            if (recentIncidents.isNotEmpty()) {
                item {
                    Text(
                        "Recent incidents",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                items(recentIncidents.take(10)) { incident ->
                    IncidentSummaryCard(incident = incident)
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "No incidents yet",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                "Tap + to report a new incident",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StatusBar(
    isModelReady: Boolean,
    unsyncedCount: Int,
    syncStatus: HomeViewModel.SyncStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Offline indicator
        StatusChip(
            label = "Offline mode",
            color = EdgeTeleGreen,
            modifier = Modifier.weight(1f)
        )

        // Unsynced count
        if (unsyncedCount > 0) {
            StatusChip(
                label = "$unsyncedCount pending sync",
                color = ConfidenceYellow,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (syncStatus is HomeViewModel.SyncStatus.Done) {
        Text(
            text = syncStatus.message,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun StatusChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun IncidentSummaryCard(incident: Incident) {
    val levelColor = when (incident.confidenceLevel) {
        ConfidenceLevel.GREEN  -> ConfidenceGreen
        ConfidenceLevel.YELLOW -> ConfidenceYellow
        ConfidenceLevel.RED    -> ConfidenceRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    incident.category.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    formatTimestamp(incident.timestampEpochMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Sev ${incident.severity}/5",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(levelColor)
                )
                if (!incident.synced) {
                    Text(
                        "Pending",
                        fontSize = 10.sp,
                        color = ConfidenceYellow
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
}
