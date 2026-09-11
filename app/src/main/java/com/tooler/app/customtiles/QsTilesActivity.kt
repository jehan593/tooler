package com.tooler.app.customtiles

import android.app.StatusBarManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tooler.app.R
import com.tooler.app.ui.BorderedIconButton
import com.tooler.app.ui.FeatureCard
import com.tooler.app.ui.StatusTone
import com.tooler.app.ui.theme.ToolerTheme
import com.tooler.app.util.ShizukuUtils
import com.tooler.app.util.StatusNotifier
import com.tooler.app.util.TilePanelPrefs
import com.tooler.app.util.openShizuku
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * The ten custom tile slots, one row each: empty slots offer "Set up", configured ones show
 * edit/delete/add actions. Configuration happens in [TileEditorActivity]; this screen re-reads
 * everything on resume so it always shows fresh configs and panel states.
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
                var panelState by remember {
                    mutableStateOf(slotPanelStates())
                }
                var pendingDeleteSlot by remember { mutableStateOf<Int?>(null) }

                val scope = rememberCoroutineScope()

                // Flush pending add/remove callbacks, then re-read panel state.
                val refreshPanelMembership: () -> Unit = {
                    for (slot in 0 until CUSTOM_TILE_SLOT_COUNT) {
                        try {
                            TileService.requestListeningState(
                                this@QsTilesActivity,
                                TileComponentManager.componentName(this@QsTilesActivity, slot)
                            )
                        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
                        }
                    }
                    scope.launch {
                        delay(700)
                        panelState = slotPanelStates()
                    }
                }

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
                            refreshPanelMembership()
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

                // Re-read membership when a tile is added/removed while this screen is open.
                LaunchedEffect(Unit) {
                    StatusNotifier.ticks.collect {
                        panelState = slotPanelStates()
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
                                when {
                                    !shizukuAvailable -> ShizukuStatusCard(
                                        status = "Shizuku not running",
                                        description = "Custom tiles need the Shizuku app running.",
                                        actionLabel = "Open Shizuku",
                                        onAction = { openShizuku(this@QsTilesActivity) }
                                    )
                                    !shizukuGranted -> ShizukuStatusCard(
                                        status = "Setup needed",
                                        description = "Shizuku is running. Grant access to use it.",
                                        actionLabel = "Grant access",
                                        onAction = { ShizukuUtils.requestPermission() }
                                    )
                                }
                            }
                            items(CUSTOM_TILE_SLOT_COUNT) { slotIndex ->
                                val config = tiles.find { it.slotIndex == slotIndex }
                                if (config == null) {
                                    EmptySlotCard(slotNumber = slotIndex + 1) {
                                        openEditor(slotIndex)
                                    }
                                } else {
                                    val inPanel = panelState[slotIndex] == true
                                    ConfiguredSlotCard(
                                        config = config,
                                        onAddToPanel = if (Build.VERSION.SDK_INT >= 33 && !inPanel) {
                                            {
                                                val component = TileComponentManager.componentName(
                                                    this@QsTilesActivity, slotIndex
                                                )
                                                TileComponentManager.promptAddTile(
                                                    this@QsTilesActivity,
                                                    component,
                                                    config.name,
                                                    CustomTileIcons.res(config.iconId)
                                                ) { result ->
                                                    if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                                                        TilePanelPrefs.setInPanel(
                                                            applicationContext, component, true
                                                        )
                                                        panelState = slotPanelStates()
                                                    }
                                                }
                                            }
                                        } else null,
                                        onEdit = { openEditor(slotIndex) },
                                        onDelete = { pendingDeleteSlot = slotIndex }
                                    )
                                }
                            }
                        }
                    }
                }

                pendingDeleteSlot?.let { slotIndex ->
                    val config = tiles.find { it.slotIndex == slotIndex }
                    AlertDialog(
                        onDismissRequest = { pendingDeleteSlot = null },
                        modifier = Modifier.widthIn(min = 340.dp),
                        title = { Text("Delete tile") },
                        text = {
                            Text(
                                "Delete \"${config?.name?.ifBlank { "Tile ${slotIndex + 1}" }}\"?"
                            )
                        },
                        confirmButton = {
                            OutlinedButton(
                                onClick = {
                                    CustomTilePrefs.delete(this@QsTilesActivity, slotIndex)
                                    TileComponentManager.refreshTile(this@QsTilesActivity, slotIndex)
                                    tiles = CustomTilePrefs.all(this@QsTilesActivity)
                                    pendingDeleteSlot = null
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { pendingDeleteSlot = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun slotPanelStates(): Map<Int, Boolean> {
        val context = this
        return (0 until CUSTOM_TILE_SLOT_COUNT).associate { slot ->
            slot to TilePanelPrefs.isInPanel(context, TileComponentManager.componentName(context, slot))
        }
    }

    private fun openEditor(slotIndex: Int) {
        startActivity(
            Intent(this, TileEditorActivity::class.java)
                .putExtra(TileEditorActivity.EXTRA_SLOT_INDEX, slotIndex)
        )
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
                    "Tap to create a tile",
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
    onAddToPanel: (() -> Unit)?,
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BorderedIconButton(
                    iconRes = R.drawable.ic_edit,
                    contentDescription = "Edit",
                    onClick = onEdit
                )
                Spacer(Modifier.width(8.dp))
                BorderedIconButton(
                    iconRes = R.drawable.ic_delete,
                    contentDescription = "Delete",
                    onClick = onDelete,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.weight(1f))
                BorderedIconButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = "Add to panel",
                    onClick = { onAddToPanel?.invoke() },
                    enabled = onAddToPanel != null
                )
            }
        }
    }
}