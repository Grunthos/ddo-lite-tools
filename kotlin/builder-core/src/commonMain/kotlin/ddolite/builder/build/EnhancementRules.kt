package ddolite.builder.build

import ddolite.builder.model.Ability
import ddolite.builder.model.AbilityTarget
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.FeatTarget
import ddolite.builder.model.PointerStyle
import ddolite.builder.model.Requirement
import ddolite.builder.model.Requirements
import ddolite.builder.model.Selector
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle

/** Action points spent and available, as the Enhancements screen shows them. VB's GetPointsSpentAndMax outputs. */
class ApTotals(
    val spentBase: Int,
    val spentRacial: Int,
    val spentUniversal: Int,
    val maxBase: Int,
    val maxRacial: Int,
    val maxUniversal: Int,
) {
    /** "Spent: 40 +2r +0u / Max: 80 +4r +1u AP" (frmEnhancements.ShowSpentAll, :1390). */
    fun caption(): String =
        "Spent: $spentBase +${spentRacial}r +${spentUniversal}u / Max: $maxBase +${maxRacial}r +${maxUniversal}u AP"

    /** Shown in the error colour when the base AP is overspent. */
    val overspent: Boolean get() = spentBase > maxBase
}

/** Destiny AP spent and available, as the Destinies screen shows them (GetDestinyPointsSpentAndMax). */
class DestinyTotals(val spentBase: Int, val spentPdp: Int, val maxBase: Int, val maxPdp: Int) {
    /** "Spent: 10 +6pdp / Max: 40 +6pdp AP" (frmDestiny2.ShowSpentAll, :924). */
    fun caption(): String = "Spent: $spentBase +${spentPdp}pdp / Max: $maxBase +${maxPdp}pdp AP"

    val overspent: Boolean get() = spentBase > maxBase
}

/** AP spent in a tree: [byTier] is cumulative (index n = spent in tiers 0..n, as VB's plngSpent(0 To 6)). */
class TreeSpend(val byTier: IntArray, val total: Int)

/** The enhancement ability details box: caption, lines, and the Ranks / Cost / "AP spent in tree" labels. */
class AbilityDetails(val caption: String, val lines: List<DetailLine>, val ranks: String, val cost: String, val progression: String?)

/** The blank ability VB puts in a tree while an ability is dragged over it (`Ability = 0`). */
const val BLANK_ABILITY = -1

/** An ability in a tree: its tier and its 0-based position in the tier. */
data class AbilityRef(val tier: Int, val ability: Int)

/** An ability chosen in the Enhancements or Destinies lists, with its selector (numbered from 1; 0 for none). */
data class AbilityChoice(val tier: Int, val ability: Int, val selector: Int)

/** The character level and class level an ability needs. */
data class LevelReqs(val levels: Int, val classLevels: Int)

/** A slot's caption, its cost (times its ranks) and the ability's most ranks. */
class SlotInfo(val caption: String, val cost: Int, val maxRanks: Int)

/**
 * The enhancement rules: AP spent and available, what an ability needs (levels, AP spent in the
 * tree, ranks, requirements), selectors, the tier 5 tree, and the Enhancements screen's lists and
 * details. Ported from the ENHANCEMENTS / TREES parts of Source/Modules/Build/basBuild.bas
 * (:2202-:3135) and from frmEnhancements (Source/Forms/Build/frmEnhancements.frm).
 *
 * Positions: build trees are numbered from 1 as VB's (0 = none, as [BuildRules.findBuildTree]);
 * an ability's position in its tier is 0-based, as in [BuildAbility], where VB's is 1-based, so
 * where VB tests "ability 1" (the first core) the code adds one. A blank ability is [BLANK_ABILITY].
 */
class EnhancementRules(val rules: BuildRules) {
    val build: Build get() = rules.build
    private val data = rules.data

    // ---------------------------------------------------------------- spending

    /**
     * AP spent in all trees and the maximum, racial and universal AP counted separately: racial
     * trees spend racial AP first, global trees universal AP, and the rest comes from the base
     * (4 per heroic level). Ported from `GetPointsSpentAndMax` (basBuild.bas:2205).
     */
    fun apTotals(): ApTotals {
        var base = 0
        var racial = 0
        var universal = 0
        for (buildTree in build.trees) {
            val tree = data.findTree(buildTree.treeName) ?: continue
            for (ability in buildTree.abilities) {
                if (ability.ability == BLANK_ABILITY) {
                    continue
                }
                val points = points(abilityOf(tree, ability), ability.selector, ability.rank)
                if (tree.style == TreeStyle.RACE) {
                    racial += points
                } else if (tree.style == TreeStyle.GLOBAL) {
                    universal += points
                } else {
                    base += points
                }
            }
        }
        if (racial > build.racialAP) {
            base += racial - build.racialAP
            racial = build.racialAP
        }
        if (universal > build.universalAP) {
            base += universal - build.universalAP
            universal = build.universalAP
        }
        return ApTotals(base, racial, universal, rules.heroicLevels() * 4, build.racialAP, build.universalAP)
    }

