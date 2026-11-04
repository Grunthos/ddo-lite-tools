package ddolite.builder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme
import kotlinx.coroutines.Job

/**
 * A number with "<" and ">" buttons at either end. Prototype of the shared `userSpinner`
 * control (Source/Controls/userSpinner.ctl), used across all three VB6 apps.
 *
 * Mouse behaviour, from `userSpinner.UserControl_MouseDown` (userSpinner.ctl:531):
 * - left button on "<" / ">": down / up by [stepSmall]; right button: by [stepLarge];
 * - middle button: straight to [min] / [max];
 * - holding a button repeats (see [startRepeating]);
 * - the mouse wheel steps by [stepSmall] (VB: `WheelScroll`, :393).
 *
 * Not in the prototype yet: keyboard input (`UserControl_KeyDown`, :643), typing digits, the
 * "Small/Large" wheel-step toggle, and the disabled look.
 */
@Composable
fun Spinner(
    value: Int,
    min: Int,
    max: Int,
    theme: Theme,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    stepSmall: Int = 1,
    stepLarge: Int = 5,
) {
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    // The pointer handler below is set up once, so it reads the latest value through these.
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)

    val style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = 13.sp, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    val background = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val separator = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_INTERIOR)

    // Moves the value by [step], kept within min..max. From `userSpinner.Increment` (userSpinner.ctl:603).
    fun stepBy(step: Int) {
        var newValue = currentValue + step
        if (newValue < min) {
            newValue = min
        }
        if (newValue > max) {
            newValue = max
        }
        if (newValue != currentValue) {
            currentOnChange(newValue)
        }
    }

    Canvas(
        modifier = modifier
            .size(84.dp, 26.dp)
            .pointerInput(min, max, stepSmall, stepLarge) {
                // Width of each arrow area: VB uses 1.75 × the width of "<".
                val arrowWidth = textMeasurer.measure("<", style).size.width * 1.75f
                var repeating: Job? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes[0]
                        if (event.type == PointerEventType.Press) {
                            val x = change.position.x
                            val onLeft = x < arrowWidth
                            val onRight = x > size.width - arrowWidth
                            if (!onLeft && !onRight) {
                                continue // VB: a click on the number itself only toggles the wheel step
                            }
                            val button = pressedButton(event)
                            var step: Int
                            if (button == PointerButton.TERTIARY) {
                                step = if (onLeft) min - currentValue else max - currentValue
                            } else if (button == PointerButton.SECONDARY) {
                                step = if (onLeft) -stepLarge else stepLarge
                            } else {
                                step = if (onLeft) -stepSmall else stepSmall
                            }
                            stepBy(step)
                            if (button != PointerButton.TERTIARY) {
                                val repeatStep = step
                                repeating = startRepeating(scope) { stepBy(repeatStep) }
                            }
                            change.consume()
                        } else if (event.type == PointerEventType.Release || event.type == PointerEventType.Exit) {
                            repeating?.cancel()
                            repeating = null
                        } else if (event.type == PointerEventType.Scroll) {
                            if (change.scrollDelta.y < 0) {
                                stepBy(stepSmall)
                            } else if (change.scrollDelta.y > 0) {
                                stepBy(-stepSmall)
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        // From userSpinner.DrawBorders (:435) and ShowValue (:486), flat (non-3D) appearance.
        val arrowWidth = textMeasurer.measure("<", style).size.width * 1.75f
        drawRect(background)
        drawRect(border, style = Stroke(1f), size = Size(size.width - 1f, size.height - 1f), topLeft = Offset(0.5f, 0.5f))
        drawLine(separator, Offset(arrowWidth, 1f), Offset(arrowWidth, size.height - 1f))
        drawLine(separator, Offset(size.width - arrowWidth, 1f), Offset(size.width - arrowWidth, size.height - 1f))

        drawCentred(textMeasurer, "<", style, 0f, arrowWidth, size.height)
        drawCentred(textMeasurer, ">", style, size.width - arrowWidth, size.width, size.height)
        drawCentred(textMeasurer, value.toString(), style, arrowWidth, size.width - arrowWidth, size.height)
    }
}

/** Draws [text] centred between [left] and [right], vertically centred in [height]. Like VB's CurrentX/CurrentY + Print. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCentred(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    left: Float,
    right: Float,
    height: Float,
    top: Float = 0f,
) {
    if (text.isEmpty()) {
        return
    }
    val layout = textMeasurer.measure(text, style)
    val x = left + (right - left - layout.size.width) / 2f
    val y = top + (height - layout.size.height) / 2f
    drawText(layout, topLeft = Offset(x, y))
}
