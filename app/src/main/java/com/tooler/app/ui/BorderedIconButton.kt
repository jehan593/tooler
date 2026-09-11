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

/** A circular, theme-bordered icon button — the app's standard small round action (add/edit/delete).
 *  Border and glyph share one [color]; a disabled button greys both out so "already done" reads itself. */
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
            if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = color),
        modifier = modifier
    ) {
        Icon(painterResource(iconRes), contentDescription = contentDescription)
    }
}