package ddolite.builder.build

import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Stat
import ddolite.builder.model.StatTemplate

/**
 * Ability scores: the point-buy columns, level-ups and tomes, and the score at any level.
 * Ported from the stat routines in basBuild (CalculateBaseStat :248, CalculateStat :259,
 * CalculateTome :281, CalculateLevelup :292, TomeLevel :308), the userStats control
 * (Source/Controls/userStats.ctl) and the build-changing parts of frmStats
 * (Source/Forms/Build/frmStats.frm). The screen calls these, then [BuildRules.cascadeChanges]
 * with [CascadeChange.STATS] (VB's StatsChanged, frmStats.frm:984).
 *
 * Point-buy columns are [BuildPoints] (Adventurer, Champion, Hero, Legend); stats are [Stat].
 */
class StatRules(val rules: BuildRules) {
    val build: Build get() = rules.build

    // ---------------------------------------------------------------- calculations

    /**
     * The score bought with [points] on top of the race's [base]: one per point up to 6, then
     * 8 → +7, 10 → +8, 13 → +9, 16 → +10. Ported from CalculateBaseStat (basBuild.bas:248);
     * other point counts give 0, as VB's Select Case does.
     */
    fun baseStat(base: Int, points: Int): Int = when (points) {
        in 0..6 -> base + points
        8 -> base + 7
        10 -> base + 8
        13 -> base + 9
        16 -> base + 10
        else -> 0
    }

    /** The race's starting score for [stat] (VB: `db.Race(build.Race).Stats(stat)`); 0 with no race. */
    fun raceBase(stat: Stat): Int = build.race?.stat(stat) ?: 0

    /** The score bought in point-buy [column] for [stat]. */
    fun columnStat(column: BuildPoints, stat: Stat): Int = baseStat(raceBase(stat), build.statPoints[column.ordinal][stat.ordinal])

    /**
     * The score (or its modifier, with [modifier]) at character [level]: bought score in the
     * preferred column, plus level-ups, plus tomes. With [fred], a tome counts at level 1 too.
     * Ported from CalculateStat (basBuild.bas:259). Level 0 counts as level 1 (the Schedule tab).
     */
    fun stat(stat: Stat, level: Int, modifier: Boolean = false, fred: Boolean = false): Int {
        var atLevel = level
        if (atLevel == 0) {
            atLevel = 1
        }
        if (build.race == null) {
            return 0
        }
        var total = baseStat(raceBase(stat), build.statPoints[build.buildPoints.ordinal][stat.ordinal])
        total += levelups(stat, atLevel)
        total += tome(stat, atLevel, fred)
        if (!modifier) {
            return total
        }
        // VB: "Throwing away the decimal incorrectly converts -0.5 into 0, so subtract 11 instead of 10".
        // VB's \ truncates towards zero, as Kotlin's / does.
        if (total < 10) {
            return (total - 11) / 2
        }
        return (total - 10) / 2
    }

    /** The tome bonus to [stat] at [level]: none at level 1 unless [fred]. Ported from CalculateTome (basBuild.bas:281). */
    fun tome(stat: Stat, level: Int, fred: Boolean): Int {
        var tome = 0
        if (level > 1 || fred) {
            tome = tomeLevel(level)
            if (build.tomes[stat.ordinal] < tome) {
                tome = build.tomes[stat.ordinal]
            }
        }
        return tome
    }

    /**
     * Level-up points in [stat] by [level]. Ported from CalculateLevelup (basBuild.bas:292).
     * Quirk kept: VB counts only the level-ups at 4 to 32 (`iMaxLevelUp = 32 \ 4`, "NEED MAX LEVEL
     * HERE"), so the level 36 level-up never adds to the score.
     */
    fun levelups(stat: Stat, level: Int): Int {
        var count = 0
        for (i in 1..32 / 4) {
            if (level >= i * 4 && build.levelups[i] == stat) {
                count++
            }
        }
        return count
    }

    /** The biggest stat tome usable at [level] (0 if none). Ported from TomeLevel (basBuild.bas:308). */
    fun tomeLevel(level: Int): Int {
        val schedule = rules.data.tomes.stat
        var i = schedule.max
        while (i >= 1) {
            if (schedule.level(i) > 0 && schedule.level(i) <= level) {
                break
            }
            i--
        }
        return i
    }

    // ---------------------------------------------------------------- point-buy columns

    /** Recounts the points spent in every column (userStats.Refresh, userStats.ctl:442). */
    fun recountPoints() {
        for (column in BuildPoints.entries) {
            var total = 0
            for (stat in Stat.entries) {
                total += build.statPoints[column.ordinal][stat.ordinal]
            }
            build.statTotals[column.ordinal] = total
        }
    }

    /**
     * The points the next increase of [stat] in [column] costs: 1 up to 6 points, then 2, 2, 3, 3;
     * 0 when it can't go higher. Ported from userStats.NextChange (userStats.ctl:561).
     */
    fun nextCost(column: BuildPoints, stat: Stat): Int = when (build.statPoints[column.ordinal][stat.ordinal]) {
        in 0..5 -> 1
        6, 8 -> 2
        10, 13 -> 3
        else -> 0
    }

