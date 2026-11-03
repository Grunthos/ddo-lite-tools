package ddolite.builder.data

/**
 * A problem found while loading data.
 *
 * @property file the file name as VB shows it ("Races.txt").
 * @property line 1-based line number in the file, when known (VB doesn't record these).
 * @property message the first line of the VB error.log entry.
 * @property detail the second line, if VB writes one (usually the offending data line).
 * @property vbDifference true for problems VB6 doesn't report, or handles differently from
 *   the Kotlin loader (e.g. a race VB6 doesn't know). Kept apart so the error.log comparison
 *   in plan step B3 can ignore them.
 */
data class LoadIssue(
    val file: String,
    val line: Int?,
    val message: String,
    val detail: String? = null,
    val vbDifference: Boolean = false,
)

/**
 * Collects [LoadIssue]s while loading. Replaces the VB `log` structure and its writers
 * (Source/Modules/Build/basErrorLog.bas): `LogError` (:89), `LoadError` (:80) and
 * `ErrorLoading` (:221). Messages use the same wording as those, so they can later be compared
 * with a VB6 error.log.
 */
class LoadLog {
    val issues = ArrayList<LoadIssue>()

    /** `LogError` when a data file is missing (`actOpenFile`): "File not found: <file>". */
    fun fileNotFound(file: String) {
        issues.add(LoadIssue(file, null, "File not found: $file"))
    }

    /**
     * `LogError` while reading a field line (`actReadFile`):
     * "<file>: <item> has invalid line:" followed by the line.
     */
    fun invalidLine(file: String, line: Int?, item: String, text: String) {
        issues.add(LoadIssue(file, line, "$file: $item has invalid line:", text))
    }

    /** `LoadError(message)`: VB writes "<file>: <message>". */
    fun loadError(file: String, line: Int?, message: String) {
        issues.add(LoadIssue(file, line, "$file: $message"))
    }

    /**
     * `ErrorLoading`: a record missing its required field, e.g.
     * "Invalid section in Races.txt ('Stats: ' not found):".
     */
    fun invalidSection(file: String, line: Int?, requiredField: String, recordName: String) {
        issues.add(LoadIssue(file, line, "Invalid section in $file ('$requiredField' not found):", recordName))
    }

    /**
     * Any other `LogError` message, already worded as VB words it for the current activity
     * (basErrorLog.LogError, Build/basErrorLog.bas:89), e.g. the template and spell-list checks.
     */
    fun error(file: String, line: Int?, message: String, detail: String? = null) {
        issues.add(LoadIssue(file, line, message, detail))
    }

    /** A problem VB6 doesn't report, or a place where the Kotlin loader behaves differently. */
    fun vbDifference(file: String, line: Int?, message: String) {
        issues.add(LoadIssue(file, line, "$file: $message", null, vbDifference = true))
    }

    /** The problems VB reports (not the ones only this version finds). */
    fun vbErrors(): List<LoadIssue> {
        val result = ArrayList<LoadIssue>()
        for (issue in issues) {
            if (!issue.vbDifference) {
                result.add(issue)
            }
        }
        return result
    }

    /**
     * The error log's text: "N Errors:", a blank line, then each error's lines, CRLF line ends.
     * Ported from `CreateErrorLog` / `ErrorLogText` (basErrorLog.bas:260, :274). Problems VB doesn't
     * report follow under their own heading. Empty when VB would write no log.
     */
    fun errorLogText(): String {
        val errors = vbErrors()
        if (errors.isEmpty()) {
            return ""
        }
        val lines = ArrayList<String>()
        lines.add(errors.size.toString() + " Errors:")
        lines.add("")
        for (issue in errors) {
            lines.add(issue.message)
            val detail = issue.detail
            if (detail != null) {
                lines.add(detail)
            }
        }
        var first = true
        for (issue in issues) {
            if (issue.vbDifference) {
                if (first) {
                    lines.add("")
                    lines.add("Also found by the Kotlin version (VB doesn't report these):")
                    first = false
                }
                lines.add(issue.file + ": " + issue.message)
            }
        }
        return lines.joinToString("\r\n")
    }
}
