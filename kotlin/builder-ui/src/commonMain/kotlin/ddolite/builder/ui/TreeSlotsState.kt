package ddolite.builder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.Build
import ddolite.builder.build.BuildRules
import ddolite.builder.build.BuildTree
import ddolite.builder.data.BuilderData
import ddolite.builder.model.TreeStyle
import ddolite.builder.data.BuilderRecordFile
import ddolite.builder.data.ParsedLine
import ddolite.builder.data.parseLine
import ddolite.dataformat.DataSource
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

/** The prototype's builds are all Human. */
const val RACE_NAME = "Human"

/** The most enhancement trees a build can have (frmEnhancements.lstTree_DblClick: `build.Trees > 6`). */
const val TREE_SLOTS = 7

/** The few facts the prototype needs about an enhancement tree. */
class TreeHeader(val name: String, val type: String, val lockout: String?)

/**
 * Reads just the header of each tree in Enhancements.txt (name, Type, Lockout).
 *
 * A stand-in for the prototype only: plan step B2 ports the real loader
 * (`basDataLoad.LoadEnhancements`/`LoadTreeHeader`, Build/basDataLoad.bas:1321 and :1432),
 * which reads the abilities as well.
 */
fun readTreeHeaders(source: DataSource): List<TreeHeader> {
    val result = ArrayList<TreeHeader>()
    val decoded = source.readText(BuilderRecordFile.ENHANCEMENTS.path)
    if (decoded == null) {
        return result
    }
    for (record in splitRecords(decoded.text, BuilderRecordFile.ENHANCEMENTS.marker).records) {
        val lines = record.lines()
        if (lines.isEmpty()) {
            continue
        }
        var type = ""
        var lockout: String? = null
        for (i in 1 until lines.size) {
            if (lines[i].startsWith("AbilityName:")) {
                break // the header ends where the first ability starts
            }
            val parsed = parseLine(lines[i])
            if (parsed is ParsedLine.Field) {
                if (parsed.field == "type") {
                    type = parsed.item
                } else if (parsed.field == "lockout") {
                    lockout = parsed.item
                }
            }
        }
        result.add(TreeHeader(lines[0].trim(), type, lockout))
    }
    return result
}

/** A tree in the "available" list, with where it comes from (a class or race name; empty for global trees). */
class AvailableTree(val name: String, val source: String)

/**
 * What's being dragged: a tree name, and the slot it came from (null if from the available list).
 * A data class so that two equal drags compare equal: [dragSource] restarts its gesture handler when
 * its item changes, and the list is redrawn (with a new TreeDrag) as soon as a press selects a row.
 */
data class TreeDrag(val treeName: String, val fromSlot: Int?)

/** Drop target key for a tree slot. */
data class SlotKey(val index: Int)

/** Drop target key for the available-trees list. */
object AvailableListKey

/**
 * State for the prototype tree-slots screen: the build's chosen enhancement trees and the drag
 * in progress. Ported from the tree handling in frmEnhancements (Source/Forms/Build/frmEnhancements.frm):
 * `ShowAvailableTrees` (:1430), `AddTree` (:1462), `AddBuildTree` (:1564), `SwapBuildTrees` (:1609),
 * and the drop states from `ShowTrees` (:1354) and `usrTree_RequestDrag` (:1553).
 *
 * The first slot always holds the race's own tree (VB: `build.Tree(1)` with `TreeType = tseRace`,
 * which `mnuTrees_Click` (:1287) refuses to delete); it can't be moved, replaced or removed.
 *
 * Differences for the prototype: slots are fixed (VB grows `build.Tree` as trees are added), and
 * dragging a tree from a slot back onto the list removes it (VB uses a menu for that).
 */
