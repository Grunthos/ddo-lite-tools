package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.output.BuildOutput
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.OutputSettings
import ddolite.builder.output.OutputView
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The build output ([BuildOutput]) and output formats ([OutputFormat]) on the sample builds. */
class BuildOutputTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun open(name: String): BuildRules = readBuildFile(SampleBuilds.file(name).readBytes(), data, BuildPoints.ADVENTURER).rules

    private fun export(name: String, format: String): String =
        BuildOutput(open(name), OutputFormat.builtIn(format), OutputSettings()).export { "112233" }

    @Test
    fun plainText() {
        val text = export("old-data/Kensei Warpriest.build", "Plain Text")
        val lines = text.split("\r\n")
        assertEquals(listOf("Kensei Warpriest", "12/8 Fighter/Cleric, Epic 10", "Human", "", "", "Level Order", ""), lines.subList(0, 7))
        assertEquals("1. Fighter           6. Fighter          11. Cleric           16. Fighter", lines[7])
        assertTrue(lines.contains(" 1 Heroic : Power Attack"))
        assertTrue(lines.contains("30 Legend : Scion of: Arborea OR Astral Plane"))
        assertTrue(lines.contains("    • Kensei Focus: Axes, Spiritual Bond, Strike With No Thought, Power Surge"))
        // Haste Boost: Rank 3 in the file, kept at its one rank now.
        assertTrue(lines.contains("         1. Extra Action Boost III, Exotic Weapon Mastery: Axes, Haste Boost"))
        // The text ends with one line break (VB adds vbNewLine to the last line).
        assertTrue(text.endsWith("Die Hard\r\n"))
    }

    @Test
    fun forumCodes() {
        val text = export("old-data/Kensei Warpriest.build", "Default")
        assertTrue(text.startsWith("[b]Kensei Warpriest[/b]\r\n"))
        assertTrue(text.contains("[u]Level Order[/u]"))
        // Runs of spaces become dots in the background colour; lists use [list] and [*].
        assertTrue(text.contains("[color=#112233]. . . . . .[/color]"))
        assertTrue(text.contains("Kensei (30 AP)[list][*]Kensei Focus: Axes"))
        // The build has a leveling guide, so its trees are in their guide colours.
        assertTrue(text.contains("[/list][/list][/color]\r\nWarpriest (23 AP)"))
    }

    @Test
    fun reddit() {
        val text = export("old-data/Kensei Warpriest.build", "Reddit")
        val lines = text.split("\r\n")
        assertEquals(listOf("|Kensei Warpriest|", "|:--|:--", "|12/8 Fighter/Cleric, Epic 10|", "|Human|"), lines.subList(0, 4))
        assertTrue(lines.contains("|Strength|**16**|18|18|18|4: STR|"))
        assertTrue(lines.contains("|Cores|Kensei Focus: Axes, Spiritual Bond, Strike With No Thought, Power Surge|"))
    }

    @Test
    fun screenShowsTheActiveWindowsSections() {
        val rules = open("old-data/Kensei Warpriest.build")
        val all = BuildOutput(rules, OutputFormat.DEFAULT, OutputSettings()).display(OutputView.ALL)
        val feats = BuildOutput(rules, OutputFormat.DEFAULT, OutputSettings()).display(OutputView.FEATS)
        fun texts(lines: List<ddolite.builder.output.DisplayLine>): List<String> = lines.map { l -> (l.prefix?.text ?: "") + l.runs.joinToString("") { it.text } }
        assertTrue(texts(all).contains("Stats"))
        assertTrue(texts(feats).contains("Feats"))
        assertTrue(texts(feats).contains("Stats")) // ValidStats takes oeFeats too
        assertFalse(texts(feats).contains("Skills"))
        // VB quirk: the Destinies window asks for oeDestiny2, which no section accepts.
        val destiny = texts(BuildOutput(rules, OutputFormat.DEFAULT, OutputSettings()).display(OutputView.DESTINY2))
        assertEquals(listOf("Kensei Warpriest", "12/8 Fighter/Cleric, Epic 10", "Human", "", ""), destiny)
    }

    @Test
    fun errorMarksLinkToTheirWindows() {
        val lines = BuildOutput(open("old-data/Kensei Warpriest.build"), OutputFormat.DEFAULT, OutputSettings()).display(OutputView.ALL)
        val links = ArrayList<String>()
        for (line in lines) {
            for (run in line.runs) {
                if (run.link.isNotEmpty()) {
                    links.add(run.link + ":" + run.text)
                }
            }
        }
        assertTrue(links.contains("Enhancements: Errors"), links.toString())
        assertTrue(links.contains("Destinies: Errors"), links.toString())
    }

    @Test
    fun formatFiles() {
        val reddit = OutputFormat.load("Reddit", "Codes=True\r\nDots=False\r\nTextColors=True\r\nBold=False\t\t\r\nLists=False\t\t\t\t\r\nReddit=True")
        assertTrue(reddit.reddit)
        assertFalse(reddit.bold.enabled)
        val plain = OutputFormat.builtIn("Plain Text")
        assertFalse(plain.codes)
        assertEquals("[color=#\$]", OutputFormat.DEFAULT.color.open)
        // A tag line with the wrong number of parts is ignored.
        assertEquals(OutputFormat.DEFAULT.bold, OutputFormat.parse("Bold=True\t[b]", OutputFormat.DEFAULT).bold)
    }
}
