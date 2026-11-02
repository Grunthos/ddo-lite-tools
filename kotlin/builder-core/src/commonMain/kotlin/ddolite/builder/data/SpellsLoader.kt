package ddolite.builder.data

import ddolite.builder.model.Spell
import ddolite.dataformat.DataSource
import ddolite.dataformat.RawRecord
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

private const val SPELLS_FILE = "Spells.txt"

/**
 * Loads Spells.txt. Ported from `basDataLoad.LoadSpells` (Build/basDataLoad.bas:442) and
 * `SortSpells` (:511).
 *
 * Returns the spells sorted by name, as VB does so that `SeekSpell` (:528) can binary-search them.
 * VB compares strings with `<` under Option Compare Binary, which is an ordinal comparison of
 * character codes; Kotlin's `String.compareTo` is the same. VB's insertion sort is stable, and so
 * is `sortWith`, so spells with the same name stay in file order.
 *
 * New check: a spell name that appears twice is reported as a warning. VB keeps both, and its
 * binary search may find either.
 */
fun loadSpells(source: DataSource, log: LoadLog): List<Spell> {
    val decoded = source.readText(BuilderRecordFile.SPELLS.path)
    if (decoded == null) {
        log.fileNotFound(SPELLS_FILE)
        return emptyList()
    }
    val spells = ArrayList<Spell>()
    val firstLineOf = HashMap<String, Int>()
    for (record in splitRecords(decoded.text, BuilderRecordFile.SPELLS.marker).records) {
        val spell = loadSpell(record, log)
        val earlier = firstLineOf[spell.name]
        if (earlier != null) {
            log.vbDifference(
                SPELLS_FILE, record.line,
                "${spell.name} is listed more than once (also at line $earlier); VB6 keeps both and may use either",
            )
        } else {
            firstLineOf[spell.name] = record.line
        }
        spells.add(spell)
    }
    spells.sortWith(compareBy { it.name })
    return spells
}

/** Reads one spell record. Ported from `basDataLoad.LoadSpell` (Build/basDataLoad.bas:464). */
private fun loadSpell(record: RawRecord, log: LoadLog): Spell {
    val name = firstLine(record)
    var wiki = name
    var description = ""
    var rare = false

    val lines = record.numberedLines()
    for (i in 1 until lines.size) { // line 0 is the name
        val line = lines[i]
        val parsed = parseLineForLoad(line.text)
        if (parsed is ParsedLine.Blank) {
            continue
        }
        if (parsed !is ParsedLine.Field) {
            log.invalidLine(SPELLS_FILE, line.number, name, line.text)
            continue
        }
        when (parsed.field) {
            "descrip" -> description = parsed.item
            "wikiname" -> wiki = parsed.item
            "flags" -> {
                for (flag in parsed.list) {
                    if (flag.lowercase() == "rare") {
                        rare = true
                    } else {
                        log.loadError(SPELLS_FILE, line.number, "$name has invalid Flag: $flag")
                    }
                }
            }
            else -> log.invalidLine(SPELLS_FILE, line.number, name, line.text)
        }
    }
    return Spell(name, wiki, description, rare)
}