class TreeSlotsState(
    private val data: BuilderData,
    private val trees: List<TreeHeader>,
    private val skills: SkillsScreenState,
) {
    /** The chosen tree in each slot; null for an empty slot. */
    val slots = mutableStateListOf<String?>()

    val drag = DragDropState<TreeDrag>()

    /** The tree selected in the available list (VB: lstTree.ListIndex), or null. */
    var selectedTree: String? by mutableStateOf(null)

    /** The selected slot (VB: usrTree.Selected, 0 = none; here null = none), or null. */
    var selectedSlot: Int? by mutableStateOf(null)

    /** Whether the slot being pressed was already selected when the press began (for the click toggle). */
    private var slotWasSelected = false

    /** Which tab of the Enhancements window is showing (VB: mlngTab; 0 Trees, 1 Enhancements, 2 Leveling Guide). */
    var tab by mutableIntStateOf(0)

    init {
        for (i in 0 until TREE_SLOTS) {
            slots.add(null)
        }
        slots[0] = RACE_NAME
    }

    /** True for the race's own tree slot, which is fixed (wherever it is: an opened build may list it last). */
    fun isRaceSlot(slot: Int): Boolean = slots[slot] == skills.raceName

    /**
     * Shows an opened build's trees, in its order (`build.Tree(1 To .Trees)`). The rules' InitBuildTrees has
     * already added the race tree and dropped trees the build can't use.
     */
    fun openBuild(build: Build) {
        selectedTree = null
        selectedSlot = null
        for (i in 0 until TREE_SLOTS) {
            if (i < build.trees.size) {
                slots[i] = build.trees[i].treeName
            } else {
                slots[i] = null
            }
        }
    }

    /**
     * Puts the slots back into `build.Tree` for saving: trees still chosen keep their abilities;
     * a tree added here is set up as frmEnhancements.AddBuildTree (:1564) does, with its source
     * the first of the build's classes that has it (the class the available list shows it under).
     * Empty slots are left out.
     */
    fun storeInto(rules: BuildRules) {
        val build = rules.build
        val old = ArrayList<BuildTree>(build.trees)
        build.trees.clear()
        for (name in slots) {
            if (name == null) {
                continue
            }
            var tree: BuildTree? = null
            for (existing in old) {
                if (existing.treeName == name) {
                    tree = existing
                    break
                }
            }
            if (tree == null) {
                tree = newBuildTree(rules, name)
            }
            build.trees.add(tree)
        }
        rules.initLevelingGuide()
    }

    /** A tree just added to the build. Ported from frmEnhancements.AddBuildTree (:1564) and ClassLevels (:1595). */
    private fun newBuildTree(rules: BuildRules, name: String): BuildTree {
        val tree = BuildTree(name)
        for (split in rules.classSplit()) {
            if (split.characterClass.trees.contains(name)) {
                tree.source = split.characterClass
                break
            }
        }
        val source = tree.source
        if (source == null) {
            tree.classLevels = rules.build.maxLevels
            val dataTree = data.findTree(name)
            if (dataTree != null) {
                tree.treeType = dataTree.style
            }
        } else {
            tree.classLevels = 0
            for (level in 1..rules.heroicLevels()) {
                if (rules.build.levelClass(level) == source) {
                    tree.classLevels = tree.classLevels + 1
                }
            }
            tree.treeType = TreeStyle.CLASS
        }
        return tree
    }

    /**
     * The trees the build could add: its classes' trees, then its race's trees, then global trees,
     * leaving out any already chosen or locked out by a chosen tree.
     * Ported from `ShowAvailableTrees` (:1430) and `AddTree` (:1462).
     */
    fun available(): List<AvailableTree> {
        val result = ArrayList<AvailableTree>()
        // Classes, in the order they first appear in the build (VB: GetClassSplit order).
        val seen = ArrayList<String>()
        for (level in 1..skills.grid.levels) {
            val c = skills.grid.classAt(level)
            if (!seen.contains(c.name)) {
                seen.add(c.name)
                for (treeName in c.trees) {
                    addIfAllowed(result, treeName, c.name)
                }
            }
        }
        val race = data.findRace(skills.raceName)
        if (race != null) {
            for (treeName in race.trees) {
                addIfAllowed(result, treeName, race.name)
            }
        }
        for (tree in trees) {
            if (tree.type.equals("Global", ignoreCase = true)) {
                addIfAllowed(result, tree.name, "")
            }
        }
        return result
    }

    /** `AddTree`: skip trees already chosen, already listed, missing from Enhancements.txt, or locked out. */
    private fun addIfAllowed(list: MutableList<AvailableTree>, treeName: String, source: String) {
        val tree = findTree(treeName)
        if (tree == null) {
            return // VB: SeekTree returns 0, so the tree isn't listed
        }
        if (slots.contains(tree.name)) {
            return
        }
        for (existing in list) {
            if (existing.name == tree.name) {
                return
            }
        }
        val lockout = tree.lockout
        if (lockout != null && slots.contains(lockout)) {
            return
        }
        list.add(AvailableTree(tree.name, source))
    }

    private fun findTree(name: String): TreeHeader? {
        for (tree in trees) {
            if (tree.name.equals(name, ignoreCase = true)) {
                return tree
            }
        }
        return null
    }

    /**
     * Whether [slot] would accept the current drag, for colouring. From `ShowTrees(True, …)` and
     * `usrTree_RequestDrag` (:1553): every slot can take a drop except the one being dragged from
     * (and, in the prototype, the race's fixed slot).
     */
    fun canDropOn(slot: Int): Boolean {
        val dragging = drag.dragging
        if (dragging == null) {
            return false
        }
        return dragging.fromSlot != slot && !isRaceSlot(slot)
    }

    /**
     * The pointer went down on a tree in the available list: select it at once, before any drag.
     * Ported from frmEnhancements.lstTree_MouseDown (:1501), which clears the slot selection
     * (`usrTree.Selected = 0`); the ListBox itself selects the row on mouse down.
     */
    fun pressListTree(treeName: String) {
        selectedSlot = null
        selectedTree = treeName
    }

    /**
     * The pointer went down on a slot: select it at once, so it shows before any drag starts.
     * VB selects on click instead (usrTree_SlotClick, :1267); selecting on press gives the
     * immediate feedback. Clears the list selection, as VB does (`lstTree.ListIndex = -1`).
     */
    fun pressSlot(slot: Int) {
        selectedTree = null
        slotWasSelected = selectedSlot == slot
        selectedSlot = slot
    }

    /**
     * A slot was clicked (pressed and released without dragging). Left-clicking an already
     * selected slot deselects it, as in usrTree_SlotClick (:1267):
     * `If Me.usrTree.Selected = Index Then Me.usrTree.Selected = 0 Else Me.usrTree.Selected = Index`.
     */
    fun clickSlot(slot: Int) {
        if (slotWasSelected && selectedSlot == slot) {
            selectedSlot = null
        }
    }

    /**
     * A drag ended over [target]. From `usrTree_OLEDragDrop` (:1537): a tree from the list goes
     * into the slot (`AddBuildTree`, replacing what was there); a tree from another slot swaps
     * places with it (`SwapBuildTrees`).
     */
    fun dropped(item: TreeDrag, target: Any?) {
        selectedTree = null // the list is rebuilt after a drop (VB: ShowAvailableTrees)
        if (target is SlotKey && !isRaceSlot(target.index)) {
            selectedSlot = target.index // the slot that received the tree
            val from = item.fromSlot
            if (from == null) {
                slots[target.index] = item.treeName
            } else if (from != target.index) {
                val other = slots[target.index]
                slots[target.index] = item.treeName
                slots[from] = other
            }
        } else if (target == AvailableListKey) {
            // Prototype only: drag a chosen tree back to the list to remove it.
            val from = item.fromSlot
            if (from != null) {
                slots[from] = null
                selectedSlot = null
            }
        }
        onChanged()
    }

    /** Called after each change the user makes, so the app can copy the trees into the build. */
    var onChanged: () -> Unit = {}
}
