package ddolite.builder.data

import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.ClassLevel
import ddolite.builder.model.Feat
import ddolite.builder.model.FeatChannel
import ddolite.builder.model.FeatGroup
import ddolite.builder.model.PointerStyle
import ddolite.builder.model.Race
import ddolite.builder.model.RaceRequirement
import ddolite.builder.model.RaceRequirementMode
import ddolite.builder.model.Requirement
import ddolite.builder.model.Requirements
import ddolite.builder.model.Selector
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.dataformat.DataSource
import ddolite.dataformat.NumberedLine
import ddolite.dataformat.RawRecord
import ddolite.dataformat.Vb
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

private const val FEATS_FILE = "Feats.txt"

/**
 * Loads Feats.txt. Ported from `basDataLoad.LoadFeats` (Build/basDataLoad.bas:697).
 *
 * [races] and [classes] are the already-loaded Races.txt and Classes.txt, used to check and
 * normalise the race and class names that feats mention (VB checks them against its hard-coded
 * tables, `GetRaceID` and `GetClassID`; plan D-7).
 *
 * Returns the feats in file order, as VB's `db.Feat`. VB also builds two sorted indexes:
 * `FeatLookup` (by name, for `SeekFeat`) is [BuilderData.findFeat]; `FeatDisplay` (the Feats
 * screen's order, which depends on a user option) belongs with that screen.
 */
fun loadFeats(source: DataSource, races: List<Race>, classes: List<CharacterClass>, log: LoadLog): List<Feat> {
    val decoded = source.readText(BuilderRecordFile.FEATS.path)
    if (decoded == null) {
        log.fileNotFound(FEATS_FILE)
        return emptyList()
    }
    val feats = ArrayList<Feat>()
    val reader = FeatReader(races, classes, log)
    for (record in splitRecords(decoded.text, BuilderRecordFile.FEATS.marker).records) {
        // VB only loads records that contain a "Group: " line.
        if (!record.body.contains("Group: ")) {
            log.invalidSection(FEATS_FILE, record.line, "Group: ", firstLine(record))
            continue
        }
        feats.add(reader.read(record))
    }
    return feats
}

/** A selector being read: VB fills a `SelectorType` field by field, so this is mutable until [build]. */
private class SelectorDraft(val name: String) {
    var wiki = ""
    var description = ""
    var all = false
    var classBonus = ArrayList<String>()
    var stat: Stat? = null
    var statValue = 0
    var skill: Skill? = null
    var skillValue = 0
    var classes: ArrayList<ClassLevel>? = null
    var notClass: String? = null
    var requirements = Requirements.EMPTY
    var alignments: ArrayList<Alignment>? = null
    var raceMode = RaceRequirementMode.ANY
    var races = ArrayList<String>()
    var hide = false
    var notRaces: ArrayList<String>? = null
    var raceOnly: String? = null
    var classOnly: String? = null

    fun build(): Selector = Selector(
        name, wiki, description, all, classBonus, stat, statValue, skill, skillValue, classes, notClass,
        requirements, alignments, RaceRequirement(raceMode, races), hide, notRaces, raceOnly, classOnly,
    )
}

/** A feat being read; see [SelectorDraft]. Field meanings are documented on [Feat]. */
private class FeatDraft(val name: String) {
    var abbreviation = name
    var sortName = name
    var description = ""
    var wiki = name
    val groups = LinkedHashSet<FeatGroup>()
    var selectorStyle = SelectorStyle.NONE
    var parentSelector: String? = null
    var selectors = ArrayList<SelectorDraft>()
    var selectorOnly = false
    var raceBonus: ArrayList<String>? = null
    var classBonus: ArrayList<String>? = null
    var classBonusLevel: ClassLevel? = null
    var grantedBy: ClassLevel? = null
    var deity = false
    var times = 0
    var pastLife = false
    var legend = false
    var selectable = true
    var pact = false
    var domain = false
    var bab = 0
    var level = 0
    var raceMode = RaceRequirementMode.ANY
    val races = ArrayList<String>()
    var classes: ArrayList<ClassLevel>? = null
    var skill: Skill? = null
    var skillValue = 0
    var skillTome = false
    var stat: Stat? = null
    var statValue = 0
    var canCastSpell = false
    var canCastSpellLevel = 0
    var raceOnly = false
    var classOnly = false
    var classOnlyClasses = ArrayList<String>()
    val classOnlyLevels = LinkedHashSet<Int>()
    var requirements = Requirements.EMPTY

