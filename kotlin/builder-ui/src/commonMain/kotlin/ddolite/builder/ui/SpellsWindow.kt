package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddolite.builder.build.DropState
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** frmSpells' client area, in pixels at 100%. */
private const val SPELLS_WIDTH = 815
private const val SPELLS_HEIGHT = 518

/*
 * The layout, from the content corner (WindowFrame): the spell slots (headers on the first line)
 * over the details on the left; on the right the class drop-down and the level spinner on the first
 * line, and the spell list under them, ending level with the details.
 */
private const val SLOTS_WIDTH = 409
private const val SLOTS_HEIGHT = LINE + 171
private const val DETAILS_CAPTION_TOP = SLOTS_HEIGHT + 15
private const val DETAILS_TOP = DETAILS_CAPTION_TOP + LINE
private const val DETAILS_HEIGHT = 161
private const val DETAILS_BOTTOM = DETAILS_TOP + DETAILS_HEIGHT
private const val SPELLS_LEFT = SLOTS_WIDTH + GAP
private const val CLASS_WIDTH = 121
private const val LEVEL_SPINNER_WIDTH = 65
private const val SPELL_LIST_TOP = COMBO_HEIGHT + TIGHT

/**
 * The Spells window (Source/Forms/Build/frmSpells.frm): the spell slots of one class and spell
 * level, the class list (when there's more than one) and level spinner, the spell list, details,
 * and the footer ("< Feats", "Enhancements >"), in a [WindowFrame]; the client area is 815 x 518 at
 * 100%. Kotlin layout: placed from the content corner (VB's form, tools/form-layout's frmSpells.md,
 * centres the slots).
 *
 * [onNavigate] gets the footer's links.
 */
@Composable
fun SpellsWindowContent(state: BuilderSession, onNavigate: (String) -> Unit) {
    val screen = state.spells
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + screen.revision // read so this redraws after changes
    val density = LocalDensity.current.density
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var comboOpen by remember { mutableStateOf(false) }
    val label = labelStyle(theme)
    val control = controlStyle(theme)

    WindowFrame(
        theme, SPELLS_WIDTH, SPELLS_HEIGHT,
        modifier = Modifier.trackPointer { pointer = it },
        header = {
            HeaderBar(theme, emptyList(), null, listOf("Help"), isEnabled = { true }, onClick = { state.help.show("Spells") })
        },
        footer = {
            // usrFooter_Click (:240).
            HeaderBar(theme, listOf("< Feats"), null, listOf("Enhancements >"), isEnabled = { true }, onClick = { link -> onNavigate(link) })
        },
        overlay = {
            SpellDragGhost(screen, theme)
            val at = menuAt
            if (at != null && screen.menuRow != 0) {
                // Disabled items are grey and can't be chosen (as VB's).
                val items = screen.menuItems()
                PopupMenu(items, at, density, onChoose = { index ->
                    menuAt = null
                    screen.chooseMenu(items[index].caption)
                }, onDismiss = {
                    menuAt = null
                    screen.closeMenu()
                }, clientWidth = SPELLS_WIDTH)
            }
        },
    ) { area ->
        val buildRules = state.rules
        if (buildRules != null && screen.spellClass != null) {
            val rows = screen.rows()
            val rules = ddolite.builder.build.SpellRules(buildRules)
            val rightWidth = area.width - SPELLS_LEFT
            UserList(
                theme,
                listOf(
                    ListColumn("Acquired", "Pact @ 19", TextAlign.Center),
                    ListColumn("Spells", "", TextAlign.Center, slot = true),
                ),
                rows.size,
                cellText = { row, _ ->
                    val slot = rows.getOrNull(row - 1)
                    if (slot == null) "" else rules.levelText(slot)
                },
                slot = { row -> SlotContent(rows.getOrNull(row - 1)?.spell ?: "", false) },
                dropState = { row -> screen.dropStates.getOrNull(row - 1) ?: DropState.DEFAULT },
                hoverRow = screen.hoverRow,
                left = 0, top = 0, width = SLOTS_WIDTH, height = SLOTS_HEIGHT, maxRows = 21,
                drag = screen.drag,
                rowKey = { row -> SpellSlotKey(row) },
                dragItem = { row -> SpellDrag.FromSlot(row) },
                onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startDrag(item) },
                onDrop = { item, target -> screen.dropped(item, target) },
                onPress = { row, button ->
                    screen.clickSlot(row, button)
                    if (screen.menuRow != 0) {
                        menuAt = pointer
                    }
                },
                onDoubleClick = { row -> screen.doubleClickSlot(row) },
                onHover = { row -> screen.hoverRow = row },
                headerHeight = LINE,
            )
            // cboClass, shown only with more than one casting class (LoadData).
            if (screen.classes.size > 1) {
                ComboBox(theme, control, screen.spellClass?.name ?: "", SPELLS_LEFT, 0, CLASS_WIDTH, COMBO_HEIGHT) { comboOpen = !comboOpen }
            }
            // The level spinner ends at the right margin, its caption before it.
            val spinnerLeft = area.width - LEVEL_SPINNER_WIDTH
            Label("Level:", label, spinnerLeft - 42, 2)
            Spinner(
                value = screen.spellLevel,
                min = 1,
                max = screen.maxSpellLevel(),
                theme = theme,
                onValueChange = { screen.chooseLevel(it) },
                modifier = Modifier.offset(spinnerLeft.dp, 0.dp).size(LEVEL_SPINNER_WIDTH.dp, COMBO_HEIGHT.dp),
            )
            val names = ArrayList<String>()
            for (s in screen.spells) {
                names.add(s.display)
            }
            ListBox(
                theme, names, { i -> i == screen.listIndex }, SPELLS_LEFT, SPELL_LIST_TOP, rightWidth, DETAILS_BOTTOM - SPELL_LIST_TOP, screen.drag,
                onPress = { i, _ -> screen.pressSpell(i) },
                onDoubleClick = { _ -> screen.chooseSpell() },
                dragItem = { i -> SpellDrag.FromList(screen.spells[i].display) },
                onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startDrag(item) },
                onDrop = { item, target -> screen.dropped(item, target) },
                dropKey = SpellListKey,
            )
            val rare = screen.rareNote()
            if (rare != null) {
                Label(rare, label, SPELLS_LEFT, DETAILS_BOTTOM + 6)
            }
            BasicText(
                screen.details.caption,
                style = label.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(0.dp, DETAILS_CAPTION_TOP.dp).width(SLOTS_WIDTH.dp),
            )
            DetailsBox(theme, screen.details.lines, 0, DETAILS_TOP, SLOTS_WIDTH, DETAILS_HEIGHT)
            if (comboOpen) {
                val classes = screen.classes
                val classNames = ArrayList<String>()
                for (c in classes) {
                    classNames.add(c.name)
                }
                DropList(theme, control, classNames, SPELLS_LEFT, COMBO_HEIGHT, CLASS_WIDTH) { index ->
                    comboOpen = false
                    screen.chooseClass(classes[index])
                }
            }
        }
    }
}

/** The dragged spell next to the pointer (window coordinates). */
@Composable
private fun SpellDragGhost(screen: SpellsScreenState, theme: Theme) {
    val dragging = screen.drag.dragging ?: return
    var text = ""
    if (dragging is SpellDrag.FromList) {
        text = dragging.display.trim()
    } else if (dragging is SpellDrag.FromSlot) {
        text = screen.rows().getOrNull(dragging.row - 1)?.spell ?: ""
    }
    DragGhost(theme, text, screen.drag.position)
}
