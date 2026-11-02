package ddolite.prototype.skills

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.builder.build.ClassIconState
import ddolite.builder.build.MAX_LEVEL
import ddolite.builder.build.OverviewRules
import ddolite.builder.model.CharacterClass
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** Verdana 9, the VB forms' font, is 12 px at 100%. */
private val FONT_SIZE = 12.sp

/** VB's MAX_CLASS_NAME (basCONST.bas:25): the class column is as wide as this name. */
private const val MAX_CLASS_NAME = "Arcane Trickster"

/** Where VB's form puts a class icon (picClass) and its caption (lblClass), in pixels at 100%. */
private class IconPlace(val left: Int, val top: Int, val labelLeft: Int, val labelTop: Int, val labelWidth: Int, val caption: String)

/**
 * picClass(1..25) and lblClass(1..25) from frmOverview.frm, by the class's VB index (ceBarbarian = 1, …).
 * Captions are the form's own, which abbreviate Favored Soul; two-line captions wrap at their space.
 */
private val ICON_PLACES: List<IconPlace> = listOf(
    IconPlace(34, 252, 27, 296, 56, "Barbarian"),
    IconPlace(93, 252, 100, 296, 27, "Bard"),
    IconPlace(155, 252, 160, 296, 32, "Cleric"),
    IconPlace(217, 252, 217, 296, 42, "Fighter"),
    IconPlace(279, 252, 279, 296, 42, "Paladin"),
    IconPlace(339, 252, 339, 296, 42, "Ranger"),
    IconPlace(397, 252, 399, 296, 38, "Rogue"),
    IconPlace(458, 252, 453, 296, 51, "Sorcerer"),
    IconPlace(34, 324, 34, 368, 42, "Wizard"),
    IconPlace(93, 324, 97, 368, 34, "Monk"),
    IconPlace(155, 324, 150, 368, 51, "Fav Soul"),
    IconPlace(217, 324, 215, 368, 45, "Artificer"),
    IconPlace(279, 324, 285, 368, 30, "Druid"),
    IconPlace(339, 324, 336, 368, 48, "Warlock"),
    IconPlace(397, 324, 388, 368, 60, "Alchemist"),
    IconPlace(34, 416, 17, 460, 76, "Stormsinger"),
    IconPlace(101, 416, 93, 460, 58, "Dark\nApostate"),
    IconPlace(171, 416, 154, 460, 76, "Blightcaster"),
    IconPlace(240, 416, 236, 460, 52, "Sacred\nFist"),
    IconPlace(303, 416, 298, 460, 52, "Dark\nHunter"),
    IconPlace(363, 416, 358, 460, 52, "Acolyte"),
    IconPlace(34, 496, 29, 540, 52, "Dragon\nLord"),
    IconPlace(101, 496, 99, 540, 45, "Wild\nMage"),
    IconPlace(171, 496, 162, 540, 59, "Dragon\nDisciple"),
    IconPlace(240, 496, 226, 540, 70, "Arcane\nTrickster"),
)

/** The resource name of a class's icon in CharacterBuilderLite.RES (`GetClassResourceID`, basEnum.bas:466); null = CLSEMPTY. */
fun classResourceId(c: CharacterClass?): String {
    if (c == null) {
        return "CLSEMPTY"
    }
    return "CLS" + c.name.uppercase().replace(" ", "")
}

/** Drawn grey, as VB's GrayScale (basBitmap.bas:46) does to disabled icons. */
private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * The Overview window: header (Overview;Notes | Help), the build's name, race, alignment and max
 * levels, the class icons, the level grid with the three build class slots, and the footer.
 * Laid out from frmOverview.frm's control positions (see tools/form-layout's frmOverview.md).
 *
 * [classIcons] holds the icons by resource name (see [classResourceId]); the platform reads them
 * from CharacterBuilderLite.RES.
 */
