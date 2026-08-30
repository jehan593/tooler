package com.tooler.app.customtiles

/**
 * The ten pre-declared custom Quick Settings tile slots — one per manifest `<service>` entry
 * (see `CustomTileNNService` below and the manifest). aShellYou wraps the same ten classes in Hilt
 * and modules; there's no DI here, so they're plain one-liners over [BaseCustomTileService]. Each
 * owns a fixed [BaseCustomTileService.slotIndex]; `CustomTilePrefs` maps whichever user config
 * lives in that slot onto the tile at runtime.
 *
 * They're all in one file rather than one-per-file because each body is a single line — grouping
 * them keeps the slot numbering obvious at a glance, the same reason aShellYou names them Tile01..10.
 */
class CustomTile01Service : BaseCustomTileService() {
    override val slotIndex = 0
}

class CustomTile02Service : BaseCustomTileService() {
    override val slotIndex = 1
}

class CustomTile03Service : BaseCustomTileService() {
    override val slotIndex = 2
}

class CustomTile04Service : BaseCustomTileService() {
    override val slotIndex = 3
}

class CustomTile05Service : BaseCustomTileService() {
    override val slotIndex = 4
}

class CustomTile06Service : BaseCustomTileService() {
    override val slotIndex = 5
}

class CustomTile07Service : BaseCustomTileService() {
    override val slotIndex = 6
}

class CustomTile08Service : BaseCustomTileService() {
    override val slotIndex = 7
}

class CustomTile09Service : BaseCustomTileService() {
    override val slotIndex = 8
}

class CustomTile10Service : BaseCustomTileService() {
    override val slotIndex = 9
}