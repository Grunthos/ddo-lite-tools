package ddolite.builder.rules

import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Skill

/** Heroic levels: the skills grid has one column per level, up to 20. */
const val HEROIC_LEVELS = 20

/**
 * The skill points a build has spent: one number per skill per heroic level. This is VB's
 * `build.Skills(1 To 21, 1 To 20)` (`BuildType6`, Build/basBuildDefs.bas:415). It will become part
 * of the ported build model in plan step B4.
 *
 * Points, not ranks: a cross-class skill gets half a rank per point (see [SkillGrid.native]).
 */
class SkillSpend {
    private val points = Array(Skill.entries.size) { IntArray(HEROIC_LEVELS) }

    /** Points spent on [skill] at [level] (1-20). */
    fun get(skill: Skill, level: Int): Int = points[skill.ordinal][level - 1]

    fun set(skill: Skill, level: Int, value: Int) {
        points[skill.ordinal][level - 1] = value
    }

    fun copy(): SkillSpend {
        val result = SkillSpend()
        for (skill in Skill.entries) {
            for (level in 1..HEROIC_LEVELS) {
                result.set(skill, level, get(skill, level))
            }
        }
        return result
    }
}

/** How a rank or point total compares with its maximum; decides the text colour on the skills screen. */
enum class RankStatus {
    /** Below the maximum: VB draws it dimmed (cveTextDim). */
    UNDER,

    /** At the maximum: normal text (cveText). */
    FULL,

    /** Over the maximum: error colour (cveTextError). */
    OVER,
}

/**
 * The skills grid for a build: which skills are class skills at each level, the rank caps and
 * the skill points available per level.
 *
 * Ported from `basBuild.InitBuildSkills` (Build/basBuild.bas:394) and VB's `SkillGridType`
 * (Build/basBuildDefs.bas:63). VB stores the grid in the global `Skill` and caches the ranks in
 * it; here ranks are always worked out from a [SkillSpend], so there's no cache to keep in step.
 *
 * Ranks are counted in **half ranks**, as in VB, to avoid fractions: a class skill gets 2 per
 * point, a cross-class skill 1. [formatRanks] turns them into "3½" for display.
 *
 * @param classesByLevel the class taken at each heroic level, level 1 first; its size is the
 *   number of heroic levels in the build (VB's `GetHeroicLevels()`, basBuild.bas:110).
 * @param raceSkillPoints the race's extra skill points per level (`db.Race(...).SkillPoints`).
 * @param intModifiers the Intelligence modifier at each level, level 1 first. VB gets these from
 *   `CalculateStat(aeInt, level, True)`, which isn't ported yet, so the caller supplies them.
 */