    /**
     * Raises [stat] in [column] by one step if the column can afford it. When [column] is the
     * preferred one, the other columns follow if they can. Ported from userStats.Increase
     * (:552), ChangePoints (:583) and frmStats.usrStats_StatChange (frmStats.frm:1175).
     * Returns false if nothing changed.
     */
    fun increase(column: BuildPoints, stat: Stat): Boolean {
        if (!increaseOne(column, stat)) {
            return false
        }
        if (build.buildPoints == column) {
            for (other in BuildPoints.entries) {
                if (other != column) {
                    increaseOne(other, stat)
                }
            }
        }
        return true
    }

    /** Lowers [stat] in [column] by one step, the other columns following as for [increase]. Ported from userStats.Decrease (:570). */
    fun decrease(column: BuildPoints, stat: Stat): Boolean {
        if (!decreaseOne(column, stat)) {
            return false
        }
        if (build.buildPoints == column) {
            for (other in BuildPoints.entries) {
                if (other != column) {
                    decreaseOne(other, stat)
                }
            }
        }
        return true
    }

    private fun increaseOne(column: BuildPoints, stat: Stat): Boolean {
        val change = nextCost(column, stat)
        if (change == 0 || build.statTotals[column.ordinal] + change > rules.buildPointsFor(column)) {
            return false
        }
        changePoints(column, stat, change)
        return true
    }

    private fun decreaseOne(column: BuildPoints, stat: Stat): Boolean {
        val change = when (build.statPoints[column.ordinal][stat.ordinal]) {
            in 1..6 -> -1
            8, 10 -> -2
            13, 16 -> -3
            else -> return false
        }
        changePoints(column, stat, change)
        return true
    }

    private fun changePoints(column: BuildPoints, stat: Stat, change: Int) {
        build.statPoints[column.ordinal][stat.ordinal] += change
        build.statTotals[column.ordinal] += change
    }

    /** The point-buy column's label ("28pt", …). Ported from frmStats.ShowBuildPoints (frmStats.frm:1164). */
    fun pointsLabel(): String = rules.buildPointsFor(build.buildPoints).toString() + "pt"

    /** The columns offered in the "Preferred Build Points" list: no Champion for Drow (LoadData, frmStats.frm:1084). */
    fun preferredChoices(): List<BuildPoints> {
        val result = ArrayList<BuildPoints>()
        for (points in BuildPoints.entries) {
            if (points != BuildPoints.CHAMPION || !isDrow()) {
                result.add(points)
            }
        }
        return result
    }

    /** True if the column's Include box can be changed: not Champion for a Drow (userStats.ShowPoints, :453). */
    fun canInclude(column: BuildPoints): Boolean = !(column == BuildPoints.CHAMPION && isDrow())

    private fun isDrow(): Boolean {
        val race = build.race
        return race != null && race.vbIndex == RACE_DROW
    }

    /** A new preferred column, which is always shown. Ported from frmStats.cboBuildPoints_Click (:1154). */
    fun setPreferred(column: BuildPoints) {
        build.buildPoints = column
        build.includePoints[column.ordinal] = true
    }

    /**
     * A column's Include box was changed. Turning off the preferred column makes the next shown
     * column (in order, wrapping round) the preferred one. Ported from userStats.usrchkInclude_UserChange
     * (userStats.ctl:536) and frmStats.usrStats_Include (frmStats.frm:1183).
     */
    fun setIncluded(column: BuildPoints, include: Boolean) {
        build.includePoints[column.ordinal] = include
        if (include || build.buildPoints != column) {
            return
        }
        for (i in 1..4) {
            var next = column.ordinal + i
            if (next > 3) {
                next -= 4
            }
            if (build.includePoints[next]) {
                setPreferred(BuildPoints.entries[next])
                return
            }
        }
    }

    // ---------------------------------------------------------------- level-ups and tomes

    /**
     * Sets level-up [index] (0 = "All Levels", 1-9 = levels 4-36) to [stat] (null = none).
     * "All Levels" sets every level; otherwise "All Levels" shows the stat only if every level
     * agrees. Ported from frmStats.cboLevelup_Click (frmStats.frm:1202).
     */
    fun setLevelup(index: Int, stat: Stat?) {
        build.levelups[index] = stat
        if (index == 0) {
            for (i in 1..MAX_LEVELUPS) {
                build.levelups[i] = stat
            }
            return
        }
        var uniform: Stat? = build.levelups[1]
        for (i in 2..MAX_LEVELUPS) {
            if (build.levelups[i] != uniform) {
                uniform = null
            }
        }
        build.levelups[0] = uniform
    }

    /** True if the level-up at [index] (1-9, level index × 4) is shown: the build reaches it (EnforceLevelMax, :1140). */
    fun levelupShown(index: Int): Boolean {
        if (index == 0) {
            return levelupShown(1)
        }
        return build.maxLevels >= index * 4
    }

