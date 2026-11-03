package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.builder.build.BuildRules
import ddolite.builder.build.DropState
import ddolite.builder.build.FeatListEntry
import ddolite.builder.build.FeatTab
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme
import kotlin.time.TimeSource

/** frmFeats' client width and the top of its footer, in pixels at 100%. */
private const val FEATS_WIDTH = 815
private const val FEATS_FOOTER_TOP = 568

/** usrList's place on frmFeats. */
private const val LIST_LEFT = 24
private const val LIST_TOP = 40
private const val LIST_WIDTH = 409
private const val LIST_HEIGHT = 521

/** usrList's column headers sit above its border, which starts 30 pixels down (mlngTop). */
private const val LIST_HEADER = 30

/** userList's DEFAULT_MAX_ROWS: more rows than this get a scroll bar. */
private const val MAX_ROWS = 21

/** A ListBox item's height. */
private const val ITEM_HEIGHT = 15

/** Time within which a second press counts as a double-click. */
private const val DOUBLE_CLICK_MS = 500L

/**
 * The Feats window (Source/Forms/Build/frmFeats.frm): the header (Selected, the channel tabs if
 * "Channels" is on, Granted; Help), the feat slots (usrList), the feat and selector lists, the
 * details box and the filter list, and the footer ("< Skills", "Channels", "Spells >" or
 * "Enhancements >"). The client area is 815 x 593 at 100%.
 *
 * [onNavigate] gets the footer's links.
 */
@Composable
fun FeatsWindowContent(state: PrototypeState, onNavigate: (String) -> Unit) {
    val screen = state.feats
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + screen.revision // read so this redraws after changes
    val rules = state.rules
    val density = LocalDensity.current.density
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pointer = event.changes[0].position
                    }
                }
            },
    ) {
        if (rules != null) {
            FeatsBody(screen, rules, theme) { menuAt = pointer }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            // usrHeader_Click (frmFeats.frm:343): a tab, or Help.
            val captions = ArrayList<String>()
            var current = 0
            for (i in screen.tabs.indices) {
                captions.add(screen.tabs[i].caption(rules?.build?.race))
                if (screen.tabs[i] == screen.tab) {
                    current = i
                }
            }
            HeaderBar(
                theme,
                captions,
                current,
                listOf("Help"),
                isEnabled = { true },
                onClick = { link ->
                    if (link == "Help") {
                        state.help.show("Feats")
                    }
                    val tab = FeatTab.find(link)
                    if (tab != null) {
                        screen.chooseTab(tab)
                    }
                },
            )
        }
        Box(modifier = Modifier.offset(0.dp, FEATS_FOOTER_TOP.dp).size(FEATS_WIDTH.dp, 26.dp)) {
            // LoadData (:426): "Spells >" for casters, else "Enhancements >". usrFooter_Click (:350).
            var next = "Enhancements >"
            if (rules != null && rules.build.canCastSpell[1] != 0) {
                next = "Spells >"
            }
            HeaderBar(
                theme,
                listOf("< Skills"),
                null,
                listOf(next),
                isEnabled = { true },
                onClick = { link -> onNavigate(link) },
            )
        }
        if (screen.hasChannels()) {
            CheckBox(theme, "Channels", screen.channels, true, 368, 576, 113) { screen.changeChannels(!screen.channels) }
        }
        FeatDragGhost(screen, theme)

        // The slot menu (mnuMain(0)), opened by a right-click on a slot (ContextMenu, :749).
        val at = menuAt
        if (at != null && screen.menuRow != 0) {
            val items = screen.menuItems()
            PopupMenu(items, -1, at, density, onChoose = { index ->
                menuAt = null
                screen.chooseMenu(items[index])
            }, onDismiss = {
                menuAt = null
                screen.closeMenu()
            }, clientWidth = FEATS_WIDTH)
        }
    }
}

