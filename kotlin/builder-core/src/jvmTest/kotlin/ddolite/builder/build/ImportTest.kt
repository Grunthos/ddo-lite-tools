package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.imports.ForumImport
import ddolite.builder.imports.PlannerImport
import ddolite.builder.model.Alignment
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.output.BuildOutput
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.OutputSettings
import ddolite.builder.output.PlannerExport
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** File › Import: Ron's Planner, DDO Builder ([PlannerImport]) and from Forums ([ForumImport]). */
class ImportTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun open(name: String): BuildRules = readBuildFile(SampleBuilds.file(name).readBytes(), data, BuildPoints.ADVENTURER).rules

    /** The past lives come from feats, which the imports don't read (as VB), so they're left out of the comparison. */
    private fun withoutPastLives(ron: String): String {
        val lines = ArrayList(ron.split("\r\n"))
        val at = lines.indexOf("PASTLIFE: ")
        if (at >= 0) {
            lines.removeAt(at + 1)
        }
        return lines.joinToString("\r\n")
    }

    private fun withoutSpecialFeats(xml: String): String =
        xml.replace(Regex("<SpecialFeats>.*?</SpecialFeats>", RegexOption.DOT_MATCHES_ALL), "").replace("<SpecialFeats/>", "")

    @Test
    fun ronRoundTrip() {
        for (name in SampleBuilds.names()) {
            val exported = PlannerExport(open(name)).ronFile()
            val imported = PlannerImport.ron(exported, "x.txt", data, BuildPoints.ADVENTURER)
            assertEquals(withoutPastLives(exported), withoutPastLives(PlannerExport(imported.rules).ronFile()), name)
        }
    }

    @Test
    fun builderRoundTrip() {
        for (name in SampleBuilds.names()) {
            val exported = PlannerExport(open(name)).builderFile()
            val imported = PlannerImport.builder(exported, data, BuildPoints.ADVENTURER)
            assertEquals(withoutSpecialFeats(exported), withoutSpecialFeats(PlannerExport(imported.rules).builderFile()), name)
        }
    }

    @Test
    fun ronDetails() {
        val text = "VERSION: 4.36.104;\r\nNAME: Some, One;\r\nRACE: 5;\r\nALIGNMENT: 9;\r\nABILITY4: 3;\r\n\r\n"
        val build = PlannerImport.ron(text, "C:\\Builds\\My File.txt", data, BuildPoints.LEGEND).build
        assertEquals("Some One", build.buildName)
        assertEquals("Drow", build.race?.name)
        assertNull(build.alignment)
        assertEquals(Stat.INT, build.levelups[1])
        // VB quirk: a missing ABILITYn: line reads as 0, Strength.
        assertEquals(Stat.STR, build.levelups[2])
        assertNull(build.levelups[0])
        // No past lives and no ABILITYFAVORBONUS: Adventurer, the only column included.
        assertEquals(BuildPoints.ADVENTURER, build.buildPoints)
        assertTrue(build.includePoints[0])
        assertFalse(build.includePoints[3])
        assertEquals("My File", PlannerImport.ron("NAME: ;", "C:\\Builds\\My File.txt", data, BuildPoints.LEGEND).build.buildName)
    }

    @Test
    fun builderDetails() {
        val xml = "<DDOCharacterData>\r\n<Character>\r\n<Name>X</Name>\r\n<Alignment>Neutral</Alignment>\r\n<Race>Drow</Race>\r\n" +
            "<AbilitySpend>\r\n<AvailableSpend>28</AvailableSpend>\r\n<StrSpend>8</StrSpend>\r\n</AbilitySpend>\r\n" +
            "<SkillTomes>\r\n<UMD>2</UMD>\r\n</SkillTomes>\r\n</Character>\r\n</DDOCharacterData>\r\n"
        val build = PlannerImport.builder(xml, data, BuildPoints.LEGEND).build
        assertEquals("X", build.buildName)
        assertEquals(Alignment.TRUE_NEUTRAL, build.alignment)
        assertEquals(2, build.skillTomes[Skill.UMD.ordinal])
        // VB quirk: a Drow exports Champion as 28, which comes back as Adventurer.
        assertEquals(BuildPoints.ADVENTURER, build.buildPoints)
        assertEquals(10, build.statPoints[0][Stat.STR.ordinal])
        // VB quirk: an XML declaration has no close tag, which stops the reading.
        val declared = PlannerImport.builder("<?xml version=\"1.0\"?>\r\n$xml", data, BuildPoints.LEGEND).build
        assertEquals("New Build", declared.buildName)
    }

    private fun levelOrderLine(a: Int, classA: String, b: Int, classB: String): String =
        ("$a. $classA").padEnd(18) + (if (b < 10) " " else "") + "$b. $classB"

    @Test
    fun forumText() {
        val lines = listOf(
            "Test Build",
            "6/4 Fighter/Cleric",
            "Lawful Good Human",
            "",
            "Level Order",
            "",
            levelOrderLine(1, "Fighter", 6, "Fighter"),
            levelOrderLine(2, "Fighter", 7, "Cleric"),
            levelOrderLine(3, "Cleric", 8, "Fighter"),
            levelOrderLine(4, "Fighter", 9, "Cleric"),
            levelOrderLine(5, "Cleric", 10, "Fighter"),
            "",
            "Stats",
            "               28pt     32pt     Level Up",
            "               ----     ----     --------",
            "Strength        16       18       4: STR",
            "Dexterity        8        8       8: DEX",
            "",
            "Skills",
            "          F  F  C  F  C  F  C  F  C  F",
            "         ------------------------------",
            "Jump".padEnd(10) + "4  1  ½".padEnd(60) + "6",
            "         ------------------------------",
            "",
            "Feats",
            "",
            " 1 Heroic : Power Attack",
            " 1 Fighter: Cleave",
            " 3 Heroic : Weapon Focus: Slashing",
            "",
            "Enhancements (Spent: 4 / Max: 80 AP)",
            "",
            "Kensei (4 AP)",
            "    • Kensei Focus: Axes",
            "Extra Action Boost III",
        )
        val result = ForumImport(data, BuildPoints.LEGEND, OutputSettings()).translate(lines.joinToString("\r\n"))
        assertNotNull(result)
        val build = result.build
        assertEquals("Test Build", build.buildName)
        assertEquals(Alignment.LAWFUL_GOOD, build.alignment)
        assertEquals("Human", build.race?.name)
        assertEquals(10, build.maxLevels)
        assertEquals("Fighter", build.buildClasses[0]?.name)
        assertEquals("Cleric", build.buildClasses[1]?.name)
        assertEquals("Fighter", build.levelClass(6)?.name)
        assertEquals("Cleric", build.levelClass(9)?.name)
        // 16 and 18 less Human's 8: 8 and 10 raises, 10 and 16 points.
        assertEquals(10, build.statPoints[BuildPoints.ADVENTURER.ordinal][Stat.STR.ordinal])
        assertEquals(16, build.statPoints[BuildPoints.CHAMPION.ordinal][Stat.STR.ordinal])
        assertTrue(build.includePoints[BuildPoints.ADVENTURER.ordinal] && build.includePoints[BuildPoints.CHAMPION.ordinal])
        assertFalse(build.includePoints[BuildPoints.HERO.ordinal])
        assertEquals(Stat.STR, build.levelups[1])
        assertEquals(Stat.DEX, build.levelups[2])
        // Ranks as points: two per rank when cross-class, plus one for "½".
        val fighter = build.levelClass(1)!!
        val perRank = if (fighter.nativeSkills.contains(Skill.JUMP)) 1 else 2
        assertEquals(4 * perRank, build.skills[Skill.JUMP.ordinal][0])
        assertEquals(1 * perRank, build.skills[Skill.JUMP.ordinal][1])
        assertEquals(1, build.skills[Skill.JUMP.ordinal][2]) // "½": no ranks, one point
        val names = ArrayList<String>()
        for (slot in build.feats(BuildFeatType.STANDARD)) {
            names.add(slot.featName)
        }
        assertTrue(names.contains("Power Attack"), names.toString())
        assertTrue(names.contains("Weapon Focus"), names.toString())
        var cleave = false
        for (slot in build.feats(BuildFeatType.CLASS1)) {
            if (slot.featName == "Cleave") {
                cleave = true
            }
        }
        assertTrue(cleave)
        var kensei: BuildTree? = null
        for (t in build.trees) {
            if (t.treeName == "Kensei") {
                kensei = t
            }
        }
        assertNotNull(kensei)
        assertEquals(2, kensei.abilities.size)
        assertEquals(0, kensei.abilities[0].tier)
        assertTrue(kensei.abilities[0].selector > 0)
        assertEquals(1, kensei.abilities[1].tier)
        assertEquals(3, kensei.abilities[1].rank)
    }

    @Test
    fun forumQuirks() {
        // VB quirk: "Destinies" isn't a heading (VB looks for "Destiny"), so the destinies are read as
        // more tiers of the last tree, which runs past its tiers: the whole import fails.
        val rules = open("old-data/Kensei Warpriest.build")
        val text = BuildOutput(rules, OutputFormat.builtIn("Plain Text"), OutputSettings()).export { "112233" }
        assertNull(ForumImport(data, BuildPoints.ADVENTURER, OutputSettings()).translate(text))
        // VB quirk: the output's Level Order columns are wider than the import's, so only levels 1-5 come back.
        val eresh = BuildOutput(open("Eresh WFWizRog-v4.15.build"), OutputFormat.builtIn("Plain Text"), OutputSettings()).export { "112233" }
        val result = ForumImport(data, BuildPoints.ADVENTURER, OutputSettings()).translate(eresh.substring(0, eresh.indexOf("Feats")))
        assertNotNull(result)
        assertEquals("Wizard", result.build.levelClass(5)?.name)
        assertNull(result.build.levelClass(6))
        assertEquals("Eresh WF/Wiz/Rog", result.build.buildName)
        assertEquals("Warforged", result.build.race?.name)
    }
}
