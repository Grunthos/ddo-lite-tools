package ddolite.builder.output

import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.BuildRules
import ddolite.builder.build.HEROIC_LEVELS
import ddolite.builder.build.MAX_LEVEL
import ddolite.builder.build.MAX_LEVELUPS
import ddolite.builder.build.RACE_DROW
import ddolite.builder.build.StatRules
import ddolite.builder.data.KNOWN_CLASSES
import ddolite.builder.data.FeatMapKey
import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat

/**
 * A feat slot as the other planners know it. Ported from `ExportFeatType` (basFormat.bas:9).
 *
 * @property ronType the slot kind in Ron's planner (`RonSlotEnum`, basFormatRon.bas:52); 0 = unknown.
 */
class ExportFeat(
    val type: BuildFeatType,
    val level: Int,
    val characterClass: CharacterClass?,
    val classLevel: Int,
    val liteName: String,
    val ronName: String,
    val builderName: String,
) {
    var ronType = 0
    var builderType = ""
}

/** Past lives by VB class number (index 1 onwards), and how many there are in all. */
class PastLives(val lives: IntArray, val count: Int)

/**
 * Exports to Ron's Character Planner (.txt) and DDO Builder (.ddocp). Ported from basFormat.bas
 * (the shared helpers), basFormatRon.ExportFileRon (:123) and basFormatBuilder.ExportFileBuilder
 * (:89). Each returns the file's text; lines are joined with CRLF.
 */
class PlannerExport(private val rules: BuildRules) {
    private val build = rules.build
    private val data = rules.data

    // ---------------------------------------------------------------- shared (basFormat)

    /**
     * The build's alignment, or with none chosen the first of True Neutral, Lawful Neutral,
     * Chaotic Neutral, Neutral Good, Lawful Good, Chaotic Good that the classes and any Pact or
     * Domain allow (True Neutral if none). GetExportAlignment (basFormat.bas:34).
     *
     * VB detail kept: a Pact or Domain selector with no alignment restriction allows none, so the
     * answer is then True Neutral.
     */
    fun exportAlignment(): Alignment {
        val chosen = build.alignment
        if (chosen != null) {
            return chosen
        }
        val allowed = BooleanArray(Alignment.entries.size) { true }
        for (c in build.buildClasses) {
            if (c == null) {
                continue // db.Class(ceAny) allows every alignment
            }
            for (a in Alignment.entries) {
                if (allowed[a.ordinal] && !c.alignments.contains(a)) {
                    allowed[a.ordinal] = false
                }
            }
        }
        for (type in listOf(BuildFeatType.CLASS1, BuildFeatType.CLASS2, BuildFeatType.CLASS3)) {
            for (slot in build.feats(type)) {
                if (slot.selector != 0 && (slot.featName == "Pact" || slot.featName == "Domain")) {
                    val feat = data.findFeat(slot.featName) ?: continue
                    val selector = feat.selectors.getOrNull(slot.selector - 1) ?: continue
                    for (a in Alignment.entries) {
                        val ok = selector.alignments?.contains(a) ?: false
                        if (allowed[a.ordinal] && !ok) {
                            allowed[a.ordinal] = false
                        }
                    }
                }
            }
        }
        val order = listOf(Alignment.TRUE_NEUTRAL, Alignment.LAWFUL_NEUTRAL, Alignment.CHAOTIC_NEUTRAL, Alignment.NEUTRAL_GOOD, Alignment.LAWFUL_GOOD, Alignment.CHAOTIC_GOOD)
        for (a in order) {
            if (allowed[a.ordinal]) {
                return a
            }
        }
        return Alignment.TRUE_NEUTRAL
    }

    /** Points spent on a stat as the planners count them (8 → 7, 10 → 8, 13 → 9, 16 → 10). GetExportStatRaise (:101). */
    fun exportStatRaise(stat: Stat): Int {
        val points = build.statPoints[build.buildPoints.ordinal][stat.ordinal]
        return when (points) {
            8 -> 7
            10 -> 8
            13 -> 9
            16 -> 10
            else -> points
        }
    }

