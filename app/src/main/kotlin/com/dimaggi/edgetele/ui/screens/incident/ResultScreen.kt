package com.dimaggi.edgetele.ui.screens.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dimaggi.edgetele.data.model.Incident
import com.dimaggi.edgetele.data.model.PlaybookAction
import com.dimaggi.edgetele.ui.components.ConfidenceBadge
import com.dimaggi.edgetele.ui.components.DecisionSupportWatermark
import com.dimaggi.edgetele.ui.components.ExpertConfirmationBanner
import com.dimaggi.edgetele.ui.components.PlaybookActionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is ResultUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is ResultUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(state.message) }

            is ResultUiState.Ready -> ResultContent(
                modifier = Modifier.padding(padding),
                incident = state.incident,
                actions = state.actions,
                onNewIncident = onHome
            )
        }
    }
}

@Composable
private fun ResultContent(
    incident: Incident,
    actions: List<PlaybookAction>,
    onNewIncident: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Safety watermark — always shown first
        DecisionSupportWatermark(modifier = Modifier.fillMaxWidth())

        // Expert confirmation banner (shown for YELLOW and RED only)
        ExpertConfirmationBanner(level = incident.confidenceLevel)

        // Classification header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = incident.category.displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeverityChip(severity = incident.severity)
                ConfidenceBadge(
                    confidence = incident.confidence,
                    level = incident.confidenceLevel
                )
            }
        }

        HorizontalDivider()

        // Recommended actions
        if (actions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Recommended Actions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                actions.forEachIndexed { index, action ->
                    PlaybookActionCard(action = action, index = index)
                }
            }
        }

        HorizontalDivider()

        // Incident metadata
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetaRow("Time", formatTimestamp(incident.timestampEpochMs))
            MetaRow("Language", incident.language.displayName)
            if (incident.latitude != null && incident.longitude != null) {
                MetaRow("Location", "%.5f, %.5f".format(incident.latitude, incident.longitude))
            }
            MetaRow("Record ID", incident.id.take(8).uppercase())
            MetaRow(
                "Sync status",
                if (incident.synced) "Synced" else "Pending sync"
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onNewIncident,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("New Incident")
        }
    }
}

@Composable
private fun SeverityChip(severity: Int) {
    val color = when {
        severity >= 4 -> MaterialTheme.colorScheme.error
        severity >= 3 -> MaterialTheme.colorScheme.tertiary
        else          -> MaterialTheme.colorScheme.secondary
    }
    Text(
        text = "Severity $severity/5",
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    )
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Text(text = value, fontSize = 12.sp)
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
}
