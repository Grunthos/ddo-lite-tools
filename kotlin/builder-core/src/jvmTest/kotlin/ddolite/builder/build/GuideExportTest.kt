package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.output.GuideExport
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The leveling guide's csv and txt exports ([GuideExport]) on a guide built for Kensei Warpriest. */
class GuideExportTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun guide(): BuildRules {
        val rules = readBuildFile(SampleBuilds.file("old-data/Kensei Warpriest.build").readBytes(), data, BuildPoints.ADVENTURER).rules
        // From an empty guide (the saved one's missing steps load as red rows).
        GuideRules(rules).clear()
        rules.initLevelingGuide()
        val g = GuideRules(rules)
        val kensei = g.treeList(false).first { it.text == "Kensei" }.number
        g.addAbility(kensei, 0, 0, 1, 1) // Kensei Focus: Archery
        g.addAbility(kensei, 1, 0, 0, 2) // Extra Action Boost I, II
        g.addCommand(200)
        return rules
    }

    @Test
    fun csv() {
        val lines = GuideExport.csv(guide()).split("\r\n")
        assertEquals("\"Level\",\"Tree\",\"Tier\",\"Enhancement\",\"AP\",\"Prog\",\"Order\"", lines[0])
        assertEquals("1,\"Kensei\",\"Core 1\",\"Kensei Focus: Archery\",1,1,1", lines[1])
        assertTrue(lines[2].startsWith("1,\"Kensei\",\"Tier 1\",\"Extra Action Boost I\",2,3,"))
        // A new level gets an empty line with its order number first.
        assertTrue(lines.any { it.matches(Regex(",,,,,,[0-9]+")) })
        assertTrue(lines.last().contains("\"Bank "))
    }

    @Test
    fun txt() {
        val lines = GuideExport.txt(guide()).split("\r\n")
        assertTrue(lines[0].startsWith("Level  ") && lines[0].contains("Enhancement") && lines[0].endsWith("AP  Prog"))
        assertTrue(lines[1].startsWith("-----  ------  ------  "))
        assertTrue(lines[2].startsWith("   1   Kensei  Core 1  Kensei Focus: Archery"))
        assertEquals("Leveling Guide.csv", GuideExport.suggestedName(null, "", "csv"))
        assertEquals("Kensei.txt", GuideExport.suggestedName("Kensei", "Other", "txt"))
    }
}