    /** The ability a build ability names. */
    fun abilityOf(tree: Tree, ability: BuildAbility): Ability = tree.tiers[ability.tier][ability.ability]

    /**
     * AP an ability costs at [ranks]: its (or its selector's) cost times the ranks. Ported from
     * `GetPoints` (basBuild.bas:2838); the rank 2/3 costs in the data aren't used, as in VB.
     */
    fun points(ability: Ability, selector: Int, ranks: Int): Int {
        var points = ability.cost
        if (selector != 0 && ability.selectors.isNotEmpty() && selector <= ability.selectors.size) {
            points = ability.selectors[selector - 1].cost
        }
        if (ranks > 1) {
            points *= ranks
        }
        return points
    }

    /**
     * AP spent in a tree, cumulative by tier, and in total. Ported from `GetSpentInTree`
     * (basBuild.bas:2581); class tree cores count as the tier of their number ([spentTier]).
     */
    fun spentInTree(tree: Tree?, buildTree: BuildTree?): TreeSpend {
        val byTier = IntArray(7)
        var total = 0
        if (tree == null || buildTree == null) {
            return TreeSpend(byTier, 0)
        }
        for (ability in buildTree.abilities) {
            if (ability.ability == BLANK_ABILITY) {
                continue
            }
            val points = points(abilityOf(tree, ability), ability.selector, ability.rank)
            val tier = spentTier(ability.tier, ability.ability + 1, tree.style)
            for (j in tier..6) {
                byTier[j] += points
            }
            total += points
        }
        return TreeSpend(byTier, total)
    }

    /** AP spent in build tree [buildTree] (1-based); 0 = all trees. Ported from `QuickSpentInTree` (basBuild.bas:2611). */
    fun quickSpentInTree(buildTree: Int): Int {
        var total = 0
        for (i in build.trees.indices) {
            if (buildTree != 0 && buildTree != i + 1) {
                continue
            }
            val bt = build.trees[i]
            val tree = data.findTree(bt.treeName)
            for (ability in bt.abilities) {
                if (ability.ability != BLANK_ABILITY && tree != null) {
                    total += points(abilityOf(tree, ability), ability.selector, ability.rank)
                }
            }
        }
        return total
    }

    /**
     * The tier an ability counts as for AP spent: class-style cores count as the core's number
     * (core 1 as tier 0). [abilityNumber] is 1-based. Ported from `GetTier` (basBuild.bas:2659).
     */
    fun spentTier(tier: Int, abilityNumber: Int, style: TreeStyle): Int {
        if (style == TreeStyle.RACE || tier > 0) {
            return tier
        }
        if (abilityNumber == 1) {
            return 0
        }
        return abilityNumber
    }

    /**
     * AP that must be spent in the tree first. Ported from `GetSpentReq` (basBuild.bas:2794);
     * [abilityNumber] is 1-based.
     */
    fun spentReq(style: TreeStyle, tier: Int, abilityNumber: Int): Int {
        return when (style) {
            TreeStyle.RACE -> when (tier) {
                1 -> 1
                2 -> 5
                3 -> 10
                4 -> 15
                else -> 0
            }
            TreeStyle.CLASS, TreeStyle.GLOBAL, TreeStyle.RACE_CLASS -> when (tier) {
                1 -> 1
                2 -> 5
                3 -> 10
                4 -> 20
                5 -> 30
                0 -> when (abilityNumber) {
                    2 -> 5
                    3 -> 10
                    4 -> 20
                    5 -> 30
                    6 -> 40
                    else -> 0
                }
                else -> 0
            }
            TreeStyle.DESTINY -> when (tier) {
                0 -> when (abilityNumber) {
                    2 -> 5
                    3 -> 10
                    4 -> 20
                    else -> 0
                }
                1 -> 1
                2 -> 5
                3 -> 10
                4 -> 20
                5 -> 30
                else -> 0
            }
        }
    }

