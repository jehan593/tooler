package com.tooler.app.ui

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
import androidx.compose.material3.Card
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
import com.tooler.app.R
import com.tooler.app.ui.theme.nord13

/** A one-time setup permission a tile depends on, shown inside [TileCard] only while not [granted];
 *  [actions] are the buttons that move setup forward (the [TilePermissionAction.primary] one draws filled). */
data class TilePermission(
    val label: String,
    val description: String,
    val granted: Boolean,
    val actions: List<TilePermissionAction> = emptyList(),
)

/** One button in a tile card's footer. [primary] draws it filled; secondary ones are outlined. */
data class TilePermissionAction(
    val label: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
)

/** One button in a tile card's footer. [primary] draws it filled; secondary ones are outlined. */
data class TileAction(
    val label: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
)

/** The standard tile row: icon + title, description, an optional permission strip when setup is
 *  missing, and a footer of StatusChip (left), actions, and the add button (right). The add icon
 *  greys out once [added] is true. */
@Composable
fun TileCard(
    title: String,
    iconRes: Int,
    status: String,
    statusTone: StatusTone = StatusTone.NEUTRAL,
    description: String,
    permission: TilePermission? = null,
    addIcon: Int? = null,
    addDescription: String? = null,
    added: Boolean = false,
    onAdd: (() -> Unit)? = null,
    actions: List<TileAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (permission != null && !permission.granted) {
                PermissionRow(permission)
            }
            HorizontalDivider(modifier = Modifier.padding(top = 14.dp))
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(status, statusTone)
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    actions.forEach { action ->
                        if (action.primary) {
                            Button(onClick = action.onClick) { Text(action.label) }
                        } else {
                            OutlinedButton(onClick = action.onClick) { Text(action.label) }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (addIcon != null) {
                    BorderedIconButton(
                        iconRes = addIcon,
                        contentDescription = addDescription,
                        onClick = { onAdd?.invoke() },
                        enabled = onAdd != null && !added
                    )
                }
            }
        }
    }
}

/** The "Setup needed" strip — a tinted rounded block with icon + label + description, and the
 *  grant action on its own row below so a long description never squeezes the button. */
@Composable
fun PermissionRow(permission: TilePermission, modifier: Modifier = Modifier) {
    if (permission.granted) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_custom_security),
                    contentDescription = null,
                    tint = nord13,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(permission.label, style = MaterialTheme.typography.titleSmall)
                    if (permission.description.isNotBlank()) {
                        Text(
                            permission.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (permission.actions.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    permission.actions.forEach { action ->
                        if (action.primary) {
                            Button(onClick = action.onClick, modifier = Modifier.fillMaxWidth()) {
                                Text(action.label)
                            }
                        } else {
                            OutlinedButton(onClick = action.onClick, modifier = Modifier.fillMaxWidth()) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}