package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue

/** Both prototype screens, with tabs to switch between them. */
class PrototypeState(val skills: SkillsScreenState, val trees: TreeSlotsState) {
    /** 0 = skills grid, 1 = enhancement tree slots. */
    var tab by mutableIntStateOf(0)
}

/** The prototype's whole window: a tab bar, the chosen screen, and anything being dragged. */
@Composable
fun PrototypeScreen(state: PrototypeState) {
    val theme = state.skills.theme
    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoiceButton(theme, "Skills", selected = state.tab == 0) { state.tab = 0 }
                ChoiceButton(theme, "Enhancement trees", selected = state.tab == 1) { state.tab = 1 }
            }
            if (state.tab == 0) {
                SkillsScreen(state.skills)
            } else {
                TreeSlotsScreen(state.trees, state.skills)
            }
        }
        DragGhost(state.trees.drag, state.skills)
    }
}

/**
 * The tree-slots screen: the trees the build could add on the left, the build's seven tree slots
 * on the right. Drag a tree onto a slot to add it, between slots to swap, back to the list to
 * remove. Prototype of the trees part of frmEnhancements (lstTree and usrTree).
 */
@Composable
fun TreeSlotsScreen(state: TreeSlotsState, skills: SkillsScreenState) {
    val theme = skills.theme
    val text = TextStyle(fontSize = 13.sp, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val dim = text.copy(color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM))
    val ctlText = TextStyle(fontSize = 13.sp, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))

    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Build: " + skills.presets[skills.presetIndex].name + " (Human)", style = text)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Available trees. Also a drop target, so a tree dragged back here is removed.
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .dropTarget(state.drag, AvailableListKey)
                    .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
                    .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText("Available trees", style = ctlText)
                for (tree in state.available()) {
                    Row(
                        modifier = Modifier
                            .dragSource(state.drag, TreeDrag(tree.name, null)) { item, target -> state.dropped(item, target) }
                            .padding(vertical = 3.dp, horizontal = 4.dp),
                    ) {
                        BasicText(tree.name, style = ctlText, modifier = Modifier.width(200.dp))
                        BasicText(tree.source, style = ctlText.copy(color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM)))
                    }
                }
            }

            // The build's tree slots.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("Build trees", style = text)
                for (i in 0 until TREE_SLOTS) {
                    TreeSlot(state, skills, i)
                }
            }
        }
        BasicText(
            "Drag a tree onto a slot to add it (replacing what's there), between slots to swap them, " +
                "or back onto the list to remove it. Works the same with a mouse or touch.",
            style = dim,
        )
    }
}

/**
 * One tree slot. Colours show the drop state, as VB's `userList.SetDropState` does:
 * normal; can drop here (while dragging); and the slot under the pointer.
 */
@Composable
private fun TreeSlot(state: TreeSlotsState, skills: SkillsScreenState, index: Int) {
    val theme = skills.theme
    val treeName = state.slots[index]
    var background = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    var border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_INTERIOR)
    if (state.canDropOn(index)) {
        background = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND)
        border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)
        if (state.drag.hoverTarget == SlotKey(index)) {
            background = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_HIGHLIGHT)
            border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_HIGHLIGHT)
        }
    }
    var modifier = Modifier
        .width(280.dp)
        .dropTarget(state.drag, SlotKey(index))
        .background(background)
        .border(1.dp, border)
    if (treeName != null && !state.isRaceSlot(index)) {
        modifier = modifier.dragSource(state.drag, TreeDrag(treeName, index)) { item, target -> state.dropped(item, target) }
    }
    Box(modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
        var label = "Tree ${index + 1}: (empty)"
        var color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM)
        if (treeName != null) {
            label = "Tree ${index + 1}: $treeName"
            if (state.isRaceSlot(index)) {
                label = label + "  (race tree, fixed)"
            }
            color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
        }
        BasicText(label, style = TextStyle(fontSize = 13.sp, color = color))
    }
}

/** The name of the tree being dragged, following the pointer. */
@Composable
private fun DragGhost(drag: DragDropState<TreeDrag>, skills: SkillsScreenState) {
    val item = drag.dragging
    if (item == null) {
        return
    }
    val theme = skills.theme
    val position = drag.position
    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.toInt() + 12, position.y.toInt() + 8) }
            .background(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_HIGHLIGHT).copy(alpha = 0.9f))
            .border(1.dp, theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_HIGHLIGHT))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        BasicText(item.treeName, style = TextStyle(fontSize = 13.sp, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)))
    }
}