    /**
     * Character and class levels an ability needs: (levels, class levels). Ported from
     * `GetLevelReqs` (basBuild.bas:2701), `GetLevelReqsCore` (:2735) and `GetLevelReqsTier` (:2775);
     * [abilityNumber] is 1-based.
     */
    fun levelReqs(style: TreeStyle, tier: Int, abilityNumber: Int): LevelReqs {
        var levels = 0
        var classLevels = 0
        if (tier == 0) {
            val min = when (style) {
                TreeStyle.CLASS, TreeStyle.GLOBAL -> when (abilityNumber) {
                    1 -> 1
                    2 -> 3
                    3 -> 6
                    4 -> 12
                    5 -> 18
                    6 -> 20
                    else -> 0
                }
                TreeStyle.RACE -> when (abilityNumber) {
                    1 -> 1
                    2 -> 4
                    3 -> 7
                    4 -> 11
                    5 -> 16
                    else -> 0
                }
                TreeStyle.RACE_CLASS -> when (abilityNumber) {
                    1 -> 1
                    2 -> 4
                    3 -> 8
                    4 -> 15
                    5 -> 22
                    6 -> 25
                    else -> 0
                }
                TreeStyle.DESTINY -> when (abilityNumber) {
                    1 -> 20
                    2 -> 23
                    3 -> 26
                    else -> 0
                }
            }
            if (style == TreeStyle.CLASS) {
                classLevels = min
            } else {
                levels = min
            }
        } else {
            when (style) {
                TreeStyle.CLASS -> {
                    classLevels = tier
                    if (tier == 5) {
                        levels = 12
                    }
                }
                TreeStyle.GLOBAL, TreeStyle.RACE_CLASS -> levels = if (tier == 5) 12 else tier
                TreeStyle.DESTINY -> levels = when (tier) {
                    1, 2 -> 20
                    3 -> 23
                    4 -> 26
                    5 -> 30
                    else -> 0
                }
                TreeStyle.RACE -> {}
            }
        }
        return LevelReqs(levels, classLevels)
    }

    /**
     * The build level at which an ability's level needs are met, -1 if never. Ported from
     * `GetBuildLevelReq` (basBuild.bas:2711).
     */
    fun buildLevelReq(levels: Int, classLevels: Int, c: CharacterClass?): Int {
        var buildLevels = 0
        if (classLevels != 0) {
            var count = 0
            var reached = false
            buildLevels = 1
            while (buildLevels <= rules.heroicLevels()) {
                if (c != null && build.levelClass(buildLevels) == c) {
                    count++
                    if (count == classLevels) {
                        reached = true
                        break
                    }
                }
                buildLevels++
            }
            if (!reached) {
                return -1
            }
        }
        if (levels > buildLevels) {
            buildLevels = levels
        }
        if (buildLevels > build.maxLevels) {
            buildLevels = -1
        }
        return buildLevels
    }

    // ---------------------------------------------------------------- errors

    /**
     * Null if [ability] can be in [buildTree]; otherwise why not. Ported from `CheckAbilityErrors`
     * (basBuild.bas:2854) with `CheckLevels` (:2683), `CheckSpentInTreeAbility` (:2670) and the
     * requirement checks.
     *
     * VB details kept: rank class requirements are never set for trees, so that check always
     * passes; with no selector, VB's extra check of the selector's rank requirements can't run.
     */
    fun abilityError(tree: Tree, buildTree: BuildTree, ability: BuildAbility, spend: TreeSpend): String? {
        val number = ability.ability + 1
        val (levels, classLevels) = levelReqs(buildTree.treeType ?: tree.style, ability.tier, number)
        if (build.maxLevels < levels) {
            return "Not enough character levels"
        }
        if (buildTree.classLevels < classLevels) {
            return "Not enough class levels"
        }
        val tier = spentTier(ability.tier, number, tree.style)
        if (tier > 0 && spend.byTier[tier - 1] < spentReq(tree.style, ability.tier, number)) {
            return "Not enough spent in tree"
        }
        val data = abilityOf(tree, ability)
        if (ability.selector == 0 || ability.selector > data.selectors.size) {
            val error = abilityReqsError(data.requirements, ability.rank, false, tree)
            if (error != null) {
                return error
            }
            if (ability.rank > 1 && data.rankReqs) {
                val rankError = abilityReqsError(rankRequirements(data.rankDetails, ability.rank), ability.rank, true, tree)
                if (rankError != null) {
                    return rankError
                }
            }
        } else {
            val selector = data.selectors[ability.selector - 1]
            val error = abilityReqsError(selector.requirements, ability.rank, false, tree)
            if (error != null) {
                return error
            }
            if (ability.rank > 1 && selector.rankReqs) {
                val rankError = abilityReqsError(rankRequirements(selector.rankDetails, ability.rank), ability.rank, true, tree)
                if (rankError != null) {
                    return rankError
                }
            }
        }
        return null
    }

