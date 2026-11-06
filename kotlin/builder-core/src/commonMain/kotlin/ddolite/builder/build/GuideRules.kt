package ddolite.builder.build

import ddolite.builder.model.Ability
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Feat
import ddolite.builder.model.AbilityTarget
import ddolite.builder.model.FeatTarget
import ddolite.builder.model.PointerStyle
import ddolite.builder.model.RaceType
import ddolite.builder.model.Requirement
import ddolite.builder.model.Requirements
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle

/** What a leveling guide step is. Ported from `GuideEnhancementEnum` (geUnknown …). */
enum class GuideStepStyle { UNKNOWN, ENHANCEMENT, RESET_TREE, RESET_ALL_TREES, BANK_AP, MISSING }

/**
 * One leveling guide step with what's worked out from it. Ported from `GuideEnhancementType`
 * (basBuildDefs.bas:252), "a perfect mirror of build.Guide.Enhancement()".
 *
 * @property guideTree 1-based position in [BuildRules.guideTrees]; 0 if none.
 * @property buildGuideTree 1-based position in [BuildGuide.trees]; for a reset, the tree reset.
 * @property buildTree 1-based position in [Build.trees]; 0 if the build hasn't the tree.
 * @property ability 0-based position in the tier.
 * @property ml the lowest level it can be taken at (`GuideAbilityML`); 0 if never.
 */
class GuideEnhancement(val style: GuideStepStyle) {
    var guideTree = 0
    var tree: Tree? = null
    var buildTree = 0
    var buildGuideTree = 0
    var tier = 0
    var ability = 0
    var selector = 0
    var rank = 0
    var display = ""
    var rankText = ""
    var level = 0
    var cost = 0
    var spentInTree = 0
    var spent = 0
    var spentRacial = 0
    var ml = 0
    var bank = 0
    var errorState = false
    var errorText = ""

    /** For a [GuideStepStyle.MISSING] step: the tree as the file names it ("" if the line can't be read). */
    var missingTree = ""
}

/** The Leveling Guide tab's ability filter (cboGuideAbility): `GuideFilterEnum` (frmEnhancements.frm:955). */
enum class GuideFilter(val caption: String) {
    SELECTED_AVAILABLE("Selected and Available"),
    ALL_SELECTED("All Selected"),
    ALL_AVAILABLE("Available"),
    ALL_ENHANCEMENTS("Show All"),
}

/**
 * Something the guide's ability list offers: a command (Bank, Reset …: [command] is the step's
 * ID), or an ability rank in the tree chosen.
 */
class GuideChoice(val text: String, val command: Int, val tier: Int, val ability: Int, val rank: Int, val selector: Int)

/**
 * The totals under the guide grid. Ported from frmEnhancements.GuideTotals (:2500).
 *
 * @property level "Cur. Level": the level the next step would be taken at.
 * @property ap AP available at [level] (red when negative).
 */
class GuideTotals(
    val level: Int,
    val ap: Int,
    val racialAvailable: Int,
    val universalAvailable: Int,
    val spent: Int,
    val racialSpent: Int,
    val universalSpent: Int,
    val spentError: Boolean,
    val classes: String = "",
) {
    /**
     * VB's "Cur. Level: 3"; Kotlin only, with the build's classes at that level: "Level: 3 (Rog 1/Wiz 2)".
     */
    fun levelCaption(): String {
        if (classes.isEmpty()) {
            return "Cur. Level: $level"
        }
        return "Level: $level ($classes)"
    }

    fun apCaption(): String = "Available: $ap + ${racialAvailable}r + ${universalAvailable}u"

    /** VB writes "r +" with no space before the universal AP. */
    fun spentCaption(): String = "Spent: $spent + ${racialSpent}r +${universalSpent}u"
}

/** The guide's details box: caption, lines, and the cost and requirement labels (null = hidden). */
class GuideDetails(
    val caption: String,
    val lines: List<DetailLine>,
    val cost: String?,
    val requirement: RequirementLabel?,
)

/**
 * The leveling guide's rules: each step's cost, level and errors, and what the Leveling Guide tab
 * offers and does. Ported from basBuild.bas (InitGuideEnhancements :3242 to CheckGuideErrorFeat
 * :3798) and the guide parts of frmEnhancements.frm (:2491-:3455).
 *
 * The guide is VB's `Guide` ([BuildRules.guideTrees], [BuildRules.guideEnhancements]) kept with
 * the build's own `build.Guide` ([Build.guide]).
 */
/** An entry in one of the Leveling Guide tab's lists: what it shows, and its number (a guide tree,
 * 0 for "(Commands)"; or a selector). */
class GuideListItem(val text: String, val number: Int)

class GuideRules(private val rules: BuildRules) {
    private val build = rules.build
    private val data = rules.data
    private val enhancements = EnhancementRules(rules)

    private fun guideTree(index: Int): GuideTree? {
        if (index < 1 || index > rules.guideTrees.size) {
            return null
        }
        return rules.guideTrees[index - 1]
    }

    /** `Guide.TreeLookup(id)`: the guide tree for build guide tree [id]; 0 if none. */
    private fun lookup(id: Int): Int {
        if (id < 1 || id >= rules.guideTreeLookup.size) {
            return 0
        }
        return rules.guideTreeLookup[id]
    }

    private fun abilityData(tree: Tree?, tier: Int, ability: Int): Ability? {
        if (tree == null || tier < 0 || tier >= tree.tiers.size || ability < 0 || ability >= tree.tiers[tier].size) {
            return null
        }
        return tree.tiers[tier][ability]
    }

    // ---------------------------------------------------------------- steps