@Composable
fun OverviewWindowContent(state: PrototypeState, classIcons: Map<String, ImageBitmap>) {
    val theme = state.skills.theme
    val overview = state.overview
    val rules = state.rules
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision // read so this redraws when the build changes
    val workspace = theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)
    Box(modifier = Modifier.fillMaxSize().background(workspace).dropTarget(overview.drag, OverviewWindowKey)) {
        if (rules != null) {
            OverviewBody(state, OverviewRules(rules), theme, classIcons)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            HeaderBar(
                theme,
                listOf("Overview", "Notes"),
                if (overview.notesTab) 1 else 0,
                listOf("Help"),
                isEnabled = { link -> link != "Help" },
                onClick = { link -> overview.showTab(link == "Notes") },
            )
        }
        Box(modifier = Modifier.offset(8.dp, 584.dp).size(858.dp, 26.dp)) {
            // usrFooter: "Stats >" (usrFooter_Click :1407); the Stats window isn't ported yet.
            HeaderBar(theme, emptyList(), null, listOf("Stats >"), isEnabled = { false }, onClick = {})
        }
        OverviewDragGhost(overview.drag, classIcons, rules)
    }
}

/** The whole window is a drop target, so a build class slot dropped anywhere clears it. */
object OverviewWindowKey

@Composable
private fun OverviewBody(state: PrototypeState, rules: OverviewRules, theme: Theme, classIcons: Map<String, ImageBitmap>) {
    val overview = state.overview
    val build = rules.build
    val label = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val control = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    var alignmentListOpen by remember { mutableStateOf(false) }

    if (overview.notesTab) {
        // ShowControls (:1334): the notes fill both panels.
        TextBox(theme, build.notes, { overview.setNotes(it) }, 8, 32, 857, 541, singleLine = false)
        return
    }

    Frame(theme, 8, 32, 521, 541) // shpLeft
    Frame(theme, 536, 32, 329, 437) // shpRight

    Label("Name", label, 28, 46)
    TextBox(theme, build.buildName, { overview.setName(it) }, 120, 44, 237, 22, singleLine = true)

    Label("Race", label, 28, 71)
    RaceBox(overview, theme, control, build.race?.name ?: "", 120, 68, 161, 22)
    if (rules.parentRace() != null) {
        val link = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_LINK), textDecoration = TextDecoration.Underline)
        BasicText(
            "Sub-Race",
            style = link,
            modifier = Modifier.offset(292.dp, 71.dp).clickable { overview.showSubRaceNotice() },
        )
    }

    Label("Alignment", label, 28, 95)
    var alignmentText = ""
    val alignment = build.alignment
    if (alignment != null) {
        alignmentText = alignment.displayName
    }
    ComboBox(theme, control, alignmentText, 120, 92, 161, 22) { alignmentListOpen = !alignmentListOpen }

    Label("Max Levels", label, 28, 130)
    Spinner(
        value = build.maxLevels,
        min = 1,
        max = MAX_LEVEL,
        theme = theme,
        onValueChange = { overview.setMaxLevels(it) },
        modifier = Modifier.offset(120.dp, 128.dp).size(65.dp, 20.dp),
    )

    // Icon layout (ShowControls :1340): "Notes" sits where "Main Class" would be.
    Label("Notes", label, 28, 167)
    TextBox(theme, build.notes, { overview.setNotes(it) }, 120, 164, 237, 70, singleLine = false)

    ClassIcons(overview, rules, theme, classIcons)
    LevelGrid(overview, rules, theme, classIcons, 540, 36, 309, 417)

    // Drop-down lists go last so they're drawn over everything else.
    if (alignmentListOpen) {
        val items = ArrayList<String>()
        for (a in overview.choices.alignments) {
            items.add(a?.displayName ?: "")
        }
        DropList(theme, control, items, 120, 92 + 22, 161) { index ->
            alignmentListOpen = false
            overview.chooseAlignment(overview.choices.alignments[index])
        }
    }
    if (overview.raceListOpen) {
        RaceList(overview, theme, 120, 68 + 22 + 1)
    }
}

// ---------------------------------------------------------------- simple controls

@Composable
private fun Label(text: String, style: TextStyle, left: Int, top: Int) {
    BasicText(text, style = style, modifier = Modifier.offset(left.dp, top.dp))
}

/** A Shape: border only, in the workspace's highlight border colour (clsConfig.ApplyColors, Shape). */
@Composable
private fun Frame(theme: Theme, left: Int, top: Int, width: Int, height: Int) {
    Box(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .border(1.dp, theme.color(ColorGroup.WORKSPACE, ColorValue.BORDER_HIGHLIGHT)),
    )
}

