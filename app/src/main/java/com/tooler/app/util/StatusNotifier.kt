package com.tooler.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process wake-up signal for MainActivity when a tile or service changes state underneath it
 * (a tile tapped from the QS overlay, the Keep Awake notification's action, a hardware volume key).
 * Carries no state — MainActivity just re-reads the system when it ticks.
 */
object StatusNotifier {
    private val _ticks = MutableStateFlow(0)

    /** Collect in the UI to be told "something changed, go re-read live state." */
    val ticks: StateFlow<Int> = _ticks

    fun notifyChanged() {
        _ticks.value += 1
    }
}