    /**
     * Works out every step: style, tree, cost, display, AP spent, then each guide tree's abilities,
     * the levels and the errors. Ported from `InitGuideEnhancements` (basBuild.bas:3242).
     *
     * Not copied: where VB would stop with "Subscript out of range" (a step whose tree isn't one of
     * the guide's trees), the step is left as unknown.
     */
    fun initGuideEnhancements() {
        val steps = build.guide.enhancements
        if (steps.isEmpty()) {
            rules.guideEnhancements = emptyList()
            return
        }
        val trees = rules.guideTrees
        var spentInTree = IntArray(trees.size + 1)
        var spent = 0
        var spentRacial = 0
        val result = ArrayList<GuideEnhancement>()
        for (step in steps) {
            val id = step.id
            var style = GuideStepStyle.UNKNOWN
            if (id in 1..99) {
                style = GuideStepStyle.ENHANCEMENT
            } else if (id == 100) {
                style = GuideStepStyle.RESET_ALL_TREES
            } else if (id in 101..199) {
                style = GuideStepStyle.RESET_TREE
            } else if (id == 200) {
                style = GuideStepStyle.BANK_AP
            }
            val missing = step.missing
            if (missing != null) {
                style = GuideStepStyle.MISSING
            }
            var buildGuideTree = id
            var guide = 0
            if (style == GuideStepStyle.ENHANCEMENT) {
                guide = lookup(buildGuideTree)
            } else if (style == GuideStepStyle.RESET_TREE) {
                buildGuideTree = id - 100
                guide = lookup(buildGuideTree)
            } else {
                buildGuideTree = 0
            }
            if ((style == GuideStepStyle.ENHANCEMENT || style == GuideStepStyle.RESET_TREE) && guide == 0) {
                style = GuideStepStyle.UNKNOWN
                buildGuideTree = 0
            }
            val e = GuideEnhancement(style)
            e.tier = step.tier
            e.ability = step.ability
            e.selector = step.selector
            e.rank = step.rank
            e.buildGuideTree = buildGuideTree
            e.guideTree = guide
            var racialTree = false
            when (style) {
                GuideStepStyle.ENHANCEMENT -> racialTree = trees[guide - 1].tree?.style == TreeStyle.RACE
                GuideStepStyle.RESET_ALL_TREES -> {
                    spentInTree = IntArray(trees.size + 1)
                    spent = 0
                    spentRacial = 0
                }
                GuideStepStyle.RESET_TREE -> {
                    spent = spent - spentInTree[guide]
                    spentInTree[guide] = 0
                    e.spent = spent
                    if (trees[guide - 1].tree?.style == TreeStyle.RACE) {
                        spentRacial = 0
                    }
                    e.spentRacial = spentRacial
                }
                GuideStepStyle.BANK_AP -> {
                    e.spent = spent
                    e.spentInTree = spent
                    e.spentRacial = spentRacial
                }
                GuideStepStyle.UNKNOWN -> {}
                GuideStepStyle.MISSING -> {
                    // "Human Tier 0: Human Versatility: Damage Boost": the tree, tier and the rest, for
                    // the grid's columns. Costs nothing: the totals carry on from the step before.
                    val text = missing ?: ""
                    e.display = text
                    val tierAt = text.indexOf(" Tier ")
                    val colon = text.indexOf(": ", tierAt + 1)
                    if (tierAt > 0 && colon > tierAt) {
                        val tier = text.substring(tierAt + 6, colon).toIntOrNull()
                        if (tier != null) {
                            e.missingTree = text.substring(0, tierAt)
                            e.tier = tier
                            e.display = text.substring(colon + 2)
                        }
                    }
                    e.spent = spent
                    e.spentRacial = spentRacial
                }
            }
            if (e.buildGuideTree != 0) {
                e.buildTree = trees[guide - 1].buildTree
                e.tree = trees[guide - 1].tree
            }
            val data = abilityData(e.tree, e.tier, e.ability)
            if (style == GuideStepStyle.ENHANCEMENT && data != null) {
                e.ml = abilityMinLevel(guide, e.tier, e.ability, e.selector, e.rank)
                val selector = data.selectors.getOrNull(e.selector - 1)
                if (data.selectorStyle == SelectorStyle.NONE || selector == null) {
                    // (VB would stop on a selector ability without a selector; the screen never adds one.)
                    e.cost = data.cost
                    e.display = data.abbreviation
                } else {
                    e.cost = selector.cost
                    if (data.selectorOnly) {
                        e.display = selector.name
                    } else {
                        e.display = data.abbreviation + ": " + selector.name
                    }
                }
                e.rankText = if (data.ranks > 1) " III".substring(0, minOf(e.rank + 1, 4)) else ""
                spent = spent + e.cost
                spentInTree[guide] = spentInTree[guide] + e.cost
                e.spentInTree = spentInTree[guide]
                e.spent = spent
                if (racialTree) {
                    spentRacial = spentRacial + e.cost
                }
                e.spentRacial = spentRacial
            }
            result.add(e)
        }
        rules.guideEnhancements = result
        for (tree in trees) {
            tree.guideBuildTree.abilities.clear()
        }
        for (step in steps) {
            addGuideBuildAbility(step)
        }
        calculateGuideLevels()
        checkGuideErrors()
    }

    /**
     * The lowest level an ability rank can be taken at, from the tree's level rules, the feats it
     * needs (at the level they're taken) and, for requirements in the same tree, theirs; 0 if
     * never. Ported from `GuideAbilityML` (basBuild.bas:3344).
     *
     * VB details kept: only the "all of" feats count; once one required feat is found, a missing
     * one no longer makes it 0 (`GuideFeatReqLevel`); rank class requirements are never set for
     * trees, so `GuideClassReqLevel` changes nothing.
     */
    fun abilityMinLevel(guide: Int, tier: Int, ability: Int, selector: Int, rank: Int): Int {
        val g = guideTree(guide) ?: return 0
        val tree = g.tree ?: return 0
        val data = abilityData(tree, tier, ability) ?: return 0
        val (levels, classLevels) = enhancements.levelReqs(tree.style, tier, ability + 1)
        var result = enhancements.buildLevelReq(levels, classLevels, g.characterClass)
        if (result == -1) {
            result = 0
        } else if (result == 0) {
            result = 1
        }
        result = featReqLevel(data.requirements, result)
        if (data.rankReqs && rank > 1) {
            result = featReqLevel(enhancements.rankRequirements(data.rankDetails, rank), result)
        }
        if (selector != 0 && selector <= data.selectors.size) {
            val s = data.selectors[selector - 1]
            result = featReqLevel(s.requirements, result)
            if (s.rankReqs && rank > 1) {
                result = featReqLevel(enhancements.rankRequirements(s.rankDetails, rank), result)
            }
        }
        if (result >= 1) {
            for (req in data.requirements.all) {
                val target = req.target
                if (target is AbilityTarget && target.style == PointerStyle.ENHANCEMENT && target.treeName == tree.name) {
                    val recurse = abilityMinLevel(guide, target.tier, target.abilityIndex, selectorOf(tree, target), target.rank)
                    if (recurse == 0 || recurse > result) {
                        result = recurse
                    }
                }
            }
        }
        return result
    }

