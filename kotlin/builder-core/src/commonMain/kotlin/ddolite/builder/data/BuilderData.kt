package ddolite.builder.data

import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Race
import ddolite.builder.model.Spell
import ddolite.builder.model.StatTemplate
import ddolite.dataformat.DataSource

/**
 * The Character Builder's game data. Replaces the VB global `db` (`DatabaseType`,
 * Build/basDataDefs.bas) as it gets ported; so far races, classes, spells and stat templates.
 *
 * Races and classes are looked up by name (plan D-7), accepting the same alternative
 * spellings as VB's `GetRaceID`/`GetClassID`.
 */
class BuilderData(
    val races: List<Race>,
    val classes: List<CharacterClass>,
    /** Sorted by name, as VB keeps `db.Spell`. */
    val spells: List<Spell>,
    val templates: List<StatTemplate>,
) {
    private val spellsByName = HashMap<String, Spell>()

    init {
        for (spell in spells) {
            if (!spellsByName.containsKey(spell.name)) {
                spellsByName[spell.name] = spell
            }
        }
    }

    /**
     * The spell called exactly [name] (case matters), or null. Replaces `basDataLoad.SeekSpell`
     * (Build/basDataLoad.bas:528), a binary search with an exact comparison; a map gives the same answer.
     */
    fun findSpell(name: String): Spell? = spellsByName[name]

    /** The race called [name] (any case, or a VB alternative spelling), or null. Replaces `GetRaceID`. */
    fun findRace(name: String): Race? {
        val canonical = canonicalName(KNOWN_RACES, name)
        for (race in races) {
            if (race.name.equals(name, ignoreCase = true) || race.name.equals(canonical, ignoreCase = true)) {
                return race
            }
        }
        return null
    }

    /** The class called [name] (any case), or null. Replaces `GetClassID`. */
    fun findClass(name: String): CharacterClass? {
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
}

/**
 * Loads all the Builder data ported so far and checks references between files.
 * Corresponds to `basDataLoad.InitData` (Build/basDataLoad.bas:6), in its order, followed by the
 * checks from `basProcess.ProcessData` (Build/basProcess.bas:8) for the files loaded so far.
 */
fun loadBuilderData(source: DataSource, log: LoadLog): BuilderData {
    val races = loadRaces(source, log)
    val templates = loadTemplates(source, log)
    val spells = loadSpells(source, log)
    val classes = loadClasses(source, log)
    val data = BuilderData(races, classes, spells, templates)
    checkReferences(data, log)
    checkClassSpells(data, log)
    checkTemplates(templates, log)
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