    internal fun rankRequirements(details: List<ddolite.builder.model.RankDetail>?, rank: Int): Requirements {
        if (details == null || rank < 2 || rank - 2 >= details.size) {
            return Requirements.EMPTY
        }
        return details[rank - 2].requirements
    }

    /** Ported from `CheckAbilityReqs` (basBuild.bas:2980): the first group that fails. */
    private fun abilityReqsError(reqs: Requirements, ranks: Int, rankReq: Boolean, currentTree: Tree): String? {
        val groups = listOf(reqs.all, reqs.one, reqs.none)
        for (group in 0..2) {
            val error = abilityReqError(groups[group], group, ranks, rankReq, currentTree)
            if (error != null) {
                return error
            }
        }
        return null
    }

    /**
     * Null if one requirement list (0 = all, 1 = one, 2 = none) is met. Ported from
     * `CheckAbilityReq` (basBuild.bas:2993). A requirement in a tree the build doesn't have counts
     * neither as taken nor as missing, as VB's.
     */
    private fun abilityReqError(reqs: List<Requirement>, group: Int, ranks: Int, rankReq: Boolean, currentTree: Tree): String? {
        if (reqs.isEmpty()) {
            return null
        }
        var matches = 0
        var taken = ""
        var missing = ""
        val featRules = FeatRules(rules)
        for (req in reqs) {
            val target = req.target
            if (target is FeatTarget) {
                val pointer = featRules.featPointer(req)
                val display = requirementDisplay(req, abbreviate = true, currentTree = null)
                var has: Boolean
                val takenFeats = featRules.identifyTakenFeats(build.maxLevels)
                if (pointer.second == 0) {
                    has = takenFeats.of(pointer.first).times != 0
                } else {
                    has = takenFeats.selectorTaken(pointer.first, pointer.second)
                }
                if (has) {
                    if (taken.isEmpty()) {
                        taken = display
                    }
                    matches++
                } else if (missing.isEmpty()) {
                    missing = display
                }
            } else if (target is AbilityTarget) {
                val holder = buildTreeFor(target) ?: continue
                val display = requirementDisplay(req, abbreviate = true, currentTree = treeFor(target))
                if (checkAbility(holder, target, ranks)) {
                    if (taken.isEmpty()) {
                        taken = display
                    }
                    matches++
                } else if (missing.isEmpty()) {
                    missing = display
                }
            }
        }
        if (group == 0 && matches < reqs.size) {
            if (rankReq) {
                return "Rank $ranks requires $missing"
            }
            return "Requires $missing"
        }
        if (group == 1 && matches < 1) {
            return "Nothing taken from the 'One of' list"
        }
        if (group == 2 && matches > 0) {
            if (rankReq) {
                return "Rank $ranks excludes $taken"
            }
            return "Antireq for $taken"
        }
        return null
    }

    private fun treeFor(target: AbilityTarget): Tree? {
        if (target.style == PointerStyle.DESTINY) {
            return data.findDestiny(target.treeName)
        }
        return data.findTree(target.treeName)
    }

    /** The build's tree (or destiny) a requirement points into, or null if the build hasn't got it. */
    private fun buildTreeFor(target: AbilityTarget): BuildTree? {
        val list = if (target.style == PointerStyle.DESTINY) build.destinies else build.trees
        for (bt in list) {
            if (bt.treeName == target.treeName) {
                return bt
            }
        }
        return null
    }

    /**
     * True if [buildTree] has the ability [target] points at (with its selector, if it names one)
     * with enough ranks: the target's rank, or else [ranks]; an ability with fewer ranks than that
     * counts if it can't have that many. Ported from `CheckAbility` (basBuild.bas:3076) and
     * `GetAbilityMaxRanks` (:3110), which gives 3 for an ability it can't find or with no ranks.
     */
    fun checkAbility(buildTree: BuildTree, target: AbilityTarget, ranks: Int): Boolean {
        var needed = ranks
        if (target.rank != 0) {
            needed = target.rank
        }
        val tree = treeFor(target)
        var selectorNumber = 0
        val selectorName = target.selectorName
        if (tree != null && selectorName != null && target.tier < tree.tiers.size && target.abilityIndex < tree.tiers[target.tier].size) {
            val selectors = tree.tiers[target.tier][target.abilityIndex].selectors
            for (i in selectors.indices) {
                if (selectors[i].name == selectorName) {
                    selectorNumber = i + 1
                }
            }
        }
        for (ability in buildTree.abilities) {
            if (ability.tier != target.tier || ability.ability != target.abilityIndex) {
                continue
            }
            if (selectorNumber != 0 && ability.selector != selectorNumber) {
                continue
            }
            if (needed == 0) {
                return true
            }
            var maxRanks = 0
            if (tree != null && ability.tier < tree.tiers.size && ability.ability < tree.tiers[ability.tier].size) {
                maxRanks = tree.tiers[ability.tier][ability.ability].ranks
            }
            if (maxRanks == 0) {
                maxRanks = 3
            }
            return ability.rank >= needed || maxRanks < needed
        }
        return false
    }