@Composable
private fun FeatsBody(screen: FeatsScreenState, rules: BuildRules, theme: Theme, onMenu: () -> Unit) {
    val label = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val link = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_LINK))
    SlotList(screen, rules, theme, onMenu)
    val granted = screen.tab == FeatTab.GRANTED
    if (!granted) {
        // lblFeats, usrchkShowAll (moved to its right by FeatListTitle), lblSelectors.
        Label(screen.listTitle, label, 448, 48)
        CheckBox(theme, "Show All", screen.showAll, true, 544, 46, 89) { screen.changeShowAll(!screen.showAll) }
        Label("Selectors", label, 648, 48)
        val featNames = ArrayList<String>()
        for (feat in screen.feats) {
            featNames.add(screen.listCaption(feat))
        }
        ListBox(
            theme, featNames, { i -> i == screen.featIndex }, 448, 64, 185, 282, screen,
            dragItem = { i -> FeatDrag.FromList(screen.feats[i]) },
            dropKey = FeatListKey,
            onPress = { i, _ -> screen.clickFeat(i) },
            onDoubleClick = { screen.doubleClickList() },
        )
        val selectorNames = ArrayList<String>()
        for (s in screen.selectors) {
            selectorNames.add(s.name)
        }
        ListBox(
            theme, selectorNames, { i -> i == screen.selectorIndex }, 648, 64, 145, 282, screen,
            dragItem = { _ -> screen.feats.getOrNull(screen.featIndex)?.let { FeatDrag.FromList(it) } },
            onPress = { i, _ -> screen.clickSelector(i) },
            onDoubleClick = { screen.doubleClickList() },
        )
    }
    // Details: lblDetails (a link that widens or restores the box), >> and <<, usrDetails.
    val details = screen.details
    val expanded = screen.detailsExpanded
    var detailsWidth = 185
    if (expanded) {
        detailsWidth = 345
    }
    BasicText(
        details.caption,
        style = label.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.offset(448.dp, 356.dp).width((detailsWidth - 22).dp).clickable { screen.toggleDetails() },
    )
    if (!granted) {
        // lblExpand ">>" while normal size, lblRestore "<<" while widened (when shown).
        if (!expanded) {
            BasicText(">>", style = link, modifier = Modifier.offset(611.dp, 356.dp).clickable { screen.toggleDetails() })
        } else if (screen.restoreLinkShown) {
            BasicText("<<", style = link, modifier = Modifier.offset(771.dp, 356.dp).clickable { screen.toggleDetails() })
        }
    }
    DetailsBox(screen, theme, 448, 372, detailsWidth, 189)
    if (!expanded) {
        Label("Filter", label, 648, 356)
        ListBox(
            theme, screen.groupNames(), { i -> screen.groupSelection.contains(i) }, 648, 372, 145, 197, screen,
            onPress = { i, ctrl -> screen.clickGroup(i, ctrl) },
        )
    }
}

/** usrDetails: the lines, wrapped, with the wiki link and errors in their colours (userDetails.DrawText). */
@Composable
private fun DetailsBox(screen: FeatsScreenState, theme: Theme, left: Int, top: Int, width: Int, height: Int) {
    val uriHandler = LocalUriHandler.current
    val text = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).offset(5.dp, 2.dp).width((if (scroll.maxValue in 1 until Int.MAX_VALUE) width - 12 - SCROLL_BAR else width - 12).dp)) {
            for (line in screen.details.lines) {
                var color = text
                if (line.error) {
                    color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_ERROR)
                }
                // Lines starting with a space are indented when they wrap (ProcessLine's blnIndent).
                var style = TextStyle(fontSize = FONT_SIZE, color = color)
                if (line.text.startsWith(" ")) {
                    style = style.copy(textIndent = TextIndent(restLine = 14.sp))
                }
                val lineLink = line.link
                if (lineLink != null && line.text.endsWith("(Wiki)")) {
                    // lblLink sits over "(Wiki)" in the link colour; here the whole last line opens the page.
                    val linkStart = line.text.length - "(Wiki)".length
                    val annotated = buildAnnotatedString {
                        append(line.text.substring(0, linkStart))
                        withStyle(SpanStyle(color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_LINK))) {
                            append("(Wiki)")
                        }
                    }
                    BasicText(
                        annotated,
                        style = style,
                        modifier = Modifier.clickable { uriHandler.openUri(lineLink) },
                    )
                } else {
                    BasicText(line.text, style = style)
                }
            }
        }
        ScrollBar(theme, scroll, width - 1 - SCROLL_BAR, 1, height - 2)
    }
}

