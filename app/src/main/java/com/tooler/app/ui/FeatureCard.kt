package com.tooler.app.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/** Semantic tone for a status chip, mapped onto Nord's aurora colors (green = good, yellow = needs
 *  action, otherwise the theme's primary frost blue). */
enum class StatusTone { NEUTRAL, SUCCESS, WARNING }

/**
 * One tile's status + optional action, shared by all the rows in [com.tooler.app.MainActivity].
 * Renders as a leading tile icon, title, a color-coded status chip, description, and a filled
 * action button — the chip and the filled button are deliberately unmistakable against the rest of
 * the text (the earlier plain-status + TextButton layout read as nothing but paragraphs).
 */
@Composable
fun FeatureCard(
    title: String,
    status: String,
    description: String,
    iconRes: Int? = null,
    statusTone: StatusTone = StatusTone.NEUTRAL,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            StatusChip(status, statusTone, Modifier.padding(top = 8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
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