    /**
     * A requirement as text, from inside [currentTree]: feats get "Feat: " in front; an
     * enhancement in another tree gets that tree's abbreviation. Ported from `PointerDisplay`
     * (basBuild.bas:22) with a tree.
     */
    fun requirementDisplay(req: Requirement, abbreviate: Boolean, currentTree: Tree?): String {
        val target = req.target
        if (target is FeatTarget) {
            val featRules = FeatRules(rules)
            val pointer = featRules.featPointer(req)
            return featRules.featDisplay(pointer.first, pointer.second, abbreviate, featPrefix = true)
        }
        if (target !is AbilityTarget) {
            return ""
        }
        val tree = treeFor(target)
        var treeText = ""
        if (target.style == PointerStyle.ENHANCEMENT && tree != currentTree) {
            treeText = (tree?.abbreviation ?: target.treeName) + " "
        }
        var abilityText = target.abilityName
        if (tree != null && target.tier < tree.tiers.size && target.abilityIndex < tree.tiers[target.tier].size) {
            val ability = tree.tiers[target.tier][target.abilityIndex]
            var name = ability.name
            if (abbreviate) {
                name = ability.abbreviation
            }
            val selectorName = target.selectorName
            abilityText = if (selectorName == null) name else if (ability.selectorOnly) selectorName else "$name: $selectorName"
        }
        var rank = ""
        if (target.rank > 0) {
            rank = " (Rank " + target.rank + ")"
        }
        return treeText + "Tier " + target.tier + ": " + abilityText + rank
    }

    // ---------------------------------------------------------------- abilities in a tree

    /** Ported from `AbilityTaken` (basBuild.bas:2457). */
    fun abilityTaken(buildTree: BuildTree?, tier: Int, ability: Int): Boolean {
        if (buildTree == null) {
            return false
        }
        for (a in buildTree.abilities) {
            if (a.tier == tier && a.ability == ability) {
                return true
            }
        }
        return false
    }

    /**
     * Where an ability goes in the tree's list, kept in tier and ability order (0-based). Ported
     * from `GetInsertionPoint` (basBuild.bas:2424).
     */
    fun insertionPoint(buildTree: BuildTree, tier: Int, ability: Int): Int {
        var i = 0
        while (i < buildTree.abilities.size) {
            val a = buildTree.abilities[i]
            if (a.tier > tier || (a.tier == tier && a.ability >= ability)) {
                break
            }
            i++
        }
        return i
    }

    /** Ported from `RemoveBlanks` (basBuild.bas:2439). */
    fun removeBlanks(buildTree: BuildTree) {
        var i = buildTree.abilities.size - 1
        while (i >= 0) {
            if (buildTree.abilities[i].ability == BLANK_ABILITY) {
                buildTree.abilities.removeAt(i)
            }
            i--
        }
    }

    /**
     * Which of an ability's selectors can be chosen (index 1..n). Ported from `GetSelectors`
     * (basBuild.bas:2473): all of a root's; for a shared selector, those its parent feat has taken,
     * or the one its parent ability has; for an exclusive one, all but its parent's and siblings'.
     */
    fun selectors(tree: Tree, tier: Int, ability: Int): BooleanArray {
        val data = tree.tiers[tier][ability]
        val count = data.selectors.size
        val result = BooleanArray(count + 1)
        when (data.selectorStyle) {
            SelectorStyle.ROOT -> {
                for (i in 1..count) {
                    result[i] = true
                }
            }
            SelectorStyle.SHARED -> {
                val parent = data.parent
                val target = parent?.target
                if (target is FeatTarget) {
                    val featRules = FeatRules(rules)
                    val feat = this.data.findFeat(target.featName)
                    val taken = featRules.identifyTakenFeats(build.maxLevels)
                    for (i in 1..count) {
                        result[i] = taken.selectorTaken(feat, i)
                    }
                } else if (parent != null) {
                    val chosen = selectorChosen(parent)
                    if (chosen in 1..count) {
                        result[chosen] = true
                    }
                }
            }
            SelectorStyle.EXCLUSIVE -> {
                for (i in 1..count) {
                    result[i] = true
                }
                val parent = data.parent
                if (parent != null) {
                    val chosen = selectorChosen(parent)
                    if (chosen in 1..count) {
                        result[chosen] = false
                    }
                }
                for (sibling in data.siblings) {
                    val chosen = selectorChosen(sibling)
                    if (chosen in 1..count) {
                        result[chosen] = false
                    }
                }
            }
            SelectorStyle.NONE -> {}
        }
        return result
    }

