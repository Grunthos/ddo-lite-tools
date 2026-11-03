package ddolite.builder.data

import ddolite.builder.model.AutoGrantedFeat
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Feat
import ddolite.builder.model.NameChange
import ddolite.builder.model.FeatMapEntry
import ddolite.builder.model.Race
import ddolite.builder.model.Spell
import ddolite.builder.model.StatTemplate
import ddolite.builder.model.Tree
import ddolite.builder.model.Tomes
import ddolite.dataformat.DataSource

/**
 * The Character Builder's game data. Replaces the VB global `db` (`DatabaseType`,
 * Build/basDataDefs.bas) as it gets ported; so far races, classes, spells, stat templates, feats, the feat map, auto-granted feats, enhancement trees, destinies, tome limits and name changes: everything VB's `InitData` loads.
 *
 * Races and classes are looked up by name (plan D-7), accepting the same alternative
 * spellings as VB's `GetRaceID`/`GetClassID`.
 */
class BuilderData(
    val races: List<Race>,
    val classes: List<CharacterClass>,
    /** Sorted by name, as VB keeps `db.Spell`. */
    val spells: List<Spell>,
    templates: List<StatTemplate>,
    /** In file order, as VB keeps `db.Feat`. */
    val feats: List<Feat>,
    /** FeatMap.txt, in file order (blank entries for bad lines, as VB). */
    val featMap: List<FeatMapEntry>,
    val autoGrantedFeats: List<AutoGrantedFeat>,
    /** Enhancement trees, sorted by name (VB `db.Tree`; VB's TreeID is the position + 1). */
    val trees: List<Tree>,
    /** Epic destinies, sorted by name (VB `db.Destiny`). */
    val destinies: List<Tree>,
    val tomes: Tomes,
    /** Sorted by type, then old name (VB `db.NameChange`). */
    val nameChanges: List<NameChange>,
) {
    /** The stat templates; [reloadTemplates] replaces them. */
    var templates: List<StatTemplate> = templates
        private set

    /**
     * Stats › Customize › Reload Templates (frmStats.ReloadTemplates, :1497): LoadTemplates from
     * [text] (null: no file), and their checks.
     *
     * Difference from VB: VB runs all of ProcessData again, so its error log also repeats the
     * cross-file checks' errors; here only the templates' errors are logged.
     */
    fun reloadTemplates(text: String?, log: LoadLog) {
        val loaded = loadTemplatesText(text, log)
        for (template in loaded) {
            if (findClass(template.className) == null) {
                log.loadError("Templates.txt", null, "Class not recognized: ${template.className}")
            }
        }
        checkTemplates(loaded, log)
        templates = loaded
    }

    /** The feat map in VB's working order (`db.FeatMap` as last sorted by an import or export). */
    val featMapOrder = FeatMapOrder(featMap)

    private val spellsByName = HashMap<String, Spell>()
    private val featsByName = HashMap<String, Feat>()

    init {
        for (spell in spells) {
            if (!spellsByName.containsKey(spell.name)) {
                spellsByName[spell.name] = spell
            }
        }
        for (feat in feats) {
            if (!featsByName.containsKey(feat.name)) {
                featsByName[feat.name] = feat
            }
        }
    }

    /**
     * The spell called exactly [name] (case matters), or null. Replaces `basDataLoad.SeekSpell`
     * (Build/basDataLoad.bas:528), a binary search with an exact comparison; a map gives the same answer.
     */
    fun findSpell(name: String): Spell? = spellsByName[name]

    /**
     * The feat called exactly [name] (case matters), or null. Replaces `basDataLoad.SeekFeat`
     * (Build/basDataLoad.bas:1179) with its default exact match; its case-insensitive form is only
     * used for old builds and comes with that code.
     */
    fun findFeat(name: String): Feat? = featsByName[name]

    /** cfg.FeatOrder is Group Related (foeGroupRelated) rather than Alphabetical. */
    var featOrderGroupRelated = false
        private set

    /**
     * VB's `db.FeatDisplay`: the order the Feats screen lists feats in. Ported from
     * `IndexFeatDisplay` (Build/basDataLoad.bas:1142): sorted (case-sensitive) by abbreviation, or by
     * `SortName` for Group Related; equal ones in file order, as its insertion sort leaves them.
     */
    var featsInDisplayOrder: List<Feat> = displayOrder(false)
        private set

    /** IndexFeatDisplay for the Feat Order setting: at start-up (InitData) and when it changes in Options. */
    fun indexFeatDisplay(groupRelated: Boolean) {
        featOrderGroupRelated = groupRelated
        featsInDisplayOrder = displayOrder(groupRelated)
    }

    private fun displayOrder(groupRelated: Boolean): List<Feat> {
        val sorted = ArrayList(feats)
        if (groupRelated) {
            sorted.sortWith { a, b -> a.sortName.compareTo(b.sortName) }
        } else {
            sorted.sortWith { a, b -> a.abbreviation.compareTo(b.abbreviation) }
        }
        return sorted
    }

    /**
     * A feat as the Feats screen's list shows it (frmFeats.ShowAvailable, :1322): its abbreviation,
     * two spaces in when grouped under a related feat (Group Related, and a SortName that differs
     * from the name and ends in a digit).
     */
    fun featListCaption(feat: Feat): String {
        if (featOrderGroupRelated && feat.sortName != feat.name && feat.sortName.isNotEmpty() && feat.sortName.last().isDigit()) {
            return "  " + feat.abbreviation
        }
        return feat.abbreviation
    }

    /** VB's `db.FeatLookup`: the feats sorted by name (case-sensitive), equal names in file order. */
    private val featLookup: List<Feat> = sortedByName(feats)

    private fun sortedByName(list: List<Feat>): List<Feat> {
        val sorted = ArrayList(list)
        sorted.sortWith { a, b -> a.name.compareTo(b.name) } // stable, like IndexFeatLookup's insertion sort
        return sorted
    }

    /**
     * `SeekFeat(name, False)`, used when opening builds: a binary search over [featLookup] that
     * compares lower-cased names. Ported from `basDataLoad.SeekFeat` (Build/basDataLoad.bas:1179).
     *
     * VB quirk, kept: the list is sorted case-sensitively but searched case-insensitively, so a name
     * whose position differs between the two orders can be missed.
     */
    fun findFeatIgnoringCase(name: String): Feat? {
        if (name.isEmpty()) {
            return null
        }
        val lower = name.lowercase()
        var first = 0
        var last = featLookup.size - 1
        while (first <= last) {
            val mid = (first + last) / 2
            val candidate = featLookup[mid].name.lowercase()
            if (candidate < lower) {
                first = mid + 1
            } else if (candidate > lower) {
                last = mid - 1
            } else {
                return featLookup[mid]
            }
        }
        return null
    }

    /**
     * The enhancement tree called exactly [name], or null. Replaces `basDataLoad.SeekTree(name, peEnhancement)`
     * (Build/basDataLoad.bas:1966), a binary search over the sorted trees with an exact comparison.
     */
    fun findTree(name: String): Tree? = findExactName(trees, name)

    /** The destiny called exactly [name], or null. Replaces `SeekTree(name, peDestiny)`. */
    fun findDestiny(name: String): Tree? = findExactName(destinies, name)

    /**
     * The rename for [oldName] of [type] ("Feat"), or null. Replaces `basDataLoad.SeekNameChange`
     * (Build/basDataLoad.bas:2100), an exact binary search.
     */
    fun findNameChange(type: String, oldName: String): NameChange? {
        for (change in nameChanges) {
            if (change.type == type && change.oldName == oldName) {
                return change
            }
        }
        return null
    }

    private fun findExactName(list: List<Tree>, name: String): Tree? {
        for (tree in list) {
            if (tree.name == name) {
                return tree
            }
        }
        return null
    }

    /** The race called [name] (any case, or a VB alternative spelling), or null. Replaces `GetRaceID`. */
    fun findRace(name: String): Race? = findRaceIn(races, name)

    /** The class called [name] (any case), or null. Replaces `GetClassID`. */
    fun findClass(name: String): CharacterClass? = findClassIn(classes, name)
}