/**
 * The feat slots (userList with userSlot rows): column headers above a bordered list; each row
 * has its texts and a slot drawn by its drop state (userSlot.Refresh). Rows are numbered from 1.
 */
@Composable
private fun SlotList(screen: FeatsScreenState, rules: BuildRules, theme: Theme, onMenu: () -> Unit) {
    val rows = screen.rows()
    val granted = screen.tab == FeatTab.GRANTED
    val textMeasurer = rememberTextMeasurer()
    val normal = TextStyle(fontSize = FONT_SIZE)
    val bold = TextStyle(fontSize = FONT_SIZE, fontWeight = FontWeight.Bold)
    // The measurer works in pixels; the layout below is in dp.
    val scale = LocalDensity.current.density
    // userList.UserControl_Initialize: slot height 4/3 of the text, a quarter of that between slots,
    // two spaces between columns.
    val textHeight = (textMeasurer.measure("X", normal).size.height / scale).toInt()
    val slotHeight = textHeight * 4 / 3
    val marginY = slotHeight / 4
    val marginX = (textMeasurer.measure("  ", normal).size.width / scale).toInt()
    var clientWidth = LIST_WIDTH - 2
    if (rows.size > MAX_ROWS) {
        clientWidth -= 17
    }
    // DefineColumn: (alignment, header, size text); the slot column takes what's left (Refresh).
    val headers: List<String>
    val sizeTexts: List<String>
    val aligns: List<TextAlign>
    val slotColumn: Int
    if (granted) {
        headers = listOf("Source", "Granted Feat", "Level")
        sizeTexts = listOf("Arcane Trickster", "", "20") // MAX_CLASS_NAME
        aligns = listOf(TextAlign.Start, TextAlign.Center, TextAlign.End)
        slotColumn = 1
    } else {
        headers = listOf("Source", "Level", "Feat", "BAB")
        sizeTexts = listOf("Legendary", "Level", "", "BAB")
        aligns = listOf(TextAlign.Start, TextAlign.End, TextAlign.Center, TextAlign.Center)
        slotColumn = 2
    }
    val lefts = IntArray(headers.size)
    val widths = IntArray(headers.size)
    var x = marginX
    for (i in 0 until slotColumn) {
        lefts[i] = x
        widths[i] = (textMeasurer.measure(sizeTexts[i], bold).size.width / scale).toInt()
        x = lefts[i] + widths[i] + marginX
    }
    lefts[slotColumn] = x
    var right = clientWidth - marginX
    for (i in headers.size - 1 downTo slotColumn + 1) {
        widths[i] = (textMeasurer.measure(sizeTexts[i], bold).size.width / scale).toInt()
        lefts[i] = right - widths[i]
        right = lefts[i] - marginX
    }
    widths[slotColumn] = right - lefts[slotColumn]

    val textStyle = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    // DrawColumnHeaders: a header may be wider than its column; right-aligned ones then reach left.
    for (i in headers.indices) {
        val headerWidth = (textMeasurer.measure(headers[i], normal).size.width / scale).toInt()
        var x = lefts[i]
        if (aligns[i] == TextAlign.Center) {
            x = lefts[i] + (widths[i] - headerWidth) / 2
        } else if (aligns[i] == TextAlign.End) {
            x = lefts[i] + widths[i] - headerWidth
        }
        BasicText(
            headers[i],
            style = textStyle,
            softWrap = false,
            modifier = Modifier.offset((LIST_LEFT + 1 + x).dp, (LIST_TOP + LIST_HEADER - textHeight - 2).dp),
        )
    }
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier.offset(LIST_LEFT.dp, (LIST_TOP + LIST_HEADER).dp).size(LIST_WIDTH.dp, (LIST_HEIGHT - LIST_HEADER).dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.WORKSPACE, ColorValue.BORDER_EXTERIOR)),
    ) {
        Box(
            modifier = Modifier.offset(1.dp, 1.dp).size((LIST_WIDTH - 2).dp, (LIST_HEIGHT - LIST_HEADER - 2).dp)
                .verticalScroll(scroll),
        ) {
            val contentHeight = (slotHeight + marginY) * rows.size + marginY * 2
            Box(modifier = Modifier.size(clientWidth.dp, contentHeight.dp)) {
                val controlText = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
                for (r in rows.indices) {
                    val row = r + 1
                    val entry = rows[r]
                    val top = (slotHeight + marginY) * r + marginY
                    val texts = rowTexts(entry, rules, granted)
                    val hover = screen.hoverRow == row
                    var style = TextStyle(fontSize = FONT_SIZE, color = controlText)
                    if (hover) {
                        style = style.copy(fontWeight = FontWeight.Bold)
                    }
                    for (i in headers.indices) {
                        if (i == slotColumn) {
                            continue
                        }
                        BasicText(
                            texts[i],
                            style = style.copy(textAlign = aligns[i]),
                            maxLines = 1,
                            modifier = Modifier.offset(lefts[i].dp, (top + (slotHeight - textHeight) / 2).dp).width(widths[i].dp),
                        )
                    }
                    FeatSlot(screen, theme, entry, row, lefts[slotColumn], top, widths[slotColumn], slotHeight, onMenu)
                }
            }
        }
        ScrollBar(theme, scroll, LIST_WIDTH - 1 - SCROLL_BAR, 1, LIST_HEIGHT - LIST_HEADER - 2)
    }
}