    /** The 1-based number of the selector a requirement names in its ability; 0 if none. */
    private fun selectorOf(tree: Tree?, target: AbilityTarget): Int {
        val name = target.selectorName ?: return 0
        val data = abilityData(tree, target.tier, target.abilityIndex) ?: return 0
        for (i in data.selectors.indices) {
            if (data.selectors[i].name == name) {
                return i + 1
            }
        }
        return 0
    }

    /** Ported from `GuideFeatReqLevel` (basBuild.bas:3404): the level once the "all of" feats are taken. */
    private fun featReqLevel(reqs: Requirements, level: Int): Int {
        if (level < 1) {
            return level
        }
        var result = level
        var need = false
        var found = false
        val featRules = FeatRules(rules)
        for (req in reqs.all) {
            if (req.target is FeatTarget) {
                need = true
                val feat = featRules.featPointer(req).feat
                for (entry in rules.featList) {
                    if (entry.actualType != BuildFeatType.ALTERNATE && feat != null && entry.feat == feat) {
                        found = true
                        if (result < entry.level) {
                            result = entry.level
                        }
                        break
                    }
                }
            }
        }
        if (need && !found) {
            result = 0
        }
        return result
    }

    /** AP at a level: 4 a level to 80. `GetAP` (basBuild.bas:3517). */
    private fun ap(level: Int): Int = if (level > 20) 80 else level * 4

    /**
     * Each step's level: a step comes at the first level with enough AP (racial AP up to the
     * build's racial past lives don't count), and not before its minimum level; a bank comes at the
     * first level with AP left, and the next step a level later; a reset comes the level after the
     * step before. Iconic races start at level 14 (or their max). Ported from `CalculateGuideLevels`
     * (basBuild.bas:3448).
     */
    private fun calculateGuideLevels() {
        var spentInTree = IntArray(build.guide.trees.size + 1)
        var level = 1
        val race = build.race
        if (race != null && race.type == RaceType.ICONIC) {
            level = if (build.maxLevels > 14) 14 else build.maxLevels
        }
        var spent = 0
        var spentRacial = 0
        val steps = rules.guideEnhancements
        for (i in steps.indices) {
            val e = steps[i]
            e.level = 0
            e.bank = 0
            e.spentInTree = 0
            when (e.style) {
                GuideStepStyle.UNKNOWN -> {}
                GuideStepStyle.MISSING -> e.level = level // doesn't move the level on
                GuideStepStyle.ENHANCEMENT -> {
                    spent = spent + e.cost
                    spentInTree[e.buildGuideTree] = spentInTree[e.buildGuideTree] + e.cost
                    e.spentInTree = spentInTree[e.buildGuideTree]
                    spentRacial = if (e.spentRacial > build.racialAP) build.racialAP else e.spentRacial
                    if (level < e.ml) {
                        level = e.ml
                    }
                    while (spent > ap(level) + spentRacial) {
                        level++
                        if (level > build.maxLevels) {
                            level = build.maxLevels
                            break
                        }
                    }
                    e.level = level
                }
                GuideStepStyle.BANK_AP -> {
                    do {
                        e.bank = ap(level) - spent + spentRacial
                        if (e.bank < 1) {
                            level++
                            if (level > build.maxLevels) {
                                level = build.maxLevels
                                break
                            }
                        }
                    } while (e.bank <= 0)
                    e.level = level
                    level++
                    e.display = "Bank " + e.bank + " AP"
                }
                GuideStepStyle.RESET_TREE -> {
                    e.level = if (i == 0) 1 else steps[i - 1].level + 1
                    level = e.level
                    spent = spent - spentInTree[e.buildGuideTree]
                    spentInTree[e.buildGuideTree] = 0
                    e.spentInTree = 0
                    val g = guideTree(e.guideTree)
                    if (g?.tree?.style == TreeStyle.RACE) {
                        spentRacial = 0
                    }
                    e.display = "Reset " + (g?.treeName ?: "")
                }
                GuideStepStyle.RESET_ALL_TREES -> {
                    e.level = if (i == 0) 1 else steps[i - 1].level + 1
                    level = e.level
                    spent = 0
                    spentRacial = 0
                    spentInTree = IntArray(build.guide.trees.size + 1)
                    e.spentInTree = 0
                    e.display = "Reset All Trees"
                }
            }
        }
    }

    /**
     * Adds a step to its guide tree's abilities (a higher rank of one already there raises it),
     * or empties the trees a reset resets. Ported from `AddGuideBuildAbility` (basBuild.bas:3521).
     */
    private fun addGuideBuildAbility(step: BuildGuideEnhancement) {
        val id = step.id
        if (id in 1..99) {
            val g = guideTree(lookup(id)) ?: return
            val abilities = g.guideBuildTree.abilities
            for (i in abilities.indices) {
                val a = abilities[i]
                if (a.tier == step.tier && a.ability == step.ability) {
                    if (a.rank < step.rank) {
                        abilities[i] = a.copy(rank = step.rank)
                    }
                    return
                }
            }
            val at = enhancements.insertionPoint(g.guideBuildTree, step.tier, step.ability)
            abilities.add(at, BuildAbility(step.tier, step.ability, step.rank, step.selector))
        } else if (id == 100) {
            for (g in rules.guideTrees) {
                g.guideBuildTree.abilities.clear()
            }
        } else if (id in 101..199) {
            val g = guideTree(lookup(id - 100)) ?: return
            g.guideBuildTree.abilities.clear()
        }
    }


