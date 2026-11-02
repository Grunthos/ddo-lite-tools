package ddolite.builder.build

import ddolite.builder.data.BuilderData
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.Alignment
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Overview screen's rules (frmOverview), with small made-up data. */
class OverviewRulesTest {
    private val data: BuilderData = loadBuilderData(
        MapDataSource.ofText(
            "Builder/Races.txt" to
                "RaceName: Human\nType: Free\nStats: 8, 8, 8, 8, 8, 8\nFlags: ListFirst\n\n" +
                "RaceName: Elf\nType: Free\nStats: 8, 10, 6, 8, 8, 8\n\n" +
                "RaceName: Drow\nType: Free\nStats: 8, 10, 6, 10, 8, 10\n\n" +
                "RaceName: Wood Elf\nSubRace: Elf\nType: Free\nStats: 8, 10, 8, 6, 8, 8\n\n" +
                "RaceName: Warforged\nType: Premium\nStats: 8, 8, 10, 8, 6, 6\n\n" +
                "RaceName: Bladeforged\nType: Iconic\nIconicClass: Paladin\nStats: 8, 6, 10, 8, 6, 8\n",
            "Builder/Classes.txt" to
                "ClassName: Fighter\nBAB: 1\nSkillPoints: 2\n" +
                "Alignment: Lawful Neutral, Lawful Good, Chaotic Neutral, Chaotic Good, True Neutral, Neutral Good\n\n" +
                "ClassName: Paladin\nBAB: 1\nSkillPoints: 2\nAlignment: Lawful Good\n\n" +
                "ClassName: Monk\nBAB: 0.75\nSkillPoints: 4\nAlignment: Lawful Neutral, Lawful Good\n\n" +
                "ClassName: Bard\nBAB: 0.75\nSkillPoints: 6\nAlignment: Lawful Neutral, Lawful Good, Chaotic Neutral, Chaotic Good, True Neutral, Neutral Good\n\n" +
                "ClassName: Stormsinger\nBAB: 0.75\nSkillPoints: 6\nAlignment: Lawful Neutral, Lawful Good, Chaotic Neutral, Chaotic Good, True Neutral, Neutral Good\n" +
                "DisallowedClasses: Bard\n",
        ),
        LoadLog(),
    )

    private fun newOverview(): OverviewRules {
        val build = Build()
        val rules = BuildRules(data, build)
        rules.setBuildDefaults(BuildPoints.ADVENTURER)
        return OverviewRules(rules)
    }

    private fun cls(name: String) = data.findClass(name)!!

    private fun levelNames(overview: OverviewRules): List<String?> = overview.build.levelClasses.map { it?.name }

    @Test
    fun firstClassTakesEveryLevelAndSplashesTakeLevelsByClick() {
        val overview = newOverview()
        overview.classChange(0, cls("Fighter"))
        assertEquals(List(20) { "Fighter" }, levelNames(overview))
        assertEquals(20, overview.build.bab[19])
        overview.classChange(1, cls("Monk"))
        // A new splash has no levels until they're clicked.
        assertEquals(List(20) { "Fighter" }, levelNames(overview))
        assertTrue(overview.changeLevelSplit(2, 1))
        assertFalse(overview.changeLevelSplit(2, 1)) // already Monk
        assertFalse(overview.changeLevelSplit(3, 2)) // slot 2 is empty
        assertEquals("Monk", overview.build.levelClass(2)?.name)
        assertEquals(19 + 0, overview.build.bab[19]) // Fighter 19 + Monk 1 (3/4 BAB gives nothing at its first level)
        val row = overview.gridRows()[2]
        assertEquals(listOf(2, 1, 0), row.classLevels.toList())
        assertEquals(1, overview.slotOfLevel(2))
    }

    @Test
    fun removingAClassGivesItsLevelsToTheMainClass() {
        val overview = newOverview()
        overview.classChange(0, cls("Fighter"))
        overview.classChange(1, cls("Monk"))
        overview.classChange(2, cls("Bard"))
        overview.changeLevelSplit(1, 1)
        overview.changeLevelSplit(2, 2)
        // Dragging the first splash away clears it and the one after it.
        overview.clearBuildClass(1)
        assertEquals(listOf("Fighter", null, null), overview.build.buildClasses.map { it?.name })
        assertEquals(List(20) { "Fighter" }, levelNames(overview))
    }

    @Test
    fun duplicatesAndGapsAreRemoved() {
        val overview = newOverview()
        overview.classChange(0, cls("Fighter"))
        overview.classChange(1, cls("Monk"))
        overview.classChange(2, cls("Bard"))
        // Choosing the main class again in the first splash: it becomes none and the second splash moves up.
        overview.classChange(1, cls("Fighter"))
        assertEquals(listOf("Fighter", "Bard", "Bard"), overview.build.buildClasses.map { it?.name })
    }

    @Test
    fun alignmentsAndClassesFilterEachOther() {
        val overview = newOverview()
        overview.classChange(0, cls("Monk"))
        var choices = overview.filterChoices(disallowed = true)
        assertEquals(listOf(null, Alignment.LAWFUL_NEUTRAL, Alignment.LAWFUL_GOOD), choices.alignments)
        assertTrue(choices.isAllowed(cls("Paladin"))) // shares Lawful Good
        overview.classChange(1, cls("Paladin"))
        choices = overview.filterChoices(disallowed = true)
        // Only Lawful Good is left, so it's chosen.
        assertEquals(Alignment.LAWFUL_GOOD, overview.build.alignment)
        assertEquals(ClassIconState.EMPTY, overview.iconState(cls("Paladin"), choices))
        assertEquals(ClassIconState.NORMAL, overview.iconState(cls("Fighter"), choices))
    }