class SkillGrid(
    private val classesByLevel: List<CharacterClass>,
    private val raceSkillPoints: Int,
    private val intModifiers: List<Int>,
) {
    /** Number of heroic levels in the build (columns in the grid). */
    val levels: Int = classesByLevel.size

    // Per skill: has it been a class skill at this level or any earlier one? (VB's blnEverNative)
    private val everNativeByLevel = Array(Skill.entries.size) { BooleanArray(HEROIC_LEVELS) }
    private val everNative = BooleanArray(Skill.entries.size)

    init {
        require(levels in 1..HEROIC_LEVELS)
        require(intModifiers.size >= levels)
        for (skill in Skill.entries) {
            var native = false
            for (level in 1..levels) {
                if (classesByLevel[level - 1].isNativeSkill(skill)) {
                    native = true
                }
                everNativeByLevel[skill.ordinal][level - 1] = native
            }
            everNative[skill.ordinal] = native
        }
    }

    /** The class taken at [level]. */
    fun classAt(level: Int): CharacterClass = classesByLevel[level - 1]

    /** Half ranks per point for [skill] at [level]: 2 if it's a class skill for that level's class, else 1. */
    fun native(skill: Skill, level: Int): Int {
        if (classAt(level).isNativeSkill(skill)) {
            return 2
        }
        return 1
    }

    /**
     * The most half ranks [skill] can have by [level]: level + 3 full ranks if it has been a class
     * skill at this level or before, otherwise half that. (VB: `.MaxRanks = lngLevel + 3`, doubled
     * if `blnEverNative`.)
     */
    fun maxRanks(skill: Skill, level: Int): Int {
        val max = level + 3
        if (everNativeByLevel[skill.ordinal][level - 1]) {
            return max * 2
        }
        return max
    }

    /** True if [level]'s class can train Disable Device and Open Lock (`Trapper: true` in Classes.txt). */
    fun trapper(level: Int): Boolean = classAt(level).trapper

    /**
     * Skill points available at [level]: class points + race points + Intelligence modifier,
     * at least 1, and four times that at level 1.
     */
    fun maxPoints(level: Int): Int {
        var points = classAt(level).skillPoints + raceSkillPoints + intModifiers[level - 1]
        if (points < 1) {
            points = 1
        }
        if (level == 1) {
            points = points * 4
        }
        return points
    }

    /** Half ranks gained in [skill] at [level] from the points spent there. */
    fun ranks(spend: SkillSpend, skill: Skill, level: Int): Int = spend.get(skill, level) * native(skill, level)

    /** Half ranks in [skill] from level 1 up to and including [level]. Ported from `frmSkills.CumulativeRanks` (frmSkills.frm:1624). */
    fun cumulativeRanks(spend: SkillSpend, skill: Skill, level: Int): Int {
        var last = level
        if (last > levels) {
            last = levels
        }
        var total = 0
        for (l in 1..last) {
            total += ranks(spend, skill, l)
        }
        return total
    }

    /** Skill points spent at [level], over all skills. */
    fun pointsSpent(spend: SkillSpend, level: Int): Int {
        var total = 0
        for (skill in Skill.entries) {
            total += spend.get(skill, level)
        }
        return total
    }

    /** Total half ranks in [skill] over the whole build. */
    fun totalRanks(spend: SkillSpend, skill: Skill): Int = cumulativeRanks(spend, skill, levels)

    /** The most half ranks [skill] can have at the end of the build. */
    fun totalMaxRanks(skill: Skill): Int {
        val max = levels + 3
        if (everNative[skill.ordinal]) {
            return max * 2
        }
        return max
    }

    // ---- Status, for colouring. From the Draw* routines in frmSkills.frm. ----

    /** A cell's colour: its running total against that level's cap. From `frmSkills.DrawCell` (frmSkills.frm:1572). */
    fun cellStatus(spend: SkillSpend, skill: Skill, level: Int): RankStatus =
        compare(cumulativeRanks(spend, skill, level), maxRanks(skill, level))

    /**
     * The total column's colour. From `frmSkills.DrawRowFooter` (frmSkills.frm:1482): one half rank
     * under an odd maximum still counts as full, because a cross-class skill can't reach it.
     */
    fun totalStatus(spend: SkillSpend, skill: Skill): RankStatus {
        val max = totalMaxRanks(skill)
        var min = max
        if (max % 2 != 0) {
            min = max - 1
        }
        val ranks = totalRanks(spend, skill)
        if (ranks > max) {
            return RankStatus.OVER
        }
        if (ranks >= min) {
            return RankStatus.FULL
        }
        return RankStatus.UNDER
    }

    /** A level's points-spent colour. From `frmSkills.DrawColumnFooter` (frmSkills.frm:1551). */
    fun pointsStatus(spend: SkillSpend, level: Int): RankStatus = compare(pointsSpent(spend, level), maxPoints(level))

    private fun compare(value: Int, max: Int): RankStatus {
        if (value > max) {
            return RankStatus.OVER
        }
        if (value < max) {
            return RankStatus.UNDER
        }
        return RankStatus.FULL
    }

    // ---- Editing. Rules taken out of the frmSkills.frm event handlers. ----

    /**
     * True if one more point can go into [skill] at [level]. Ported from `frmSkills.Increment`
     * (frmSkills.frm:1791):
     * - Disable Device and Open Lock need a trapper class at this level or before;
     * - the level must have points left;
     * - the new running total must not go over the level's cap.
     */
    fun canIncrement(spend: SkillSpend, skill: Skill, level: Int): Boolean {
        if (skill == Skill.DISABLE_DEVICE || skill == Skill.OPEN_LOCK) {
            var anyTrapper = false
            for (l in 1..level) {
                if (trapper(l)) {
                    anyTrapper = true
                }
            }
            if (!anyTrapper) {
                return false
            }
        }
        if (pointsSpent(spend, level) >= maxPoints(level)) {
            return false
        }
        return cumulativeRanks(spend, skill, level) + native(skill, level) <= maxRanks(skill, level)
    }

    /** Adds a point if [canIncrement] allows it. Returns true if it did. (`Increment` + `AddPoint 1`, frmSkills.frm:1824.) */
    fun increment(spend: SkillSpend, skill: Skill, level: Int): Boolean {
        if (!canIncrement(spend, skill, level)) {
            return false
        }
        spend.set(skill, level, spend.get(skill, level) + 1)
        return true
    }

    /** Removes a point, if there is one. Returns true if it did. Ported from `frmSkills.Decrement` (frmSkills.frm:1820). */
    fun decrement(spend: SkillSpend, skill: Skill, level: Int): Boolean {
        if (spend.get(skill, level) <= 0) {
            return false
        }
        spend.set(skill, level, spend.get(skill, level) - 1)
        return true
    }

    /** Clears one cell. From `frmSkills.ClearCell` (frmSkills.frm:1855; middle mouse button). */
    fun clearCell(spend: SkillSpend, skill: Skill, level: Int) {
        spend.set(skill, level, 0)
    }

    /** Clears every skill at [level]. From `frmSkills.ClearColumn` (frmSkills.frm:1874; right-click on a level header). */
    fun clearLevel(spend: SkillSpend, level: Int) {
        for (skill in Skill.entries) {
            spend.set(skill, level, 0)
        }
    }

    /** Clears [skill] at every level. From `frmSkills.SkillClear` (frmSkills.frm:1904). */
    fun clearSkill(spend: SkillSpend, skill: Skill) {
        for (level in 1..HEROIC_LEVELS) {
            spend.set(skill, level, 0)
        }
    }

    /**
     * The first level [skill] can be trained: 1, except for Disable Device and Open Lock, which
     * need a trapper level. Ported from `frmSkills.FirstLevel` (frmSkills.frm:2003). Returns 21
     * (past the end) if there's no trapper level, as VB does.
     */
    fun firstLevel(skill: Skill): Int {
        if (skill != Skill.DISABLE_DEVICE && skill != Skill.OPEN_LOCK) {
            return 1
        }
        for (level in 1..HEROIC_LEVELS) {
            if (level <= levels && trapper(level)) {
                return level
            }
        }
        return HEROIC_LEVELS + 1
    }

    /**
     * Puts the most half ranks possible into [skill] at every level, from [firstLevel] on.
     * Ported from `frmSkills.SkillMaxHalfRanks` (frmSkills.frm:1919). Like VB, this ignores the
     * points available per level; the column totals then show red where it's too many.
     * (VB loops on to level 20, setting levels past the end of the build to 0; those are
     * always 0 already, so stopping at [levels] gives the same result.)
     */
    fun maxHalfRanks(spend: SkillSpend, skill: Skill) {
        var cumulative = 0
        for (level in firstLevel(skill)..levels) {
            val native = native(skill, level)
            val ranks = ((maxRanks(skill, level) - cumulative) / native) * native
            spend.set(skill, level, ranks / native)
            cumulative += ranks
        }
    }

    /**
     * Like [maxHalfRanks], but only whole ranks (rounds each level's ranks down to even).
     * Ported from `frmSkills.SkillMaxFullRanks` (frmSkills.frm:1944).
     */
    fun maxFullRanks(spend: SkillSpend, skill: Skill) {
        var cumulative = 0
        for (level in firstLevel(skill)..levels) {
            var ranks = maxRanks(skill, level) - cumulative
            if (ranks % 2 == 1) {
                ranks = ranks - 1
            }
            spend.set(skill, level, ranks / native(skill, level))
            cumulative += ranks
        }
    }

    /**
     * Half the cap at level 1, then whole ranks on even levels only. Ported from
     * `frmSkills.SkillMaxEvenLevels` (frmSkills.frm:1976).
     */
    fun maxEvenLevels(spend: SkillSpend, skill: Skill) {
        var cumulative = 0
        for (level in firstLevel(skill)..levels) {
            var ranks = 0
            if (level == 1) {
                ranks = maxRanks(skill, level) / 2
            } else if (level % 2 == 0) {
                ranks = maxRanks(skill, level) - cumulative
                if (ranks % 2 == 1) {
                    ranks = ranks - 1
                }
            }
            spend.set(skill, level, ranks / native(skill, level))
            cumulative += ranks
        }
    }
}

/**
 * Half ranks as text: 0 → "", 1 → "½", 7 → "3½", 8 → "4".
 * Ported from `frmSkills.FormatRanks` (frmSkills.frm:1616).
 */
fun formatRanks(halfRanks: Int): String {
    if (halfRanks < 1) {
        return ""
    }
    if (halfRanks == 1) {
        return "½"
    }
    if (halfRanks % 2 != 0) {
        return (halfRanks / 2).toString() + "½"
    }
    return (halfRanks / 2).toString()
}