    fun build(): Feat {
        val builtSelectors = ArrayList<Selector>()
        for (selector in selectors) {
            builtSelectors.add(selector.build())
        }
        return Feat(
            name = name,
            abbreviation = abbreviation,
            sortName = sortName,
            description = description,
            wiki = wiki,
            groups = groups,
            selectorStyle = selectorStyle,
            parentSelector = parentSelector,
            selectors = builtSelectors,
            selectorOnly = selectorOnly,
            raceBonus = raceBonus,
            classBonus = classBonus,
            classBonusLevel = classBonusLevel,
            grantedBy = grantedBy,
            channel = featChannel(this),
            deity = deity,
            times = times,
            pastLife = pastLife,
            legend = legend,
            selectable = selectable,
            pact = pact,
            domain = domain,
            bab = bab,
            level = level,
            race = RaceRequirement(raceMode, ArrayList(races)),
            classes = classes,
            skill = skill,
            skillValue = skillValue,
            skillTome = skillTome,
            stat = stat,
            statValue = statValue,
            canCastSpell = canCastSpell,
            canCastSpellLevel = canCastSpellLevel,
            raceOnly = raceOnly,
            classOnly = classOnly,
            classOnlyClasses = classOnlyClasses,
            classOnlyLevels = classOnlyLevels,
            requirements = requirements,
        )
    }
}