    @Test
    fun disallowedClassesOnlyFilterTheIcons() {
        val overview = newOverview()
        overview.classChange(0, cls("Stormsinger"))
        assertEquals(ClassIconState.DISABLED, overview.iconState(cls("Bard"), overview.filterChoices(disallowed = true)))
        assertTrue(overview.filterChoices(disallowed = false).isAllowed(cls("Bard")))
    }

    @Test
    fun iconicRaceSetsTheMainClassAndAnotherRaceClearsIt() {
        val overview = newOverview()
        overview.setRace(data.findRace("Bladeforged"), include32 = true, iconicClass = true)
        assertEquals("Paladin", overview.build.buildClasses[0]?.name)
        assertEquals(List(20) { "Paladin" }, levelNames(overview))
        // VB's mblnIconicClass stays on, so a non-iconic race sets the main class back to none.
        overview.setRace(data.findRace("Human"), include32 = true, iconicClass = true)
        assertNull(overview.build.buildClasses[0])
        assertEquals(List<String?>(20) { null }, levelNames(overview))
    }

    @Test
    fun drowCantUseChampionPoints() {
        val overview = newOverview()
        overview.build.buildPoints = BuildPoints.CHAMPION
        overview.setRace(data.findRace("Drow"), include32 = true, iconicClass = false)
        assertFalse(overview.build.includePoints[BuildPoints.CHAMPION.ordinal])
        assertEquals(BuildPoints.ADVENTURER, overview.build.buildPoints)
        assertEquals(28, overview.rules.buildPointsFor(BuildPoints.ADVENTURER))
        assertEquals(30, overview.rules.buildPointsFor(BuildPoints.HERO))
        // Back to a non-Drow race: Champion comes back only if it was on when the screen opened.
        overview.setRace(data.findRace("Human"), include32 = true, iconicClass = false)
        assertTrue(overview.build.includePoints[BuildPoints.CHAMPION.ordinal])
    }

    @Test
    fun raceListIsGroupedByTypeWithListFirstRacesFirst() {
        val rows = newOverview().raceRows()
        assertEquals(
            listOf("Free to Play", "Human", "Drow", "Elf", "Wood Elf", "Premium", "Warforged", "Iconic", "Bladeforged"),
            rows.map { it.caption },
        )
        assertTrue(rows[7].classHeader)
        assertEquals("Paladin", rows[8].iconicClass?.name)
    }

    @Test
    fun subRaceLink() {
        val overview = newOverview()
        overview.build.race = data.findRace("Wood Elf")
        assertEquals("Elf", overview.parentRace()?.name)
        assertEquals(
            "Wood Elf is a racial variant of Elf and for the purpose of Racial Reincarnation will award Elf Racial Past Lives.",
            overview.subRaceMessage(),
        )
    }

    @Test
    fun levelsSwapByDragAndBuildClassesSwapWithoutMovingLevels() {
        val overview = newOverview()
        overview.classChange(0, cls("Fighter"))
        overview.classChange(1, cls("Monk"))
        overview.changeLevelSplit(1, 1)
        assertTrue(overview.canDragLevel(1))
        assertFalse(overview.canSwapLevels(2, 3)) // both Fighter
        assertTrue(overview.swapLevels(1, 5))
        assertEquals("Fighter", overview.build.levelClass(1)?.name)
        assertEquals("Monk", overview.build.levelClass(5)?.name)
        assertTrue(overview.swapBuildClasses(1, 0))
        assertEquals(listOf("Monk", "Fighter", null), overview.build.buildClasses.map { it?.name })
        assertEquals("Monk", overview.build.levelClass(5)?.name)
    }

    @Test
    fun gatingMessages() {
        val overview = newOverview()
        val rules = overview.rules
        assertEquals("Select a Race first.", openScreenNotice(rules, BuildScreen.STATS, BuildScreen.OVERVIEW))
        assertEquals("Select a Race in the Overview screen first.", openScreenNotice(rules, BuildScreen.SKILLS, null))
        overview.setRace(data.findRace("Human"), include32 = true, iconicClass = false)
        assertEquals("Select a class split in the Overview screen first.", openScreenNotice(rules, BuildScreen.ENHANCEMENTS, null))
        overview.classChange(0, cls("Fighter"))
        assertNull(openScreenNotice(rules, BuildScreen.ENHANCEMENTS, null))
        assertEquals("Allocate base stats in the Stats screen first.", openScreenNotice(rules, BuildScreen.SKILLS, null))
        assertEquals("This build cannot cast spells.", openScreenNotice(rules, BuildScreen.SPELLS, null))
        overview.setMaxLevels(12)
        assertEquals("This build never reaches level 20.", openScreenNotice(rules, BuildScreen.DESTINY, null))
        assertTrue(closesAfterChange(rules, BuildScreen.DESTINY, CascadeChange.MAX_LEVEL))
    }
}
