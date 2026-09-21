package com.example.seefix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.ui.theme.SafetyStatusCaution
import com.example.seefix.ui.theme.SafetyStatusDangerous
import com.example.seefix.ui.theme.SafetyStatusSafe

@Composable
fun StatusBadge(
    safetyStatus: SafetyStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, icon, label) = when (safetyStatus.level) {
        SafetyLevel.SAFE -> Quadruple(
            SafetyStatusSafe.copy(alpha = 0.15f),
            SafetyStatusSafe,
            Icons.Rounded.CheckCircle,
            "SAFE"
        )
        SafetyLevel.CAUTION -> Quadruple(
            SafetyStatusCaution.copy(alpha = 0.15f),
            SafetyStatusCaution,
            Icons.Rounded.WarningAmber,
            "CAUTION"
        )
        SafetyLevel.DANGEROUS_STOP -> Quadruple(
            SafetyStatusDangerous.copy(alpha = 0.2f),
            SafetyStatusDangerous,
            Icons.Rounded.Warning,
            "DANGEROUS STOP"
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.dp, contentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
