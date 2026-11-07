package ddolite.builder.build

import ddolite.builder.model.TreeStyle

/**
 * The edits the Enhancements and Destinies windows make to the build's trees and their abilities.
 * Taken out of frmEnhancements.frm and frmDestiny2.frm, which do the same things to `build.Tree` and
 * `build.Destiny`; [destinies] picks which list, and which tier 5 tree, is edited.
 *
 * Rows are numbered from 1, as in the windows. The callers refresh their screens and record the
 * change (SetDirty, CascadeChanges).
 */
class TreeEdits(private val rules: BuildRules, private val destinies: Boolean) {
    private val build = rules.build
    private val enhancementRules = EnhancementRules(rules)

    /** `build.Tree` or `build.Destiny`. */
    val list: ArrayList<BuildTree> get() = if (destinies) build.destinies else build.trees

    /** The tier 5 tree or destiny's name. */
    private var tier5: String
        get() = if (destinies) build.destinyTier5 else build.tier5
        set(value) {
            if (destinies) {
                build.destinyTier5 = value
            } else {
                build.tier5 = value
            }
        }

    /** SetTier5 / SetDestinyTier5: the tier 5 tree, removing tier 5 abilities from the others. */
    private fun moveTier5(name: String) {
        if (destinies) {
            enhancementRules.setDestinyTier5(name)
        } else {
            enhancementRules.setTier5(name)
        }
    }

    // ---------------------------------------------------------------- trees

    /**
     * The tree menu's Delete (mnuTrees_Click: frmEnhancements.frm:1287, frmDestiny2.frm:826). The
     * race tree can't be deleted. VB quirk kept: the tier 5 tree stays set. True if deleted.
     */
    fun deleteTree(row: Int): Boolean {
        val bt = list.getOrNull(row - 1) ?: return false
        if (!destinies && bt.treeType == TreeStyle.RACE) {
            return false
        }
        list.removeAt(row - 1)
        return true
    }

    /**
     * "Delete All Trees" (mnuTrees_Click). Enhancements start again with a new race tree. VB quirk
     * kept: the race tree's abilities are lost with the rest.
     */
    fun deleteAllTrees() {
        list.clear()
        if (!destinies) {
            val race = BuildTree(build.race?.name ?: "")
            race.treeType = TreeStyle.RACE
            list.add(race)
        }
    }

    /** "Reset <tree>" (mnuTrees_Click): its abilities go. VB quirk kept: the tier 5 tree stays set. */
    fun resetTree(row: Int) {
        val bt = list.getOrNull(row - 1) ?: return
        bt.abilities.clear()
        bt.missing.clear()
    }

    /** "Reset All Trees" (mnuTrees_Click). */
    fun resetAllTrees() {
        for (bt in list) {
            bt.abilities.clear()
            bt.missing.clear()
        }
    }

    /**
     * A tree row double-clicked (usrTree_SlotDblClick: frmEnhancements.frm:1333, frmDestiny2.frm:866):
     * the tree goes (not the race tree), and stops being the tier 5 tree. True if removed.
     */
    fun removeTree(row: Int): Boolean {
        val bt = list.getOrNull(row - 1) ?: return false
        if (!destinies && bt.treeType == TreeStyle.RACE) {
            return false
        }
        if (tier5 == bt.treeName) {
            tier5 = ""
        }
        list.removeAt(row - 1)
        return true
    }

    /**
     * A new enhancement tree for the build. AddBuildTree (frmEnhancements.frm:1564) with ClassLevels
     * (:1595): a class tree gets that class's heroic levels; anything else the max level and its own type.
     * [sourceClass] is the class column of the available-trees list ("" for none).
     */
    fun newEnhancementTree(name: String, sourceClass: String): BuildTree {
        val bt = BuildTree(name)
        val c = rules.data.findClass(sourceClass)
        if (c == null) {
            bt.classLevels = build.maxLevels
            bt.treeType = rules.data.findTree(name)?.style
        } else {
            bt.source = c
            var levels = 0
            for (level in 1..rules.heroicLevels()) {
                if (build.levelClass(level) == c) {
                    levels++
                }
            }
            bt.classLevels = levels
            bt.treeType = TreeStyle.CLASS
        }
        return bt
    }

    /** A new destiny for the build. AddBuildDestiny (frmDestiny2.frm:1080). */
    fun newDestiny(name: String): BuildTree {
        val bt = BuildTree(name)
        bt.treeType = TreeStyle.DESTINY
        return bt
    }

    /** Puts [tree] in row [row], replacing the tree there or, past the end, adding it. */
    fun putTree(row: Int, tree: BuildTree) {
        if (row > list.size) {
            list.add(tree)
        } else {
            list[row - 1] = tree
        }
    }

    /** A tree row dragged onto another (usrTree_OLEDragDrop): the two change places. */
    fun swapTrees(a: Int, b: Int) {
        val swap = list[a - 1]
        list[a - 1] = list[b - 1]
        list[b - 1] = swap
    }

    /** usrspnRacialAP_Change (frmEnhancements.frm:1236). */
    fun setRacialAp(value: Int) {
        build.racialAP = value
    }