/** Reads feat records; holds the loaded races and classes for name checks. */
private class FeatReader(
    private val races: List<Race>,
    private val classes: List<CharacterClass>,
    private val log: LoadLog,
) {
    /**
     * Reads one feat record. Ported from `basDataLoad.LoadFeat` (Build/basDataLoad.bas:730).
     *
     * Quirks kept from VB:
     * - A `Selector:` line copies the feat's ClassBonus, Race, requirements, Skill and Stat into each
     *   selector *as they are at that point*: lines after it don't reach the selectors.
     * - The first `SelectorName:` line ends the feat's own lines; everything from there on is read
     *   as selector sections ([readSelector]).
     * - Several problems on one line give one "has invalid line" error (VB's `blnError`), except
     *   where VB reports straight away.
     */
    fun read(record: RawRecord): Feat {
        val feat = FeatDraft(firstLine(record))
        val lines = record.numberedLines()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val parsed = parseLineForLoad(line.text)
            if (parsed is ParsedLine.Blank) {
                continue
            }
            if (parsed !is ParsedLine.Field) {
                invalidFeatLine(feat, line)
                continue
            }
            if (parsed.field == "selectorname") {
                readSelectors(feat, record)
                break // VB: Exit For
            }
            if (!readFeatField(feat, parsed, line)) {
                invalidFeatLine(feat, line)
            }
        }
        return feat.build()
    }

    private fun invalidFeatLine(feat: FeatDraft, line: NumberedLine) {
        log.invalidLine(FEATS_FILE, line.number, feat.name, line.text)
    }

    /**
     * One `Field: value` line of a feat. Returns false if the line is invalid and VB reports it
     * once at the end (`blnError` or a direct `LogError`); some problems are reported here directly.
     */
    private fun readFeatField(feat: FeatDraft, parsed: ParsedLine.Field, line: NumberedLine): Boolean {
        val value = parsed.value
        when (parsed.field) {
            "abbreviation" -> feat.abbreviation = parsed.item
            "sortname" -> feat.sortName = parsed.item
            "descrip" -> feat.description = parsed.item
            "wikiname" -> feat.wiki = parsed.item
            "group" -> {
                for (name in parsed.list) {
                    val group = FeatGroup.find(name)
                    if (group == null) {
                        log.loadError(FEATS_FILE, line.number, "${feat.name} has invalid group: $name")
                    } else {
                        feat.groups.add(group)
                    }
                }
            }
            "bab" -> {
                if (value < 1 || value > 20) {
                    return false
                }
                feat.bab = value
            }
            "repeat" -> {
                // 2 was allowed in 2020 for two ranks of Alchemical Studies.
                if (value != 1 && value != 2 && value != 3 && value != 99) {
                    return false
                }
                feat.times = value
            }
            "stat" -> {
                feat.stat = Stat.find(parsed.item)
                if (feat.stat == null) {
                    return false
                }
                feat.statValue = value
            }
            "skill" -> {
                feat.skill = Skill.find(parsed.item)
                if (feat.skill == null) {
                    return false
                }
                feat.skillValue = value
            }
            "class" -> {
                var list = feat.classes
                if (list == null) {
                    list = ArrayList()
                    feat.classes = list
                }
                return readClassLevels(parsed.list, list)
            }
            "grantedby" -> {
                val c = findClassIn(classes, parsed.item)
                if (c == null || value < 1 || value > 20) {
                    return false
                }
                feat.grantedBy = ClassLevel(c.name, value)
            }
            "classbonuslevel" -> {
                val c = findClassIn(classes, parsed.item)
                if (c == null || value < 1 || value > 20) {
                    return false
                }
                feat.classBonusLevel = ClassLevel(c.name, value)
            }
            "level" -> {
                if (value < 1 || value > BuilderLimits.MAX_LEVEL) {
                    return false
                }
                feat.level = value
            }
            "race" -> {
                // VB doesn't clear the race list for a second Race line, only sets the mode again.
                val mode = readRaceRequirement(parsed.list, feat.races)
                if (mode == null) {
                    feat.raceMode = RaceRequirementMode.ANY
                    return false
                }
                feat.raceMode = mode.first
                return mode.second
            }
            "cancastspell" -> {
                if (value < 0 || value > 9) {
                    return false
                }
                feat.canCastSpell = true
                feat.canCastSpellLevel = value
            }
            "racebonus" -> {
                var list = feat.raceBonus
                if (list == null) {
                    list = ArrayList()
                    feat.raceBonus = list
                }
                for (name in parsed.list) {
                    val race = findRaceIn(races, name)
                    if (race == null) {
                        // VB: LoadError strFeat & vbNewLine & "RaceBonus includes invalid race: " & name
                        log.error(FEATS_FILE, line.number, "$FEATS_FILE: ${feat.name}", "RaceBonus includes invalid race: $name")
                    } else {
                        list.add(race.name)
                    }
                }
            }
            "classbonus" -> {
                var list = feat.classBonus
                if (list == null) {
                    list = ArrayList()
                    feat.classBonus = list
                }
                return readClassNames(parsed.list, list)
            }
            "flags" -> {
                var ok = true
                for (flag in parsed.list) {
                    when (flag.lowercase()) {
                        "selectoronly" -> feat.selectorOnly = true
                        "deity" -> feat.deity = true
                        "classonly" -> {
                            feat.classOnly = true
                            feat.classOnlyClasses = ArrayList() // VB: ReDim .ClassOnlyClasses
                        }
                        "raceonly" -> feat.raceOnly = true
                        "pastlife" -> feat.pastLife = true
                        "legend" -> feat.legend = true
                        "unselectable" -> feat.selectable = false
                        "skilltome" -> feat.skillTome = true
                        "pact" -> feat.pact = true
                        "domain" -> feat.domain = true
                        else -> ok = false
                    }
                }
                return ok
            }
            "classonlyclass" -> return readClassNames(parsed.list, feat.classOnlyClasses)
            "classonlylevel" -> {
                var ok = true
                for (text in parsed.list) {
                    val level = Vb.cLng(Vb.valOf(text))
                    if (level < 1 || level > 20) {
                        ok = false
                    } else {
                        feat.classOnlyLevels.add(level)
                    }
                }
                return ok
            }
            "all", "one", "none" -> feat.requirements = feat.requirements.withGroup(parsed.field, featRequirements(parsed.list))
            "selector" -> {
                if (feat.selectorStyle == SelectorStyle.NONE) {
                    feat.selectorStyle = SelectorStyle.ROOT
                }
                // A new list each time, as VB's ReDim. Each selector starts as a copy of the feat's
                // class bonus, race, requirements, skill and stat so far.
                val list = ArrayList<SelectorDraft>()
                for (name in parsed.list) {
                    val selector = SelectorDraft(name)
                    selector.all = name == "All"
                    val bonus = feat.classBonus
                    if (bonus != null) {
                        selector.classBonus = ArrayList(bonus)
                    }
                    selector.raceMode = feat.raceMode
                    selector.races = ArrayList(feat.races)
                    selector.requirements = feat.requirements
                    selector.skill = feat.skill
                    selector.skillValue = feat.skillValue
                    selector.stat = feat.stat
                    selector.statValue = feat.statValue
                    list.add(selector)
                }
                feat.selectors = list
            }
            "sharedselector" -> {
                feat.selectorStyle = SelectorStyle.SHARED
                feat.parentSelector = parsed.item
            }
            else -> return false
        }
        return true
    }

    /**
     * Reads the selector sections: VB splits the whole feat record again on "SelectorName: " and
     * passes each part to `LoadFeatSelector`.
     */
    private fun readSelectors(feat: FeatDraft, record: RawRecord) {
        for (section in splitRecords(record.body, BuilderSubMarkers.SELECTOR).records) {
            // Line numbers: the section's line within the record, offset by where the record starts.
            readSelector(feat, section, record.line + section.line - 1)
        }
    }

    /**
     * One selector section. Ported from `basDataLoad.LoadFeatSelector` (Build/basDataLoad.bas:968).
     * Errors use VB's `actLoadSelector` wording: "Feats.txt: <feat> SelectorName: <name> has invalid line:".
     */
    private fun readSelector(feat: FeatDraft, section: RawRecord, firstLineNumber: Int) {
        val name = firstLine(section)
        if (name.isEmpty()) {
            return
        }
        var selector: SelectorDraft? = null
        for (candidate in feat.selectors) {
            if (candidate.name == name) {
                selector = candidate
                break
            }
        }
        if (selector == null) {
            // VB reports whatever line it read last; the section's own first line is clearer.
            invalidSelectorLine(feat, name, firstLineNumber, "SelectorName: $name")
            return
        }
        val lines = section.numberedLines()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val lineNumber = firstLineNumber + line.number - 1
            val parsed = parseLineForLoad(line.text)
            if (parsed is ParsedLine.Blank) {
                continue
            }
            if (parsed !is ParsedLine.Field) {
                invalidSelectorLine(feat, name, lineNumber, line.text)
                continue
            }
            if (!readSelectorField(feat, selector, parsed, lineNumber, line.text)) {
                invalidSelectorLine(feat, name, lineNumber, line.text)
            }
        }
    }

    private fun invalidSelectorLine(feat: FeatDraft, selector: String, lineNumber: Int, text: String) {
        log.error(FEATS_FILE, lineNumber, "$FEATS_FILE: ${feat.name} SelectorName: $selector has invalid line:", text)
    }

    /** One line of a selector section; false if VB reports it at the end of the line. */
    private fun readSelectorField(
        feat: FeatDraft,
        selector: SelectorDraft,
        parsed: ParsedLine.Field,
        lineNumber: Int,
        text: String,
    ): Boolean {
        val value = parsed.value
        when (parsed.field) {
            "wikiname" -> selector.wiki = parsed.item
            "descrip" -> selector.description = parsed.item
            "stat" -> {
                selector.stat = Stat.find(parsed.item)
                if (selector.stat == null) {
                    return false
                }
                selector.statValue = value
            }
            "skill" -> {
                selector.skill = Skill.find(parsed.item)
                if (selector.skill == null) {
                    return false
                }
                selector.skillValue = value
            }
            "race" -> {
                selector.races = ArrayList() // VB: ReDim .Race
                val mode = readRaceRequirement(parsed.list, selector.races)
                if (mode == null) {
                    selector.raceMode = RaceRequirementMode.ANY
                    return false
                }
                selector.raceMode = mode.first
                return mode.second
            }
            "classbonus" -> {
                selector.classBonus = ArrayList() // VB: ReDim .ClassBonus
                return readClassNames(parsed.list, selector.classBonus)
            }
            "class" -> {
                var list = selector.classes
                if (list == null) {
                    list = ArrayList()
                    selector.classes = list
                }
                return readClassLevels(parsed.list, list)
            }
            "all", "one", "none" -> selector.requirements = selector.requirements.withGroup(parsed.field, featRequirements(parsed.list))
            "alignment" -> {
                var list = selector.alignments
                if (list == null) {
                    list = ArrayList()
                    selector.alignments = list
                }
                for (name in parsed.list) {
                    val alignment = Alignment.find(name)
                    if (alignment == null) {
                        invalidSelectorLine(feat, selector.name, lineNumber, text) // VB reports each one
                    } else {
                        list.add(alignment)
                    }
                }
            }
            "hide" -> selector.hide = true
            "notclass" -> {
                val c = findClassIn(classes, parsed.item)
                if (c == null) {
                    return false
                }
                selector.notClass = c.name
            }
            "classonly" -> {
                val c = findClassIn(classes, parsed.item)
                if (c == null) {
                    return false
                }
                selector.classOnly = c.name
            }
            "raceonly" -> {
                val race = findRaceIn(races, parsed.item)
                if (race == null) {
                    return false
                }
                selector.raceOnly = race.name
            }
            "notrace" -> {
                var list = selector.notRaces
                if (list == null) {
                    list = ArrayList()
                    selector.notRaces = list
                }
                for (name in parsed.list) {
                    val race = findRaceIn(races, name)
                    if (race == null) {
                        invalidSelectorLine(feat, selector.name, lineNumber, text) // VB reports each one
                    } else {
                        list.add(race.name)
                    }
                }
            }
            else -> return false
        }
        return true
    }

    /**
     * `Class: Fighter 4, Paladin 6`: adds each class and level to [into]. Returns false if any entry
     * is invalid. Each entry is read by `basDataLoad.ParseClassLevel` (Build/basDataLoad.bas:2182):
     * the class is everything before the last space, the level (1-20) everything after it.
     */
    private fun readClassLevels(entries: List<String>, into: MutableList<ClassLevel>): Boolean {
        var ok = true
        for (entry in entries) {
            val space = entry.lastIndexOf(' ')
            if (space < 0) {
                ok = false
                continue
            }
            val c = findClassIn(classes, entry.substring(0, space))
            if (c == null) {
                ok = false
                continue
            }
            val level = Vb.cLng(Vb.valOf(entry.substring(space + 1)))
            if (level < 1 || level > 20) {
                ok = false
                continue
            }
            into.add(ClassLevel(c.name, level))
        }
        return ok
    }

    /** A list of class names; adds the loaded class's name for each. False if any is unknown. */
    private fun readClassNames(names: List<String>, into: MutableList<String>): Boolean {
        var ok = true
        for (name in names) {
            val c = findClassIn(classes, name)
            if (c == null) {
                ok = false
            } else {
                into.add(c.name)
            }
        }
        return ok
    }

    /**
     * `Race: Required, Human, Elf`: the first entry is the mode, the rest are races, added to [into].
     * Returns null if the mode is unknown (VB then ignores the races), else the mode and whether
     * every race was known.
     */
    private fun readRaceRequirement(entries: List<String>, into: MutableList<String>): Pair<RaceRequirementMode, Boolean>? {
        if (entries.isEmpty()) {
            return null
        }
        val mode = RaceRequirementMode.find(entries[0])
        if (mode == null) {
            return null
        }
        var ok = true
        for (i in 1 until entries.size) {
            val race = findRaceIn(races, entries[i])
            if (race == null) {
                ok = false
            } else {
                into.add(race.name)
            }
        }
        return Pair(mode, ok)
    }
}

