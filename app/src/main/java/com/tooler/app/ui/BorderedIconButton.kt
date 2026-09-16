package com.tooler.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Compact icon action with a quiet Nord outline and an obvious disabled state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorderedIconButton(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
        ),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            contentColor = color,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        modifier = modifier
    ) {
        Icon(painterResource(iconRes), contentDescription = contentDescription)
    }
}
