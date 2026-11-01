package ddolite.dataformat

// Record splitting, shared by all data loaders.
//
// The VB6 loaders don't parse files line by line. They read the whole file with
// clsFile.LoadToString and cut it into records with `Split(strRaw, "<Marker>: ")`, e.g.
// basDataLoad.LoadFeats (Build/basDataLoad.bas:709) splits on "FeatName: ". Each record is
// then cleaned with CleanText and split on vbNewLine. This file reproduces those steps.

/**
 * One record: everything after a record marker (e.g. "FeatName: ") up to the next marker.
 * Equivalent to one element (after the first) of the VB `Split(strRaw, marker)` array.
 *
 * @property body the raw text, starting with the record's name (the rest of the marker line).
 * @property line the 1-based line number of the marker in the file. VB has no equivalent;
 *   it's here so the validator can say where a problem is.
 */
data class RawRecord(val body: String, val line: Int) {
    /**
     * The record's lines exactly as the VB loaders see them: `Split(CleanText(raw), vbNewLine)`
     * (e.g. the start of basDataLoad.LoadFeat, Build/basDataLoad.bas:747). Index 0 is the
     * record's name; VB loops from index 1 for the fields.
     */
    fun lines(): List<String> = Vb.split(cleanText(body), "\n")

    /**
     * The raw lines, each with its line number in the file, for error reporting.
     * Index 0 is the marker line. Unlike [lines], blank lines at either end are kept, so the
     * numbering stays exact.
     */
    fun numberedLines(): List<NumberedLine> {
        val parts = body.split("\n")
        val result = ArrayList<NumberedLine>(parts.size)
        for (i in parts.indices) {
            result.add(NumberedLine(line + i, parts[i]))
        }
        return result
    }
}

/** A line of a file, with its 1-based line number. */
data class NumberedLine(val number: Int, val text: String)

/**
 * A file split into records.
 *
 * @property preamble the text before the first marker (element 0 of the VB `Split` array),
 *   which the VB loaders ignore. Comments at the top of a file end up here.
 */
data class SplitText(val preamble: String, val records: List<RawRecord>)

/**
 * Splits [text] on every occurrence of [marker], exactly like the VB loaders'
 * `Split(strRaw, "FeatName: ")`: case-sensitive, and **anywhere** in the text, not only at
 * the start of a line. So a description that happens to contain "FeatName: " starts a new
 * record in VB, and here too; [markersNotAtLineStart] lets the validator report those.
 */
fun splitRecords(text: String, marker: String): SplitText {
    require(marker.isNotEmpty())

    // Find where every marker starts.
    val starts = ArrayList<Int>()
    var i = text.indexOf(marker)
    while (i >= 0) {
        starts.add(i)
        i = text.indexOf(marker, i + marker.length)
    }
    if (starts.isEmpty()) {
        return SplitText(text, emptyList())
    }

    // Each record runs from the end of its marker to the start of the next marker.
    val records = ArrayList<RawRecord>(starts.size)
    for (n in starts.indices) {
        val bodyStart = starts[n] + marker.length
        var bodyEnd = text.length
        if (n + 1 < starts.size) {
            bodyEnd = starts[n + 1]
        }
        records.add(RawRecord(text.substring(bodyStart, bodyEnd), lineNumberAt(text, starts[n])))
    }
    return SplitText(text.substring(0, starts[0]), records)
}

/**
 * Line numbers (1-based) of [marker] occurrences that don't start a line. New for the
 * validator (no VB equivalent): these split a record in the middle, which VB does silently.
 */
fun markersNotAtLineStart(text: String, marker: String): List<Int> {
    val result = ArrayList<Int>()
    var i = text.indexOf(marker)
    while (i >= 0) {
        if (i > 0 && text[i - 1] != '\n') {
            result.add(lineNumberAt(text, i))
        }
        i = text.indexOf(marker, i + marker.length)
    }
    return result
}

/** The 1-based line number of character [index] in [text]. */
private fun lineNumberAt(text: String, index: Int): Int {
    var line = 1
    for (k in 0 until index) {
        if (text[k] == '\n') {
            line++
        }
    }
    return line
}