    /** The selector the build chose for the ability [pointer] names; 0 if none. `GetSelector` / `GetSelectorChosen` (basBuild.bas:2518, :2534). */
    private fun selectorChosen(pointer: Requirement): Int {
        val target = pointer.target as? AbilityTarget ?: return 0
        val buildTree = buildTreeFor(target) ?: return 0
        for (a in buildTree.abilities) {
            if (a.tier == target.tier && a.ability == target.abilityIndex) {
                return a.selector
            }
        }
        return 0
    }

    /**
     * "Cost: 2 AP", "Cost: 1 AP/rank", or "Cost: Varies" when the selectors cost different
     * amounts. Ported from `CostDescrip` (basBuild.bas:2402).
     */
    fun costDescrip(ability: Ability, selector: Int, perRank: Boolean = true): String {
        var cost: Int
        if (selector == 0) {
            cost = ability.cost
            for (s in ability.selectors) {
                if (s.cost != cost) {
                    return "Cost: Varies"
                }
            }
        } else {
            cost = ability.selectors[selector - 1].cost
        }
        if (ability.ranks > 1 && perRank) {
            return "Cost: $cost AP/rank"
        }
        return "Cost: $cost AP"
    }

    // ---------------------------------------------------------------- tiers

    /**
     * The highest tier a build tree offers: 4 for racial trees; class levels for class trees, the
     * max level for global and racial class trees, capped at 5 (4 below level 12). Ported from
     * frmEnhancements.GetMaxTier (:2199) and CapClassTreeTier (:2211).
     */
    fun maxTier(buildTree: BuildTree?): Int {
        if (buildTree == null) {
            return 0
        }
        return when (buildTree.treeType) {
            TreeStyle.CLASS -> capTier(buildTree.classLevels)
            TreeStyle.GLOBAL, TreeStyle.RACE_CLASS -> capTier(build.maxLevels)
            TreeStyle.RACE -> 4
            else -> 0
        }
    }

    private fun capTier(tier: Int): Int {
        var cap = tier
        if (cap > 5) {
            cap = 5
        }
        if (cap == 5 && build.maxLevels < 12) {
            cap = 4
        }
        return cap
    }

    /** Removes a tree's tier 5 abilities from the end of its list. Ported from frmEnhancements.ShaveTree (:2220). */
    fun shaveTree(treeName: String) {
        val index = rules.findBuildTree(treeName)
        if (index == 0) {
            return
        }
        val abilities = build.trees[index - 1].abilities
        while (abilities.isNotEmpty() && abilities[abilities.size - 1].tier >= 5) {
            abilities.removeAt(abilities.size - 1)
        }
    }

    /**
     * Makes [treeName] the tier 5 tree ("" for none), removing the old one's tier 5 abilities.
     * Ported from frmEnhancements.SetTier5 (:1863).
     */
    fun setTier5(treeName: String) {
        if (build.tier5.isEmpty()) {
            build.tier5 = treeName
        } else if (build.tier5 != treeName) {
            shaveTree(build.tier5)
            build.tier5 = treeName
        }
    }

    // ---------------------------------------------------------------- destinies

    /**
     * Destiny AP spent and available: permanent destiny points are spent first, then the base
     * (4 per epic and legendary level). Ported from `GetDestinyPointsSpentAndMax` (basBuild.bas:2331),
     * `GetEpicLevels` (:119) and `GetLegendaryLevels` (:130).
     *
     * VB quirk kept: `GetEpicLevels` gives 0 at exactly level 30 (its cases are "< 30" and "> 30"),
     * so a level 30 build has no base destiny AP.
     */
    fun destinyTotals(): DestinyTotals {
        var base = 0
        var pdp = 0
        val maxPdp = build.permDestinyPoints
        for (bt in build.destinies) {
            val tree = data.findDestiny(bt.treeName) ?: continue
            for (ability in bt.abilities) {
                if (ability.ability == BLANK_ABILITY) {
                    continue
                }
                val points = points(abilityOf(tree, ability), ability.selector, ability.rank)
                if (pdp < maxPdp) {
                    pdp += points
                    if (pdp > maxPdp) {
                        base = pdp - maxPdp
                        pdp = maxPdp
                    }
                } else {
                    base += points
                }
            }
        }
        return DestinyTotals(base, pdp, (epicLevels() + legendaryLevels()) * 4, maxPdp)
    }

