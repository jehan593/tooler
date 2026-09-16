package com.tooler.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** A Settings-screen card for one non-runtime-grantable permission: live granted state, description,
 *  and the action that moves it forward. Status chip bottom-left, action button bottom-right. */
@Composable
fun PermissionCard(
    title: String,
    iconRes: Int,
    granted: Boolean,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actions: List<TilePermissionAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            HorizontalDivider(
                color = colorScheme.outline.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    if (granted) "Granted" else "Not granted",
                    if (granted) StatusTone.SUCCESS else StatusTone.WARNING
                )
                Spacer(Modifier.weight(1f))
                if (!granted && actionLabel != null && onAction != null) {
                    OutlinedButton(
                        onClick = onAction,
                        border = BorderStroke(1.dp, colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary)
                    ) {
                        Text(actionLabel)
                    }
                }
            }
            if (!granted && actions.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    actions.forEach { action ->
                        if (action.primary) {
                            Button(
                                onClick = action.onClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(action.label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = action.onClick,
                                border = BorderStroke(1.dp, colorScheme.outline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}