    /**
     * Each step's error, in guide order: the same tree from two classes at once, more than 6
     * non-racial trees, tree lockouts, AP spent in the tree, character and class levels so far,
     * tier 5 from two trees, requirements taken so far, and earlier ranks. Ported from
     * `CheckGuideErrors` (basBuild.bas:3566); `do … while (false)` with `break` is VB's
     * `Do … Exit Do … Loop Until True`.
     *
     * VB details kept: universal trees count towards the 6, and a tree is counted even when it's the
     * one that goes over; a reset takes one off the count even for a tree that wasn't counted.
     */
    private fun checkGuideErrors() {
        var treeMap = HashMap<String, Any>() // enTreeMap: the class (or race) each tree is taken from
        var treeUsed = HashSet<String>() // blnTreeMap
        val classLevel = HashMap<CharacterClass, Int>()
        var currentLevel = 0
        var tier5 = 0
        var treeCount = 0
        val steps = rules.guideEnhancements
        for (i in steps.indices) {
            val e = steps[i]
            e.errorState = false
            e.errorText = ""
            if (e.style == GuideStepStyle.MISSING) {
                e.errorState = true
                e.errorText = "Not in the current data"
                continue
            }
            val tree = e.tree
            val g = guideTree(e.guideTree)
            do {
                if (e.style == GuideStepStyle.ENHANCEMENT && tree != null && g != null) {
                    // Update class level counters
                    if (currentLevel < e.level) {
                        for (level in currentLevel + 1..e.level) {
                            if (level <= rules.heroicLevels()) {
                                val c = build.levelClass(level)
                                if (c != null) {
                                    classLevel[c] = (classLevel[c] ?: 0) + 1
                                }
                            }
                        }
                        currentLevel = e.level
                    }
                    // Make sure the same class tree isn't taken by two different classes
                    val holder = treeMap[tree.name]
                    if (tree.style == TreeStyle.CLASS && holder != null && holder != g.characterClass) {
                        val holderName = (holder as? CharacterClass)?.name ?: ""
                        e.errorText = "Can't take " + tree.name + " from both " + holderName + " and " + (g.characterClass?.name ?: "") + " at the same time"
                        break
                    }
                    // Enforce the 6 tree limit
                    if (tree.style != TreeStyle.RACE && !treeUsed.contains(tree.name)) {
                        treeUsed.add(tree.name)
                        treeCount++
                        if (treeCount > 6) {
                            e.errorText = "Can't use more than 6 class trees at any one time."
                            break
                        }
                    }
                    // Commit this tree (VB stores reAny, the same as ceAny, when there's no race)
                    val taker: Any? = if (g.tree?.style == TreeStyle.CLASS) g.characterClass else build.race
                    if (taker != null) {
                        treeMap[tree.name] = taker
                    } else {
                        treeMap.remove(tree.name)
                    }
                    // Tree lockouts (Savants, Eldritch Knight, AA/Racial AA)
                    val lockout = tree.lockout
                    if (!lockout.isNullOrEmpty()) {
                        val locker = data.findTree(lockout)
                        if (locker != null && treeMap.containsKey(locker.name)) {
                            e.errorText = tree.name + " is locked out by " + locker.name
                            break
                        }
                    }
                    // Spent in tree
                    val spentReq = enhancements.spentReq(tree.style, e.tier, e.ability + 1)
                    if (e.spentInTree - e.cost < spentReq) {
                        e.errorText = "Requires $spentReq AP spent in tree, only " + (e.spentInTree - e.cost) + " AP spent"
                        break
                    }
                    // Character and class levels
                    val (levels, classLevels) = enhancements.levelReqs(tree.style, e.tier, e.ability + 1)
                    if (currentLevel < levels) {
                        e.errorText = "Requires $levels character levels"
                        break
                    }
                    val c = g.characterClass
                    if (tree.style == TreeStyle.CLASS && c != null && (classLevel[c] ?: 0) < classLevels) {
                        e.errorText = "Requires $classLevels " + c.name + " levels"
                        break
                    }
                    // Tier 5 from one tree only
                    if (e.tier == 5) {
                        if (tier5 == 0) {
                            tier5 = e.guideTree
                        } else if (tier5 != e.guideTree) {
                            e.errorText = "Can't take Tier 5s from both " + (guideTree(tier5)?.display ?: "") + " and " + g.display
                            break
                        }
                    }
                    // Requirements
                    val data = abilityData(tree, e.tier, e.ability)
                    if (data != null) {
                        val error = guideErrorReqs(i, data, e.selector, e.rank, currentLevel)
                        if (error != null) {
                            e.errorText = error
                            break
                        }
                    }
                    // Ranks
                    if (e.rank > 1 && !findGuideAbility(i, tree, e.tier, e.ability, e.selector, e.rank - 1)) {
                        e.errorText = "Rank " + e.rank + " requires first taking Rank " + (e.rank - 1)
                        break
                    }
                } else if (e.style == GuideStepStyle.RESET_TREE) {
                    if (tree != null) {
                        treeMap.remove(tree.name)
                        treeUsed.remove(tree.name)
                    }
                    treeCount--
                    if (tier5 == e.guideTree) {
                        tier5 = 0
                    }
                } else if (e.style == GuideStepStyle.RESET_ALL_TREES) {
                    treeMap = HashMap()
                    treeUsed = HashSet()
                    treeCount = 0
                    tier5 = 0
                }
            } while (false)
            e.errorState = e.errorText.isNotEmpty()
        }
    }

    /**
     * True if one of the first [count] steps takes the ability (with [selector] and [rank] if not 0)
     * and no reset after it undoes that. Ported from `FindGuideAbility` (basBuild.bas:3691).
     */
    private fun findGuideAbility(count: Int, tree: Tree?, tier: Int, ability: Int, selector: Int, rank: Int): Boolean {
        var found = false
        val steps = rules.guideEnhancements
        for (i in 0 until count) {
            val e = steps[i]
            if (e.style == GuideStepStyle.ENHANCEMENT) {
                if (e.ability == ability && e.tier == tier && e.tree == tree &&
                    (selector == 0 || e.selector == selector) && (rank == 0 || e.rank == rank)
                ) {
                    found = true
                }
            } else if (e.style == GuideStepStyle.RESET_TREE) {
                if (e.tree == tree) {
                    found = false
                }
            } else if (e.style == GuideStepStyle.RESET_ALL_TREES) {
                found = false
            }
        }
        return found
    }

    /**
     * The first requirement group a step fails, given the steps before it ([index] of them) and
     * the feats taken by [level]. Ported from `CheckGuideErrorReqs` (basBuild.bas:3722).
     *
     * VB detail kept: with a selector, only the selector's requirements are checked, not the
     * ability's.
     */
    private fun guideErrorReqs(index: Int, data: Ability, selector: Int, rank: Int, level: Int): String? {
        var reqs = data.requirements
        var rankReqs = data.rankReqs
        var rankDetails = data.rankDetails
        if (selector != 0 && selector <= data.selectors.size) {
            val s = data.selectors[selector - 1]
            reqs = s.requirements
            rankReqs = s.rankReqs
            rankDetails = s.rankDetails
        }
        val groups = listOf(reqs.all, reqs.one, reqs.none)
        for (group in 0..2) {
            val error = guideErrorReq(index, groups[group], group, level)
            if (error != null) {
                return error
            }
        }
        if (rank > 1 && rankReqs) {
            val rankGroups = enhancements.rankRequirements(rankDetails, rank)
            val list = listOf(rankGroups.all, rankGroups.one, rankGroups.none)
            for (group in 0..2) {
                val error = guideErrorReq(index, list[group], group, level)
                if (error != null) {
                    return error
                }
            }
        }
        return null
    }