    /** usrspnUniversalAP_Change (frmEnhancements.frm:1255). */
    fun setUniversalAp(value: Int) {
        build.universalAP = value
    }

    /** usrspnPDP_Change (frmDestiny2.frm:790). */
    fun setPermDestinyPoints(value: Int) {
        build.permDestinyPoints = value
    }

    /**
     * What the window does to the build as it loads (LoadData: frmEnhancements.frm:1148,
     * frmDestiny2.frm:707): the tier 5 tree is dropped below level 12 (destinies: below level 29), and
     * the permanent destiny points are raised to the minimum. VB quirk kept (destinies): the tier 5
     * label shows only above level 29, so at exactly 29 it's kept but not shown.
     */
    fun loadDataLimits() {
        if (destinies) {
            if (build.permDestinyPoints < rules.data.tomes.permDestinyPointMin) {
                build.permDestinyPoints = rules.data.tomes.permDestinyPointMin
            }
            if (build.maxLevels < 29 && build.destinyTier5.isNotEmpty()) {
                enhancementRules.shaveDestiny(build.destinyTier5)
                build.destinyTier5 = ""
            }
        } else if (build.maxLevels < 12 && build.tier5.isNotEmpty()) {
            enhancementRules.shaveTree(build.tier5)
            build.tier5 = ""
        }
    }

    // ---------------------------------------------------------------- abilities

    /** The slot menu's "Reset tree" (mnuEnhancements_Click): every ability goes, and it's no longer the tier 5 tree. */
    fun resetAbilities(bt: BuildTree) {
        bt.abilities.clear()
        bt.missing.clear()
        if (tier5 == bt.treeName) {
            moveTier5("")
        }
    }

    /**
     * ClearSlot (frmEnhancements.frm:1962, frmDestiny2.frm:1438): the ability in [row] goes. VB quirk
     * kept: emptying any tree clears the tier 5 tree, even when it's another tree (whose tier 5
     * abilities are then removed). True if a slot was cleared.
     */
    fun clearSlot(bt: BuildTree, row: Int): Boolean {
        if (row < 1 || row > bt.abilities.size) {
            return false
        }
        bt.abilities[row - 1] = bt.abilities[row - 1].copy(ability = BLANK_ABILITY)
        enhancementRules.removeBlanks(bt)
        if (bt.abilities.isEmpty()) {
            moveTier5("")
        } else if (bt.abilities[bt.abilities.size - 1].tier < 5 && tier5 == bt.treeName) {
            moveTier5("")
        }
        return true
    }

    /** A rank arrow (usrList_RankChange): the ability in [row] at [ranks]. Returns it as it was, or null. */
    fun setRanks(bt: BuildTree, row: Int, ranks: Int): BuildAbility? {
        val a = bt.abilities.getOrNull(row - 1) ?: return null
        bt.abilities[row - 1] = a.copy(rank = ranks)
        return a
    }

    /**
     * AddAbility (frmEnhancements.frm:1792, frmDestiny2.frm:1267): [ability] of [tier] inserted in tier
     * order at [ranks] with [selector], or a blank slot to drop onto when [blank]. A tier 5 ability
     * makes this the tier 5 tree. Returns the 0-based position it went in.
     */
    fun insertAbility(bt: BuildTree, tier: Int, ability: Int, ranks: Int, selector: Int, blank: Boolean): Int {
        val insert = enhancementRules.insertionPoint(bt, tier, ability)
        if (blank) {
            // Kotlin: the blank keeps the tier (VB: 0), so placeholders sit right around it ([BuildTree.slots]).
            bt.abilities.add(insert, BuildAbility(tier, BLANK_ABILITY, 0, 0))
        } else {
            bt.abilities.add(insert, BuildAbility(tier, ability, ranks, selector))
            if (tier == 5) {
                moveTier5(bt.treeName)
            }
        }
        return insert
    }

    /**
     * DropAbility: an ability dragged onto the blank slot in [row]. VB detail kept: this doesn't make
     * the tree the tier 5 tree (only [confirmTier5] moves an existing one).
     */
    fun dropAbility(bt: BuildTree, row: Int, tier: Int, ability: Int, ranks: Int, selector: Int) {
        bt.abilities[row - 1] = BuildAbility(tier, ability, ranks, selector)
    }

    /** The blank slots left by a drag that didn't land (RemoveBlanks). */
    fun removeBlanks(bt: BuildTree) {
        enhancementRules.removeBlanks(bt)
    }

    /**
     * ConfirmTier5Change (frmEnhancements.frm:1882, frmDestiny2.frm): taking a tier 5 ability from a
     * second tree needs the tier 5 tree moved. Returns the question to ask, or null if none is needed.
     */
    fun tier5Question(bt: BuildTree, tier: Int): String? {
        val current = tier5
        if (tier == 5 && current.isNotEmpty() && current != bt.treeName) {
            val kind = if (destinies) "destinies" else "enhancements"
            return "Make " + bt.treeName + " your Tier 5 tree?\n\n(This will clear Tier 5 " + kind + " from the " + current + " tree.)"
        }
        return null
    }

    /** The answer to [tier5Question] was yes: [bt] becomes the tier 5 tree. */
    fun confirmTier5(bt: BuildTree) {
        moveTier5(bt.treeName)
    }
}
