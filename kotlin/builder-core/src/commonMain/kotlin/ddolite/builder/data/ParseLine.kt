package ddolite.builder.data

import ddolite.dataformat.Vb
import ddolite.dataformat.trimAll

/**
 * One line of a Character Builder data file: the result of the VB6 `ParseLine`
 * (Source/Modules/Build/basDataLoad.bas:2126). VB returns this through five ByRef
 * parameters (field, item, value, list, list size) plus a Boolean; here it's one value.
 *
 * Each VB6 app has its own `ParseLine` with different rules, so this one is for the
 * Builder only. The others will be ported separately:
 * - Cannith/basData.bas:1921 – ';' comments, ';' list separator, "notes" kept untrimmed
 * - Cannith/basGearset.bas:1145 – different value handling, "named"/"notes" not split
 * - Compendium/basDataLoad.bas:1444 and Compendium/basImport.bas:262 – tab-separated lists
 */
sealed interface ParsedLine {
    /** Empty after trimming; VB returns False without logging. */
    data object Blank : ParsedLine

    /** No ':' on the line; VB calls `LogError`. */
    data class MissingColon(val text: String) : ParsedLine

    /**
     * @property field the key, lower-cased (e.g. "grantedfeats1").
     * @property item the text value. Empty when the value is a comma list.
     * @property value a trailing number: "Monk 12" gives item "Monk", value 12; a lone
     *   number gives item and value. Rounded half-to-even, as VB assigns a Double to a Long.
     * @property list comma-separated parts (trimmed of spaces), or the whole value as one
     *   element. Empty for `Descrip` and `Wiki*` fields.
     */
    data class Field(
        val field: String,
        val item: String,
        val value: Int,
        val list: List<String>,
    ) : ParsedLine
}

/**
 * Ported from `basDataLoad.ParseLine` (Build/basDataLoad.bas:2126), statement for statement.
 *
 * Used for every field line of every Builder data file. Quirks kept on purpose:
 * - Tabs anywhere on the line are deleted (via [trimAll]).
 * - A value containing a comma is always a list, so "Descrip"/"Wiki*" are checked first.
 * - "BAB: 0.75" gives value 1 (rounded), item "0.75"; callers that need the decimal
 *   read [ParsedLine.Field.item].
 * - "1d6" counts as a number (VB `IsNumeric` accepts a D exponent), giving 1000000.
 *
 * @throws ArithmeticException if a number overflows 32 bits; VB would stop with run-time error 6.
 */
fun parseLine(line: String): ParsedLine {
    var text = trimAll(line)
    if (text.isEmpty()) return ParsedLine.Blank

    val colon = text.indexOf(':')
    if (colon < 0) return ParsedLine.MissingColon(line)

    val field = trimAll(text.substring(0, colon)).lowercase()
    text = trimAll(text.substring(colon + 1))

    // Descriptions are free text: commas and trailing numbers are not special.
    if (field.startsWith("wiki") || field == "descrip") {
        return ParsedLine.Field(field, text, 0, emptyList())
    }

    if (text.contains(',')) {
        val list = ArrayList<String>()
        for (part in text.split(",")) {
            list.add(Vb.trim(part))
        }
        return ParsedLine.Field(field, "", 0, list)
    }

    var item = text
    var value = 0
    if (text.contains(' ')) {
        // A number after the last space is the value: "Monk 12" → item "Monk", value 12.
        val space = text.lastIndexOf(' ')
        val last = text.substring(space + 1)
        if (Vb.isNumeric(last)) {
            item = text.substring(0, space)
            value = Vb.cLng(Vb.valOf(last))
        }
    } else if (Vb.isNumeric(item)) {
        // A lone number is returned as both item and value.
        value = Vb.cLng(Vb.valOf(item))
    }
    return ParsedLine.Field(field, item, value, listOf(text))
}