/** The texts of a row's columns (the slot's own column is drawn by [FeatSlot]): InitSelectedFeatsList (:505), ShowGrantedFeats (:479). */
private fun rowTexts(entry: FeatListEntry, rules: BuildRules, granted: Boolean): List<String> {
    if (granted) {
        return listOf(entry.sourceForm, "", entry.level.toString())
    }
    var bab = ""
    if (entry.level >= 1 && entry.level <= rules.build.bab.size) {
        bab = rules.build.bab[entry.level - 1].toString()
    }
    return listOf(entry.sourceForm, entry.level.toString() + "  ", "", bab)
}

/**
 * One feat slot (userSlot): its background shows the drop state, red text an error, a dark
 * border the row under the mouse. A press chooses it (left) or opens the menu (right), a second
 * press soon after is a double-click (clears it), and a left drag moves its feat.
 */
@Composable
private fun FeatSlot(screen: FeatsScreenState, theme: Theme, entry: FeatListEntry, row: Int, left: Int, top: Int, width: Int, height: Int, onMenu: () -> Unit) {
    val state = screen.dropStates.getOrNull(row - 1) ?: DropState.DEFAULT
    val background = when (state) {
        DropState.DEFAULT -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND)
        DropState.CAN_DROP -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_HIGHLIGHT)
        DropState.CAN_DROP_ERROR -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_ERROR)
        DropState.SUBORDINATE -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_RELATED)
    }
    var border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)
    if (screen.hoverRow == row) {
        border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_HIGHLIGHT)
    }
    var textColor = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)
    if (entry.errorState) {
        textColor = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT_ERROR)
    }
    val clicks = remember { ClickTimer() }
    val currentOnMenu by rememberUpdatedState(onMenu)
    Box(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .dropTarget(screen.drag, FeatSlotKey(row))
            .background(background)
            .border(1.dp, border)
            .pointerInput(row) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                            if (screen.drag.dragging == null) {
                                screen.hoverRow = row
                            }
                        } else if (event.type == PointerEventType.Exit) {
                            if (screen.hoverRow == row && screen.drag.dragging == null) {
                                screen.hoverRow = 0
                            }
                        } else if (event.type == PointerEventType.Press) {
                            val button = pressedButton(event)
                            screen.lastButton = button
                            if (button == PointerButton.PRIMARY && clicks.isDoubleClick(row)) {
                                screen.doubleClickSlot(row)
                            } else {
                                screen.clickSlot(row, button)
                                if (screen.menuRow != 0) {
                                    currentOnMenu()
                                }
                            }
                        }
                    }
                }
            }
            .dragSource(screen.drag, FeatDrag.FromSlot(row), onStart = { item ->
                screen.lastButton == PointerButton.PRIMARY && screen.startDrag(item)
            }) { item, target -> screen.dropped(item, target) },
    ) {
        BasicText(
            entry.display,
            style = TextStyle(fontSize = FONT_SIZE, color = textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(4.dp, ((height - 15) / 2).dp).width((width - 8).dp),
        )
    }
}

