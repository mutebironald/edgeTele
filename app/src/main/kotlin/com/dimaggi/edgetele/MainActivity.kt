package com.dimaggi.edgetele

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dimaggi.edgetele.ui.navigation.EdgeTeleNavGraph
import com.dimaggi.edgetele.ui.theme.EdgeTeleTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Responder ID persisted in SharedPreferences.
    // In a real deployment this comes from a login/profile screen.
    private val responderID: String by lazy {
        getSharedPreferences("edge_tele_prefs", MODE_PRIVATE)
            .let { prefs ->
                prefs.getString("responder_id", null)
                    ?: UUID.randomUUID().toString().take(8).uppercase()
                        .also { id -> prefs.edit().putString("responder_id", id).apply() }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdgeTeleTheme {
                EdgeTeleNavGraph(responderID = responderID)
            }
        }
    }
}
