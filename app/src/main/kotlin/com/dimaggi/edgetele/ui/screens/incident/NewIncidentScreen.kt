package com.dimaggi.edgetele.ui.screens.incident

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dimaggi.edgetele.audio.SpeechRecognitionManager
import com.dimaggi.edgetele.data.model.enums.Language
import com.dimaggi.edgetele.ui.theme.ConfidenceRed
import com.dimaggi.edgetele.ui.theme.EdgeTeleGreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewIncidentScreen(
    responderID: String,
    onBack: () -> Unit,
    onClassified: (incidentId: String) -> Unit,
    viewModel: NewIncidentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isInferring by viewModel.isInferring.collectAsStateWithLifecycle()
    val isUsingApiFallback by viewModel.isUsingApiFallback.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSttAvailable by viewModel.isSttAvailable.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // GPS — request permission and start location updates
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> locationGranted = granted }

    LaunchedEffect(Unit) {
        if (!locationGranted) locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    DisposableEffect(locationGranted) {
        if (!locationGranted) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        lastKnown?.let { viewModel.setLocation(it) }
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) = viewModel.setLocation(loc)
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
        }
        try {
            @Suppress("MissingPermission")
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5_000L, 0f, listener, Looper.getMainLooper()
            )
        } catch (_: Exception) {}
        onDispose { lm.removeUpdates(listener) }
    }

    LaunchedEffect(uiState.savedIncidentId) {
        uiState.savedIncidentId?.let { onClassified(it) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.sttError) {
        uiState.sttError?.let { err ->
            val isPackMissing = err == SpeechRecognitionManager.SttError.LANGUAGE_PACK_MISSING
            val msg = when (err) {
                SpeechRecognitionManager.SttError.RECOGNITION_UNAVAILABLE ->
                    "Speech recognition not available on this device"
                SpeechRecognitionManager.SttError.MICROPHONE_UNAVAILABLE ->
                    "Microphone unavailable — check permissions"
                SpeechRecognitionManager.SttError.LANGUAGE_NOT_SUPPORTED ->
                    "Language not supported for speech — try English"
                SpeechRecognitionManager.SttError.LANGUAGE_PACK_MISSING ->
                    "Speech pack missing. Tap Download → profile photo → Settings → Voice → Offline speech recognition → English"
                SpeechRecognitionManager.SttError.TIMEOUT ->
                    "No speech detected — tap mic and speak"
                SpeechRecognitionManager.SttError.RECOGNITION_FAILED ->
                    "Speech recognition failed — check internet connection"
            }
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (isPackMissing) "Download" else null,
                duration = if (isPackMissing) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (isPackMissing && result == SnackbarResult.ActionPerformed) {
                // AiAi language pack settings — look for "Speech" or "Language" section
                val aiaiSettings = Intent().apply {
                    component = android.content.ComponentName(
                        "com.google.android.as",
                        "com.google.android.apps.miphone.aiai.settings.ui.user.AsiSettingsActivity"
                    )
                }
                val intent = if (aiaiSettings.resolveActivity(context.packageManager) != null)
                    aiaiSettings
                else
                    Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                context.startActivity(intent)
            }
            viewModel.clearSttError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Incident") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InferenceModeChip(isUsingApiFallback = isUsingApiFallback)

            LanguageSelector(selected = uiState.language, onSelected = viewModel::setLanguage)

            PhotoSection(
                photoBytes = uiState.photoBytes,
                onPhotoCaptured = viewModel::setPhoto
            )

            if (isSttAvailable) {
                VoiceSection(
                    isListening = isListening,
                    transcript = uiState.transcript,
                    partialTranscript = uiState.transcriptPartial,
                    onStartListening = viewModel::startListening,
                    onStopListening = viewModel::stopListening
                )
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::setNotes,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Additional notes (optional)") },
                minLines = 3
            )

            // Location status
            uiState.location?.let { loc ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = EdgeTeleGreen
                    )
                    Text(
                        "GPS: %.5f, %.5f".format(loc.latitude, loc.longitude),
                        fontSize = 11.sp,
                        color = EdgeTeleGreen
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.classifyIncident(responderID) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isClassifying && (uiState.photoBytes != null
                        || uiState.transcript.isNotBlank()
                        || uiState.notes.isNotBlank())
            ) {
                if (uiState.isClassifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Classify Incident", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InferenceModeChip(isUsingApiFallback: Boolean) {
    val label = if (isUsingApiFallback) "🌐 API Mode" else "📵 Offline Mode"
    val color = if (isUsingApiFallback) MaterialTheme.colorScheme.tertiary else EdgeTeleGreen
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LanguageSelector(selected: Language, onSelected: (Language) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { expanded = true }) {
            Text("Language: ${selected.displayName}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Language.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text("${lang.displayName} (${lang.nativeName})") },
                    onClick = { onSelected(lang); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photoBytes: ByteArray?,
    onPhotoCaptured: (File, ByteArray) -> Unit
) {
    val context = LocalContext.current
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var captureFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = captureUri ?: return@rememberLauncherForActivityResult
            val file = captureFile ?: return@rememberLauncherForActivityResult
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
            onPhotoCaptured(file, bytes)
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Write to filesDir (persistent) not cacheDir (evictable by Android)
            val dir = File(context.filesDir, "photos").also { it.mkdirs() }
            val file = File(dir, "incident_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            captureFile = file
            captureUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { cameraPermission.launch(Manifest.permission.CAMERA) }
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (photoBytes != null) {
                AsyncImage(
                    model = photoBytes,
                    contentDescription = "Incident photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Tap to take photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VoiceSection(
    isListening: Boolean,
    transcript: String,
    partialTranscript: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onStartListening() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice description", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isListening) ConfidenceRed else MaterialTheme.colorScheme.primary)
                    .clickable {
                        if (isListening) onStopListening()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.RadioButtonChecked else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop" else "Record",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column {
                if (isListening && partialTranscript.isNotBlank()) {
                    Text(
                        text = partialTranscript,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
                if (transcript.isNotBlank()) {
                    Text(text = transcript, fontSize = 13.sp)
                } else if (!isListening) {
                    Text(
                        text = "Tap mic to record",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