    /**
     * Null if one requirement list (0 = all, 1 = one, 2 = none) is met. Ported from
     * `CheckGuideErrorReq` (basBuild.bas:3750).
     *
     * VB detail kept: destiny requirements aren't looked at, so one in an "all of" list always
     * fails, as "Requires " with nothing after it.
     */
    private fun guideErrorReq(index: Int, reqs: List<Requirement>, group: Int, level: Int): String? {
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
                val display = enhancements.requirementDisplay(req, abbreviate = true, currentTree = null)
                if (guideErrorFeat(pointer.feat, pointer.selector, level)) {
                    if (taken.isEmpty()) {
                        taken = display
                    }
                    matches++
                } else if (missing.isEmpty()) {
                    missing = display
                }
            } else if (target is AbilityTarget && target.style == PointerStyle.ENHANCEMENT) {
                val tree = data.findTree(target.treeName)
                val display = enhancements.requirementDisplay(req, abbreviate = true, currentTree = tree)
                if (findGuideAbility(index, tree, target.tier, target.abilityIndex, selectorOf(tree, target), target.rank)) {
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
            return "Requires $missing"
        }
        if (group == 1 && matches < 1) {
            return "Nothing taken from the 'One of' list"
        }
        if (group == 2 && matches > 0) {
            return "Antireq for $taken"
        }
        return null
    }