    /**
     * Past lives by VB class number (index 1..25) and how many: hero and legend builds get one per
     * Past Life feat (all classes for Completionist); a hero build has exactly one, a legend build
     * at least two (made up with the main class). GetExportPastLives (:128).
     */
    fun exportPastLives(): PastLives {
        var lives = IntArray(KNOWN_CLASSES.size + 1)
        var count = 0
        var lastClass = 0
        if (build.buildPoints == BuildPoints.HERO || build.buildPoints == BuildPoints.LEGEND) {
            val pastLife = data.findFeat("Past Life")
            for (slot in build.feats(BuildFeatType.STANDARD)) {
                if (slot.featName == "Past Life") {
                    if (pastLife != null && slot.selector > 0 && slot.selector <= pastLife.selectors.size) {
                        val c = classByName(pastLife.selectors[slot.selector - 1].name)
                        lastClass = c
                        if (c != 0) {
                            lives[c] = 1
                        }
                    }
                } else if (slot.featName == "Completionist") {
                    for (j in 1 until lives.size) {
                        lives[j] = 1
                    }
                }
            }
            for (i in 1 until lives.size) {
                count += lives[i]
            }
        }
        if (build.buildPoints == BuildPoints.HERO) {
            if (count != 1) {
                var c = lastClass
                for (i in 0..2) {
                    val bc = build.buildClasses[i]
                    if (bc != null && lives[bc.vbIndex] == 1) {
                        c = bc.vbIndex
                        break
                    }
                }
                if (c == 0) {
                    c = build.buildClasses[0]?.vbIndex ?: 0
                }
                if (c == 0) {
                    c = FIGHTER
                }
                lives = IntArray(KNOWN_CLASSES.size + 1)
                lives[c] = 1
            }
            count = 1
        } else if (build.buildPoints == BuildPoints.LEGEND) {
            if (count < 2) {
                var c = build.buildClasses[0]?.vbIndex ?: 0
                if (c == 0) {
                    c = FIGHTER
                }
                lives[c] = lives[c] + 2 - count
                count = 2
            }
        }
        return PastLives(lives, count)
    }

    /** GetClassID: a class name to its VB number, 0 if unknown. */
    private fun classByName(name: String): Int {
        for (k in KNOWN_CLASSES) {
            if (k.name.equals(name, ignoreCase = true)) {
                return k.vbIndex
            }
        }
        return 0
    }

    /**
     * Each level's feats with their names in the other planners, and the slot kinds. InitExportFeats
     * (:199), TransposeFeats (:218) and IdentifyChannels (:274); the feat map is searched by our
     * names in VB's working order ([ddolite.builder.data.FeatMapOrder]).
     */
    fun exportFeats(): List<List<ExportFeat>> {
        val levels = ArrayList<ArrayList<ExportFeat>>()
        for (i in 0..MAX_LEVEL) {
            levels.add(ArrayList())
        }
        val map = data.featMapOrder
        map.sort(FeatMapKey.LITE)
        rules.initFeatList()
        val list = rules.featList
        for (i in list.indices) {
            val entry = list[i]
            val t = entry.actualType
            if (t == BuildFeatType.GRANTED || t == BuildFeatType.ALTERNATE || t == BuildFeatType.EXCHANGE) {
                continue
            }
            var index = entry.exchangeIndex
            if (index < 0 || list[index].feat == null) {
                index = i
            }
            var feat = list[index].feat
            val selector = list[index].selector
            if (feat != null) {
                if (selector == 0 && feat.selectors.isNotEmpty()) {
                    feat = null
                } else if (selector != 0 && feat.selectors.isEmpty()) {
                    feat = null
                } else if (selector > feat.selectors.size) {
                    feat = null
                }
            }
            if (feat == null || entry.level < 1 || entry.level > MAX_LEVEL) {
                continue
            }
            var lite = feat.name
            if (selector != 0) {
                lite = lite + ": " + feat.selectors[selector - 1].name
            }
            val found = map.seek(lite) ?: continue
            levels[entry.level].add(ExportFeat(t, entry.level, entry.characterClass, entry.classLevel, lite, found.ron, found.builder))
        }
        for (level in levels) {
            for (f in level) {
                identifyChannels(f)
            }
        }
        return levels
    }

