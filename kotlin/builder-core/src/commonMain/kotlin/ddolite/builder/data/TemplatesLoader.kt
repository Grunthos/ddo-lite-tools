package ddolite.builder.data

import ddolite.builder.model.Stat
import ddolite.builder.model.StatTemplate
import ddolite.dataformat.DataSource
import ddolite.dataformat.RawRecord
import ddolite.dataformat.Vb
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

private const val TEMPLATES_FILE = "Templates.txt"

/**
 * Loads Templates.txt. Ported from `basDataLoad.LoadTemplates` (Build/basDataLoad.bas:590).
 *
 * VB reads the user's own Templates.txt (in cfg.LitePath, the Save folder) instead, if it exists
 * and isn't older than the shipped one (`DataFile`, `UserTemplateOlder`); the platform decides that
 * and passes its text to [loadTemplatesText].
 */
fun loadTemplates(source: DataSource, log: LoadLog): List<StatTemplate> = loadTemplatesText(source.readText(BuilderRecordFile.TEMPLATES.path)?.text, log)

/**
 * Reads templates from a Templates.txt's text: the shipped one, or the user's own (Save\Templates.txt,
 * which VB's DataFile reads instead when it isn't older). Null is a missing file.
 */
fun loadTemplatesText(text: String?, log: LoadLog): List<StatTemplate> {
    if (text == null) {
        log.fileNotFound(TEMPLATES_FILE)
        return emptyList()
    }
    val templates = ArrayList<StatTemplate>()
    for (record in splitRecords(text, BuilderRecordFile.TEMPLATES.marker).records) {
        templates.add(loadTemplate(record, log))
    }
    return templates
}

/**
 * Reads one template record. Ported from `basDataLoad.LoadTemplate` (Build/basDataLoad.bas:611).
 *
 * The class name isn't checked here: VB checks it against its class table (`GetClassID`), and
 * `loadBuilderData` checks it against the loaded classes instead (plan D-7).
 */
private fun loadTemplate(record: RawRecord, log: LoadLog): StatTemplate {
    val lines = record.numberedLines()
    // VB uses strLine(0) untrimmed for the class; the trimmed name is used to look the class up.
    val className = firstLine(record)
    // The item name in error messages; VB changes log.LoadItem once the caption is known.
    var item = className
    var trapping = false
    var always = false
    var caption = ""
    var description = ""
    var warning = ""
    var levelups: Stat? = null
    val statPoints = ArrayList<MutableList<Int>>()
    for (column in 0 until StatTemplate.COLUMNS) {
        val row = ArrayList<Int>()
        for (stat in Stat.entries) {
            row.add(0)
        }
        statPoints.add(row)
    }

    for (i in 1 until lines.size) {
        val line = lines[i]
        val parsed = parseTemplateLine(line.text)
        if (parsed == null) {
            continue // blank line
        }
        if (parsed.field == null) {
            log.invalidLine(TEMPLATES_FILE, line.number, item, line.text) // no ':'
            continue
        }
        val value = parsed.item
        when (parsed.field) {
            "flags" -> {
                // VB: Split(LCase$(strItem), ",") then Trim$ each part.
                for (part in Vb.split(value.lowercase(), ",")) {
                    val flag = Vb.trim(part)
                    if (flag == "traps") {
                        trapping = true
                    } else if (flag == "always") {
                        always = true
                    } else {
                        log.invalidLine(TEMPLATES_FILE, line.number, item, line.text)
                    }
                }
            }
            "caption" -> {
                caption = value
                item = "$className: $value"
                if (trapping) {
                    item = "$item (Traps)"
                }
            }
            "descrip" -> description = value
            "levelups" -> {
                levelups = Stat.find(value)
                if (levelups == null) {
                    log.invalidLine(TEMPLATES_FILE, line.number, item, line.text)
                }
            }
            "warning" -> warning = value
            "str", "dex", "con", "int", "wis", "cha" -> {
                // The field is one of the six stat names, so this always finds it.
                val stat = Stat.find(parsed.field) ?: throw IllegalStateException("not a stat: " + parsed.field)
                // Fixed-width columns: VB's Val(Mid$(strItem, (i * 3) + 1, 2)) for i = 0 to 4.
                for (column in 0 until StatTemplate.COLUMNS) {
                    val text = vbMid(value, column * 3 + 1, 2)
                    statPoints[column][stat.ordinal] = Vb.cLng(Vb.valOf(text))
                }
            }
            "pts" -> {} // the column headings; VB ignores them
            else -> log.invalidLine(TEMPLATES_FILE, line.number, item, line.text)
        }
    }
    return StatTemplate(className, trapping, always, caption, description, warning, levelups, statPoints)
}

/** A line split by [parseTemplateLine]: [field] is null when the line has no ':'. */
private class TemplateLine(val field: String?, val item: String)

/**
 * Splits a template line into field and item. Ported from `basDataLoad.ParseTemplate`
 * (Build/basDataLoad.bas:672): the line is trimmed of spaces, the field is the lower-cased text
 * before the first ':', and the item is everything from two characters after it
 * (VB: `Mid$(pstrLine, lngPos + 2)`, skipping the ": "). Unlike `ParseLine`, the item isn't trimmed,
 * which keeps the fixed-width stat columns lined up. Returns null for a blank line.
 */
private fun parseTemplateLine(text: String): TemplateLine? {
    val line = Vb.trim(text)
    if (line.isEmpty()) {
        return null
    }
    val colon = line.indexOf(':')
    if (colon < 0) {
        return TemplateLine(null, "")
    }
    val field = Vb.trim(line.substring(0, colon)).lowercase()
    return TemplateLine(field, vbMid(line, colon + 3, Int.MAX_VALUE))
}

/** VB's `Mid$(text, start, length)` with a 1-based [start]; past the end gives "". */
private fun vbMid(text: String, start: Int, length: Int): String {
    val from = start - 1
    if (from >= text.length) {
        return ""
    }
    var to = text.length
    if (length < text.length - from) {
        to = from + length
    }
    return text.substring(from, to)
}

/**
 * Checks each template's stat points: every value must be one a stat can actually have
 * (0–6, 8, 10, 13 or 16 points spent), and each column must add up to its build points.
 * Ported from `basProcess.ProcessTemplates` (Build/basProcess.bas:99), with its messages
 * (basErrorLog.LogError, `actTemplateStats` and `actTemplatePoints`).
 */
internal fun checkTemplates(templates: List<StatTemplate>, log: LoadLog) {
    for (template in templates) {
        for (column in 0 until StatTemplate.COLUMNS) {
            val buildPoints = StatTemplate.buildPoints(column)
            var total = 0
            for (stat in Stat.entries) {
                val points = template.points(column, stat)
                val valid = (points in 0..6) || points == 8 || points == 10 || points == 13 || points == 16
                if (!valid) {
                    log.error(
                        TEMPLATES_FILE, null,
                        "$TEMPLATES_FILE: ${template.logName} has invalid stat point value",
                        "${buildPoints}pt ${stat.displayName}: $points",
                    )
                }
                total += points
            }
            if (total != buildPoints) {
                log.error(
                    TEMPLATES_FILE, null,
                    "$TEMPLATES_FILE: ${template.logName} has invalid points total",
                    "${buildPoints}pt build totals $total",
                )
            }
        }
    }
}
