package ddolite.builder.build

import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle
import ddolite.dataformat.Vb
import ddolite.dataformat.Windows1252

/**
 * Writes a .build file as Windows-1252 bytes, exactly as VB does. See [writeBuildText].
 */
fun writeBuildFile(rules: BuildRules): ByteArray = Windows1252.encode(writeBuildText(rules))

/**
 * The text of a .build file for [rules]' build. Ported from `basFormatLite.SaveFileLite`
 * (basFormatLite.bas:67) and `clsFile.SaveStringAs` (Source/Classes/clsFile.cls:549), which writes
 * the text with `Print #`: one CRLF is added at the end, after dropping one if the text already
 * ends with a blank line.
 *
 * VB quirks, kept:
 * - "MaxLevels:" is written twice.
 * - A deity feat's slot is written with its character level ("Deity 7"), but reading matches deity
 *   slots by class level, so a deity feat whose class level differs from its character level is
 *   lost when the file is opened again.
 */
fun writeBuildText(rules: BuildRules): String {
    val writer = BuildFileWriter(rules)
    writer.write()
    var text = writer.text()
    if (text.endsWith("\r\n\r\n")) {
        text = text.substring(0, text.length - 2)
    }
    return text + "\r\n"
}

/** VB's line array (`mstrLine`, `mlngLines`) and the Save routines of basFormatLite. */
private class BuildFileWriter(private val rules: BuildRules) {
    private val build = rules.build
    private val data = rules.data

    /** The lines written so far; VB's `mlngLines` is its size, and blank lines are "". */
    private val lines = ArrayList<String>()

    fun write() {
        saveOverview()
        saveStats()
        saveSkills()
        saveFeats()
        saveSpells()
        saveEnhancements()
        saveLevelingGuide()
        saveDestiny()
    }

    /** `Join(mstrLine, vbNewLine)` after `TrimArray`, which keeps one empty element past the last line. */
    fun text(): String {
        val all = ArrayList(lines)
        all.add("")
        return all.joinToString("\r\n")
    }

    // ---------------------------------------------------------------- the line array

    /** Ported from `AddLine` (basFormatLite.bas:93): the line, then [blanks] - 1 blank lines. */
    private fun addLine(line: String, blanks: Int = 1) {
        lines.add(line)
        blankLine(blanks - 1)
    }

    /**
     * A section heading with up to two blank lines before it (none at the start of the file) and
     * one after. Ported from `AddSection` (basFormatLite.bas:98).
     */
    private fun addSection(name: String) {
        val count = lines.size
        if (count > 2) {
            var blanks = 2
            if (lines[count - 1].isEmpty()) {
                blanks = 1
                if (lines[count - 2].isEmpty()) {
                    blanks = 0
                }
            }
            blankLine(blanks)
        }
        addLine("[$name]", 2)
    }

    private fun blankLine(count: Int = 1) {
        for (i in 0 until count) {
            lines.add("")
        }
    }

    /** Drops the lines from [count] on. Ported from `RevertToLine` (basFormatLite.bas:128). */
    private fun revertToLine(count: Int) {
        while (lines.size > count) {
            lines.removeAt(lines.size - 1)
        }
    }

    // ---------------------------------------------------------------- overview

    /** Ported from `SaveOverviewLite` (basFormatLite.bas:157) and `SaveClassLevelsLite` (:194). */
    private fun saveOverview() {
        addSection("Overview")
        addLine("Name: " + build.buildName)
        val race = build.race
        if (race != null) {
            addLine("Race: " + race.name)
        }
        val alignment = build.alignment
        if (alignment != null) {
            addLine("Alignment: " + alignment.displayName)
        }
        addLine("MaxLevels: " + build.maxLevels)
        addLine("MaxLevels: " + build.maxLevels)
        saveNotes()
        if (build.buildClasses[0] != null) {
            blankLine()
        }
        for (c in build.buildClasses) {
            if (c != null) {
                addLine("Class: " + c.name)
            }
        }
        // Levels only if the build is multiclassed.
        var level = 2
        while (level <= HEROIC_LEVELS && build.levelClass(level) == build.levelClass(1)) {
            level++
        }
        if (level > HEROIC_LEVELS) {
            return
        }
        blankLine()
        for (i in 1..HEROIC_LEVELS) {
            addLine("Level: " + i + "\t" + (build.levelClass(i)?.name ?: ""))
        }
    }

    /** One "Notes:" line per line of the notes, without trailing blank lines. Ported from `SaveNotesLite` (basFormatLite.bas:178). */
    private fun saveNotes() {
        if (build.notes.isEmpty()) {
            return
        }
        val noteLines = Vb.split(build.notes, "\r\n")
        var last = noteLines.size - 1
        while (last >= 0 && Vb.trim(noteLines[last]).isEmpty()) {
            last--
        }
        blankLine()
        for (i in 0..last) {
            addLine("Notes: " + noteLines[i])
        }
    }