    /**
     * True if [feat] (with [selector], if not 0) is taken by [level]: in a feat slot, or as what a
     * slot was exchanged for by then. Ported from `CheckGuideErrorFeat` (basBuild.bas:3798), which
     * stops at the first slot above the level.
     */
    private fun guideErrorFeat(feat: Feat?, selector: Int, level: Int): Boolean {
        val list = rules.featList
        for (entry in list) {
            if (entry.level > level) {
                return false
            }
            if (entry.actualType == BuildFeatType.ALTERNATE || entry.actualType == BuildFeatType.EXCHANGE) {
                continue
            }
            var exchange = entry.exchangeIndex
            if (exchange >= 0 && list[exchange].level > level) {
                exchange = -1
            }
            val takenFeat = if (exchange >= 0) list[exchange].feat else entry.feat
            val takenSelector = if (exchange >= 0) list[exchange].selector else entry.selector
            if (takenFeat == feat && (selector == 0 || takenSelector == selector)) {
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------- the Leveling Guide tab

    /** Ported from frmEnhancements.GuideTotals (:2500). */
    fun totals(): GuideTotals {
        val steps = rules.guideEnhancements
        var level: Int
        var ap: Int
        var spent = 0
        var racialSpent = 0
        var universalSpent = 0
        val iconic = build.race?.type == RaceType.ICONIC
        if (steps.isEmpty()) {
            if (iconic) {
                level = if (build.maxLevels > 14) 14 else build.maxLevels
                ap = level * 4
            } else {
                level = 1
                ap = 4
            }
        } else {
            for (e in steps) {
                val style = e.tree?.style
                if (style == TreeStyle.RACE) {
                    racialSpent += e.cost
                }
                if (style == TreeStyle.GLOBAL) {
                    universalSpent += e.cost
                }
            }
            if (racialSpent > build.racialAP) {
                racialSpent = build.racialAP
            }
            if (universalSpent > build.universalAP) {
                universalSpent = build.universalAP
            }
            val last = steps[steps.size - 1]
            level = if (last.style == GuideStepStyle.BANK_AP) last.level + 1 else last.level
            if (level > build.maxLevels) {
                level = build.maxLevels
            }
            val apAtLevel = if (level > 20) 80 else level * 4
            spent = last.spent - racialSpent - universalSpent
            ap = apAtLevel - spent
            if (ap == 0 && level < 20) {
                level++
                ap += 4
            }
        }
        return GuideTotals(
            level, ap, build.racialAP - racialSpent, build.universalAP - universalSpent,
            spent, racialSpent, universalSpent, spent > rules.heroicLevels() * 4, classesAt(level),
        )
    }

    /**
     * The build's classes up to [level] (heroic levels only), in the order first taken: "Rog 1/Wiz 2".
     * Kotlin only: VB's label shows just the level.
     */
    fun classesAt(level: Int): String {
        val classes = ArrayList<CharacterClass>()
        val counts = ArrayList<Int>()
        val last = if (level > HEROIC_LEVELS) HEROIC_LEVELS else level
        for (i in 1..last) {
            val c = build.levelClass(i) ?: continue
            val index = classes.indexOf(c)
            if (index == -1) {
                classes.add(c)
                counts.add(1)
            } else {
                counts[index] = counts[index] + 1
            }
        }
        val text = StringBuilder()
        for (i in classes.indices) {
            if (i > 0) {
                text.append('/')
            }
            var name = initial3(classes[i])
            if (name.isEmpty()) {
                name = classes[i].abbreviation
            }
            text.append(name).append(' ').append(counts[i])
        }
        return text.toString()
    }

    /**
     * The tree list (lstGuideTree): the build's trees, or with [showAll] every tree the guide can
     * use, then "(Commands)" (0). Ported from LoadGuideTrees (:2570).
     */
    fun treeList(showAll: Boolean): List<GuideListItem> {
        val result = ArrayList<GuideListItem>()
        val trees = rules.guideTrees
        for (i in trees.indices) {
            if (showAll || trees[i].buildTree != 0) {
                result.add(GuideListItem(trees[i].display, i + 1))
            }
        }
        result.add(GuideListItem("(Commands)", 0))
        return result
    }

    /**
     * "(Commands)": Bank, a reset for each tree the guide has used, and "Reset all trees". The
     * first half of ShowGuideAvailable (:2624).
     */
    fun commands(guideAP: Int): List<GuideChoice> {
        val result = ArrayList<GuideChoice>()
        result.add(GuideChoice("Bank $guideAP AP", 200, 0, 0, 0, 0))
        val trees = rules.guideTrees
        val active = BooleanArray(trees.size + 1)
        for (e in rules.guideEnhancements) {
            active[e.guideTree] = true
        }
        var resetAll = false
        for (i in 1..trees.size) {
            if (active[i]) {
                val g = trees[i - 1]
                var display = g.treeName
                if (g.duplicate) {
                    display = display + " (" + initial3(g.characterClass) + ")"
                }
                result.add(GuideChoice("Reset $display", 100 + g.buildGuideTree, 0, 0, 0, 0))
                resetAll = true
            }
        }
        if (resetAll) {
            result.add(GuideChoice("Reset all trees", 100, 0, 0, 0, 0))
        }
        return result
    }

    /**
     * The ability ranks of guide tree [guide] the list offers. The second half of
     * ShowGuideAvailable (:2648), with GuideAbilityTaken (:2715), GuideSelected (:2736) and
     * CheckGuideAvailable (:2760).
     *
     * Ranks after the first keep the selector the guide took the first with (Kotlin only, see
     * [guideSelector]).
     *
     * VB quirks kept: "Selected and Available" (the default) lists the same as "Show All": every
     * rank not yet in the guide; only "Available" checks levels and requirements, and an ability
     * that can never be taken (minimum level 0) still passes the level check.
     */
    fun availableAbilities(guide: Int, filter: GuideFilter?, guideLevel: Int): List<GuideChoice> {
        val result = ArrayList<GuideChoice>()
        val g = guideTree(guide) ?: return result
        val tree = g.tree ?: return result
        val spend = enhancements.spentInTree(tree, g.guideBuildTree)
        for (tier in 0..g.maxTier) {
            if (tier >= tree.tiers.size) {
                break
            }
            val abilities = tree.tiers[tier]
            for (ability in abilities.indices) {
                var maxRanks = abilities[ability].ranks
                if (maxRanks == 0) {
                    maxRanks = 1
                }
                for (rank in 1..maxRanks) {
                    if (abilityTaken(g, tier, ability, rank)) {
                        continue
                    }
                    var selector = selectedSelector(g, filter, tier, ability, rank)
                    if (selector < 0) {
                        continue
                    }
                    if (rank > 1) {
                        val earlier = guideSelector(g, tier, ability)
                        if (earlier != 0) {
                            selector = earlier
                        }
                    }
                    if (filter == GuideFilter.ALL_AVAILABLE || filter == null) {
                        if (abilityMinLevel(guide, tier, ability, selector, rank) > guideLevel) {
                            continue
                        }
                        if (unavailable(tree, g.guideBuildTree, tier, ability, selector, rank, spend)) {
                            continue
                        }
                    }
                    result.add(GuideChoice(abilityDisplay(tree, tier, ability, selector, rank), 0, tier, ability, rank, selector))
                }
            }
        }
        return result
    }

    /**
     * "Spent in Kensei: 13 AP": what the whole guide spends in guide tree [guide] (after any resets).
     * Kotlin only: VB shows only the spend at the chosen row ("Spent in Tree").
     */
    fun treeSpentCaption(guide: Int): String {
        val g = guideTree(guide) ?: return ""
        val spent = enhancements.spentInTree(g.tree, g.guideBuildTree).total
        return "Spent in " + g.display + ": " + spent + " AP"
    }

    /**
     * Why "Available" lists nothing for guide tree [guide] at [guideLevel]: everything is already
     * in the guide, or the earliest level anything left can be taken, or (when that level has been
     * reached) that the rest need other abilities or AP spent first. Kotlin only: VB shows an empty
     * list, which looks like a fault when, say, the build's first level is in another class.
     */
    fun nothingAvailableMessage(guide: Int, guideLevel: Int): String {
        val name = guideTree(guide)?.display ?: ""
        val left = availableAbilities(guide, GuideFilter.ALL_ENHANCEMENTS, guideLevel)
        if (left.isEmpty()) {
            return "Everything in $name is already in the guide."
        }
        var earliest = 0
        for (c in left) {
            val level = abilityMinLevel(guide, c.tier, c.ability, c.selector, c.rank)
            if (level > 0 && (earliest == 0 || level < earliest)) {
                earliest = level
            }
        }
        if (earliest > guideLevel) {
            return "Nothing in $name at level $guideLevel: the earliest is level $earliest."
        }
        return "Nothing in $name at level $guideLevel: the rest need other abilities, or more AP spent in the tree, first."
    }

    /**
     * The selector the guide already took this ability with (an earlier rank, not reset since), or 0.
     * Kotlin only: VB offers every selector again for ranks II and III (VB-BUGS 22k), so a guide
     * could take Efficient Metamagic I as Maximize and II as Empower; here the later ranks keep
     * the first rank's choice.
     */
    private fun guideSelector(g: GuideTree, tier: Int, ability: Int): Int {
        for (a in g.guideBuildTree.abilities) {
            if (a.tier == tier && a.ability == ability && a.selector != 0) {
                return a.selector
            }
        }
        return 0
    }

    /** "1: Abbr: Selector II". Ported from GuideBuildAbilityDisplay (:2694). */
    private fun abilityDisplay(tree: Tree, tier: Int, ability: Int, selector: Int, rank: Int): String {
        val data = tree.tiers[tier][ability]
        var text: String
        if (selector == 0) {
            text = data.abbreviation
        } else if (data.selectorOnly) {
            text = data.selectors[selector - 1].name
        } else {
            text = data.abbreviation + ": " + data.selectors[selector - 1].name
        }
        text = "$tier: $text"
        if (data.ranks > 1) {
            text = text + " III".substring(0, minOf(rank + 1, 4))
        }
        return text
    }

    /**
     * True if the guide already takes this rank in this tree, and no reset since. Ported from
     * GuideAbilityTaken (:2715).
     */
    private fun abilityTaken(g: GuideTree, tier: Int, ability: Int, rank: Int): Boolean {
        var taken = false
        for (step in build.guide.enhancements) {
            if (step.id == 100 || step.id == 100 + g.buildGuideTree) {
                taken = false
            } else if (step.id == g.buildGuideTree) {
                if (step.tier == tier && step.ability == ability && step.rank == rank) {
                    taken = true
                }
            }
        }
        return taken
    }

    /**
     * The "only selected enhancements" filter: -1 if this rank isn't in the build's tree at that
     * rank or more; otherwise the selector the build chose (0 for the other filters, whose ranks
     * all pass). Ported from GuideSelected (:2736).
     */
    private fun selectedSelector(g: GuideTree, filter: GuideFilter?, tier: Int, ability: Int, rank: Int): Int {
        if (filter == GuideFilter.ALL_AVAILABLE || filter == GuideFilter.ALL_ENHANCEMENTS || filter == GuideFilter.SELECTED_AVAILABLE) {
            return 0
        }
        if (g.buildTree == 0) {
            return -1
        }
        for (a in build.trees[g.buildTree - 1].abilities) {
            if (a.tier == tier && a.ability == ability) {
                return if (a.rank >= rank) a.selector else -1
            }
        }
        return -1
    }

    /**
     * True if the guide's tree can't take this rank yet: levels, AP spent in the tree, or
     * requirements, against everything the whole guide takes. Ported from CheckGuideAvailable
     * (:2760) and CheckGuideReqs / CheckGuideReq (:2802, :2812).
     *
     * VB details kept: destiny requirements aren't checked and an enhancement in a tree the guide
     * can't use counts as neither taken nor missing, so either in an "all of" list fails it.
     */
    private fun unavailable(tree: Tree, guideBuildTree: BuildTree, tier: Int, ability: Int, selector: Int, rank: Int, spend: TreeSpend): Boolean {
        val (levels, classLevels) = enhancements.levelReqs(guideBuildTree.treeType ?: tree.style, tier, ability + 1)
        if (build.maxLevels < levels || guideBuildTree.classLevels < classLevels) {
            return true
        }
        val spentTier = enhancements.spentTier(tier, ability + 1, tree.style)
        if (spentTier > 0 && spend.byTier[spentTier - 1] < enhancements.spentReq(tree.style, tier, ability + 1)) {
            return true
        }
        val data = tree.tiers[tier][ability]
        var reqs = data.requirements
        if (selector != 0 && selector <= data.selectors.size) {
            reqs = data.selectors[selector - 1].requirements
        }
        if (!guideReqsMet(reqs, rank)) {
            return true
        }
        if (rank > 1 && data.rankReqs) {
            if (!guideReqsMet(enhancements.rankRequirements(data.rankDetails, rank), rank)) {
                return true
            }
            if (selector != 0 && selector <= data.selectors.size) {
                if (!guideReqsMet(enhancements.rankRequirements(data.selectors[selector - 1].rankDetails, rank), rank)) {
                    return true
                }
            }
        }
        return false
    }

    private fun guideReqsMet(reqs: Requirements, ranks: Int): Boolean {
        val groups = listOf(reqs.all, reqs.one, reqs.none)
        val featRules = FeatRules(rules)
        for (group in 0..2) {
            val list = groups[group]
            if (list.isEmpty()) {
                continue
            }
            var matches = 0
            for (req in list) {
                val target = req.target
                if (target is FeatTarget) {
                    val pointer = featRules.featPointer(req)
                    val taken = featRules.identifyTakenFeats(build.maxLevels)
                    val has = if (pointer.selector == 0) taken.of(pointer.feat).times != 0 else taken.selectorTaken(pointer.feat, pointer.selector)
                    if (has) {
                        matches++
                    }
                } else if (target is AbilityTarget && target.style == PointerStyle.ENHANCEMENT) {
                    val g = findGuideTree(target.treeName)
                    if (g != null && enhancements.checkAbility(g.guideBuildTree, target, ranks)) {
                        matches++
                    }
                }
            }
            if (group == 0 && matches < list.size) {
                return false
            }
            if (group == 1 && matches < 1) {
                return false
            }
            if (group == 2 && matches > 0) {
                return false
            }
        }
        return true
    }

    /** The first guide tree for a tree name. FindGuideTree (:2872). */
    private fun findGuideTree(treeName: String): GuideTree? {
        for (g in rules.guideTrees) {
            if (g.tree?.name == treeName) {
                return g
            }
        }
        return null
    }

    /**
     * Which selectors (index 1..n) an ability in guide tree [guide] offers. Ported from
     * GetGuideSelectors (:2938) with GetGuideSelector (:2985).
     *
     * VB detail kept: a parent or sibling ability's choice is looked up in the guide tree being
     * shown, whatever tree the parent is in.
     */
    fun selectors(guide: Int, tier: Int, ability: Int): BooleanArray {
        val g = guideTree(guide) ?: return BooleanArray(1)
        val data = abilityData(g.tree, tier, ability) ?: return BooleanArray(1)
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
                    val feat = this.data.findFeat(target.featName)
                    val taken = FeatRules(rules).identifyTakenFeats(build.maxLevels)
                    for (i in 1..count) {
                        result[i] = taken.selectorTaken(feat, i)
                    }
                } else {
                    val chosen = guideSelectorChosen(g, parent)
                    if (chosen in 0..count) {
                        result[chosen] = true
                    }
                }
            }
            SelectorStyle.EXCLUSIVE -> {
                for (i in 1..count) {
                    result[i] = true
                }
                val chosen = guideSelectorChosen(g, data.parent)
                if (chosen in 0..count) {
                    result[chosen] = false
                }
                for (sibling in data.siblings) {
                    val c = guideSelectorChosen(g, sibling)
                    if (c in 0..count) {
                        result[c] = false
                    }
                }
            }
            SelectorStyle.NONE -> {}
        }
        return result
    }