/** `All:`/`One:`/`None:` lists in Feats.txt name feats: VB stores each as Raw = "Feat: " & name, Style = peFeat. */
private fun featRequirements(names: List<String>): List<Requirement> {
    val result = ArrayList<Requirement>()
    for (name in names) {
        result.add(Requirement("Feat: $name", PointerStyle.FEAT))
    }
    return result
}

/**
 * Which feat slots the feat goes in, from its flags. Ported from `basDataLoad.SetFeatChannel`
 * (Build/basDataLoad.bas:1090). The first matching flag wins.
 */
private fun featChannel(feat: FeatDraft): FeatChannel {
    if (feat.deity) {
        return FeatChannel.DEITY
    }
    if (feat.domain) {
        return FeatChannel.CLERIC
    }
    if (feat.pact) {
        return FeatChannel.WARLOCK
    }
    if (feat.raceOnly) {
        return FeatChannel.RACIAL
    }
    if (feat.classOnly) {
        val only = feat.classOnlyClasses
        if (only.contains("Monk")) {
            return FeatChannel.MONK
        }
        if (only.contains("Ranger")) {
            return FeatChannel.FAVORED_ENEMY
        }
        if (only.contains("Rogue")) {
            return FeatChannel.ROGUE
        }
        if (only.contains("Druid")) {
            return FeatChannel.WILD_SHAPE
        }
        if (only.contains("Favored Soul")) {
            if (feat.classOnlyLevels.contains(5)) {
                return FeatChannel.ENERGY
            }
            return FeatChannel.FAVORED_SOUL
        }
    }
    return FeatChannel.GENERAL
}

