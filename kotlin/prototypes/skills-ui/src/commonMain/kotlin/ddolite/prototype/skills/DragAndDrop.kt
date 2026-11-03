package ddolite.prototype.skills

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Drag-and-drop inside the app, written for the F1 test drive (plan §8.2, prototype 3).
 *
 * Why not Compose's own `dragAndDropSource`/`dragAndDropTarget`? They carry platform data
 * (AWT `Transferable` on desktop, `ClipData` on Android), so shared code would need a separate
 * version per platform. Everything dragged in these apps stays inside the app, so this keeps the
 * drag in plain shared state instead, and works the same for mouse and touch.
 *
 * It replaces VB's OLE drag-and-drop in frmEnhancements: `OLEDrag` / `OLEStartDrag` (start),
 * `OLEDragOver` (hovering), `OLEDragDrop` (drop), `OLECompleteDrag` (finished either way).
 *
 * @param T what is being dragged.
 */
class DragDropState<T> {
    /** The item being dragged, or null when no drag is in progress. */
    var dragging: T? by mutableStateOf(null)
        private set

    /** The pointer position, in window coordinates. */
    var position: Offset by mutableStateOf(Offset.Zero)
        private set

    /** The drop target under the pointer, or null. */
    var hoverTarget: Any? by mutableStateOf(null)
        private set

    // Where each drop target is on screen, by its key. Updated whenever layout changes.
    private val targets = LinkedHashMap<Any, Rect>()

    fun start(item: T, at: Offset) {
        dragging = item
        position = at
        hoverTarget = targetAt(at)
    }

    fun moveTo(at: Offset) {
        position = at
        hoverTarget = targetAt(at)
    }

    /** Ends the drag. Returns the item and the target it was dropped on (null if none). */
    fun finish(): Pair<T, Any?>? {
        val item = dragging
        val target = hoverTarget
        dragging = null
        hoverTarget = null
        if (item == null) {
            return null
        }
        return Pair(item, target)
    }

    fun cancel() {
        dragging = null
        hoverTarget = null
    }

    internal fun registerTarget(key: Any, bounds: Rect) {
        targets[key] = bounds
    }

    /** The target under [at]; where targets are nested (a slot inside a window-wide target), the smallest. */
    private fun targetAt(at: Offset): Any? {
        var found: Any? = null
        var foundArea = 0f
        for (entry in targets.entries) {
            val bounds = entry.value
            if (bounds.contains(at)) {
                val area = bounds.width * bounds.height
                if (found == null || area < foundArea) {
                    found = entry.key
                    foundArea = area
                }
            }
        }
        return found
    }
}

/** Marks this element as a place things can be dropped, identified by [key]. */
fun <T> Modifier.dropTarget(state: DragDropState<T>, key: Any): Modifier {
    return this.onGloballyPositioned { coordinates ->
        state.registerTarget(key, coordinates.boundsInWindow())
    }
}

/**
 * Makes this element draggable: a drag that starts here carries [item]. When it ends,
 * [onDrop] gets the item and the key of the target it was dropped on (null if none).
 * With a mouse the drag starts once the pointer moves a little, as VB's
 * `lstTree_MouseMove` waits for actual movement; with touch, likewise after a short slide.
 */
fun <T> Modifier.dragSource(state: DragDropState<T>, item: T, onStart: (T) -> Boolean = { true }, onDrop: (T, Any?) -> Unit): Modifier = composed {
    // This element's position, kept across recompositions, to turn drag offsets into window coordinates.
    val placement = remember { PlacementHolder() }
    // The gesture handler outlives recompositions, so it calls the latest callbacks through these.
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnStart by rememberUpdatedState(onStart)
    Modifier
        .onGloballyPositioned { coordinates -> placement.coordinates = coordinates }
        .pointerInput(item) {
            detectDragGestures(
                onDragStart = { offset ->
                    // [onStart] can refuse the drag (VB's RequestDrag with Allow = False).
                    if (currentOnStart(item)) {
                        state.start(item, placement.toWindow(offset))
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    state.moveTo(placement.toWindow(change.position))
                },
                onDragEnd = {
                    val result = state.finish()
                    if (result != null) {
                        currentOnDrop(result.first, result.second)
                    }
                },
                onDragCancel = { state.cancel() },
            )
        }
}

/**
 * Calls [onPress] as soon as the pointer goes down here (before any drag starts), and [onClick]
 * when it comes up again without having moved (a click or tap, not the end of a drag).
 * Works alongside [dragSource] on the same element.
 *
 * Gives the immediate feedback VB's controls give: a ListBox selects its item on mouse down
 * (frmEnhancements.lstTree_MouseDown), and userList slots toggle their selection on click
 * (usrTree_SlotClick).
 */
fun Modifier.pressAndClick(onPress: () -> Unit, onClick: () -> Unit): Modifier = composed {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnClick by rememberUpdatedState(onClick)
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            currentOnPress()
            val up = waitForUpOrCancellation()
            if (up != null) {
                val moved = (up.position - down.position).getDistance()
                if (moved < viewConfiguration.touchSlop) {
                    currentOnClick()
                }
            }
        }
    }
}

/** Remembers an element's layout position, to convert its local coordinates to window ones. */
private class PlacementHolder {
    var coordinates: LayoutCoordinates? = null

    fun toWindow(local: Offset): Offset {
        val c = coordinates
        if (c == null || !c.isAttached) {
            return local
        }
        return c.localToWindow(local)
    }
}