    /** IdentifyChannels (:274): the slot kind in each planner. */
    private fun identifyChannels(f: ExportFeat) {
        when (f.type) {
            BuildFeatType.STANDARD -> when (f.level) {
                1, 3, 6, 9, 12, 15, 18 -> {
                    f.builderType = "Standard"
                    f.ronType = RON_STANDARD
                }
                21, 24, 27, 30, 33, 36 -> {
                    f.builderType = "EpicFeat"
                    f.ronType = RON_STANDARD
                }
                22, 25, 28, 31, 34 -> {
                    f.builderType = "EpicDestinyFeat"
                    f.ronType = 18
                }
            }
            BuildFeatType.LEGEND -> {
                f.builderType = "Legendary"
                f.ronType = 20
            }
            BuildFeatType.DEITY -> {
                when (f.classLevel) {
                    1 -> f.builderType = "FollowerOf"
                    3 -> f.builderType = "ChildOf"
                    6 -> f.builderType = "Deity"
                    12 -> f.builderType = "BelovedOf"
                    20 -> f.builderType = "DamageReduction"
                }
                f.ronType = 9
            }
            BuildFeatType.RACE -> when (build.race?.name) {
                "Human" -> {
                    f.builderType = "HumanBonus"
                    f.ronType = 2
                }
                "Half-Elf" -> {
                    f.builderType = "Dilettante"
                    f.ronType = 15
                }
                "Purple Dragon Knight" -> {
                    f.builderType = "PDKBonus"
                    f.ronType = 2
                }
                "Dragonborn" -> {
                    f.builderType = "DragonbornRacial"
                    f.ronType = 21
                }
                "Aasimar" -> {
                    f.builderType = "AasimarBond"
                    f.ronType = 23
                }
            }
            BuildFeatType.CLASS1, BuildFeatType.CLASS2, BuildFeatType.CLASS3 -> when (f.characterClass?.name) {
                "Artificer" -> {
                    f.builderType = "ArtificerBonus"
                    f.ronType = 16
                }
                "Druid" -> {
                    f.builderType = "DruidWildShape"
                    f.ronType = 17
                }
                "Fighter" -> {
                    f.builderType = "FighterBonus"
                    f.ronType = 3
                }
                "Ranger" -> {
                    f.builderType = "FavoredEnemy"
                    f.ronType = 5
                }
                "Rogue" -> {
                    f.builderType = "RogueSpecialAbility"
                    f.ronType = 6
                }
                "Warlock" -> {
                    f.builderType = "WarlockPact"
                    f.ronType = 19
                }
                "Wizard" -> {
                    f.builderType = "Metamagic"
                    f.ronType = 4
                }
                "Favored Soul" -> {
                    when (f.classLevel) {
                        5, 10, 15 -> f.builderType = "EnergyResistance"
                        2 -> f.builderType = "FavoredSoulBattle"
                        7 -> f.builderType = "FavoredSoulHeart"
                    }
                    f.ronType = 10
                }
                "Cleric" -> {
                    f.builderType = "Domain"
                    f.ronType = 22
                }
                "Monk" -> when (f.classLevel) {
                    1, 2 -> {
                        f.builderType = "MonkBonus"
                        f.ronType = 7
                    }
                    3 -> {
                        f.builderType = "MonkPhilosophy"
                        f.ronType = 8
                    }
                    6 -> {
                        f.builderType = "MonkBonus6"
                        f.ronType = 7
                    }
                }
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- Ron's Character Planner

    /**
     * Ron's Character Planner file. ExportFileRon (basFormatRon.bas:123).
     *
     * Not copied: VB's past-life line indexes slot 0 for every class Ron's planner doesn't know
     * (Alchemist and later), which stops the export with "Subscript out of range"; here those
     * classes are left out and the rest is written as VB means to.
     */
    fun ronFile(): String {
        val lines = ArrayList<String>()
        lines.add("VERSION: 4.36.104;")
        lines.add("NAME: " + ronName() + ";")
        lines.add("RACE: " + ronRace() + ";")
        lines.add("SEX: 0;")
        lines.add("ALIGNMENT: " + ronAlignment() + ";")
        lines.add("CLASSRECORD: ")
        for (i in 1..HEROIC_LEVELS) {
            lines.add((build.levelClass(i)?.name ?: "None") + ",")
        }
        for (i in 1..10) {
            lines.add((build.levelClass(1)?.name ?: "None") + ",")
        }
        lines.add(";")
        if (build.buildPoints == BuildPoints.CHAMPION && build.race?.vbIndex != RACE_DROW) {
            lines.add("ABILITYFAVORBONUS: ;")
        }
        val raises = ArrayList<String>()
        for (stat in Stat.entries) {
            raises.add(exportStatRaise(stat).toString())
        }
        lines.add("ABILITYRAISE: " + raises.joinToString(", ") + ";")
        for (i in 1..MAX_LEVELUPS) {
            val levelup = build.levelups[i]
            lines.add("ABILITY" + (i * 4) + ": " + (if (levelup == null) 0 else levelup.ordinal) + ";")
        }
        lines.add("TOMERAISE: ")
        for (stat in Stat.entries) {
            lines.add(build.tomes[stat.ordinal].toString() + ", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,")
        }
        lines.add(";")
        lines.add("SKILLRAISE: ")
        for (level in 1..HEROIC_LEVELS) {
            val values = ArrayList<String>()
            for (skill in Skill.entries) {
                values.add(build.skills[skill.ordinal][level - 1].toString())
            }
            values.add("") // VB's array is one longer than the skills
            lines.add(values.joinToString(", "))
        }
        lines.add(";")
        // GetRonFeats (:272)
        val feats = exportFeats()
        var count = 0
        for (level in 1..30) {
            for (f in feats[level]) {
                if (f.ronName.isNotEmpty() && f.ronType != 0) {
                    count++
                }
            }
        }
        lines.add("FEATLIST: $count,")
        for (level in 1..30) {
            for (f in feats[level]) {
                if (f.ronName.isNotEmpty() && f.ronType != 0) {
                    lines.add(f.ronName + ", " + level + ", " + f.ronType + ",")
                }
            }
        }
        lines.add(";")
        lines.add("ENHANCEMENTTREELIST: ")
        for (i in 1..7) {
            lines.add("NoTree,")
        }
        lines.add(";")
        lines.add("ENHANCEMENTLIST: ")
        lines.add(";")
        lines.add("SPELLLIST: 0,")
        lines.add(";")
        lines.add("ITEMS: 0, ")
        lines.add(";")
        lines.add("EQUIPPED: ")
        lines.add("-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, ;")
        lines.add("PASTLIFE: ")
        lines.add(ronPastLives())
        lines.add("ICONICPL: ")
        lines.add("0, 0, 0, 0, 0, ;")
        lines.add("EPICPL: ")
        lines.add("0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ;")
        lines.add("RACEPL: ")
        lines.add("0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ;")
        return lines.joinToString("\r\n")
    }

    /** GetRonName (:207): "First, Last", first name to 12 letters, last to 20. */
    private fun ronName(): String {
        val name = build.buildName
        if (name.isEmpty()) {
            return " , "
        }
        var first = ""
        var last = ""
        val space = name.indexOf(' ')
        if (space < 0) {
            if (name.length > 12) last = name else first = name
        } else {
            first = name.substring(0, space)
            last = name.substring(space + 1)
        }
        if (first.length > 12) {
            first = first.substring(0, 12)
        }
        if (last.length > 20) {
            last = last.substring(0, 20)
        }
        return "$first, $last"
    }

    /** GetRonRaceID (:228): Ron's race numbers; anything else is Human (0). */
    private fun ronRace(): Int = when (build.race?.vbIndex) {
        2 -> 5 // Drow
        3 -> 3 // Dwarf
        4 -> 1 // Elf
        5 -> 2 // Halfling
        6 -> 6 // Half-Elf
        7 -> 7 // Half-Orc
        8 -> 4 // Warforged
        9 -> 8 // Bladeforged
        10 -> 10 // Purple Dragon Knight
        11 -> 9 // Morninglord
        12 -> 11 // Shadar-kai
        13 -> 12 // Gnome
        14 -> 13 // Deep Gnome
        15 -> 14 // Dragonborn
        16 -> 15 // Aasimar
        17 -> 16 // Scourge
        18 -> 17 // Wood Elf
        else -> 0
    }

    /** GetRonAlignID (:251). */
    private fun ronAlignment(): Int = when (exportAlignment()) {
        Alignment.TRUE_NEUTRAL -> 3
        Alignment.NEUTRAL_GOOD -> 2
        Alignment.LAWFUL_NEUTRAL -> 1
        Alignment.LAWFUL_GOOD -> 0
        Alignment.CHAOTIC_NEUTRAL -> 5
        Alignment.CHAOTIC_GOOD -> 4
    }

    /** GetRonPastLife (:293): lives in Ron's class order, then blanks to VB's class count. */
    private fun ronPastLives(): String {
        val lives = exportPastLives().lives
        val values = Array(KNOWN_CLASSES.size + 1) { "" }
        for (c in 1 until lives.size) {
            val ron = ronClass(c)
            if (ron != 0) {
                values[ron - 1] = lives[c].toString()
            }
        }
        return values.joinToString(", ") + ";"
    }

    /** GetRonClassID (:309): Ron's class numbers by VB class number; 0 for classes it doesn't have. */
    private fun ronClass(vbClass: Int): Int = when (vbClass) {
        1 -> 3 // Barbarian
        2 -> 10 // Bard
        3 -> 7 // Cleric
        4 -> 1 // Fighter
        5 -> 2 // Paladin
        6 -> 6 // Ranger
        7 -> 5 // Rogue
        8 -> 9 // Sorcerer
        9 -> 8 // Wizard
        10 -> 4 // Monk
        11 -> 11 // Favored Soul
        12 -> 12 // Artificer
        13 -> 13 // Druid
        14 -> 14 // Warlock
        else -> 0
    }

    // ---------------------------------------------------------------- DDO Builder

    private val tags = ArrayList<String>()
    private val groups = ArrayList<String>()

    private fun openTag(tag: String) {
        groups.add(tag)
        addTag("<$tag>")
    }

    private fun closeTag() {
        addTag("</" + groups[groups.size - 1] + ">")
        groups.removeAt(groups.size - 1)
    }

    private fun tagValue(tag: String, value: String) {
        if (value.isEmpty()) {
            nullTag(tag)
        } else {
            addTag("<$tag>$value</$tag>", true)
        }
    }

    private fun nullTag(tag: String) {
        addTag("<$tag/>", true)
    }

    /** AddTag (basFormatBuilder.bas:67): three spaces of indent per open group (one less for a group's own tags). */
    private fun addTag(tag: String, value: Boolean = false) {
        var spaces = if (value) groups.size else groups.size - 1
        if (spaces < 0) {
            spaces = 0
        }
        tags.add(" ".repeat(spaces * 3) + tag)
    }

    /** The DDO Builder file (XML). ExportFileBuilder (basFormatBuilder.bas:89). */
    fun builderFile(): String {
        tags.clear()
        groups.clear()
        openTag("DDOCharacterData")
        openTag("Character")
        tagValue("Name", build.buildName)
        tagValue("Alignment", builderAlignment())
        tagValue("Race", build.race?.name ?: "Human")
        addStatTags()
        tagValue("TomeOfFate", "0")
        openTag("SkillTomes")
        for (skill in Skill.entries) {
            val tome = build.skillTomes[skill.ordinal].toString()
            when (skill) {
                Skill.SPELLCRAFT -> tagValue("SpellCraft", tome)
                Skill.UMD -> tagValue("UMD", tome)
                else -> tagValue(skill.displayName.replace(" ", ""), tome)
            }
        }
        closeTag()
        addPastLives()
        nullTag("ActiveStances")
        for (i in 1..MAX_LEVELUPS) {
            tagValue("Level" + (i * 4), build.levelups[i]?.displayName ?: "Strength")
        }
        for (i in 0..2) {
            tagValue("Class" + (i + 1), build.buildClasses[i]?.name ?: "Unknown")
        }
        openTag("SelectedEnhancementTrees")
        for (i in 1..6) {
            tagValue("TreeName", "No Selection")
        }
        closeTag()
        addLevelTraining()
        nullTag("ActiveEpicDestiny")
        tagValue("FatePoints", "5")
        for (i in 1..5) {
            openTag("TwistOfFate")
            tagValue("Tier", "0")
            closeTag()
        }
        tagValue("ActiveGear", "Standard")
        openTag("EquippedGear")
        tagValue("Name", "Standard")
        closeTag()
        while (groups.isNotEmpty()) {
            closeTag()
        }
        return tags.joinToString("\r\n")
    }

    /** BldGetAlignmentName (:156): True Neutral is "Neutral". */
    private fun builderAlignment(): String = when (exportAlignment()) {
        Alignment.TRUE_NEUTRAL -> "Neutral"
        else -> exportAlignment().displayName
    }

    /** AddStatTags (:167). */
    private fun addStatTags() {
        val drow = build.race?.vbIndex == RACE_DROW
        val points = when (build.buildPoints) {
            BuildPoints.ADVENTURER -> 28
            BuildPoints.CHAMPION -> if (drow) 28 else 32
            BuildPoints.HERO -> if (drow) 30 else 34
            BuildPoints.LEGEND -> if (drow) 32 else 36
        }
        openTag("AbilitySpend")
        tagValue("AvailableSpend", points.toString())
        for (stat in Stat.entries) {
            tagValue(stat.abbreviation + "Spend", exportStatRaise(stat).toString())
        }
        closeTag()
        tagValue("GuildLevel", "0")
        for (stat in Stat.entries) {
            tagValue(stat.abbreviation + "Tome", build.tomes[stat.ordinal].toString())
        }
    }

    /** AddPastLives (:190). */
    private fun addPastLives() {
        val pastLives = exportPastLives()
        val lives = pastLives.lives
        val count = pastLives.count
        if (count == 0) {
            nullTag("SpecialFeats")
            return
        }
        openTag("SpecialFeats")
        for (c in 1 until lives.size) {
            val className = classNameByIndex(c)
            for (i in 1..lives[c]) {
                openTag("TrainedFeat")
                tagValue("FeatName", "Past Life: $className")
                tagValue("Type", "Special")
                tagValue("LevelTrainedAt", "0")
                closeTag()
            }
        }
        closeTag()
    }

    /** db.Class(n).ClassName: the data's class with that VB number, or the known name. */
    private fun classNameByIndex(vbIndex: Int): String {
        for (c in data.classes) {
            if (c.vbIndex == vbIndex) {
                return c.name
            }
        }
        for (k in KNOWN_CLASSES) {
            if (k.vbIndex == vbIndex) {
                return k.name
            }
        }
        return ""
    }

    /** AddLevelTraining (:214) with TrainedFeats (:258): levels 1-20 with skills, then 21-30 as Epic. */
    private fun addLevelTraining() {
        val feats = exportFeats()
        val stats = StatRules(rules)
        for (level in 1..HEROIC_LEVELS) {
            openTag("LevelTraining")
            val c = build.levelClass(level)
            if (c == null) {
                tagValue("SkillPointsAvailable", "0")
                tagValue("SkillPointsSpent", "0")
                nullTag("AutomaticFeats")
                nullTag("TrainedFeats")
            } else {
                // Skill.Col(level).MaxPoints and .Points, as InitBuildSkills works them out.
                var max = c.skillPoints + (build.race?.skillPoints ?: 0) + stats.stat(Stat.INT, level, modifier = true)
                if (max < 1) {
                    max = 1
                }
                if (level == 1) {
                    max *= 4
                }
                var spent = 0
                for (skill in Skill.entries) {
                    spent += build.skills[skill.ordinal][level - 1]
                }
                tagValue("Class", c.name)
                tagValue("SkillPointsAvailable", max.toString())
                tagValue("SkillPointsSpent", spent.toString())
                nullTag("AutomaticFeats")
                trainedFeats(feats[level], level)
                if (spent > 0) {
                    for (skill in Skill.entries) {
                        for (i in 1..build.skills[skill.ordinal][level - 1]) {
                            openTag("TrainedSkill")
                            tagValue("Skill", skill.displayName)
                            closeTag()
                        }
                    }
                }
            }
            closeTag()
        }
        for (level in 21..30) {
            openTag("LevelTraining")
            tagValue("Class", "Epic")
            tagValue("SkillPointsAvailable", "0")
            tagValue("SkillPointsSpent", "0")
            nullTag("AutomaticFeats")
            trainedFeats(feats[level], level)
            closeTag()
        }
    }

    private fun trainedFeats(feats: List<ExportFeat>, level: Int) {
        var count = 0
        for (f in feats) {
            if (f.builderName.isNotEmpty() && f.builderType.isNotEmpty()) {
                count++
            }
        }
        if (count == 0) {
            nullTag("TrainedFeats")
            return
        }
        openTag("TrainedFeats")
        for (f in feats) {
            if (f.builderName.isNotEmpty() && f.builderType.isNotEmpty()) {
                openTag("TrainedFeat")
                tagValue("FeatName", f.builderName)
                tagValue("Type", f.builderType)
                tagValue("LevelTrainedAt", (level - 1).toString())
                closeTag()
            }
        }
        closeTag()
    }

    private companion object {
        /** ceFighter. */
        const val FIGHTER = 4

        /** rseStandard. */
        const val RON_STANDARD = 1
    }
}
