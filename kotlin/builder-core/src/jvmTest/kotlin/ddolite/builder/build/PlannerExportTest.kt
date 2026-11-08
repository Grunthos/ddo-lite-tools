package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.Alignment
import ddolite.builder.output.PlannerExport
import ddolite.dataformat.DirectoryDataSource
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Ron's Planner and DDO Builder exports ([PlannerExport]) on Kensei Warpriest. */
class PlannerExportTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun open(name: String): BuildRules = readBuildFile(SampleBuilds.file(name).readBytes(), data, BuildPoints.ADVENTURER).rules

    @Test
    fun ron() {
        val text = PlannerExport(open("old-data/Kensei Warpriest.build")).ronFile()
        File(System.getProperty("java.io.tmpdir"), "kensei-ron.txt").writeText(text)
        val lines = text.split("\r\n")
        assertEquals("VERSION: 4.36.104;", lines[0])
        assertEquals("NAME: Kensei, Warpriest;", lines[1])
        assertEquals("RACE: 0;", lines[2])
        assertEquals("CLASSRECORD: ", lines[5])
        assertEquals("Fighter,", lines[6])
        assertTrue(lines.contains("ABILITY4: 0;")) // Strength
        assertTrue(lines.any { it.startsWith("FEATLIST: ") })
        assertTrue(lines.contains("Power Attack, 1, 1,"))
        // The past lives line has a value for each of Ron's 14 classes, then blanks to VB's 25 classes.
        val pastLife = lines[lines.indexOf("PASTLIFE: ") + 1]
        assertEquals(26, pastLife.removeSuffix(";").split(", ").size)
    }

    @Test
    fun builder() {
        val text = PlannerExport(open("old-data/Kensei Warpriest.build")).builderFile()
        File(System.getProperty("java.io.tmpdir"), "kensei.ddocp").writeText(text)
        val lines = text.split("\r\n")
        assertEquals("<DDOCharacterData>", lines[0])
        assertEquals("   <Character>", lines[1])
        assertEquals("      <Name>Kensei Warpriest</Name>", lines[2])
        assertTrue(lines.contains("      <Race>Human</Race>"))
        assertTrue(lines.contains("         <StrSpend>8</StrSpend>"))
        assertTrue(lines.contains("               <FeatName>Power Attack</FeatName>"))
        assertEquals("</DDOCharacterData>", lines.last())
    }

    @Test
    fun alignmentWithNoneChosen() {
        val rules = open("old-data/Kensei Warpriest.build")
        rules.build.alignment = null
        // Fighter and Cleric allow True Neutral.
        assertEquals(Alignment.TRUE_NEUTRAL, PlannerExport(rules).exportAlignment())
    }
}
