package com.dimaggi.edgetele.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import com.dimaggi.edgetele.ui.theme.ConfidenceGreen
import com.dimaggi.edgetele.ui.theme.ConfidenceRed
import com.dimaggi.edgetele.ui.theme.ConfidenceYellow

/**
 * Confidence badge shown on every classification and recommendation screen.
 * Non-negotiable safety UI element per Section 6.2 of the product strategy.
 */
@Composable
fun ConfidenceBadge(
    confidence: Float,
    level: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val (color, icon, label) = when (level) {
        ConfidenceLevel.GREEN  -> Triple(
            ConfidenceGreen,
            Icons.Default.CheckCircle,
            "High"
        )
        ConfidenceLevel.YELLOW -> Triple(
            ConfidenceYellow,
            Icons.Default.Warning,
            "Low"
        )
        ConfidenceLevel.RED    -> Triple(
            ConfidenceRed,
            Icons.Default.Error,
            "Very Low"
        )
    }

    val confidencePct = (confidence * 100).toInt()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "$label confidence ($confidencePct%)",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
