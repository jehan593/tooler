package com.tooler.app.customtiles

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tooler.app.R
import com.tooler.app.ui.StatusTone
import com.tooler.app.ui.FeatureCard
import com.tooler.app.ui.theme.ToolerTheme
import com.tooler.app.util.ShizukuUtils
import rikka.shizuku.Shizuku

/**
 * Management screen for the ten user-created custom tiles — Tooler's take on aShellYou's
 * `TileDashBoardScreen`/`CreateTileScreen` pair (one list screen instead of two, since this app
 * deliberately has no navigation). Each row is one of the ten fixed slots: an empty slot offers
 * "Set up", a configured one shows its title/command and opens the same dialog for editing, with
 * the system "add tile to Quick Settings" prompt fired on every save (see
 * [TileComponentManager.promptAddTile]).
 *
 * The Shizuku gate mirrors aShellYou's dashboard cards: the slot list itself stays usable, but a
 * banner explains what's missing when Shizuku isn't running or shell access isn't granted — custom
 * tiles are pure Shizuku features, they only execute privileged commands.
 */
class QsTilesActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ToolerTheme {
                var shizukuAvailable by remember { mutableStateOf(ShizukuUtils.isAvailable()) }
                var shizukuGranted by remember { mutableStateOf(ShizukuUtils.isGranted()) }
                var tiles by remember { mutableStateOf(CustomTilePrefs.all(this)) }
                var editingSlot by remember { mutableStateOf<Int?>(null) }

                // Keeps the banner + grant button in sync the moment the grant dialog closes, and
                // re-reads everything on return to the screen the same way MainActivity does.
                DisposableEffect(Unit) {
                    val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
                        if (requestCode == ShizukuUtils.REQUEST_CODE) {
                            shizukuGranted = ShizukuUtils.isGranted()
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(permissionListener)

                    val lifecycleObserver = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            tiles = CustomTilePrefs.all(this@QsTilesActivity)
                            shizukuAvailable = ShizukuUtils.isAvailable()
                            shizukuGranted = ShizukuUtils.isGranted()
                        }
                    }
                    lifecycle.addObserver(lifecycleObserver)

