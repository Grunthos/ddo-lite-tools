package ddolite.tools.validate

import ddolite.builder.data.BuilderRecordFile
import ddolite.builder.data.LoadLog
import ddolite.builder.data.ParsedLine
import ddolite.builder.data.loadBuilderData
import ddolite.builder.data.parseLine
import ddolite.dataformat.DataSource
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.LineEndings
import ddolite.dataformat.SourceEncoding
import ddolite.dataformat.markersNotAtLineStart
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Data validator (plan step B3), run by CI on every change to Data/.
 *
 * Usage: `validate <Data folder>`. Exit code 1 if any errors were found.
 *
 * Current checks:
 * - every file decodes; line endings other than CRLF are warned about, because VB6
 *   splits on CRLF and would misread the file;
 * - Builder record files: record markers in the middle of a line (VB6 silently starts a new
 *   record there), and field lines without ':' (what basDataLoad.ParseLine reports via
 *   LogError, Build/basDataLoad.bas:2142);
 * - Races.txt and Classes.txt: everything the ported loaders check (invalid values, unknown
 *   names, cross-references), with VB6's wording.
 *
 * More files are checked as their loaders are ported (step B2). The goal is the same set of
 * problems as VB6's error.log.
 */
fun main(args: Array<String>) {
    var dir = Path.of("Data")
    if (args.isNotEmpty()) {
        dir = Path.of(args[0])
    }
    if (!Files.isDirectory(dir)) {
        System.err.println("Data folder not found: ${dir.toAbsolutePath()}")
        exitProcess(2)
    }

    val report = validate(DirectoryDataSource(dir))
    for (issue in report.issues) {
        println(issue)
    }
    println("${report.filesChecked} files checked: ${report.errorCount()} error(s), ${report.warningCount()} warning(s)")
    if (report.errorCount() > 0) {
        exitProcess(1)
    }
}

enum class Severity { ERROR, WARNING }

/** One problem found. [line] is null when the problem is with the file as a whole. */
data class Issue(val severity: Severity, val path: String, val line: Int?, val message: String) {
    override fun toString(): String {
        var location = path
        if (line != null) {
            location = "$path:$line"
        }
        return "${severity.name.padEnd(7)} $location  $message"
    }
}

class Report(val filesChecked: Int, val issues: List<Issue>) {
    fun errorCount(): Int = countOf(Severity.ERROR)

    fun warningCount(): Int = countOf(Severity.WARNING)

    private fun countOf(severity: Severity): Int {
        var count = 0
        for (issue in issues) {
            if (issue.severity == severity) {
                count++
            }
        }
        return count
    }
}

/** Runs all checks over [source]. Kept separate from [main] so it can be tested. */
fun validate(source: DataSource): Report {
    val issues = ArrayList<Issue>()

    // Every text file in Data/ and the three app folders.
    val files = ArrayList<String>()
    for (folder in listOf("", "Builder", "Cannith", "Compendium")) {
        for (path in source.list(folder)) {
            if (path.endsWith(".txt", ignoreCase = true)) {
                files.add(path)
            }
        }
    }
    for (path in files) {
        checkEncoding(source, path, issues)
    }
    for (file in BuilderRecordFile.entries) {
        checkBuilderRecordFile(source, file, issues)
    }
    checkWithLoaders(source, issues)
    return Report(files.size, issues)
}

/**
 * Runs the ported loaders (plan step B2; races, templates, spells, classes and feats so far) and reports what they found.
 * VB6's own errors are ERRORs; problems VB6 doesn't report, or handles differently, are WARNINGs.
 *
 * The loaders also report lines without ':', which [checkBuilderRecordFile] has already reported,
 * so those duplicates are skipped.
 */
private fun checkWithLoaders(source: DataSource, issues: MutableList<Issue>) {
    val log = LoadLog()
    loadBuilderData(source, log)
    for (found in log.issues) {
        val path = "Builder/" + found.file
        val line = found.line
        if (line != null && alreadyReported(issues, path, line)) {
            continue
        }
        var message = found.message
        if (found.detail != null) {
            message = message + " " + found.detail
        }
        var severity = Severity.ERROR
        if (found.vbDifference) {
            severity = Severity.WARNING
        }
        issues.add(Issue(severity, path, found.line, message))
    }
}

private fun alreadyReported(issues: List<Issue>, path: String, line: Int): Boolean {
    for (issue in issues) {
        if (issue.path == path && issue.line == line) {
            return true
        }
    }
    return false
}

/** Line endings VB6 would misread, and unexpected byte-order marks. */
private fun checkEncoding(source: DataSource, path: String, issues: MutableList<Issue>) {
    val decoded = source.readText(path)
    if (decoded == null) {
        return
    }
    if (decoded.lineEndings == LineEndings.LF || decoded.lineEndings == LineEndings.MIXED) {
        issues.add(
            Issue(
                Severity.WARNING, path, null,
                "line endings are ${decoded.lineEndings}; VB6 expects CRLF and will misread this file",
            ),
        )
    }
    // Spells.txt has always had a BOM, and VB6 copes with it there (see DataFolderTest).
    if (decoded.encoding == SourceEncoding.UTF8_BOM && path != BuilderRecordFile.SPELLS.path) {
        issues.add(Issue(Severity.WARNING, path, 1, "file has a UTF-8 BOM; VB6 reads it as part of the first line"))
    }
}

/** Markers in the middle of lines, and lines `ParseLine` would reject. */
private fun checkBuilderRecordFile(source: DataSource, file: BuilderRecordFile, issues: MutableList<Issue>) {
    val decoded = source.readText(file.path)
    if (decoded == null) {
        issues.add(Issue(Severity.ERROR, file.path, null, "file is missing"))
        return
    }
    val text = decoded.text

    for (line in markersNotAtLineStart(text, file.marker)) {
        issues.add(
            Issue(
                Severity.WARNING, file.path, line,
                "\"${file.marker.trim()}\" in the middle of a line starts a new record in VB6",
            ),
        )
    }

    if (!file.usesParseLine) {
        return // has its own parser; its checks arrive with its loader in B2
    }
    for (record in splitRecords(text, file.marker).records) {
        val lines = record.numberedLines()
        // Line 0 is the record's name, which VB never passes to ParseLine.
        for (i in 1 until lines.size) {
            val line = lines[i]
            val parsed: ParsedLine
            try {
                parsed = parseLine(line.text)
            } catch (e: ArithmeticException) {
                issues.add(
                    Issue(Severity.ERROR, file.path, line.number, "number too large (VB6 would stop with an Overflow error)"),
                )
                continue
            }
            if (parsed is ParsedLine.MissingColon) {
                issues.add(Issue(Severity.ERROR, file.path, line.number, "no ':' in \"${line.text.trim()}\""))
            }
        }
    }
}
