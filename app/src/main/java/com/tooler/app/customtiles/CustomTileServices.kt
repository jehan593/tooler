package com.tooler.app.customtiles

/** The ten pre-declared custom tile slots, one per manifest `<service>` entry. Each is a one-line
 *  subclass over [BaseCustomTileService] owning a fixed [BaseCustomTileService.slotIndex]; grouped
 *  in one file so the slot numbering is obvious at a glance. */
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