                    onDispose {
                        Shizuku.removeRequestPermissionResultListener(permissionListener)
                        lifecycle.removeObserver(lifecycleObserver)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Custom tiles") },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        LazyColumn(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    "Up to 10 custom tiles that run a shell command through Shizuku — " +
                                        "one-shot or on/off, each with its own icon and label. Once set up, " +
                                        "it works like any other tile in the panel.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            item {
                                when {
                                    !shizukuAvailable -> ShizukuStatusCard(
                                        status = "Shizuku not running",
                                        description = "Custom tiles need the Shizuku app running with access " +
                                            "granted. You can still set up slots below in the meantime.",
                                        actionLabel = "Open Shizuku",
                                        onAction = { openShizuku() }
                                    )
                                    !shizukuGranted -> ShizukuStatusCard(
                                        status = "Setup needed",
                                        description = "Shizuku is running, but Tooler needs your permission to " +
                                            "use it. Grant access below — it also unlocks the Lock Quick " +
                                            "Settings tile.",
                                        actionLabel = "Grant access",
                                        onAction = { ShizukuUtils.requestPermission() }
                                    )
                                }
                            }
                            items(CUSTOM_TILE_SLOT_COUNT) { slotIndex ->
                                val config = tiles.find { it.slotIndex == slotIndex }
                                if (config == null) {
                                    EmptySlotCard(slotNumber = slotIndex + 1) { editingSlot = slotIndex }
                                } else {
                                    ConfiguredSlotCard(
                                        config = config,
                                        onEdit = { editingSlot = slotIndex },
                                        onDelete = {
                                            CustomTilePrefs.delete(this@QsTilesActivity, slotIndex)
                                            TileComponentManager.refreshTile(this@QsTilesActivity, slotIndex)
                                            tiles = CustomTilePrefs.all(this@QsTilesActivity)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                editingSlot?.let { slotIndex ->
                    val existing = tiles.find { it.slotIndex == slotIndex }
                    val isNew = existing == null
                    TileEditorDialog(
                        slotIndex = slotIndex,
                        initial = existing,
                        onDismiss = { editingSlot = null },
                        onSave = { config ->
                            CustomTilePrefs.save(this, config)
                            TileComponentManager.setComponentEnabled(this, slotIndex, true)
                            TileComponentManager.promptAddTile(
                                this,
                                slotIndex,
                                config.name,
                                CustomTileIcons.res(config.iconId)
                            )
                            TileComponentManager.refreshTile(this, slotIndex)
                            editingSlot = null
                            tiles = CustomTilePrefs.all(this)
                        },
                        onDelete = if (isNew) null else {
                            {
                                CustomTilePrefs.delete(this, slotIndex)
                                TileComponentManager.refreshTile(this, slotIndex)
                                editingSlot = null
                                tiles = CustomTilePrefs.all(this)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun openShizuku() {
        val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
        }
    }
}

@Composable
private fun ShizukuStatusCard(
    status: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    FeatureCard(
        title = "Shizuku",
        status = status,
        statusTone = StatusTone.WARNING,
        iconRes = R.drawable.ic_terminal,
        description = description,
        actionLabel = actionLabel,
        onAction = onAction
    )
}

@Composable
private fun EmptySlotCard(slotNumber: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_terminal),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Tile $slotNumber", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap to set up a custom tile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConfiguredSlotCard(
    config: CustomTileConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(CustomTileIcons.res(config.iconId)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    config.name.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                when {
                    config.isToggleable ->
                        "Toggle — on: \"${config.onCommand.ifBlank { "—" }}\" off: \"${config.offCommand.ifBlank { "—" }}\""
                    else -> "Tap action — \"${config.onCommand.ifBlank { "—" }}\""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(
                    onClick = {
                        onDelete()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/**
 * The create/edit dialog for one slot — the form descendant of aShellYou's `CreateTileScreen`.
 * Fields map 1-to-1 onto [CustomTileConfig]: name, on/off-toggle switch, initial state, the one or
 * two commands, subtitles, and an icon picker. Editing an existing tile pre-fills everything from
 * [initial].
 *
 * Every text field holds its value as a `TextFieldValue` (not a bare `String`) deliberately:
 * Compose's `AlertDialog` + String-overload text fields is the long-standing AndroidX cursor bug
 * (issuetracker 169602175) where the cursor can't be moved once the field is focused. The
 * `TextFieldValue` overload carries the selection in state, which is the documented workaround —
 * same reason aShellYou's create form stores its command/subtitle values as `TextFieldValue`.
 * The command/subtitle fields are additionally *not* `singleLine`: a wrapping field keeps every
 * character visible (or vertically scrollable), so the selection handle can always reach any part
 * of a long command instead of hitting the invisible edge of a horizontally-clipped field.
 */
@Composable
private fun TileEditorDialog(
    slotIndex: Int,
    initial: CustomTileConfig?,
    onDismiss: () -> Unit,
    onSave: (CustomTileConfig) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var nameField by remember(initial) { mutableStateOf(TextFieldValue(initial?.name ?: "")) }
    var isToggleable by remember(initial) { mutableStateOf(initial?.isToggleable ?: false) }
    var initialState by remember(initial) { mutableStateOf(initial?.isActive ?: false) }
    var onCommand by remember(initial) { mutableStateOf(TextFieldValue(initial?.onCommand ?: "")) }
    var offCommand by remember(initial) { mutableStateOf(TextFieldValue(initial?.offCommand ?: "")) }
    var onSubtitle by remember(initial) { mutableStateOf(TextFieldValue(initial?.onSubtitle ?: "On")) }
    var offSubtitle by remember(initial) { mutableStateOf(TextFieldValue(initial?.offSubtitle ?: "Off")) }
    var selectedIcon by remember(initial) { mutableStateOf(initial?.iconId ?: CustomTileIcons.DEFAULT_ID) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Set up Tile ${slotIndex + 1}" else "Edit Tile ${slotIndex + 1}") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("Tile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // aShellYou's "is_toggleable" switch.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On/off toggle", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Two commands; tapping switches between them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isToggleable, onCheckedChange = { isToggleable = it })
                }

                // aShellYou's "Initial State" switch.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Initial state", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Whether the tile starts on. For on/off tiles it changes with each tap; " +
                                "for one-shot tiles it stays fixed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = initialState, onCheckedChange = { initialState = it })
                }

                if (isToggleable) {
                    OutlinedTextField(
                        value = onCommand,
                        onValueChange = { onCommand = it },
                        label = { Text("Command to turn ON") },
                        // Not singleLine: a wrapped field never clips text off-screen horizontally,
                        // so the selection handle can always reach every character (see the dialog
                        // doc comment for why that matters).
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = offCommand,
                        onValueChange = { offCommand = it },
                        label = { Text("Command to turn OFF") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = onCommand,
                        onValueChange = { onCommand = it },
                        label = { Text("Command") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Subtitle", style = MaterialTheme.typography.bodyMedium)
                if (isToggleable) {
                    OutlinedTextField(
                        value = onSubtitle,
                        onValueChange = { onSubtitle = it },
                        label = { Text("Subtitle when on") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = offSubtitle,
                        onValueChange = { offSubtitle = it },
                        label = { Text("Subtitle when off") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = onSubtitle,
                        onValueChange = { onSubtitle = it },
                        label = { Text("Subtitle") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Icon", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CustomTileIcons.icons, key = { it.id }) { entry ->
                        FilterChip(
                            selected = selectedIcon == entry.id,
                            onClick = { selectedIcon = entry.id },
                            label = { Text(entry.label) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(CustomTileIcons.res(entry.id)),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CustomTileConfig(
                            slotIndex = slotIndex,
                            name = nameField.text,
                            iconId = selectedIcon,
                            isToggleable = isToggleable,
                            isActive = initialState,
                            onCommand = onCommand.text,
                            // aShellYou discards the inactive command for static tiles; same here.
                            offCommand = if (isToggleable) offCommand.text else "",
                            onSubtitle = onSubtitle.text,
                            // For static tiles the on-subtitle is shown either way (aShellYou sets
                            // inactiveTileSubtitle = activeTileSubtitle); mirror it so the subtitle
                            // never reflects a state the tile can't reach.
                            offSubtitle = if (isToggleable) offSubtitle.text else onSubtitle.text,
                        )
                    )
                },
                enabled = nameField.text.isNotBlank() && onCommand.text.isNotBlank()
            ) {
                Text("Save & add to panel")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}