/**
 * The race in [races] called [name] (any case, or a VB alternative spelling), or null.
 * Replaces `GetRaceID` (basEnum.bas); used by loaders before the whole [BuilderData] exists.
 */
fun findRaceIn(races: List<Race>, name: String): Race? {
    val canonical = canonicalName(KNOWN_RACES, name)
    for (race in races) {
        if (race.name.equals(name, ignoreCase = true) || race.name.equals(canonical, ignoreCase = true)) {
            return race
        }
    }
    return null
}

/** The class in [classes] called [name] (any case, or a VB alternative spelling), or null. Replaces `GetClassID`. */
fun findClassIn(classes: List<CharacterClass>, name: String): CharacterClass? {
    val canonical = canonicalName(KNOWN_CLASSES, name)
    for (c in classes) {
        if (c.name.equals(name, ignoreCase = true) || c.name.equals(canonical, ignoreCase = true)) {
            return c
        }
    }
    return null
}

/** The name VB would use for [text] if it's one of VB's alternative spellings, else [text] itself. */
private fun canonicalName(table: List<KnownName>, text: String): String {
    val known = findKnownName(table, text)
    if (known == null) {
        return text
    }
    return known.name
}

/**
 * Loads all the Builder data ported so far and checks references between files.
 * Corresponds to `basDataLoad.InitData` (Build/basDataLoad.bas:6), in its order, followed by the
 * checks from `basProcess.ProcessData` (Build/basProcess.bas:8) for the files loaded so far.
 *
 * [userTemplates] is the text of the user's own Templates.txt, read in place of the shipped one.
 */
