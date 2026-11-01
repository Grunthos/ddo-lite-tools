package ddolite.builder.data

import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Race
import ddolite.dataformat.DataSource

/**
 * The Character Builder's game data. Replaces the VB global `db` (`DatabaseType`,
 * Build/basDataDefs.bas) as it gets ported; so far races and classes.
 *
 * Races and classes are looked up by name (plan D-7), accepting the same alternative
 * spellings as VB's `GetRaceID`/`GetClassID`.
 */
class BuilderData(val races: List<Race>, val classes: List<CharacterClass>) {
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
 * Corresponds to the start of `basDataLoad.InitData` (Build/basDataLoad.bas:6).
 */
fun loadBuilderData(source: DataSource, log: LoadLog): BuilderData {
    val races = loadRaces(source, log)
    val classes = loadClasses(source, log)
    val data = BuilderData(races, classes)
    checkReferences(data, log)
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
    for (c in data.classes) {
        for (disallowed in c.disallowedClasses) {
            if (data.findClass(disallowed) == null) {
                // VB wording, from basDataLoad.LoadClass (:307).
                log.loadError("Classes.txt", null, "${c.name} has invalid DisallowedClass: $disallowed")
            }
        }
    }
}
