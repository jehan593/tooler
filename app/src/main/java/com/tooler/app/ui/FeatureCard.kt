package com.tooler.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tooler.app.ui.theme.nord0
import com.tooler.app.ui.theme.nord13
import com.tooler.app.ui.theme.nord14

/** Status chip tone — Nord green (good), yellow (needs action), or theme frost. */
enum class StatusTone { NEUTRAL, SUCCESS, WARNING }

/**
 * Simple status + one-action card, shared by the extra rows in MainActivity and QsTilesActivity's
 * Shizuku banner. [TileCard] is the richer variant with a permission row. [accented] paints the
 * card in the theme's secondary container so the one genuinely different kind of feature stands out.
 */
@Composable
fun FeatureCard(
    title: String,
    status: String?,
    description: String,
    iconRes: Int? = null,
    statusTone: StatusTone = StatusTone.NEUTRAL,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accented: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val card = if (accented) {
        CardDefaults.cardColors(
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(
        colors = card,
        border = if (accented) BorderStroke(1.dp, colorScheme.secondary) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (accented) colorScheme.secondary else colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (accented) {
                    colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                } else {
                    colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(top = 14.dp))
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status != null) {
                    StatusChip(status, statusTone)
                    Spacer(Modifier.weight(1f))
                }
                if (actionLabel != null && onAction != null) {
                    OutlinedButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (status == null) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A small pill-shaped status label for a card's footer. */
@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val color =
        when (tone) {
            StatusTone.SUCCESS -> nord14
            StatusTone.WARNING -> nord13
            StatusTone.NEUTRAL -> MaterialTheme.colorScheme.primary
        }
    Surface(
        color = if (dark) color.copy(alpha = 0.16f) else color,
        contentColor = if (dark) color else nord0,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}