/** A TextBox in the controls colours. */
@Composable
private fun TextBox(theme: Theme, value: String, onChange: (String) -> Unit, left: Int, top: Int, width: Int, height: Int, singleLine: Boolean) {
    val style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)),
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
            .padding(horizontal = 3.dp, vertical = 3.dp),
    )
}

/** A drop-down list box (Style 2): the current text and an arrow; [onClick] opens or closes the list. */
@Composable
private fun ComboBox(theme: Theme, style: TextStyle, text: String, left: Int, top: Int, width: Int, height: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
            .clickable(onClick = onClick),
    ) {
        BasicText(text, style = style, modifier = Modifier.offset(4.dp, 3.dp))
        BasicText("▾", style = style, modifier = Modifier.offset((width - 14).dp, 2.dp))
    }
}

/** The open list of a [ComboBox]. */
@Composable
private fun DropList(theme: Theme, style: TextStyle, items: List<String>, left: Int, top: Int, width: Int, onChoose: (Int) -> Unit) {
    var hover by remember { mutableStateOf(-1) }
    Column(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, (items.size * 18 + 2).dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
    ) {
        for (i in items.indices) {
            var rowStyle = style
            var back = Color.Transparent
            if (i == hover) {
                rowStyle = style.copy(color = HIGHLIGHT_TEXT)
                back = HIGHLIGHT
            }
            Box(
                modifier = Modifier.fillMaxWidth().size(width.dp, 18.dp).background(back)
                    .pointerInput(i) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                                    hover = i
                                } else if (event.type == PointerEventType.Press) {
                                    onChoose(i)
                                }
                            }
                        }
                    },
            ) {
                BasicText(items[i], style = rowStyle, modifier = Modifier.offset(4.dp, 2.dp))
            }
        }
    }
}

/** Windows' selection colours (vbHighlight / vbHighlightText), which VB's lists use whatever the theme. */
private val HIGHLIGHT = Color(0xFF0078D7)
private val HIGHLIGHT_TEXT = Color.White

// ---------------------------------------------------------------- race

/**
 * The race box: VB covers a ComboBox (cboRace) with a locked TextBox (txtRace) and drops down its
 * own list (usrRace) on click or Down/Enter/F4 (txtRace_Click :1517, txtRace_KeyDown :1496).
 */
@Composable
private fun RaceBox(overview: OverviewScreenState, theme: Theme, style: TextStyle, text: String, left: Int, top: Int, width: Int, height: Int) {
    val focus = remember { FocusRequester() }
    Box(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val key = when (event.key) {
                    Key.DirectionUp -> OverviewScreenState.RaceKey.UP
                    Key.DirectionDown -> OverviewScreenState.RaceKey.DOWN
                    Key.PageUp -> OverviewScreenState.RaceKey.PAGE_UP
                    Key.PageDown -> OverviewScreenState.RaceKey.PAGE_DOWN
                    Key.MoveHome -> OverviewScreenState.RaceKey.HOME
                    Key.MoveEnd -> OverviewScreenState.RaceKey.END
                    Key.Enter -> OverviewScreenState.RaceKey.ENTER
                    Key.F4 -> OverviewScreenState.RaceKey.F4
                    Key.Escape -> OverviewScreenState.RaceKey.ESCAPE
                    else -> null
                }
                if (key == null) {
                    false
                } else {
                    overview.raceKey(key)
                    true
                }
            }
            .focusable()
            .clickable {
                focus.requestFocus()
                overview.toggleRaceList()
            },
    ) {
        BasicText(text, style = style, modifier = Modifier.offset(4.dp, 3.dp))
        BasicText("▾", style = style, modifier = Modifier.offset((width - 14).dp, 2.dp))
    }
}

/**
 * The race list: a heading row per race type (with the stat names, and "Class" on the Iconic
 * heading), then each race with its stat changes from 8 and its iconic class. Ported from
 * userRaceCombo's InitDimensions (userRaceCombo.ctl:368), DrawList (:140), DrawRow (:152) and the
 * mouse handlers (:234, :245).
 */
