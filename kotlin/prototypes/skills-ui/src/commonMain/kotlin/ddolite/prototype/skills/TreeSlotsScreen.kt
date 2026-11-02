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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.builder.model.Skill
import ddolite.builder.rules.formatRanks
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

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
 * The Enhancements window: header with its three tabs (Trees, Enhancements, Leveling Guide), the
 * current tab, and the footer. Laid out like frmEnhancements: usrHeader, the picTab(0..2) pages
 * (only one visible at a time, `ChangeTab` :1120), then usrFooter. Only the Trees tab exists in the
 * prototype. The drag ghost is drawn over everything.
 */
@Composable
fun TreeSlotsWindowContent(state: PrototypeState) {
    val theme = state.skills.theme
    val trees = state.trees
    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // usrHeader: LeftLinks "Trees;Enhancements;Leveling Guide", RightLinks "Help" (usrHeader_Click :1082).
            HeaderBar(
                theme,
                listOf("Trees", "Enhancements", "Leveling Guide"),
                trees.tab,
                listOf("Help"),
                isEnabled = { link -> link != "Help" },
                onClick = { link ->
                    if (link == "Trees") {
                        trees.tab = 0
                    } else if (link == "Enhancements") {
                        trees.tab = 1
                    } else if (link == "Leveling Guide") {
                        trees.tab = 2
                    }
                },
            )
            Box(modifier = Modifier.weight(1f)) {
                if (trees.tab == 0) {
                    TreeSlotsScreen(trees, state.skills)
                } else {
                    NotInPrototype(theme, "This tab isn't in the prototype yet.")
                }
            }
            // usrFooter: "< Previous" (Spells or Feats) and "Destiny >" (usrFooter_Click :1092).
            HeaderBar(theme, listOf("< Previous"), null, listOf("Destiny >"), isEnabled = { false }, onClick = {})
        }
        DragGhost(trees.drag, state.skills)
    }
}

/**
 * The Skills window: header (tabs "Skill Ranks" and "Skill Tomes"; links "Contrast" and "Help"),
 * the skills grid, and the footer ("< Stats", "Feats >"), as in frmSkills.
 */
@Composable
fun SkillsWindowContent(state: PrototypeState) {
    val theme = state.skills.theme
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
        HeaderBar(
            theme,
            listOf("Skill Ranks", "Skill Tomes"),
            tab,
            listOf("Contrast", "Help"),
            isEnabled = { link -> link == "Skill Ranks" || link == "Skill Tomes" },
            onClick = { link ->
                if (link == "Skill Ranks") {
                    tab = 0
                } else if (link == "Skill Tomes") {
                    tab = 1
                }
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            if (tab == 0) {
                SkillsScreen(state.skills)
            } else {
                NotInPrototype(theme, "Skill tomes aren't in the prototype yet.")
            }
        }
        HeaderBar(theme, listOf("< Stats"), null, listOf("Feats >"), isEnabled = { false }, onClick = {})
    }
}

/** Placeholder text for parts of a window the prototype doesn't have. */
@Composable
private fun NotInPrototype(theme: Theme, message: String) {
    BasicText(
        message,
        modifier = Modifier.padding(16.dp),
        style = TextStyle(fontSize = 13.sp, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)),
    )
}
/**
 * The main window's content: a stand-in for frmMain's output view (the build summary that VB
 * regenerates as the build changes, `basOutput.GenerateOutput`, Build/basOutput.bas:154). Uses the workspace colours
 * (.screen themes have no output colours; VB keeps those in separate output colour files). Shows the chosen build and the
 * skill ranks, so changes made in the Skills window can be seen arriving in another window.
 */
@Composable
fun MainWindowContent(state: PrototypeState) {
    val skills = state.skills
    val theme = skills.theme
    val text = TextStyle(fontSize = 13.sp, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val revision = skills.revision // read so this redraws when the build changes
    val lines = ArrayList<String>()
    lines.add("Build: " + skills.presets[skills.presetIndex].name + " (" + RACE_NAME + ")   [revision " + revision + "]")
    lines.add("")
    lines.add("Skills (total ranks):")
    for (skill in Skill.entries) {
        val ranks = skills.grid.totalRanks(skills.spend, skill)
        if (ranks > 0) {
            lines.add("  " + skill.displayName + ": " + formatRanks(ranks))
        }
    }
    lines.add("")
    lines.add("Enhancement trees:")
    for (i in 0 until state.trees.slots.size) {
        val tree = state.trees.slots[i]
        if (tree != null) {
            lines.add("  " + (i + 1) + ". " + tree)
        }
    }
    lines.add("")
    lines.add("Open screens from the Edit menu (or press K / E), in the VB app's order.")
    Box(
        modifier = Modifier.fillMaxSize()
            .background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        BasicText(lines.joinToString("\n"), style = text)
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
                    // The selected row is highlighted from the moment it's pressed (VB: ListBox selection).
                    var rowModifier = Modifier
                        .pressAndClick(onPress = { state.pressListTree(tree.name) }, onClick = {})
                        .dragSource(state.drag, TreeDrag(tree.name, null)) { item, target -> state.dropped(item, target) }
                    var rowText = ctlText
                    var rowDim = ctlText.copy(color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM))
                    if (state.selectedTree == tree.name) {
                        rowModifier = rowModifier
                            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACK_HIGHLIGHT))
                            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_HIGHLIGHT))
                        rowText = ctlText.copy(fontWeight = FontWeight.Bold)
                        rowDim = rowText
                    }
                    Row(modifier = rowModifier.padding(vertical = 3.dp, horizontal = 4.dp)) {
                        BasicText(tree.name, style = rowText, modifier = Modifier.width(200.dp))
                        BasicText(tree.source, style = rowDim)
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
    if (state.selectedSlot == index) {
        // A selected slot is drawn like a drop slot, as userList.Selected does (DropState = dsCanDrop).
        background = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND)
        border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_HIGHLIGHT)
    }
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
        .pressAndClick(onPress = { state.pressSlot(index) }, onClick = { state.clickSlot(index) })
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