    // ---------------------------------------------------------------- stats

    /**
     * The point-buy table (left out if all empty) and level-ups (left out if none). Ported from
     * `SaveStatsLite` (basFormatLite.bas:211).
     */
    private fun saveStats() {
        addSection("Stats")
        addLine("Preferred: " + build.buildPoints.displayName, 2)
        for (points in BuildPoints.entries) {
            if (!build.includePoints[points.ordinal]) {
                addLine(points.displayName + ": No")
            }
        }
        var start = lines.size
        var include = false
        addLine(";    Advn  Chmp  Hero  Lgnd  Tome")
        addLine(";    ----  ----  ----  ----  ----")
        for (stat in Stat.entries) {
            val line = statLine(stat)
            if (line.hasValues) {
                include = true
            }
            addLine(line.text)
        }
        addLine(";    ----  ----  ----  ----")
        val total = statLine(null)
        if (total.hasValues) {
            include = true
        }
        addLine(total.text, 2)
        if (!include) {
            revertToLine(start)
        }
        start = lines.size
        include = false
        for (i in 0..MAX_LEVELUPS) {
            val stat = build.levelups[i]
            addLine("Levelup: " + i * 4 + "\t" + (stat?.displayName ?: ""))
            if (stat != null) {
                include = true
            }
        }
        if (!include) {
            revertToLine(start)
        }
    }

    /** A row of the stats table, and whether it has anything in it. */
    private class StatLine(val text: String, val hasValues: Boolean)

    /**
     * One row of the table ([stat] null for the totals), and whether it has anything in it.
     * Ported from `MakeStatLine` (basFormatLite.bas:241).
     */
    private fun statLine(stat: Stat?): StatLine {
        var include = false
        val line = StringBuilder()
        line.append(if (stat == null) ";    " else stat.abbreviation.uppercase() + ": ")
        for (column in 0 until 4) {
            val points = if (stat == null) build.statTotals[column] else build.statPoints[column][stat.ordinal]
            if (points == 0) {
                line.append("      ")
            } else {
                line.append(" ").append(rightAlign(points, 2)).append("   ")
                include = true
            }
        }
        if (stat != null) {
            val tome = build.tomes[stat.ordinal]
            if (tome == 0) {
                line.append("   ")
            } else {
                line.append(" ").append(rightAlign(tome, 2))
                include = true
            }
        }
        return StatLine(line.toString(), include)
    }

    // ---------------------------------------------------------------- skills

    /** The ranks table, left out if empty. Ported from `SaveSkillsLite` (basFormatLite.bas:287) and `MakeSkillsLine` (:302). */
    private fun saveSkills() {
        val start = lines.size
        var include = false
        addSection("Skills")
        addLine(";         1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19  20  Tome")
        addLine(";        ------------------------------------------------------------------------------------")
        for (skill in Skill.entries) {
            val line = StringBuilder()
            line.append((skill.abbreviation + "       ").substring(0, 7)).append(": ")
            for (level in 1..HEROIC_LEVELS) {
                val ranks = build.skills[skill.ordinal][level - 1]
                if (ranks == 0) {
                    line.append("    ")
                } else {
                    line.append(rightAlign(ranks, 2)).append("  ")
                    include = true
                }
            }
            val tome = build.skillTomes[skill.ordinal]
            if (tome == 0) {
                line.append("   ")
            } else {
                line.append("  ").append(tome)
                include = true
            }
            addLine(line.toString())
        }
        if (!include) {
            revertToLine(start)
        }
    }

    // ---------------------------------------------------------------- feats

    /** Every kind except granted, a blank line after each, left out if empty. Ported from `SaveFeatsLite` (basFormatLite.bas:329). */
    private fun saveFeats() {
        val start = lines.size
        var include = false
        addSection("Feats")
        for (type in BuildFeatType.entries) {
            if (type == BuildFeatType.GRANTED) {
                continue
            }
            if (addFeatGroup(type)) {
                blankLine()
                include = true
            }
        }
        if (!include) {
            revertToLine(start)
        }
    }

