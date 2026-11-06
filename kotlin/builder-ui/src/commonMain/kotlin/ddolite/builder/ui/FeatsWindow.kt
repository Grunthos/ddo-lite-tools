package ddolite.builder.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddolite.builder.build.BuildRules
import ddolite.builder.build.DropState
import ddolite.builder.build.FeatListEntry
import ddolite.builder.build.FeatTab
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** frmFeats' client area, in pixels at 100%. */
private const val FEATS_WIDTH = 815
private const val FEATS_HEIGHT = 593

/*
 * The layout, from the content corner (WindowFrame): the feat slots on the left, with their
 * headers on the first line; to the right the feat and selector lists under their captions, and
 * under them the details beside the filter list, both ending level with the slots.
 */

/** usrList: at the content corner. Its column headers sit on the first line, its border a line down; it ends
 * where every window's boxes end. */
private const val LIST_WIDTH = 409

/** The feat list and the selector list beside it, under their captions. */
private const val FEATS_LEFT = LIST_WIDTH + GAP
private const val FEATS_LIST_WIDTH = 185
private const val SELECTORS_LEFT = FEATS_LEFT + FEATS_LIST_WIDTH + GAP
private const val LISTS_TOP = LINE
private const val LISTS_HEIGHT = 282

/** The details caption, then the box (under the feat list) beside the filter list (under the selectors). */
private const val DETAILS_CAPTION_TOP = LISTS_TOP + LISTS_HEIGHT + 10
private const val DETAILS_TOP = DETAILS_CAPTION_TOP + LINE

/**
 * The Feats window (Source/Forms/Build/frmFeats.frm): the header (Selected, the channel tabs if
 * "Channels" is on, Granted; Help), the feat slots (usrList), the feat and selector lists, the
 * details box and the filter list, and the footer ("< Skills", "Channels", "Spells >" or
 * "Enhancements >"). The client area is 815 x 593 at 100%.
 *
 * [onNavigate] gets the footer's links.
 */
@Composable
fun FeatsWindowContent(state: BuilderSession, onNavigate: (String) -> Unit) {
    val screen = state.feats
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + screen.revision // read so this redraws after changes
    val rules = state.rules
    val density = LocalDensity.current.density
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }

    WindowFrame(
        theme, FEATS_WIDTH, FEATS_HEIGHT,
        modifier = Modifier.trackPointer { pointer = it },
        header = {
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
        },
        footer = {
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
        },
        overlay = {
            // usrchkChannels, in the footer (window coordinates).
            if (screen.hasChannels()) {
                CheckBox(theme, "Channels", screen.channels, true, 368, FEATS_HEIGHT - BAR_HEIGHT + 8, 113) { screen.changeChannels(!screen.channels) }
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
        },
    ) { area ->
        if (rules != null) {
            FeatsBody(screen, rules, theme, area) { menuAt = pointer }
        }
    }
}

