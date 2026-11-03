package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** frmSpells' client width and the top of its footer, in pixels at 100%. */
private const val SPELLS_WIDTH = 815
private const val SPELLS_FOOTER_TOP = 492

/**
 * The Spells window (Source/Forms/Build/frmSpells.frm): the spell slots of one class and spell
 * level, the class list (when there's more than one) and level spinner, the spell list, details,
 * and the footer ("< Feats", "Enhancements >"). Laid out from the form's positions
 * (tools/form-layout's frmSpells.md); the client area is 815 x 518 at 100%.
 *
 * [onNavigate] gets the footer's links.
 */
@Composable
fun SpellsWindowContent(state: PrototypeState, onNavigate: (String) -> Unit) {
    val screen = state.spells
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + screen.revision // read so this redraws after changes
    val density = LocalDensity.current.density
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var comboOpen by remember { mutableStateOf(false) }
    val label = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val control = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))

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
        if (state.rules != null && screen.spellClass != null) {
            val rows = screen.rows()
            val rules = ddolite.builder.build.SpellRules(state.rules!!)
            UserList(
                theme,
                listOf(
                    ListColumn("Acquired", "Pact @ 19", TextAlign.Center),
                    ListColumn("Spells", "", TextAlign.Center, slot = true),
                ),
                rows.size,
                cellText = { row, _ -> rows.getOrNull(row - 1)?.let { rules.levelText(it) } ?: "" },
                slot = { row -> SlotContent(rows.getOrNull(row - 1)?.spell ?: "", false) },
                dropState = { row -> screen.dropStates.getOrNull(row - 1) ?: DropState.DEFAULT },
                hoverRow = screen.hoverRow,
                left = 64, top = 56, width = 409, height = 201, maxRows = 21,
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
            )
            // cboClass, shown only with more than one casting class (LoadData).
            if (screen.classes.size > 1) {
                ComboBox(theme, control, screen.spellClass?.name ?: "", 504, 56, 121, 21) { comboOpen = !comboOpen }
            }
            Label("Level:", label, 645, 58)
            Spinner(
                value = screen.spellLevel,
                min = 1,
                max = screen.maxSpellLevel(),
                theme = theme,
                onValueChange = { screen.chooseLevel(it) },
                modifier = Modifier.offset(687.dp, 56.dp).size(65.dp, 21.dp),
            )
            val names = ArrayList<String>()
            for (s in screen.spells) {
                names.add(s.display)
            }
            ListBox(
                theme, names, { i -> i == screen.listIndex }, 504, 80, 249, 370, screen.drag,
                onPress = { i, _ -> screen.pressSpell(i) },
                onDoubleClick = { _ -> screen.chooseSpell() },
                dragItem = { i -> SpellDrag.FromList(screen.spells[i].display) },
                onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startDrag(item) },
                onDrop = { item, target -> screen.dropped(item, target) },
                dropKey = SpellListKey,
            )
            val rare = screen.rareNote()
            if (rare != null) {
                Label(rare, label, 504, 456)
            }
            BasicText(
                screen.details.caption,
                style = label.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(64.dp, 272.dp).width(409.dp),
            )
            DetailsBox(theme, screen.details.lines, 64, 288, 409, 161)
            if (comboOpen) {
                val classes = screen.classes
                val classNames = ArrayList<String>()
                for (c in classes) {
                    classNames.add(c.name)
                }
                DropList(theme, control, classNames, 504, 77, 121) { index ->
                    comboOpen = false
                    screen.chooseClass(classes[index])
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            HeaderBar(theme, emptyList(), null, listOf("Help"), isEnabled = { false }, onClick = {})
        }
        Box(modifier = Modifier.offset(0.dp, SPELLS_FOOTER_TOP.dp).size(SPELLS_WIDTH.dp, 26.dp)) {
            // usrFooter_Click (:240).
            HeaderBar(theme, listOf("< Feats"), null, listOf("Enhancements >"), isEnabled = { true }, onClick = { link -> onNavigate(link) })
        }
        // The drag's spell next to the pointer.
        val dragging = screen.drag.dragging
        if (dragging != null) {
            var text = ""
            if (dragging is SpellDrag.FromList) {
                text = dragging.display.trim()
            } else if (dragging is SpellDrag.FromSlot) {
                text = screen.rows().getOrNull(dragging.row - 1)?.spell ?: ""
            }
            if (text.isNotEmpty()) {
                val at = screen.drag.position
                Box(
                    modifier = Modifier.offset((at.x / density + 12).dp, (at.y / density + 8).dp)
                        .background(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND))
                        .border(1.dp, theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)),
                ) {
                    BasicText(text, style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)), modifier = Modifier.offset(4.dp, 1.dp))
                }
            }
        }
        val at = menuAt
        if (at != null && screen.menuRow != 0) {
            val items = screen.menuItems()
            val captions = ArrayList<String>()
            for (item in items) {
                captions.add(item.first)
            }
            PopupMenu(captions, -1, at, density, onChoose = { index ->
                menuAt = null
                if (items[index].second) {
                    screen.chooseMenu(items[index].first)
                } else {
                    screen.closeMenu()
                }
            }, onDismiss = {
                menuAt = null
                screen.closeMenu()
            }, clientWidth = SPELLS_WIDTH)
        }
    }
}