@Composable
private fun RaceList(overview: OverviewScreenState, theme: Theme, left: Int, top: Int) {
    val rows = overview.raceRows
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = FONT_SIZE)
    fun width(text: String): Int = measurer.measure(text, style).size.width
    val marginX = width("  ")
    val textHeight = measurer.measure("Q", style).size.height + 1
    val leftMargin = width(" ")
    var raceWidth = 0
    var classWidth = 0
    for (row in rows) {
        if (row.race != null) {
            raceWidth = maxOf(raceWidth, width(row.caption))
            val iconic = row.iconicClass
            if (iconic != null) {
                classWidth = maxOf(classWidth, width(iconic.name))
            }
        }
    }
    val statHeaders = listOf("Str", "Dex", "Con", "Int", "Wis", "Cha")
    var statWidth = 0
    for (header in statHeaders) {
        statWidth = maxOf(statWidth, width(header))
    }
    val statMargin = width(" ")
    val centerPos = (statWidth - width("+2")) / 2
    val centerNeg = (statWidth - width("-2")) / 2
    val classLeft = leftMargin + raceWidth + marginX * 2 + statWidth * 6 + statMargin * 5
    val totalWidth = classLeft + classWidth + width(" ")
    val totalHeight = textHeight * rows.size + 1

    val text = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
    val back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val headerText = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)
    val headerBack = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND)
    val border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthDp = with(density) { totalWidth.toDp() }
    val heightDp = with(density) { totalHeight.toDp() }
    Canvas(
        modifier = Modifier.offset(left.dp, top.dp).size(widthDp, heightDp)
            .pointerInput(rows) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val y = event.changes.first().position.y.toInt()
                        var row = y / textHeight
                        if (row < 0 || row >= rows.size) {
                            row = -1
                        }
                        if (event.type == PointerEventType.Move) {
                            overview.raceListActive = row
                        } else if (event.type == PointerEventType.Press) {
                            var race: ddolite.builder.model.Race? = null
                            if (row >= 0) {
                                race = rows[row].race
                            }
                            // A heading row is "no race", as in VB.
                            overview.chooseRace(race)
                        }
                    }
                }
            },
    ) {
        drawRect(back)
        drawRect(border, topLeft = Offset(0.5f, 0.5f), size = Size(size.width - 1f, size.height - 1f), style = Stroke(1f))
        for (i in rows.indices) {
            val row = rows[i]
            val rowTop = (i * textHeight).toFloat()
            var fore = text
            var fill = back
            if (row.race == null) {
                fore = headerText
                fill = headerBack
            } else if (i == overview.raceListActive) {
                fore = HIGHLIGHT_TEXT
                fill = HIGHLIGHT
            }
            drawRect(fill, topLeft = Offset(1f, rowTop), size = Size(size.width - 2f, textHeight.toFloat()))
            // Lines above and below the headings and the races that touch them.
            if (row.race == null || (i > 0 && rows[i - 1].race == null)) {
                drawLine(border, Offset(1f, rowTop + 0.5f), Offset(size.width - 1f, rowTop + 0.5f))
            }
            val bottom = rowTop + textHeight
            if (row.race == null || i == rows.size - 1 || rows[i + 1].race == null) {
                drawLine(border, Offset(1f, bottom - 0.5f), Offset(size.width - 1f, bottom - 0.5f))
            }
            val rowStyle = style.copy(color = fore)
            drawText(measurer, row.caption, Offset(leftMargin.toFloat(), rowTop), rowStyle)
            val race = row.race
            if (race == null) {
                var statLeft = raceWidth + marginX
                for (header in statHeaders) {
                    drawText(measurer, header, Offset((statLeft + (statWidth - width(header)) / 2).toFloat(), rowTop), rowStyle)
                    statLeft += statWidth + statMargin
                }
                if (row.classHeader) {
                    drawText(measurer, "Class", Offset(classLeft.toFloat(), rowTop), rowStyle)
                }
            } else {
                for (s in 0 until 6) {
                    val change = race.stats[s] - 8
                    val statLeft = raceWidth + marginX + s * (statWidth + statMargin)
                    if (change < 0) {
                        drawText(measurer, change.toString(), Offset((statLeft + centerNeg).toFloat(), rowTop), rowStyle)
                    } else if (change > 0) {
                        drawText(measurer, "+$change", Offset((statLeft + centerPos).toFloat(), rowTop), rowStyle)
                    }
                }
                val iconic = row.iconicClass
                if (iconic != null) {
                    drawText(measurer, iconic.name, Offset(classLeft.toFloat(), rowTop), rowStyle)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- class icons

/**
 * The class icons (picIcons with picClass/lblClass). Ported from DrawIcons (frmOverview.frm:1814):
 * a chosen class shows the empty icon, a class the filters rule out is grey with a dim caption,
 * and only the others can be dragged onto a build class slot.
 */
@Composable
private fun ClassIcons(overview: OverviewScreenState, rules: OverviewRules, theme: Theme, classIcons: Map<String, ImageBitmap>) {
    // The icon area takes build class slots dragged back to it.
    Box(modifier = Modifier.offset(16.dp, 248.dp).size(505.dp, 329.dp).dropTarget(overview.drag, ClassIconsKey))
    val choices = overview.choices
    for (c in rules.rules.data.classes) {
        if (c.vbIndex < 1 || c.vbIndex > ICON_PLACES.size) {
            continue // VB has a place on the form only for the classes it knows
        }
        val place = ICON_PLACES[c.vbIndex - 1]
        val state = rules.iconState(c, choices)
        var icon = classIcons[classResourceId(c)]
        if (state == ClassIconState.EMPTY) {
            icon = classIcons[classResourceId(null)]
        }
        var modifier = Modifier.offset(place.left.dp, place.top.dp).size(42.dp, 42.dp)
        if (state == ClassIconState.NORMAL) {
            modifier = modifier.dragSource(overview.drag, OverviewDrag.ClassIcon(c)) { item, target -> overview.dropped(item, target) }
        }
        if (icon != null) {
            Image(icon, c.name, modifier, colorFilter = if (state == ClassIconState.DISABLED) GREY else null)
        } else {
            Box(modifier.border(1.dp, theme.color(ColorGroup.WORKSPACE, ColorValue.BORDER_EXTERIOR)))
        }
        var captionColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
        if (state == ClassIconState.DISABLED) {
            captionColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)
        }
        // Centred on the label's box from the form; wider text spills evenly both sides.
        BasicText(
            place.caption,
            style = TextStyle(fontSize = FONT_SIZE, color = captionColor, textAlign = TextAlign.Center),
            softWrap = false,
            modifier = Modifier.offset((place.labelLeft + place.labelWidth / 2 - 50).dp, place.labelTop.dp).size(100.dp, 32.dp),
        )
    }
}

/** The icon following the pointer while a class or build class slot is dragged (VB's DragIcon). */
@Composable
private fun OverviewDragGhost(drag: DragDropState<OverviewDrag>, classIcons: Map<String, ImageBitmap>, rules: ddolite.builder.build.BuildRules?) {
    val item = drag.dragging ?: return
    var c: CharacterClass? = null
    if (item is OverviewDrag.ClassIcon) {
        c = item.characterClass
    } else if (item is OverviewDrag.BuildSlot && rules != null) {
        c = rules.build.buildClasses[item.slot]
    }
    val icon = classIcons[classResourceId(c)] ?: return
    val position = drag.position
    Image(
        icon,
        null,
        alpha = 0.8f,
        modifier = Modifier.offset { IntOffset(position.x.toInt() - 16, position.y.toInt() - 16) }.size(32.dp, 32.dp),
    )
}

// ---------------------------------------------------------------- level grid

/** One grid column (VB's ColumnType, frmOverview.frm:1187). */
private class GridColumn(val header: String, val left: Int, val width: Int, val align: TextAlign) {
    val right: Int get() = left + width
}

/**
 * The grid's measurements in pixels. Ported from InitGrid (frmOverview.frm:2074): columns Level,
 * Class, the three build classes and BAB, sized from bold text widths, the three class columns
 * sharing what's left; rows fill the height, 20 of them under a header as tall as a class column is
 * wide (it holds the class icons).
 */
private class GridGeometry(measurer: TextMeasurer, normal: TextStyle, bold: TextStyle, width: Int, height: Int) {
    val columns = ArrayList<GridColumn>()
    val classWidth: Int
    val top: Int
    val rowHeight: Int
    val offsetY: Int
    private val leftIndent: Int

    init {
        fun boldWidth(text: String): Int = measurer.measure(text, bold).size.width
        val extra = boldWidth("   ")
        columns.add(GridColumn("Level", 0, boldWidth("Level"), TextAlign.Center))
        var left = columns[0].right
        columns.add(GridColumn("Class", left + extra, boldWidth(" $MAX_CLASS_NAME "), TextAlign.Left))
        left = columns[1].right
        val babWidth = boldWidth(" BAB ")
        classWidth = (width - left - babWidth - extra - 1) / 3
        for (i in 0..2) {
            columns.add(GridColumn("", left, classWidth, TextAlign.Center))
            left = columns[columns.size - 1].right
        }
        columns.add(GridColumn("BAB", left + extra, babWidth, TextAlign.Center))
        top = classWidth
        rowHeight = (height - top - 1) / 20
        val textHeight = measurer.measure("X", normal).size.height
        offsetY = (rowHeight - textHeight) / 2
        leftIndent = measurer.measure("  ", normal).size.width
    }

    fun rowTop(row: Int): Int = top + (row - 1) * rowHeight

    /** CoordsToCell (:2336): row 1-20 (0 = header or outside), column 1-4 (0 = elsewhere). */
    fun cellAt(at: Offset, levels: Int): Pair<Int, Int> {
        var row = 0
        if (at.y >= top) {
            row = ((at.y - top) / rowHeight).toInt() + 1
        }
        if (row < 1 || row > levels) {
            row = 0
        }
        var col = 0
        for (i in 1..4) {
            if (at.x >= columns[i].left && at.x <= columns[i].right) {
                col = i
                break
            }
        }
        return Pair(row, col)
    }

    fun textLeft(col: Int, textWidth: Int): Int {
        val column = columns[col]
        return when (column.align) {
            TextAlign.Left -> column.left + leftIndent
            TextAlign.Right -> column.right - textWidth
            else -> column.left + (column.width - textWidth) / 2
        }
    }
}

/** DrawCell's three looks (`GridStyleEnum`, :1181). */
private enum class GridStyle { WORKSPACE, CONTROL, DROP_SLOT }

/**
 * The level grid (picGrid): drawn as VB's DrawGrid/DrawCell (:2150, :2164), with the build class
 * icons in the header (DrawIconHeader, :1926), the active cell's border (ActiveBorders, :2350)
 * and the drag of a level's class (DragClass, :2254).
 */
@Composable
private fun LevelGrid(
    overview: OverviewScreenState,
    rules: OverviewRules,
    theme: Theme,
    classIcons: Map<String, ImageBitmap>,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
) {
    val measurer = rememberTextMeasurer()
    val normal = TextStyle(fontSize = FONT_SIZE)
    val bold = normal.copy(fontWeight = FontWeight.Bold)
    var size by remember { mutableStateOf(IntSize(width, height)) }
    val geometry = remember(size, measurer) { GridGeometry(measurer, normal, bold, size.width, size.height) }
    val currentGeometry by rememberUpdatedState(geometry)
    val levels = rules.rules.heroicLevels()
    val currentLevels by rememberUpdatedState(levels)
    val build = rules.build
    val rows = rules.gridRows()
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp).onSizeChanged { size = it }) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val at = event.changes.first().position
                        val cell = currentGeometry.cellAt(at, currentLevels)
                        if (event.type == PointerEventType.Move) {
                            overview.hover(cell.first, cell.second)
                        } else if (event.type == PointerEventType.Press) {
                            overview.press(cell.first, cell.second)
                        } else if (event.type == PointerEventType.Release) {
                            overview.release(cell.first, cell.second)
                        } else if (event.type == PointerEventType.Exit) {
                            overview.hover(0, 0)
                        }
                    }
                }
            },
        ) {
            // Row 0: the headers (the class columns' headers are the icons below).
            for (col in listOf(0, 1, 5)) {
                drawCell(theme, measurer, geometry, 0, col, geometry.columns[col].header, GridStyle.WORKSPACE, bold, false)
            }
            for (row in rows) {
                val r = row.level
                val activeRow = overview.activeRow == r
                var rowStyle = normal
                if (activeRow) {
                    rowStyle = bold
                }
                drawCell(theme, measurer, geometry, r, 0, r.toString(), GridStyle.WORKSPACE, rowStyle, false)
                // Class column: drop slots once there's a splash.
                if (build.buildClasses[1] != null) {
                    val drop = overview.levelDragFrom != 0 && build.levelClass(r) != build.levelClass(overview.levelDragFrom)
                    drawCell(theme, measurer, geometry, r, 1, row.className, GridStyle.DROP_SLOT, normal, drop)
                } else {
                    drawCell(theme, measurer, geometry, r, 1, row.className, GridStyle.WORKSPACE, normal, false)
                }
                for (slot in 0..2) {
                    var style = GridStyle.CONTROL
                    if (rules.slotOfLevel(r) == slot) {
                        style = GridStyle.DROP_SLOT
                    }
                    var text = ""
                    if (build.buildClasses[slot] != null) {
                        text = row.classLevels[slot].toString()
                    }
                    drawCell(theme, measurer, geometry, r, slot + 2, text, style, normal, false)
                }
                if (build.buildClasses[0] != null) {
                    drawCell(theme, measurer, geometry, r, 5, row.bab.toString(), GridStyle.WORKSPACE, rowStyle, false)
                }
            }
            // DrawBorders: the active cell, or the level being dragged.
            var borderRow = overview.activeRow
            var borderCol = overview.activeCol
            if (overview.levelDragFrom != 0) {
                borderRow = overview.levelDragFrom
                borderCol = 1
            }
            if (borderRow in 1..levels && borderCol in 1..4 && !(borderCol == 1 && build.buildClasses[1] == null)) {
                val column = geometry.columns[borderCol]
                val rowTop = geometry.rowTop(borderRow)
                drawBox(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_HIGHLIGHT), column.left, rowTop, column.right, rowTop + geometry.rowHeight)
            }
        }
        // The build class slots in the header (picBuildClass).
        for (slot in 0..2) {
            val column = geometry.columns[slot + 2]
            val iconWidth = (geometry.classWidth * 0.9).toInt()
            val iconLeft = column.left + (geometry.classWidth - iconWidth) / 2
            val iconTop = (geometry.top - iconWidth) / 2
            val c = build.buildClasses[slot]
            val icon = classIcons[classResourceId(c)]
            val enabled = rules.isSlotEnabled(slot)
            var modifier = Modifier
                .offset(with(density) { iconLeft.toDp() }, with(density) { iconTop.toDp() })
                .size(with(density) { iconWidth.toDp() })
                .dropTarget(overview.drag, BuildSlotKey(slot))
            if (c != null) {
                modifier = modifier.dragSource(overview.drag, OverviewDrag.BuildSlot(slot)) { item, target -> overview.dropped(item, target) }
            }
            if (icon != null) {
                Image(icon, c?.name, modifier, colorFilter = if (enabled) null else GREY)
            }
        }
    }
}