    /** `GetEpicLevels` (basBuild.bas:119), with its level 30 gap. */
    fun epicLevels(): Int {
        val max = build.maxLevels
        if (max <= 20) {
            return 0
        }
        if (max < 30) {
            return max - 20
        }
        if (max > 30) {
            return 10
        }
        return 0
    }

    /** `GetLegendaryLevels` (basBuild.bas:130). */
    fun legendaryLevels(): Int {
        if (build.maxLevels >= 30) {
            return build.maxLevels - 30
        }
        return 0
    }

    /** AP spent in destiny [buildDestiny] (1-based). Ported from `QuickSpentInDestiny` (basBuild.bas:2634). */
    fun quickSpentInDestiny(buildDestiny: Int): Int {
        val bt = build.destinies.getOrNull(buildDestiny - 1) ?: return 0
        val tree = data.findDestiny(bt.treeName) ?: return 0
        var total = 0
        for (ability in bt.abilities) {
            if (ability.ability != BLANK_ABILITY) {
                total += points(abilityOf(tree, ability), ability.selector, ability.rank)
            }
        }
        return total
    }

    /** 1-based position of the build destiny called [name], 0 if none. `FindDestinyTree` (basBuild.bas:2387). */
    fun findBuildDestiny(name: String): Int {
        for (i in build.destinies.indices) {
            if (build.destinies[i].treeName == name) {
                return i + 1
            }
        }
        return 0
    }

    /** Removes a destiny's tier 5 abilities from the end of its list. frmDestiny2.ShaveTree (:1701). */
    fun shaveDestiny(name: String) {
        val index = findBuildDestiny(name)
        if (index == 0) {
            return
        }
        val abilities = build.destinies[index - 1].abilities
        while (abilities.isNotEmpty() && abilities[abilities.size - 1].tier >= 5) {
            abilities.removeAt(abilities.size - 1)
        }
    }

    /** Makes [name] the tier 5 destiny ("" for none). frmDestiny2.SetDestinyTier5 (:1336). */
    fun setDestinyTier5(name: String) {
        if (build.destinyTier5.isEmpty()) {
            build.destinyTier5 = name
        } else if (build.destinyTier5 != name) {
            shaveDestiny(build.destinyTier5)
            build.destinyTier5 = name
        }
    }

    // ---------------------------------------------------------------- the Enhancements screen

    /**
     * The abilities offered for [buildTree] of [tree]: every one up to the
     * tree's max tier that isn't taken and, unless [showAll], has no error at rank 1. Ported from
     * frmEnhancements.ShowAvailable (:2241) and frmDestiny2.ShowAvailable (:1722); destinies offer
     * every tier ([tierLimit] 5, frmDestiny2.GetMaxTier :1676).
     */
    fun availableAbilities(tree: Tree, buildTree: BuildTree?, showAll: Boolean, tierLimit: Int = -1): List<AbilityRef> {
        val result = ArrayList<AbilityRef>()
        val spend = spentInTree(tree, buildTree)
        var maxTier = tierLimit
        if (maxTier < 0) {
            maxTier = maxTier(buildTree)
        }
        val holder = buildTree ?: BuildTree(tree.name)
        for (tier in 0..maxTier) {
            if (tier >= tree.tiers.size) {
                break
            }
            for (i in tree.tiers[tier].indices) {
                if (abilityTaken(buildTree, tier, i)) {
                    continue
                }
                // VB's typCheck has rank 0 and no selector.
                if (!showAll && abilityError(tree, holder, BuildAbility(tier, i, 0, 0), spend) != null) {
                    continue
                }
                result.add(AbilityRef(tier, i))
            }
        }
        return result
    }

    /**
     * The class whose levels open [tree]: of the build's classes that have it, the one with the
     * most levels. Ported from frmEnhancements.GetClass (:2393).
     */
    fun treeClass(tree: Tree): CharacterClass? {
        var levels = 0
        var result: CharacterClass? = null
        for (split in rules.classSplit()) {
            if (split.characterClass.trees.contains(tree.name) && levels < split.levels) {
                levels = split.levels
                result = split.characterClass
            }
        }
        return result
    }