    /**
     * "Kind: Slot<tab>Feat" lines for one kind. Alternates and exchanges name the slot they belong to
     * and are written even when empty; other slots only with a feat. Ported from `AddFeatGroup`
     * (basFormatLite.bas:345).
     */
    private fun addFeatGroup(type: BuildFeatType): Boolean {
        val slots = build.feats(type)
        var added = false
        for (slot in slots) {
            var featText = slot.featName
            if (slot.selector > 0) {
                val feat = rules.findFeatOrNull(featText)
                if (feat != null && feat.selectors.size >= slot.selector) {
                    featText = featText + ": " + feat.selectors[slot.selector - 1].name
                }
            }
            val line: String
            if (type == BuildFeatType.ALTERNATE || type == BuildFeatType.EXCHANGE) {
                val childType = slot.childType ?: BuildFeatType.GRANTED // VB's 0
                val slotText = featSlotText(build.feats(childType)[slot.child])
                val levelText = if (type == BuildFeatType.EXCHANGE) slot.level.toString() + "\t" else ""
                line = type.fileName + ": " + slotText + "\t" + levelText + featText
            } else if (featText.isNotEmpty()) {
                line = type.fileName + ": " + featSlotText(slot) + "\t" + featText
            } else {
                line = ""
            }
            if (line.isNotEmpty()) {
                addLine(line)
                added = true
            }
        }
        return added
    }

    /**
     * How a slot is named in the file: "Heroic 1", "Epic 21", "Destiny 22", "Legend 30",
     * "<race> 1", "<class> <class level>", "Deity <level>". Ported from `GetBuildFeatSlot`
     * (basFormatLite.bas:402); a standard slot at a level it doesn't list (20, 23, …) gets "".
     */
    private fun featSlotText(slot: BuildFeat): String {
        return when (slot.type) {
            BuildFeatType.GRANTED -> "Granted"
            BuildFeatType.STANDARD -> when (slot.level) {
                21, 24, 27, 30, 33, 36 -> "Epic " + slot.level
                22, 25, 28, 31, 34 -> "Destiny " + slot.level
                else -> if (slot.level < 20) "Heroic " + slot.level else ""
            }
            BuildFeatType.LEGEND -> "Legend " + slot.level
            BuildFeatType.RACE -> (build.race?.name ?: "") + " " + slot.level
            BuildFeatType.CLASS1, BuildFeatType.CLASS2, BuildFeatType.CLASS3 ->
                (build.buildClasses[slot.type.classIndex]?.name ?: "") + " " + slot.classLevel
            BuildFeatType.DEITY -> "Deity " + slot.level
            BuildFeatType.ALTERNATE -> "Alternate " + slot.level
            BuildFeatType.EXCHANGE -> "Exchange " + slot.level
        }
    }

    // ---------------------------------------------------------------- spells

    /**
     * Every slot of every class, a blank line after each spell level; left out unless a standard or
     * free slot has a spell (so cleric cures and pact spells alone don't count). Ported from
     * `SaveSpellsLite` (basFormatLite.bas:434).
     */
    private fun saveSpells() {
        if (build.canCastSpell[1] == 0) {
            return
        }
        val start = lines.size
        var include = false
        addSection("Spells")
        for (spells in build.classSpells) {
            val className = spells.characterClass.name
            for (spellLevel in 1..spells.maxSpellLevel) {
                val level = spells.level(spellLevel)
                for (slot in level.slots) {
                    addLine("Spell: " + className + " " + spellLevel + "\t" + slot.spell)
                    if (slot.spell.isNotEmpty() && (slot.type == SpellSlotType.STANDARD || slot.type == SpellSlotType.FREE)) {
                        include = true
                    }
                }
                if (level.slots.isNotEmpty()) {
                    blankLine()
                }
            }
        }
        if (!include) {
            revertToLine(start)
        }
    }

    // ---------------------------------------------------------------- trees

    /** Ported from `SaveEnhancementsLite` (basFormatLite.bas:471). */
    private fun saveEnhancements() {
        if (build.tier5.isEmpty() && build.racialAP == 0 && build.universalAP == 0 && build.trees.isEmpty()) {
            return
        }
        addSection("Enhancements")
        if (build.tier5.isNotEmpty()) {
            addLine("Tier5: " + build.tier5)
        }
        if (build.racialAP != 0) {
            addLine("RacialAP: " + build.racialAP)
        }
        if (build.universalAP != 0) {
            addLine("UniversalAP: " + build.universalAP)
        }
        if (build.tier5.isNotEmpty() || build.racialAP != 0 || build.universalAP != 0) {
            blankLine()
        }
        for (tree in build.trees) {
            addLine("Tree: " + tree.treeName)
            addLine("Type: " + treeStyleName(tree))
            val source = tree.source
            if (source != null) {
                addLine("Source: " + source.name)
            }
            if (tree.classLevels != 0) {
                addLine("ClassLevels: " + tree.classLevels)
            }
            addAbilities(tree, data.findTree(tree.treeName))
            blankLine()
        }
    }

    /** `GetTreeStyleName` (basEnum.bas:638): "" for none. */
    private fun treeStyleName(tree: BuildTree): String = when (tree.treeType) {
        TreeStyle.CLASS -> "Class"
        TreeStyle.RACE -> "Race"
        TreeStyle.DESTINY -> "Destiny"
        TreeStyle.GLOBAL -> "Global"
        TreeStyle.RACE_CLASS -> "RaceClass"
        null -> ""
    }