fun loadBuilderData(source: DataSource, log: LoadLog, userTemplates: String? = null): BuilderData {
    val races = loadRaces(source, log)
    // DataFile (basDataLoad.bas:28): the user's Templates.txt when the platform found one to use.
    val templates = if (userTemplates != null) loadTemplatesText(userTemplates, log) else loadTemplates(source, log)
    val spells = loadSpells(source, log)
    val classes = loadClasses(source, log)
    val feats = processFeatSelectors(loadFeats(source, races, classes, log), log)
    val featMap = loadFeatMap(source, log)
    val autoGrantedFeats = loadAutoGrantedFeats(source, feats, log)
    val trees = loadEnhancements(source, log)
    val destinies = loadDestinies(source, log)
    val tomes = loadTomes(source, log)
    val nameChanges = loadNameChanges(source, log)
    val loaded = BuilderData(
        races, classes, spells, templates, feats, featMap, autoGrantedFeats, trees, destinies, tomes, nameChanges,
    )
    checkReferences(loaded, log)

    // basProcess.ProcessData, in its order (ProcessFeatSelectors already ran with the feats).
    val crossReferences = CrossReferences(loaded, log)
    crossReferences.validateRacialTrees()
    crossReferences.validateClassTrees()
    val sharedTrees = crossReferences.processAbilitySelectors(trees, false)
    val sharedDestinies = crossReferences.processAbilitySelectors(destinies, true)
    val resolved = crossReferences.processPointers(feats, sharedTrees, sharedDestinies)
    val data = BuilderData(
        races, classes, spells, templates, resolved.feats, featMap, autoGrantedFeats,
        resolved.trees, resolved.destinies, tomes, nameChanges,
    )
    checkClassSpells(data, log)
    checkTemplates(templates, log)
    checkFeatMap(featMap, data, log)
    crossReferences.validateTreeLockouts()
    return data
}

/**
 * Checks names that point from one record to another. VB checks these while loading, against
 * its hard-coded name tables (`GetClassID`, `GetRaceID`); here they're checked against the
 * loaded data, so that new races and classes only need data changes.
 */
private fun checkReferences(data: BuilderData, log: LoadLog) {
    for (race in data.races) {
        val iconic = race.iconicClass
        if (iconic != null && data.findClass(iconic) == null) {
            // VB wording, from basDataLoad.LoadRace (:153).
            log.loadError("Races.txt", null, "${race.name} has invalid IconicClass: $iconic")
        }
        val subRace = race.subRace
        if (subRace != null && data.findRace(subRace) == null) {
            // VB silently ignores an unknown SubRace (GetRaceID returns reAny).
            log.vbDifference("Races.txt", null, "${race.name} has unknown SubRace: $subRace (VB6 ignores it)")
        }
    }
    for (template in data.templates) {
        if (data.findClass(template.className) == null) {
            // VB: GetClassID fails and LogError reports the record (basDataLoad.LoadTemplate :611).
            log.loadError("Templates.txt", null, "Class not recognized: ${template.className}")
        }
    }
    for (c in data.classes) {
        for (disallowed in c.disallowedClasses) {
            if (data.findClass(disallowed) == null) {
                // VB wording, from basDataLoad.LoadClass (:307).
                log.loadError("Classes.txt", null, "${c.name} has invalid DisallowedClass: $disallowed")
            }
        }
    }
}

/**
 * Checks that every spell a class lists exists in Spells.txt (exact name). Ported from
 * `basProcess.ProcessSpells` and `ProcessSpellList` (Build/basProcess.bas:24, :51), with the
 * `actProcessClassSpells` wording from basErrorLog.LogError.
 */
private fun checkClassSpells(data: BuilderData, log: LoadLog) {
    for (c in data.classes) {
        checkSpellList(data, log, c.name, "Mandatory Spell", c.mandatorySpells)
        checkSpellList(data, log, c.name, "Free Spell", c.freeSpells)
        for (pact in c.pacts) {
            checkSpellList(data, log, c.name, "Pact Spell", pact.spells)
        }
        for (level in 1..c.maxSpellLevel) {
            checkSpellList(data, log, c.name, "level $level spell", c.spellList(level))
        }
    }
}

private fun checkSpellList(data: BuilderData, log: LoadLog, className: String, kind: String, spells: List<String>) {
    for (spell in spells) {
        if (data.findSpell(spell) == null) {
            log.error("Classes.txt", null, "Classes.txt: $className has invalid $kind:", spell)
        }
    }
}
