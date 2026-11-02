package ddolite.builder.data

import ddolite.builder.model.NameChange
import ddolite.builder.model.TomeSchedule
import ddolite.builder.model.Tomes
import ddolite.dataformat.DataSource
import ddolite.dataformat.Vb
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

/** Data/Tomes.txt: shared by the apps, so not in the Builder folder. */
const val TOMES_PATH = "Tomes.txt"

private const val NAME_CHANGE_FILE = "NameChange.txt"

/** The single-number settings in Tomes.txt, lower-cased, as `LoadTomeData` matches them. */
private val TOME_MAX_NAMES = setOf(
    "racialapmax", "powermax", "prr/mrrmax", "universalapmax", "permdestinypointmax",
    "permdestinypointmin", "fatetomemax", "destinytomemax",
)

/**
 * Loads Data/Tomes.txt over VB's built-in defaults. Ported from `basTomes.LoadTomeData`,
 * `SetDefaults`, `ParseTomeSchedule` and `SetTomeMax` (Source/Modules/basTomes.bas:25 onwards).
 *
 * VB reads each line with a ':' as "name: value"; unknown names are ignored silently, and so is a
 * missing file (the defaults stay). Quirks kept: a maximum of 0 (or text) leaves the default;
 * `Val` ignores spaces inside the number, so "FateTomeMax: 3 3" would mean 33 – reported as a warning.
 */
fun loadTomes(source: DataSource, log: LoadLog): Tomes {
    var stat = parseSchedule("8, 1, 1, 3, 7, 11, 15, 19, 22")!!
    var skill = parseSchedule("5, 1, 1, 3, 7, 11")!!
    var racialApMax = 2
    var universalApMax = 1
    var powerMax = 4
    var rrMax = 4
    var permDestinyPointMin = 0
    var permDestinyPointMax = 18
    var fateTomeMax = 3
    var destinyTomeMax = 2

    val decoded = source.readText(TOMES_PATH)
    if (decoded != null) {
        val lines = Vb.split(decoded.text, "\n")
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.contains(':')) {
                continue
            }
            val tokens = Vb.split(line, ":")
            val name = tokens[0].lowercase()
            val value = tokens[1]
            if (name == "stat" || name == "skill") {
                val schedule = parseSchedule(value)
                if (schedule != null) {
                    if (name == "stat") {
                        stat = schedule
                    } else {
                        skill = schedule
                    }
                }
                continue
            }
            if (!TOME_MAX_NAMES.contains(name)) {
                continue // VB ignores names it doesn't know
            }
            val number = tomeMax(value, i + 1, line, log)
            when (name) {
                "racialapmax" -> if (number != 0) racialApMax = number
                "powermax" -> if (number != 0) powerMax = number
                "prr/mrrmax" -> if (number != 0) rrMax = number
                "universalapmax" -> if (number != 0) universalApMax = number
                "permdestinypointmax" -> if (number != 0) permDestinyPointMax = number
                "permdestinypointmin" -> if (number != 0) permDestinyPointMin = number
                "fatetomemax" -> if (number != 0) fateTomeMax = number
                "destinytomemax" -> if (number != 0) destinyTomeMax = number
            }
        }
    }
    return Tomes(
        stat, skill, racialApMax, universalApMax, powerMax, rrMax,
        permDestinyPointMin, permDestinyPointMax, fateTomeMax, destinyTomeMax,
    )
}

/**
 * `SetTomeMax`'s `Val(value)`, with a warning when the text isn't a plain number but VB still
 * reads one from it (spaces inside, or text after the digits).
 */
private fun tomeMax(text: String, lineNumber: Int, line: String, log: LoadLog): Int {
    val value = Vb.cLng(Vb.valOf(text))
    val trimmed = Vb.trim(text)
    if (value != 0 && trimmed != value.toString()) {
        log.vbDifference("Tomes.txt", lineNumber, "\"$line\" is read as $value (VB's Val ignores spaces)")
    }
    return value
}

/**
 * "max, level, level, …": the first number is how many tomes, then the level for each.
 * Ported from `basTomes.ParseTomeSchedule`: no comma means the line is ignored (null); levels
 * past the maximum are ignored; missing ones are 0.
 */
private fun parseSchedule(text: String): TomeSchedule? {
    if (!text.contains(',')) {
        return null
    }
    val tokens = Vb.split(text, ",")
    val max = Vb.cLng(Vb.valOf(tokens[0]))
    val levels = ArrayList<Int>()
    for (i in 1..max) {
        if (i < tokens.size) {
            levels.add(Vb.cLng(Vb.valOf(tokens[i])))
        } else {
            levels.add(0)
        }
    }
    return TomeSchedule(max, levels)
}

/**
 * Loads NameChange.txt. Ported from `basDataLoad.LoadNameChanges`, `LoadNameChange` and
 * `SortNameChanges` (Build/basDataLoad.bas:2006 onwards).
 *
 * Only "Feat" changes are understood. A change is kept only if it has both Old and New. The list is
 * sorted by type, then old name (ordinal), as VB's `SeekNameChange` binary search needs.
 */
fun loadNameChanges(source: DataSource, log: LoadLog): List<NameChange> {
    val decoded = source.readText(BuilderRecordFile.NAME_CHANGES.path)
    if (decoded == null) {
        log.fileNotFound(NAME_CHANGE_FILE)
        return emptyList()
    }
    val changes = ArrayList<NameChange>()
    for (record in splitRecords(decoded.text, BuilderRecordFile.NAME_CHANGES.marker).records) {
        val lines = record.numberedLines()
        if (lines.isEmpty()) {
            continue
        }
        val type = lines[0].text // VB: strLine(0), after CleanText but not trimmed itself
        var oldName = ""
        var newName = ""
        var assignSelector = ""
        if (type == "Feat") {
            for (i in 1 until lines.size) {
                val line = lines[i]
                val parsed = parseLineForLoad(line.text)
                if (parsed is ParsedLine.Blank) {
                    continue
                }
                if (parsed !is ParsedLine.Field) {
                    log.invalidLine(NAME_CHANGE_FILE, line.number, type, line.text)
                    continue
                }
                when (parsed.field) {
                    "old" -> oldName = parsed.item
                    "new" -> newName = parsed.item
                    "assignselector" -> assignSelector = parsed.item
                    else -> log.invalidLine(NAME_CHANGE_FILE, line.number, type, line.text)
                }
            }
        } else {
            log.loadError(NAME_CHANGE_FILE, record.line, "Invalid Type: $type")
        }
        if (oldName.isNotEmpty() && newName.isNotEmpty()) {
            changes.add(NameChange(type, oldName, newName, assignSelector))
        }
    }
    changes.sortWith(compareBy<NameChange>({ it.type }, { it.oldName }))
    return changes
}