    private fun guideSelectorChosen(g: GuideTree, pointer: Requirement?): Int {
        val target = pointer?.target as? AbilityTarget ?: return 0
        if (target.style != PointerStyle.ENHANCEMENT) {
            return 0
        }
        for (a in g.guideBuildTree.abilities) {
            if (a.tier == target.tier && a.ability == target.abilityIndex) {
                return a.selector
            }
        }
        return 0
    }

    /** True if the ability needs a selector chosen (its selector style isn't none). */
    fun needsSelector(guide: Int, tier: Int, ability: Int): Boolean {
        val data = abilityData(guideTree(guide)?.tree, tier, ability) ?: return false
        return data.selectorStyle != SelectorStyle.NONE
    }

    /** The name of selector [selector] (1-based) of an ability in guide tree [guide]. */
    fun selectorName(guide: Int, tier: Int, ability: Int, selector: Int): String {
        val data = abilityData(guideTree(guide)?.tree, tier, ability) ?: return ""
        return data.selectors.getOrNull(selector - 1)?.name ?: ""
    }

    /** Adds a command step (Bank, Reset …) at the end. The command half of GuideChooseAbility (:3033). */
    fun addCommand(id: Int) {
        build.guide.enhancements.add(BuildGuideEnhancement(id))
        initGuideEnhancements()
    }