/** VB's `Line (l, t)-(r, b), color, B`: a one-pixel box including both corners. */
private fun DrawScope.drawBox(color: Color, left: Int, top: Int, right: Int, bottom: Int) {
    drawRect(color, topLeft = Offset(left + 0.5f, top + 0.5f), size = Size((right - left).toFloat(), (bottom - top).toFloat()), style = Stroke(1f))
}

/** One grid cell: border, fill and text. Ported from DrawCell (frmOverview.frm:2164). */
private fun DrawScope.drawCell(
    theme: Theme,
    measurer: TextMeasurer,
    geometry: GridGeometry,
    row: Int,
    col: Int,
    text: String,
    style: GridStyle,
    textStyle: TextStyle,
    drop: Boolean,
) {
    val fore: Color
    val back: Color
    val border: Color
    when (style) {
        GridStyle.WORKSPACE -> {
            fore = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
            back = theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)
            border = back
        }
        GridStyle.CONTROL -> {
            fore = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM)
            back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
            border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_INTERIOR)
        }
        GridStyle.DROP_SLOT -> {
            fore = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)
            if (drop) {
                back = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_HIGHLIGHT)
            } else {
                back = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND)
            }
            border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_INTERIOR)
        }
    }
    val column = geometry.columns[col]
    val top = geometry.rowTop(row)
    val bottom = top + geometry.rowHeight
    drawBox(border, column.left, top, column.right, bottom)
    drawRect(back, topLeft = Offset((column.left + 1).toFloat(), (top + 1).toFloat()), size = Size((column.width - 1).toFloat(), (geometry.rowHeight - 1).toFloat()))
    if (text.isEmpty()) {
        return
    }
    val styled = textStyle.copy(color = fore)
    val layout = measurer.measure(text, styled)
    val x = geometry.textLeft(col, layout.size.width)
    drawText(layout, topLeft = Offset(x.toFloat(), (top + geometry.offsetY).toFloat()))
}