/** Tells a double-click from two clicks: the same row pressed again within [DOUBLE_CLICK_MS]. */
private class ClickTimer {
    private var lastTime: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastRow = -1

    fun isDoubleClick(row: Int): Boolean {
        val now = TimeSource.Monotonic.markNow()
        val last = lastTime
        val result = row == lastRow && last != null && (now - last).inWholeMilliseconds < DOUBLE_CLICK_MS
        if (result) {
            lastRow = -1
        } else {
            lastRow = row
            lastTime = now
        }
        return result
    }
}

/**
 * A ListBox (lstFeat, lstSub, lstGroup) in the controls colours, with Windows' selection colours.
 * An item is chosen as the button goes down, as a ListBox does; [onPress] also gets whether Ctrl
 * was held (lstGroup's extended selection). [dragItem] lets an item be dragged; [dropKey] makes
 * the list a drop target.
 */
@Composable
private fun ListBox(
    theme: Theme,
    items: List<String>,
    isSelected: (Int) -> Boolean,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    screen: FeatsScreenState,
    dragItem: ((Int) -> FeatDrag?)? = null,
    dropKey: Any? = null,
    onPress: (Int, Boolean) -> Unit,
    onDoubleClick: () -> Unit = {},
) {
    var modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
    if (dropKey != null) {
        modifier = modifier.dropTarget(screen.drag, dropKey)
    }
    val clicks = remember { ClickTimer() }
    val scroll = rememberScrollState()
    val inner = if (scroll.maxValue in 1 until Int.MAX_VALUE) width - 2 - SCROLL_BAR else width - 2
    Box(
        modifier = modifier
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
    ) {
        Column(modifier = Modifier.offset(1.dp, 1.dp).size(inner.dp, (height - 2).dp).verticalScroll(scroll)) {
            for (i in items.indices) {
                var back = Color.Transparent
                var color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
                if (isSelected(i)) {
                    back = HIGHLIGHT
                    color = HIGHLIGHT_TEXT
                }
                var itemModifier = Modifier.size(inner.dp, ITEM_HEIGHT.dp).background(back)
                    .pointerInput(i, items.size) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press) {
                                    screen.lastButton = pressedButton(event)
                                    if (screen.lastButton == PointerButton.PRIMARY && clicks.isDoubleClick(i)) {
                                        onDoubleClick()
                                    } else if (screen.lastButton == PointerButton.PRIMARY) {
                                        onPress(i, event.keyboardModifiers.isCtrlPressed)
                                    }
                                }
                            }
                        }
                    }
                if (dragItem != null) {
                    val item = dragItem(i)
                    if (item != null) {
                        itemModifier = itemModifier.dragSource(screen.drag, item, onStart = { dragged ->
                            screen.lastButton == PointerButton.PRIMARY && screen.startDrag(dragged)
                        }) { dragged, target -> screen.dropped(dragged, target) }
                    }
                }
                Box(modifier = itemModifier) {
                    BasicText(
                        items[i],
                        style = TextStyle(fontSize = FONT_SIZE, color = color),
                        maxLines = 1,
                        modifier = Modifier.offset(2.dp, 0.dp),
                    )
                }
            }
        }
        ScrollBar(theme, scroll, width - 1 - SCROLL_BAR, 1, height - 2)
    }
}

/** The feat being dragged, next to the pointer (VB shows the system's drag cursor). */
@Composable
private fun FeatDragGhost(screen: FeatsScreenState, theme: Theme) {
    val item = screen.drag.dragging ?: return
    var text = ""
    if (item is FeatDrag.FromList) {
        text = item.feat.abbreviation
    } else if (item is FeatDrag.FromSlot) {
        text = screen.rows().getOrNull(item.row - 1)?.display ?: ""
    }
    if (text.isEmpty()) {
        return
    }
    val density = LocalDensity.current.density
    val at = screen.drag.position
    Box(
        modifier = Modifier.offset((at.x / density + 12).dp, (at.y / density + 8).dp)
            .background(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)),
    ) {
        BasicText(text, style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)), modifier = Modifier.offset(4.dp, 1.dp))
    }
}