    /** The most tomes usable by the build's last level, and whether that's below the data's limit ("Tomes locked to max level"). */
    fun tomeMax(): Int {
        val max = tomeLevel(build.maxLevels)
        val limit = rules.data.tomes.stat.max
        if (max >= limit) {
            return limit
        }
        return max
    }

    fun tomesLimitedByLevel(): Boolean = tomeLevel(build.maxLevels) < rules.data.tomes.stat.max

    /** Lowers any tome above [tomeMax]. The tome part of frmStats.EnforceLevelMax (frmStats.frm:1116). */
    fun enforceTomeMax() {
        val max = tomeMax()
        if (build.tomeAll > max) {
            build.tomeAll = max
        }
        for (i in build.tomes.indices) {
            if (build.tomes[i] > max) {
                build.tomes[i] = max
            }
        }
    }

    /**
     * A tome spinner changed: [stat] null is the "Supreme" spinner. Every stat tome is then at least
     * the Supreme value. Ported from frmStats.usrSpinner_Change (frmStats.frm:1237).
     */
    fun setTome(stat: Stat?, value: Int) {
        if (stat == null) {
            build.tomeAll = value
        } else {
            build.tomes[stat.ordinal] = value
        }
        for (i in build.tomes.indices) {
            if (build.tomes[i] < build.tomeAll) {
                build.tomes[i] = build.tomeAll
            }
        }
    }

    /** True if any stat has a tome (the "Keep tomes?" question, frmStats.tmrFocus_Timer :1024). */
    fun hasTomes(): Boolean {
        for (tome in build.tomes) {
            if (tome > 0) {
                return true
            }
        }
        return false
    }

    /**
     * "Clear Stats": no points, no level-ups, every column shown, the preferred column from the
     * settings; tomes too (the Supreme tome with them) unless [keepTomes]. Ported from
     * frmStats.tmrFocus_Timer (frmStats.frm:1024); the screen asks "Keep tomes?" or "Clear stats and levelups?".
     */
    fun clearStats(keepTomes: Boolean, preferred: BuildPoints) {
        if (!keepTomes) {
            build.tomes.fill(0)
            build.tomeAll = 0
        }
        for (column in build.statPoints) {
            column.fill(0)
        }
        build.statTotals.fill(0)
        build.levelups.fill(null)
        for (i in build.includePoints.indices) {
            build.includePoints[i] = true
        }
        build.buildPoints = preferred
        if (isDrow()) {
            build.includePoints[BuildPoints.CHAMPION.ordinal] = false
        }
    }

    // ---------------------------------------------------------------- templates

    /** True if any heroic level's class can disable traps. Ported from frmStats.IsTrapper (frmStats.frm:1306). */
    fun isTrapper(): Boolean {
        for (c in build.levelClasses) {
            if (c != null && c.trapper) {
                return true
            }
        }
        return false
    }

    /** The class whose templates are shown first: the build's main class by levels (DefaultTemplates, :1255). */
    fun defaultTemplateClass(): CharacterClass? {
        val split = rules.classSplit()
        if (split.isEmpty()) {
            return null
        }
        return split[0].characterClass
    }

    /**
     * Up to four templates for [c]: those for every build, and those matching whether the build
     * can disable traps. Ported from frmStats.AddTemplates (frmStats.frm:1273).
     */
    fun templatesFor(c: CharacterClass?): List<StatTemplate> {
        val result = ArrayList<StatTemplate>()
        if (c == null) {
            return result
        }
        val trapping = isTrapper()
        for (template in rules.data.templates) {
            if (template.className.equals(c.name, ignoreCase = true) && (template.always || template.trapping == trapping)) {
                result.add(template)
                if (result.size > 3) {
                    break
                }
            }
        }
        return result
    }

    /**
     * Applies a template: its points for each column (a Drow uses the 28, 28, 30 and 32 point
     * spreads; others 28, 32, 34 and 36) and its level-up stat everywhere. Ported from
     * frmStats.ApplyTemplate (frmStats.frm:1358) and ApplyStats (:1385).
     */
    fun applyTemplate(template: StatTemplate) {
        for (column in build.statPoints) {
            column.fill(0)
        }
        build.statTotals.fill(0)
        if (isDrow()) {
            applyColumn(template, 0, BuildPoints.ADVENTURER)
            applyColumn(template, 0, BuildPoints.CHAMPION)
            applyColumn(template, 1, BuildPoints.HERO)
            applyColumn(template, 2, BuildPoints.LEGEND)
        } else {
            applyColumn(template, 0, BuildPoints.ADVENTURER)
            applyColumn(template, 2, BuildPoints.CHAMPION)
            applyColumn(template, 3, BuildPoints.HERO)
            applyColumn(template, 4, BuildPoints.LEGEND)
        }
        for (i in 0..MAX_LEVELUPS) {
            build.levelups[i] = template.levelups
        }
    }

    private fun applyColumn(template: StatTemplate, templateColumn: Int, column: BuildPoints) {
        for (stat in Stat.entries) {
            val points = template.points(templateColumn, stat)
            build.statPoints[column.ordinal][stat.ordinal] = points
            build.statTotals[column.ordinal] += points
        }
    }
}