@Composable
private fun FeatsBody(screen: FeatsScreenState, rules: BuildRules, theme: Theme, area: ContentArea, onMenu: () -> Unit) {
    // The slots, and the details and filter boxes, end where every window's boxes end.
    val detailsHeight = area.boxesBottom - DETAILS_TOP
    val label = labelStyle(theme)
    val link = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_LINK))
    FeatSlots(screen, rules, theme, area.boxesBottom, onMenu)
    val granted = screen.tab == FeatTab.GRANTED
    if (!granted) {
        // lblFeats, usrchkShowAll (moved to its right by FeatListTitle), lblSelectors.
        Label(screen.listTitle, label, FEATS_LEFT, 0)
        CheckBox(theme, "Show All", screen.showAll, true, FEATS_LEFT + 96, -2, FEATS_LIST_WIDTH - 96) { screen.changeShowAll(!screen.showAll) }
        Label("Selectors", label, SELECTORS_LEFT, 0)
        val featNames = ArrayList<String>()
        for (feat in screen.feats) {
            featNames.add(screen.listCaption(feat))
        }
        ListBox(
            theme, featNames, { i -> i == screen.featIndex }, FEATS_LEFT, LISTS_TOP, FEATS_LIST_WIDTH, LISTS_HEIGHT, screen.drag,
            onPress = { i, _ -> screen.clickFeat(i) },
            onDoubleClick = { _ -> screen.doubleClickList() },
            dragItem = { i -> FeatDrag.FromList(screen.feats[i]) },
            onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startDrag(item) },
            onDrop = { item, target -> screen.dropped(item, target) },
            dropKey = FeatListKey,
        )
        val selectorNames = ArrayList<String>()
        for (s in screen.selectors) {
            selectorNames.add(s.name)
        }
        ListBox(
            theme, selectorNames, { i -> i == screen.selectorIndex }, SELECTORS_LEFT, LISTS_TOP, area.width - SELECTORS_LEFT, LISTS_HEIGHT, screen.drag,
            onPress = { i, _ -> screen.clickSelector(i) },
            onDoubleClick = { _ -> screen.doubleClickList() },
            dragItem = { _ ->
                val feat = screen.feats.getOrNull(screen.featIndex)
                if (feat == null) null else FeatDrag.FromList(feat)
            },
            onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startDrag(item) },
            onDrop = { item, target -> screen.dropped(item, target) },
        )
    }
    // Details: lblDetails (a link that widens or restores the box), >> and <<, usrDetails.
    val details = screen.details
    val expanded = screen.detailsExpanded
    // Under the feat list, or widened over the filter list out to the right margin.
    var detailsWidth = FEATS_LIST_WIDTH
    if (expanded) {
        detailsWidth = area.width - FEATS_LEFT
    }
    BasicText(
        details.caption,
        style = label.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.offset(FEATS_LEFT.dp, DETAILS_CAPTION_TOP.dp).width((detailsWidth - 22).dp).clickable { screen.toggleDetails() },
    )
    if (!granted) {
        // lblExpand ">>" while normal size, lblRestore "<<" while widened (when shown): at the box's right.
        if (!expanded) {
            BasicText(">>", style = link, modifier = Modifier.offset((FEATS_LEFT + detailsWidth - 22).dp, DETAILS_CAPTION_TOP.dp).clickable { screen.toggleDetails() })
        } else if (screen.restoreLinkShown) {
            BasicText("<<", style = link, modifier = Modifier.offset((FEATS_LEFT + detailsWidth - 22).dp, DETAILS_CAPTION_TOP.dp).clickable { screen.toggleDetails() })
        }
    }
    DetailsBox(theme, details.lines, FEATS_LEFT, DETAILS_TOP, detailsWidth, detailsHeight)
    if (!expanded) {
        // The same height as the details box (VB's runs 8 pixels lower).
        Label("Filter", label, SELECTORS_LEFT, DETAILS_CAPTION_TOP)
        ListBox(
            theme, screen.groupNames(), { i -> screen.groupSelection.contains(i) }, SELECTORS_LEFT, DETAILS_TOP, area.width - SELECTORS_LEFT, detailsHeight, screen.drag,
            onPress = { i, ctrl -> screen.clickGroup(i, ctrl) },
        )
    }
}

/**
 * The feat slots (usrList, the shared [UserList]): Source, Level, the feat and BAB on the Selected
 * and channel tabs; Source, the granted feat and Level on Granted. Each slot shows its drop state,
 * an error in red and the row under the mouse; a left press chooses it, a right press opens the menu,
 * a double-click clears it, and a left drag moves its feat. Rows are numbered from 1.
 */
@Composable
private fun FeatSlots(screen: FeatsScreenState, rules: BuildRules, theme: Theme, height: Int, onMenu: () -> Unit) {
    val rows = screen.rows()
    val granted = screen.tab == FeatTab.GRANTED
    // DefineColumn: (header, size text, alignment); the slot column takes what's left.
    val columns: List<ListColumn>
    if (granted) {
        columns = listOf(
            ListColumn("Source", "Arcane Trickster", TextAlign.Start), // MAX_CLASS_NAME
            ListColumn("Granted Feat", "", TextAlign.Center, slot = true),
            ListColumn("Level", "20", TextAlign.End),
        )
    } else {
        columns = listOf(
            ListColumn("Source", "Legendary", TextAlign.Start),
            ListColumn("Level", "Level", TextAlign.End),
            ListColumn("Feat", "", TextAlign.Center, slot = true),
            ListColumn("BAB", "BAB", TextAlign.Center),
        )
    }
    UserList(
        theme,
        columns,
        rows.size,
        cellText = { row, col ->
            val entry = rows.getOrNull(row - 1)
            if (entry == null) "" else rowTexts(entry, rules, granted)[col]
        },
        slot = { row ->
            val entry = rows.getOrNull(row - 1)
            if (entry == null) SlotContent("", false) else SlotContent(entry.display, entry.errorState)
        },
        dropState = { row -> screen.dropStates.getOrNull(row - 1) ?: DropState.DEFAULT },
        hoverRow = screen.hoverRow,
        left = 0, top = 0, width = LIST_WIDTH, height = height,
        headerHeight = LINE,
        drag = screen.drag,
        rowKey = { row -> FeatSlotKey(row) },
        dragItem = { row -> FeatDrag.FromSlot(row) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startDrag(item) },
        onDrop = { item, target -> screen.dropped(item, target) },
        onPress = { row, button ->
            screen.clickSlot(row, button)
            if (screen.menuRow != 0) {
                onMenu()
            }
        },
        onDoubleClick = { row -> screen.doubleClickSlot(row) },
        onHover = { row -> screen.hoverRow = row },
    )
}

/** The texts of a row's columns (the slot's own column is the feat): InitSelectedFeatsList (:505), ShowGrantedFeats (:479). */
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
    DragGhost(theme, text, screen.drag.position)
}
