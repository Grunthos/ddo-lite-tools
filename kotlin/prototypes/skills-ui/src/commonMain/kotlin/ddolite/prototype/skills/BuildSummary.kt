package ddolite.prototype.skills

import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.BuildLoadResult
import ddolite.builder.build.BuildPoints
import ddolite.builder.model.Stat

/**
 * Text lines describing an opened build, for the main window: the Overview fields, stats, feats,
 * trees and destinies, then anything loading couldn't match or read.
 *
 * Prototype only, so the loaded data can be checked by eye. It isn't a port: VB's real output
 * (`basOutput.GenerateOutput`, Build/basOutput.bas:154) comes with the main window.
 */
fun openedBuildLines(result: BuildLoadResult): List<String> {
    val build = result.build
    val rules = result.rules
    val lines = ArrayList<String>()

    var alignment = "(none)"
    val buildAlignment = build.alignment
    if (buildAlignment != null) {
        alignment = buildAlignment.displayName
    }
    lines.add("Alignment: " + alignment)
    lines.add("Max levels: " + build.maxLevels)
    val split = StringBuilder()
    for (entry in rules.classSplit()) {
        if (split.isNotEmpty()) {
            split.append(" / ")
        }
        split.append(entry.characterClass.name).append(' ').append(entry.levels)
    }
    if (split.isEmpty()) {
        split.append("(none)")
    }
    lines.add("Classes: " + split)
    val heroic = rules.heroicLevels()
    if (heroic > 0) {
        lines.add("BAB at level " + heroic + ": " + build.bab[heroic - 1])
    }
    lines.add("")

    // Stats: points spent in the preferred point-buy column, level-ups and tomes.
    val column = build.buildPoints.ordinal
    lines.add("Stats (" + build.buildPoints.displayName + ", " + build.statTotals[column] + " points):")
    for (stat in Stat.entries) {
        var line = "  " + stat.abbreviation + ": " + build.statPoints[column][stat.ordinal] + " points"
        if (build.tomes[stat.ordinal] > 0) {
            line = line + ", +" + build.tomes[stat.ordinal] + " tome"
        }
        lines.add(line)
    }
    val levelups = StringBuilder()
    for (i in build.levelups.indices) {
        val stat = build.levelups[i]
        if (stat != null) {
            if (levelups.isNotEmpty()) {
                levelups.append(", ")
            }
            levelups.append(i * 4).append(' ').append(stat.abbreviation)
        }
    }
    if (levelups.isNotEmpty()) {
        lines.add("  Level-ups: " + levelups)
    }
    var columns = ""
    for (points in BuildPoints.entries) {
        if (build.includePoints[points.ordinal]) {
            if (columns.isNotEmpty()) {
                columns = columns + ", "
            }
            columns = columns + points.displayName
        }
    }
    lines.add("  Columns shown: " + columns)
    lines.add("")

    // Feats in level order (the rules' feat list, as the Feats screen shows them), without the
    // granted ones, which would bury the chosen ones.
    lines.add("Feats (not granted):")
    for (entry in rules.featList) {
        val slot = entry.slot
        if (slot.featName.isEmpty() || entry.actualType == BuildFeatType.GRANTED) {
            continue
        }
        var name = slot.featName
        val feat = rules.findFeatOrNull(slot.featName)
        if (feat != null && slot.selector > 0 && slot.selector <= feat.selectors.size) {
            name = name + ": " + feat.selectors[slot.selector - 1].name
        }
        var level = "" + slot.level
        if (slot.level == 99) {
            level = "-"
        }
        lines.add("  " + level.padStart(2) + "  " + slotKind(entry.actualType).padEnd(9) + name)
    }
    lines.add("")

    lines.add("Trees (abilities taken):")
    for (tree in build.trees) {
        lines.add("  " + tree.treeName + " (" + tree.abilities.size + ")")
    }
    if (build.destinies.isNotEmpty()) {
        lines.add("Destinies (abilities taken):")
        for (tree in build.destinies) {
            lines.add("  " + tree.treeName + " (" + tree.abilities.size + ")")
        }
    }
    lines.add("Leveling guide steps: " + build.guide.enhancements.size)
    lines.add("")

    val deprecations = result.deprecations
    if (deprecations.any) {
        lines.add("Not in the current data (VB lists these on its Deprecated screen):")
        for (feat in deprecations.feats) {
            var name = feat.featName
            if (feat.selectorName.isNotEmpty()) {
                name = name + ": " + feat.selectorName
            }
            lines.add("  Feat: " + name)
        }
        for (change in deprecations.nameChanges) {
            lines.add("  Renamed: " + change.oldName + " -> " + change.newName)
        }
        for (ability in deprecations.enhancements) {
            lines.add("  Enhancement: " + ability.raw)
        }
        for (ability in deprecations.destinies) {
            lines.add("  Destiny: " + ability.raw)
        }
        val guide = deprecations.levelingGuide
        if (guide != null) {
            lines.add("  Leveling guide, from: " + guide.raw)
        }
        lines.add("")
    }
    if (result.problems.isNotEmpty()) {
        lines.add("Lines VB would have stopped on (\"Ignore error and load anyway?\"):")
        for (problem in result.problems) {
            lines.add("  Line " + problem.lineNumber + ": " + problem.line + "  (" + problem.message + ")")
        }
        lines.add("")
    }
    return lines
}

/** The slot kind for the summary; the three class kinds all read "Class". */
private fun slotKind(type: BuildFeatType): String = type.fileName
