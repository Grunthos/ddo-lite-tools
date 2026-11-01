package ddolite.prototype.skills

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Timings for click-and-hold, taken from the VB timers on frmSkills and userSpinner:
 * `tmrStart` (300 ms before repeating starts) and `tmrRepeat` (then every 50 ms).
 */
const val REPEAT_START_MS = 300L
const val REPEAT_EVERY_MS = 50L

/**
 * Starts repeating [action] while a button is held: wait [REPEAT_START_MS], then call it every
 * [REPEAT_EVERY_MS]. Replaces the VB pattern `tmrStart.Enabled = True` → `tmrStart_Timer` turns
 * on `tmrRepeat` → `tmrRepeat_Timer` calls `MouseDown`. Cancel the returned job on release
 * (VB's MouseUp disables both timers).
 */
fun startRepeating(scope: CoroutineScope, action: () -> Unit): Job {
    return scope.launch {
        delay(REPEAT_START_MS)
        while (true) {
            action()
            delay(REPEAT_EVERY_MS)
        }
    }
}

/**
 * Which button a press event was for. Touch and pen count as the primary button; the touch
 * alternatives for the other buttons (plan §2a.5) aren't in the prototype yet.
 */
fun pressedButton(event: PointerEvent): PointerButton {
    if (event.buttons.isSecondaryPressed) {
        return PointerButton.SECONDARY
    }
    if (event.buttons.isTertiaryPressed) {
        return PointerButton.TERTIARY
    }
    return PointerButton.PRIMARY
}
