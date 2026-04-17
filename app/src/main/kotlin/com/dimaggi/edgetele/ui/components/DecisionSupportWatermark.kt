package com.dimaggi.edgetele.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Decision support for trained responders" label.
 * Must appear on every recommendation screen. Non-negotiable per product strategy Section 6.2.
 */
@Composable
fun DecisionSupportWatermark(modifier: Modifier = Modifier) {
    val borderColor = Color(0xFF526350)
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Decision support for trained responders",
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            color = borderColor.copy(alpha = 0.8f)
        )
    }
}
