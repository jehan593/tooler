package com.tooler.app.customtiles

import android.app.StatusBarManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.tooler.app.R
import com.tooler.app.ui.theme.ToolerTheme
import com.tooler.app.util.StatusNotifier
import com.tooler.app.util.TilePanelPrefs

/**
 * Create/edit screen for one custom tile slot — a full view with a back arrow (not a dialog).
 * Saving persists the config, re-enables the slot, and for tiles not on the panel yet fires the
 * system "Add tile to Quick Settings?" prompt (API 33+); below that the enabled component just
 * appears in the QS editor.
 */
class TileEditorActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, 0)
        val inPanel = TilePanelPrefs.isInPanel(
            applicationContext,
            TileComponentManager.componentName(this, slotIndex)
        )

        val save: (CustomTileConfig) -> Unit = { config ->
            CustomTilePrefs.save(this, config)
            TileComponentManager.setComponentEnabled(this, config.slotIndex, true)
            val component = TileComponentManager.componentName(this, config.slotIndex)
            // Only new tiles get the add-to-panel prompt — a tile already on the panel
            // (e.g. an edit) just saves.
            if (!TilePanelPrefs.isInPanel(applicationContext, component)) {
                TileComponentManager.promptAddTile(
                    this,
                    component,
                    config.name,
                    CustomTileIcons.res(config.iconId)
                ) { result ->
                    if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                        TilePanelPrefs.setInPanel(applicationContext, component, true)
                        StatusNotifier.notifyChanged()
                    }
                }
            }
            TileComponentManager.refreshTile(this, config.slotIndex)
            finish()
        }

        val delete: () -> Unit = {
            CustomTilePrefs.delete(this, slotIndex)
            TileComponentManager.refreshTile(this, slotIndex)
            finish()
        }

        setContent {
            ToolerTheme {
                TileEditorScreen(
                    slotIndex = slotIndex,
                    initial = remember { CustomTilePrefs.load(this@TileEditorActivity, slotIndex) },
                    alreadyInPanel = inPanel,
                    onBack = { finish() },
                    onSave = save,
                    onDelete = delete
                )
            }
        }
    }

    companion object {
        const val EXTRA_SLOT_INDEX = "extra_slot_index"
    }
}

/**
 * The form body: name, on/off-toggle switch, initial state, one or two commands, subtitles, and an
 * icon picker. Fields use [TextFieldValue] (not bare [String]) — the workaround for the AndroidX
 * cursor-movement bug, same reason aShellYou's form does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileEditorScreen(
    slotIndex: Int,
    initial: CustomTileConfig?,
    alreadyInPanel: Boolean,
    onBack: () -> Unit,
    onSave: (CustomTileConfig) -> Unit,
    onDelete: () -> Unit,
) {
    var nameField by remember(initial) { mutableStateOf(TextFieldValue(initial?.name ?: "")) }
    var isToggleable by remember(initial) { mutableStateOf(initial?.isToggleable ?: false) }
    var initialState by remember(initial) { mutableStateOf(initial?.isActive ?: false) }
    var onCommand by remember(initial) { mutableStateOf(TextFieldValue(initial?.onCommand ?: "")) }
    var offCommand by remember(initial) { mutableStateOf(TextFieldValue(initial?.offCommand ?: "")) }
    var onSubtitle by remember(initial) { mutableStateOf(TextFieldValue(initial?.onSubtitle ?: "On")) }
    var offSubtitle by remember(initial) { mutableStateOf(TextFieldValue(initial?.offSubtitle ?: "Off")) }
    var selectedIcon by remember(initial) { mutableStateOf(initial?.iconId ?: CustomTileIcons.DEFAULT_ID) }
    val fieldShape = RoundedCornerShape(12.dp)

    val canSave = nameField.text.isNotBlank() && onCommand.text.isNotBlank()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (initial == null) "Set up Tile ${slotIndex + 1}" else "Edit Tile ${slotIndex + 1}") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("Tile name") },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth()
                )

                // aShellYou's is_toggleable switch.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On/off toggle", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Two commands, one per tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isToggleable, onCheckedChange = { isToggleable = it })
                }

                // aShellYou's Initial State switch.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Initial state", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Whether the tile starts on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = initialState, onCheckedChange = { initialState = it })
                }

                if (isToggleable) {
                    ShellCommandField(
                        value = onCommand,
                        onValueChange = { onCommand = it },
                        label = "Command to turn ON"
                    )
                    ShellCommandField(
                        value = offCommand,
                        onValueChange = { offCommand = it },
                        label = "Command to turn OFF"
                    )
                } else {
                    ShellCommandField(
                        value = onCommand,
                        onValueChange = { onCommand = it },
                        label = "Command"
                    )
                }

                Text("Subtitle", style = MaterialTheme.typography.bodyMedium)
                if (isToggleable) {
                    OutlinedTextField(
                        value = onSubtitle,
                        onValueChange = { onSubtitle = it },
                        label = { Text("Subtitle when on") },
                        shape = fieldShape,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = offSubtitle,
                        onValueChange = { offSubtitle = it },
                        label = { Text("Subtitle when off") },
                        shape = fieldShape,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = onSubtitle,
                        onValueChange = { onSubtitle = it },
                        label = { Text("Subtitle") },
                        shape = fieldShape,
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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (initial != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(0.4f)
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
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
                                    // Static tiles discard the off command, same as aShellYou.
                                    offCommand = if (isToggleable) offCommand.text else "",
                                    onSubtitle = onSubtitle.text,
                                    // Static tiles show the on-subtitle either way.
                                    offSubtitle = if (isToggleable) offSubtitle.text else onSubtitle.text,
                                )
                            )
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (alreadyInPanel) "Save" else "Save & add to panel")
                    }
                }
                if (!canSave) {
                    Text(
                        "Add a name and a command to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}