/**
 * Links feats that share another feat's selectors (`SharedSelector:`), and gives each one the
 * parent's selectors if it has none of its own. Ported from `basProcess.ProcessFeatSelectors`
 * (Build/basProcess.bas:191).
 *
 * VB follows the chain of parents until it reaches a feat with selectors (a shared feat earlier in
 * the file already has its copies by then, so the chain may stop there). The copied selectors take
 * only the parent selectors' names; everything else comes from the sharing feat itself, as it is
 * after loading: class bonus, race, requirements, skill and stat. VB doesn't copy the "All" flag.
 *
 * New: a loop of SharedSelectors with no selectors is reported instead of hanging (VB would loop
 * for ever).
 */
internal fun processFeatSelectors(feats: List<Feat>, log: LoadLog): List<Feat> {
    val result = ArrayList<Feat>(feats)
    val indexByName = HashMap<String, Int>()
    for (i in result.indices) {
        if (!indexByName.containsKey(result[i].name)) {
            indexByName[result[i].name] = i
        }
    }
    for (i in result.indices) {
        val feat = result[i]
        if (feat.selectorStyle != SelectorStyle.SHARED && feat.selectorStyle != SelectorStyle.EXCLUSIVE) {
            continue
        }
        // Follow the chain: VB's Do … Loop While db.Feat(lngParent).Selectors = 0.
        var parent: Feat = feat
        var found: Feat? = null
        val visited = HashSet<String>()
        while (true) {
            val parentName = parent.parentSelector
            var index: Int? = null
            if (parentName != null) {
                index = indexByName[parentName]
            }
            if (index == null) {
                log.error(FEATS_FILE, null, "Feat: ${feat.name}", "Invalid parent selector: ${feat.parentSelector ?: ""}")
                break
            }
            parent = result[index]
            if (!visited.add(parent.name)) {
                log.vbDifference(FEATS_FILE, null, "${feat.name}: SharedSelector chain loops without reaching selectors (VB6 would hang)")
                break
            }
            if (parent.selectors.isNotEmpty()) {
                found = parent
                break
            }
        }
        if (found == null || feat.selectors.isNotEmpty()) {
            continue
        }
        val copies = ArrayList<Selector>()
        for (original in found.selectors) {
            copies.add(
                Selector(
                    name = original.name,
                    wiki = "",
                    description = "",
                    all = false,
                    classBonus = feat.classBonus ?: emptyList(),
                    stat = feat.stat,
                    statValue = feat.statValue,
                    skill = feat.skill,
                    skillValue = feat.skillValue,
                    classes = null,
                    notClass = null,
                    requirements = feat.requirements,
                    alignments = null,
                    race = feat.race,
                    hide = false,
                    notRaces = null,
                    raceOnly = null,
                    classOnly = null,
                ),
            )
        }
        result[i] = feat.copy(selectors = copies)
    }
    return result
}
