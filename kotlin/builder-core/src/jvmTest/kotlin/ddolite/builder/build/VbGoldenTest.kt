package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.output.BuildOutput
import ddolite.builder.output.GuideExport
import ddolite.builder.output.OutputColor
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.OutputSettings
import ddolite.builder.output.PlannerExport
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.Vb
import ddolite.dataformat.Windows1252
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compares Kotlin with what VB wrote for the golden builds (test-builds/golden/vb-<version>, see
 * CAPTURE.md). For each "Golden <name>.build" there, <name>.build (from test-builds/golden, or Save/
 * and test-builds via [SampleBuilds]) is opened, and Kotlin's save and exports must give VB's files
 * byte for byte, as `xp.File.SaveStringAs` writes them:
 * - "Golden <name>.build": File › Save As;
 * - "Golden <key> forum-<format>.txt": File › Export › to Forums, All Sections, with
 *   Settings/<format>.bbcodes. Colours come from VB's theme, so each colour tag is compared only
 *   for using the same theme colour wherever Kotlin uses the same [OutputColor];
 * - "Golden <key> ddobuilder.ddocp": File › Export › to DDO Builder;
 * - "Golden <key> guide.csv" and "guide.txt": File › Export › Leveling Guide.
 * <key> is the name's first word: "A" for "A Three Class".
 */
class VbGoldenTest {
    private val dataDir = Path.of(System.getProperty("ddolite.dataDir"))
    private val data = loadBuilderData(DirectoryDataSource(dataDir), LoadLog())
    private val golden = File(System.getProperty("ddolite.testBuildsDir"), "golden")
    private val formats = dataDir.resolveSibling("Settings").toFile()
    private val colorTag = Regex("""\[color=#([^\]]*)\]""")

    @Test
    fun saveAndExportsMatchVb() {
        val failures = ArrayList<String>()
        var builds = 0
        var exports = 0
        for (snapshot in snapshots()) {
            val files = snapshot.listFiles() ?: continue
            files.sortBy { it.name }
            for (file in files) {
                if (!file.name.startsWith("Golden ") || !file.name.endsWith(".build")) {
                    continue
                }
                val name = file.name.substring("Golden ".length)
                val input = if (File(golden, name).isFile) File(golden, name) else SampleBuilds.file(name)
                val rules = readBuildFile(input.readBytes(), data, BuildPoints.ADVENTURER).rules
                compare(file, writeBuildText(rules), failures)
                builds++

                GuideRules(rules).initGuideEnhancements()
                val prefix = "Golden " + name.substringBefore(' ') + " "
                for (export in files) {
                    if (!export.name.startsWith(prefix) || export.name.endsWith(".build") || export.name.endsWith(".png")) {
                        continue
                    }
                    val kind = export.name.substring(prefix.length)
                    if (kind.startsWith("forum-") && kind.endsWith(".txt")) {
                        compareForum(export, rules, kind.removePrefix("forum-").removeSuffix(".txt"), failures)
                    } else if (kind == "ddobuilder.ddocp") {
                        compare(export, Vb.saveStringAs(PlannerExport(rules).builderFile()), failures)
                    } else if (kind == "guide.csv") {
                        compare(export, Vb.saveStringAs(GuideExport.csv(rules)), failures)
                    } else if (kind == "guide.txt") {
                        compare(export, Vb.saveStringAs(GuideExport.txt(rules)), failures)
                    } else {
                        continue // notes such as "reopen.txt"
                    }
                    exports++
                }
            }
        }
        assertTrue(builds >= 4, "golden builds found: $builds")
        assertTrue(exports >= 12, "golden exports found: $exports")
        assertEquals(emptyList(), failures)
    }

    /** The vb-<version> folders. */
    private fun snapshots(): List<File> {
        val result = ArrayList<File>()
        val files = golden.listFiles() ?: return result
        for (file in files) {
            if (file.isDirectory && file.name.startsWith("vb-")) {
                result.add(file)
            }
        }
        return result
    }

    private fun compare(vbFile: File, kotlinText: String, failures: MutableList<String>) {
        val difference = firstDifference(Windows1252.decode(vbFile.readBytes()), kotlinText)
        if (difference != null) {
            failures.add("${vbFile.parentFile.name}/${vbFile.name}: $difference")
        }
    }

    /**
     * The forum export with the colour tags' values set aside: the rest must match, and each
     * [OutputColor] Kotlin uses must be the same VB colour everywhere.
     */
    private fun compareForum(vbFile: File, rules: BuildRules, formatName: String, failures: MutableList<String>) {
        val formatFile = File(formats, "$formatName.bbcodes")
        val formatText = if (formatFile.isFile) Windows1252.decode(formatFile.readBytes()) else null
        val format = OutputFormat.load(formatName, formatText)
        val kotlinText = Vb.saveStringAs(BuildOutput(rules, format, OutputSettings()).export { colorCode(it) })
        val vbText = Windows1252.decode(vbFile.readBytes())
        val masked = firstDifference(colorTag.replace(vbText, "[color]"), colorTag.replace(kotlinText, "[color]"))
        if (masked != null) {
            failures.add("${vbFile.parentFile.name}/${vbFile.name}: $masked")
            return
        }
        val vbColors = colorTag.findAll(vbText).toList()
        val kotlinColors = colorTag.findAll(kotlinText).toList()
        val themeColor = HashMap<String, String>()
        for (i in kotlinColors.indices) {
            val color = kotlinColors[i].groupValues[1]
            val hex = vbColors[i].groupValues[1]
            val known = themeColor[color]
            if (known == null) {
                themeColor[color] = hex
            } else if (known != hex) {
                failures.add("${vbFile.parentFile.name}/${vbFile.name}: $color is #$known and #$hex")
                return
            }
        }
    }

    /**
     * A stand-in for [color]'s theme colour: six characters like "RRGGBB", because the dots that
     * pad the export's columns depend on the length of the text before them, colour tags included.
     */
    private fun colorCode(color: OutputColor): String = color.ordinal.toString().padStart(6, '0')

    /** The first line where [expected] and [actual] differ (line endings included), or null. */
    private fun firstDifference(expected: String, actual: String): String? {
        if (expected == actual) {
            return null
        }
        val a = expected.split("\n")
        val b = actual.split("\n")
        for (i in 0 until maxOf(a.size, b.size)) {
            if (a.getOrNull(i) != b.getOrNull(i)) {
                return "line ${i + 1}: VB \"${a.getOrNull(i)}\", Kotlin \"${b.getOrNull(i)}\""
            }
        }
        return "same lines, different bytes"
    }
}
