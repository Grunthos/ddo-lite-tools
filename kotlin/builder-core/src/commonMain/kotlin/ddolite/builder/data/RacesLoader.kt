package ddolite.builder.data

import ddolite.builder.model.GrantedFeat
import ddolite.builder.model.Race
import ddolite.builder.model.RaceType
import ddolite.dataformat.DataSource
import ddolite.dataformat.RawRecord
import ddolite.dataformat.Vb
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

/** Game limits from Source/Modules/basCONST.bas. */
object BuilderLimits {
    /** `MAX_LEVEL` (basCONST.bas:12): the level cap, used to check `GrantedFeats<level>` lines. */
    const val MAX_LEVEL = 36
}

private const val RACES_FILE = "Races.txt"

/**
 * Loads Races.txt. Ported from `basDataLoad.LoadRaces` (Build/basDataLoad.bas:72).
 *
 * Returns the races in VB enum order (see [Race.vbIndex]), then any races VB6 doesn't know,
 * in file order. If a race appears twice, the later one replaces the earlier, as in VB
 * (`db.Race(enRace) = typNew`).
 */
fun loadRaces(source: DataSource, log: LoadLog): List<Race> {
    val decoded = source.readText(BuilderRecordFile.RACES.path)
    if (decoded == null) {
        log.fileNotFound(RACES_FILE)
        return emptyList()
    }

    val byName = LinkedHashMap<String, Race>()
    for (record in splitRecords(decoded.text, BuilderRecordFile.RACES.marker).records) {
        // VB only loads records that contain a "Stats: " line.
        if (!record.body.contains("Stats: ")) {
            log.invalidSection(RACES_FILE, record.line, "Stats: ", firstLine(record))
            continue
        }
        val race = loadRace(record, log)
        byName[race.name.lowercase()] = race
    }
    return sortByVbIndex(byName.values)
}

/**
 * Reads one race record. Ported from `basDataLoad.LoadRace` (Build/basDataLoad.bas:91).
 *
 * Difference from VB: a race name VB6 doesn't recognise is still loaded (plan D-7), with a
 * warning, where VB reports "Race not recognized" and skips it.
 */
private fun loadRace(record: RawRecord, log: LoadLog): Race {
    val name = firstLine(record)
    val known = findKnownName(KNOWN_RACES, name)
    var vbIndex = 0
    if (known != null) {
        vbIndex = known.vbIndex
    } else {
        log.vbDifference(
            RACES_FILE, record.line,
            "Race not recognized by VB6: $name (VB6 needs RaceEnum, GetRaceID and GetRaceName updated in basEnum.bas)",
        )
    }

    var abbreviation = name
    var type: RaceType? = null
    var iconicClass: String? = null
    var subRace: String? = null
    var listFirst = false
    var bonusFeat = false
    val stats = ArrayList<Int>()
    for (i in 0 until 6) {
        stats.add(0)
    }
    var skillPoints = 0
    val grantedFeats = ArrayList<GrantedFeat>()
    var trees: List<String> = emptyList()

    val lines = record.numberedLines()
    for (i in 1 until lines.size) { // line 0 is the name
        val line = lines[i]
        val parsed = parseLineForLoad(line.text)
        if (parsed is ParsedLine.Blank) {
            continue
        }
        if (parsed !is ParsedLine.Field) {
            log.invalidLine(RACES_FILE, line.number, name, line.text)
            continue
        }

        when (parsed.field) {
            "stats" -> {
                // Exactly six values, each 6, 8, 9 or 10. VB keeps the values even when invalid.
                var valid = parsed.list.size == 6
                if (valid) {
                    for (s in 0 until 6) {
                        val value = Vb.cLng(Vb.valOf(parsed.list[s]))
                        stats[s] = value
                        if (value != 6 && value != 8 && value != 9 && value != 10) {
                            valid = false
                        }
                    }
                }
                if (!valid) {
                    log.invalidLine(RACES_FILE, line.number, name, line.text)
                }
            }
            "abbreviation" -> abbreviation = parsed.item
            "type" -> {
                type = RaceType.find(parsed.item)
                if (type == null) {
                    log.loadError(RACES_FILE, line.number, "$name has invalid Type: ${parsed.item}")
                }
            }
            // Checked against the loaded classes and races later, in loadBuilderData.
            "iconicclass" -> iconicClass = parsed.item
            "subrace" -> subRace = parsed.item
            "flags" -> {
                for (flag in parsed.list) {
                    when (flag.lowercase()) {
                        "bonusfeat" -> bonusFeat = true
                        "bonusskill" -> skillPoints = 1
                        "listfirst" -> listFirst = true
                        else -> log.loadError(RACES_FILE, line.number, "$name has invalid Flag: $flag")
                    }
                }
            }
            "trees" -> trees = parsed.list
            else -> {
                if (parsed.field.startsWith("grantedfeats")) {
                    readGrantedFeats(parsed, grantedFeats, RACES_FILE, line.number, name, line.text, log)
                } else {
                    log.invalidLine(RACES_FILE, line.number, name, line.text)
                }
            }
        }
    }

    return Race(
        name = name,
        vbIndex = vbIndex,
        abbreviation = abbreviation,
        type = type,
        iconicClass = iconicClass,
        subRace = subRace,
        listFirst = listFirst,
        bonusFeat = bonusFeat,
        stats = stats,
        skillPoints = skillPoints,
        grantedFeats = grantedFeats,
        trees = trees,
    )
}

/**
 * Reads a `GrantedFeats<level>: feat, feat, …` line. Ported from `basDataLoad.LoadGrantedFeats`
 * (Build/basDataLoad.bas:188), shared by races and classes.
 */
internal fun readGrantedFeats(
    parsed: ParsedLine.Field,
    into: MutableList<GrantedFeat>,
    file: String,
    lineNumber: Int,
    item: String,
    lineText: String,
    log: LoadLog,
) {
    // The level follows "grantedfeats" in the field name: VB's Val(Mid$(field, 13)).
    val level = Vb.cLng(Vb.valOf(parsed.field.substring("grantedfeats".length)))
    if (level < 1 || level > BuilderLimits.MAX_LEVEL) {
        log.invalidLine(file, lineNumber, item, lineText)
        return
    }
    for (featName in parsed.list) {
        into.add(GrantedFeat(level, featName))
    }
}

/**
 * [parseLine] for the loaders: a number too big for VB's Long is returned as
 * [ParsedLine.MissingColon], so it's reported as an invalid line and loading carries on.
 * VB would stop with an Overflow error.
 */
internal fun parseLineForLoad(text: String): ParsedLine {
    try {
        return parseLine(text)
    } catch (e: ArithmeticException) {
        return ParsedLine.MissingColon(text)
    }
}

/** The record's name: the first line after `CleanText`, trimmed, as `Trim$(strLine(0))` in VB. */
internal fun firstLine(record: RawRecord): String {
    val lines = record.lines()
    if (lines.isEmpty()) {
        return ""
    }
    return Vb.trim(lines[0])
}

/** VB stores races in an array indexed by `RaceEnum`; this gives the same order. Unknown ones (index 0) go last. */
private fun sortByVbIndex(races: Collection<Race>): List<Race> {
    val known = ArrayList<Race>()
    val unknown = ArrayList<Race>()
    for (race in races) {
        if (race.vbIndex > 0) {
            known.add(race)
        } else {
            unknown.add(race)
        }
    }
    known.sortBy { it.vbIndex }
    known.addAll(unknown)
    return known
}