    /**
     * The details box for ability [ability] of [tier] with [selector], and the slot's error if
     * [slot] is given. Ported from frmEnhancements.ShowDetails (:2293), ShowDetailsReqs (:2416),
     * ShowRankReqs (:2433) and basWiki.TierLink; for destinies frmDestiny2.ShowDetails (:1778), where
     * a selector's description gets a wiki link too and the level requirements aren't shown (VB's code
     * for them is commented out).
     */
    fun details(tree: Tree, buildTree: BuildTree?, tier: Int, ability: Int, selector: Int, slot: BuildAbility?): AbilityDetails {
        val data = tree.tiers[tier][ability]
        val out = DetailsBuilder()
        if (data.description.isNotEmpty()) {
            out.addDescrip(data.description, makeWiki(tree.wiki) + tierLink(tier))
        }
        var sel: Selector? = null
        if (selector > 0 && selector <= data.selectors.size) {
            sel = data.selectors[selector - 1]
            if (sel.description.isNotEmpty()) {
                out.addDescrip(sel.name, "")
                if (tree.style == TreeStyle.DESTINY) {
                    var wiki = sel.wiki
                    if (wiki.isEmpty()) {
                        wiki = tree.wiki
                    }
                    out.addDescrip(sel.description, makeWiki(wiki) + tierLink(tier))
                } else {
                    out.addDescrip(sel.description, "")
                }
            }
        }
        val reqs = sel?.requirements ?: data.requirements
        detailsReqs(out, tree, reqs, 0)
        if (sel == null) {
            if (data.rankReqs) {
                rankReqs(out, tree, data.rankDetails)
            }
        } else if (sel.rankReqs) {
            rankReqs(out, tree, sel.rankDetails)
        }
        val (levels, classLevels) = levelReqs(tree.style, tier, ability + 1)
        val buildLevels = buildLevelReq(levels, classLevels, treeClass(tree))
        if (tree.style != TreeStyle.DESTINY && (buildLevels > 1 || buildLevels == -1)) {
            out.addText("Level requirements:")
            if (classLevels != 0) {
                out.addText(" - $classLevels class levels")
            }
            if (levels != 0) {
                out.addText(" - $levels character levels")
            }
            if (buildLevels == -1) {
                out.addText(" - Unattainable for this build")
            } else {
                out.addText(" - $buildLevels build levels")
            }
        }
        if (slot != null && buildTree != null) {
            val error = abilityError(tree, buildTree, slot, spentInTree(tree, buildTree))
            if (error != null) {
                out.addErrorText("Error: $error")
            }
        }
        var progression: String? = null
        val prog = spentReq(tree.style, tier, ability + 1)
        if (prog != 0) {
            progression = "$prog AP spent in tree"
        }
        return AbilityDetails("Tier $tier: " + data.name, out.build(), "Ranks: " + data.ranks, costDescrip(data, selector), progression)
    }

    internal fun detailsReqs(out: DetailsBuilder, tree: Tree, reqs: Requirements, rank: Int) {
        val names = listOf("all", "one", "none")
        val groups = listOf(reqs.all, reqs.one, reqs.none)
        for (group in 0..2) {
            if (groups[group].isEmpty()) {
                continue
            }
            if (rank == 0) {
                out.addText("Requires " + names[group] + " of:")
            } else {
                out.addText("Rank $rank requires " + names[group] + " of:")
            }
            for (req in groups[group]) {
                out.addText(" - " + requirementDisplay(req, abbreviate = true, currentTree = tree))
            }
        }
    }

    internal fun rankReqs(out: DetailsBuilder, tree: Tree, details: List<ddolite.builder.model.RankDetail>?) {
        for (rank in 2..3) {
            detailsReqs(out, tree, rankRequirements(details, rank), rank)
        }
    }

    /** Ported from `TierLink` (Source/Modules/basWiki.bas:36). */
    internal fun tierLink(tier: Int): String = when (tier) {
        0 -> "#Core_abilities"
        1 -> "#Tier_One"
        2 -> "#Tier_Two"
        3 -> "#Tier_Three"
        4 -> "#Tier_Four"
        5 -> "#Tier_Five"
        6 -> "#Tier_Six"
        else -> ""
    }

    /**
     * A slot's caption, cost and ranks: "Abbr", "Abbr: Selector" or the selector alone; the cost
     * times the ranks; the ability's max ranks. Ported from frmEnhancements.GetSlotInfo (:1726).
     */
    fun slotInfo(tree: Tree, ability: BuildAbility): SlotInfo {
        val data = abilityOf(tree, ability)
        var caption = data.abbreviation
        var cost = data.cost
        if (ability.selector != 0 && ability.selector <= data.selectors.size) {
            val selector = data.selectors[ability.selector - 1]
            caption = if (data.selectorOnly) selector.name else data.abbreviation + ": " + selector.name
            cost = selector.cost
        }
        if (ability.rank != 0) {
            cost *= ability.rank
        }
        return SlotInfo(caption, cost, data.ranks)
    }
}