    /** The tree's ability lines; none if the tree isn't in the data. Ported from `AddAbilityLite` (basFormatLite.bas:496). */
    private fun addAbilities(buildTree: BuildTree, tree: Tree?) {
        if (buildTree.abilities.isEmpty() || tree == null) {
            return
        }
        for (ability in buildTree.abilities) {
            addLine(abilityLine(ability, tree))
        }
    }

    /**
     * "Ability: Tier n: Name[: Selector][ (Rank r)]", or "Error" if the ability isn't in the tree
     * (VB's error handler). Ported from `GetAbilityLine` (basFormatLite.bas:513).
     */
    private fun abilityLine(ability: BuildAbility, tree: Tree): String {
        val dataAbility = tree.tiers.getOrNull(ability.tier)?.getOrNull(ability.ability) ?: return "Error"
        var line = "Ability: Tier " + ability.tier + ": " + dataAbility.name
        if (ability.selector != 0) {
            val selector = dataAbility.selectors.getOrNull(ability.selector - 1) ?: return "Error"
            line = line + ": " + selector.name
        }
        if (dataAbility.ranks > 1) {
            line = line + " (Rank " + ability.rank + ")"
        }
        return line
    }

    // ---------------------------------------------------------------- leveling guide

    /** Ported from `SaveLevelingGuideLite` (basFormatLite.bas:546). */
    private fun saveLevelingGuide() {
        if (build.guide.trees.isEmpty() && build.guide.enhancements.isEmpty()) {
            return
        }
        addSection("LevelingGuide")
        for (step in build.guide.enhancements) {
            addLine("Guide: " + guideLine(step))
        }
    }

    /**
     * One guide step. Trees are named as the guide displays them (see [GuideTree.display]);
     * "Error" where VB's error handler takes over. Ported from `GetGuideLine` (basFormatLite.bas:562).
     */
    private fun guideLine(step: BuildGuideEnhancement): String {
        val id = step.id
        if (id == 0) {
            return "Unknown"
        }
        if (id in 1..99) {
            val guideTree = guideTreeFor(id) ?: return "Error"
            val tree = guideTree.tree ?: return "Error"
            val ability = tree.tiers.getOrNull(step.tier)?.getOrNull(step.ability) ?: return "Error"
            var line = guideTree.display + " Tier " + step.tier + ": " + ability.name
            if (step.selector != 0) {
                val selector = ability.selectors.getOrNull(step.selector - 1) ?: return "Error"
                line = line + ": " + selector.name
            }
            if (ability.ranks > 1) {
                line = line + " (Rank " + step.rank + ")"
            }
            return line
        }
        if (id == 100) {
            return "Reset All Trees"
        }
        if (id in 101..199) {
            val guideTree = guideTreeFor(id - 100) ?: return "Error"
            return "Reset Tree: " + guideTree.display
        }
        if (id == 200) {
            return "Bank AP"
        }
        return ""
    }

    /** VB's `Guide.Tree(Guide.TreeLookup(buildGuideTree))`, or null where VB fails. */
    private fun guideTreeFor(buildGuideTree: Int): GuideTree? {
        val lookup = rules.guideTreeLookup
        if (buildGuideTree >= lookup.size) {
            return null
        }
        val guideTree = lookup[buildGuideTree]
        if (guideTree == 0) {
            return null
        }
        return rules.guideTrees[guideTree - 1]
    }

    // ---------------------------------------------------------------- destiny

    /** Ported from `SaveDestinyLite` (basFormatLite.bas:616). */
    private fun saveDestiny() {
        if (build.destinyTier5.isEmpty() && build.destinies.isEmpty() && build.permDestinyPoints == 0) {
            return
        }
        addSection("Destiny")
        if (build.destinyTier5.isNotEmpty()) {
            addLine("DestinyTier5: " + build.destinyTier5)
        }
        if (build.destinyAP != 0) {
            addLine("DestinyAP: " + build.destinyAP)
        }
        if (build.permDestinyPoints != 0) {
            addLine("PermDestinyPoints: " + build.permDestinyPoints)
        }
        if (build.destinyTier5.isNotEmpty() || build.destinyAP != 0) {
            blankLine()
        }
        for (destiny in build.destinies) {
            addLine("Destiny: " + destiny.treeName)
            addLine("Type: " + treeStyleName(destiny))
            addAbilities(destiny, data.findDestiny(destiny.treeName))
            blankLine()
        }
    }

    /** VB's `Right$("  " & n, width)`. */
    private fun rightAlign(n: Int, width: Int): String {
        val text = "  $n"
        return text.substring(text.length - width)
    }
}
