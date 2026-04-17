package com.dimaggi.edgetele.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.dimaggi.edgetele.ai.ConfidenceGate
import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import com.dimaggi.edgetele.ui.theme.ConfidenceRed
import com.dimaggi.edgetele.ui.theme.ConfidenceYellow

/**
 * Warning banner shown when confidence is below threshold.
 * Per product strategy Section 6.2: shown for YELLOW and RED levels. Never hidden.
 */
@Composable
fun ExpertConfirmationBanner(
    level: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val message = ConfidenceGate.bannerMessage(level) ?: return

    val (backgroundColor, textColor) = when (level) {
        ConfidenceLevel.YELLOW -> Color(0xFFFFF8E1) to ConfidenceYellow
        ConfidenceLevel.RED    -> Color(0xFFFFEBEE) to ConfidenceRed
        ConfidenceLevel.GREEN  -> return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = textColor
        )
        Text(
            text = message,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}