    /**
     * Adds an ability rank at the end, with any lower ranks not yet taken since the last reset of
     * the tree first. Ported from AddGuideAbility (:3072) and AddGuideAbilityRank (:3099); then
     * InitGuideEnhancements, as GuideChooseAbility does.
     */
    fun addAbility(guide: Int, tier: Int, ability: Int, selector: Int, rank: Int) {
        val g = guideTree(guide) ?: return
        var highest = 0
        if (rank > 1) {
            for (e in rules.guideEnhancements) {
                if (e.style == GuideStepStyle.RESET_ALL_TREES) {
                    highest = 0
                } else if (e.style == GuideStepStyle.RESET_TREE) {
                    if (e.guideTree == guide) {
                        highest = 0
                    }
                } else if (e.style == GuideStepStyle.ENHANCEMENT) {
                    if (e.guideTree == guide && e.tier == tier && e.ability == ability && highest < e.rank) {
                        highest = e.rank
                    }
                }
            }
        }
        for (r in highest + 1..rank) {
            // AddGuideAbilityRank: the tree joins build.Guide.Tree with its first step.
            if (g.buildGuideTree == 0) {
                build.guide.trees.add(BuildGuideTree(g.tree?.name ?: g.treeName, g.characterClass))
                val n = build.guide.trees.size
                val lookup = IntArray(n + 1)
                for (i in rules.guideTreeLookup.indices) {
                    if (i <= n) {
                        lookup[i] = rules.guideTreeLookup[i]
                    }
                }
                lookup[n] = guide
                rules.guideTreeLookup = lookup
                g.buildGuideTree = n
            }
            val step = BuildGuideEnhancement(g.buildGuideTree)
            step.tier = tier
            step.ability = ability
            step.selector = selector
            step.rank = r
            build.guide.enhancements.add(step)
        }
        initGuideEnhancements()
    }

    // ---------------------------------------------------------------- rows

    /** Removes the selected rows (index 0 = row 1). DeleteRows (:3348); the caller refreshes. */
    fun deleteRows(selected: List<Boolean>) {
        val steps = build.guide.enhancements
        val kept = ArrayList<BuildGuideEnhancement>()
        for (i in steps.indices) {
            if (i >= selected.size || !selected[i]) {
                kept.add(steps[i])
            }
        }
        steps.clear()
        steps.addAll(kept)
    }

    /** "Delete to End" from 1-based [row]: SetRows(row - 1), or ClearGuide for row 1 (mnuGuide_Click :3328). */
    fun deleteToEnd(row: Int) {
        if (row == 1) {
            clear()
            return
        }
        val steps = build.guide.enhancements
        while (steps.size > row - 1) {
            steps.removeAt(steps.size - 1)
        }
    }

    /** ClearGuide (:3333). */
    fun clear() {
        build.guide.enhancements.clear()
        build.guide.trees.clear()
        rules.guideEnhancements = emptyList()
        rules.guideTrees = emptyList()
        rules.guideTreeLookup = IntArray(0)
    }

    /**
     * Moves the selected rows to just before 1-based [row] (which isn't selected), keeping their
     * order. Returns the first row they now fill (1-based) and how many. Ported from InsertRows (:3376).
     */
    fun insertRows(selected: List<Boolean>, row: Int): Pair<Int, Int> {
        val steps = build.guide.enhancements
        if (row < 1 || row > steps.size) {
            return Pair(0, 0)
        }
        var insertionPoint = 0
        for (i in 0 until row) {
            if (i >= selected.size || !selected[i]) {
                insertionPoint++
            }
        }
        val moved = ArrayList<BuildGuideEnhancement>()
        val rest = ArrayList<BuildGuideEnhancement>()
        for (i in steps.indices) {
            if (i < selected.size && selected[i]) {
                moved.add(steps[i])
            } else {
                rest.add(steps[i])
            }
        }
        steps.clear()
        for (i in 0 until insertionPoint - 1) {
            steps.add(rest[i])
        }
        steps.addAll(moved)
        for (i in insertionPoint - 1 until rest.size) {
            steps.add(rest[i])
        }
        return Pair(insertionPoint, moved.size)
    }

    // ---------------------------------------------------------------- details

    /**
     * Details for an ability rank: description, requirements, rank requirements, the step's error
     * (for a row, 1-based [row]), cost, AP needed in the tree, and the minimum level. Ported from
     * ShowGuideDetails (:3507).
     *
     * VB bug not copied: its level requirements go into the Enhancements tab's details box
     * (`Me.usrDetails`), not this one, so they never show here.
     */
    fun details(guide: Int, tier: Int, ability: Int, selector: Int, rank: Int, row: Int): GuideDetails? {
        val g = guideTree(guide) ?: return null
        val tree = g.tree ?: return null
        val data = abilityData(tree, tier, ability) ?: return null
        val out = DetailsBuilder()
        if (data.description.isNotEmpty()) {
            out.addDescrip(data.description, makeWiki(tree.wiki) + enhancements.tierLink(tier))
        }
        var reqs = data.requirements
        var rankReqs = data.rankReqs
        var rankDetails = data.rankDetails
        if (selector != 0 && selector <= data.selectors.size) {
            val s = data.selectors[selector - 1]
            reqs = s.requirements
            rankReqs = s.rankReqs
            rankDetails = s.rankDetails
        }
        enhancements.detailsReqs(out, tree, reqs, 0)
        if (rankReqs) {
            enhancements.rankReqs(out, tree, rankDetails)
        }
        if (row != 0) {
            val e = rules.guideEnhancements[row - 1]
            if (e.errorState) {
                out.addErrorText("Error: " + e.errorText)
            }
        }
        // The requirement: AP spent in the tree, and the minimum level (GuideAbilityML; below 1 = unattainable).
        val prog = enhancements.spentReq(tree.style, tier, ability + 1)
        val ml = abilityMinLevel(guide, tier, ability, selector, rank)
        return GuideDetails(
            "Tier $tier: " + data.name, out.build(), enhancements.costDescrip(data, selector, perRank = false),
            requirementLabel(prog, ml),
        )
    }

    /**
     * Caption and text for a command. The command half of GuideDetails (:3475).
     *
     * VB quirk kept: a "Reset" command's caption looks up the guide's tree list with the build
     * guide tree's number, so it can name another tree.
     */
    fun commandDetails(command: Int, guideAP: Int): Pair<String, List<DetailLine>> {
        val out = DetailsBuilder()
        var caption = "Details"
        if (command == 100) {
            caption = "Reset All Trees"
            out.addText("Reset all your trees and re-spend all available AP starting at the current level.")
        } else if (command in 101..199) {
            caption = "Reset " + (guideTree(command - 100)?.display ?: "")
            out.addText("You can re-spend all recovered APs starting at the current level.")
        } else if (command == 200) {
            caption = "Bank $guideAP AP"
            out.addText("Save the remainder of your available AP until next level.")
        }
        return Pair(caption, out.build())
    }
}
