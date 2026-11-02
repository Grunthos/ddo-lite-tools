package ddolite.builder.build

import ddolite.builder.data.BuilderData
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.Stat
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Stats screen's rules (userStats, frmStats, basBuild's stat routines), with small made-up data. */
class StatRulesTest {
    private val data: BuilderData = loadBuilderData(
        MapDataSource.ofText(
            "Builder/Races.txt" to
                "RaceName: Human\nType: Free\nStats: 8, 8, 8, 8, 8, 8\n\n" +
                "RaceName: Drow\nType: Free\nStats: 8, 10, 6, 10, 8, 10\n",
            "Builder/Classes.txt" to "ClassName: Fighter\nBAB: 1\nSkillPoints: 2\n",
            "Builder/Templates.txt" to
                "Class: Fighter\nCaption: THF\nDescrip: Strength build\nLevelups: Strength\nPTS: 28 30 32 34 36\n" +
                "STR: 16 16 16 16 16\nDEX:  2  4     2  4\nCON: 10 10 16 16 16\n",
            "Tomes.txt" to "Stat: 8, 1, 1, 3, 7, 11, 15, 19, 22\n",
        ),
        LoadLog(),
    )

    private fun newStats(race: String = "Human"): StatRules {
        val build = Build()
        val rules = BuildRules(data, build)
        rules.setBuildDefaults(BuildPoints.ADVENTURER)
        build.race = data.findRace(race)
        return StatRules(rules)
    }

    @Test
    fun pointCostsRiseWithTheScore() {
        val stats = newStats()
        // 8 → 14 costs one point each, then 2, 2, 3, 3: 16 points for 18.
        var spent = 0
        while (stats.increase(BuildPoints.ADVENTURER, Stat.STR)) {
            spent = stats.build.statTotals[BuildPoints.ADVENTURER.ordinal]
        }
        assertEquals(16, spent)
        assertEquals(18, stats.columnStat(BuildPoints.ADVENTURER, Stat.STR))
        assertEquals(0, stats.nextCost(BuildPoints.ADVENTURER, Stat.STR))
        assertTrue(stats.decrease(BuildPoints.ADVENTURER, Stat.STR))
        assertEquals(13, stats.build.statPoints[0][Stat.STR.ordinal])
    }

    @Test
    fun preferredColumnChangesCarryToTheOthersWhenTheyCanAffordThem() {
        val stats = newStats()
        for (i in 0 until 16) {
            stats.increase(BuildPoints.ADVENTURER, Stat.STR)
        }
        for (i in 0 until 12) {
            stats.increase(BuildPoints.ADVENTURER, Stat.CON)
        }
        // Adventurer has 28 points: Str 18 (16) + Con 16 (10) = 26, and 28 would need 3 more.
        assertEquals(26, stats.build.statTotals[BuildPoints.ADVENTURER.ordinal])
        // The others only follow changes the preferred column makes, so Champion stops there too.
        assertEquals(26, stats.build.statTotals[BuildPoints.CHAMPION.ordinal])
        assertTrue(stats.increase(BuildPoints.CHAMPION, Stat.CON)) // Champion can still go on alone
        assertEquals(29, stats.build.statTotals[BuildPoints.CHAMPION.ordinal])
        // A non-preferred column changes alone.
        stats.increase(BuildPoints.LEGEND, Stat.DEX)
        assertEquals(0, stats.build.statPoints[BuildPoints.ADVENTURER.ordinal][Stat.DEX.ordinal])
    }

    @Test
    fun scoreAtLevelAddsLevelupsAndTomes() {
        val stats = newStats()
        stats.build.statPoints[0][Stat.INT.ordinal] = 6 // 14
        stats.setLevelup(0, Stat.INT)
        assertEquals(Stat.INT, stats.build.levelups[9])
        stats.setTome(Stat.INT, 3)
        assertEquals(14, stats.stat(Stat.INT, 1)) // no tome at level 1 without "fred"
        assertEquals(16, stats.stat(Stat.INT, 1, fred = true)) // tome 2 from level 1
        assertEquals(14 + 1 + 3, stats.stat(Stat.INT, 4)) // level-up at 4, tome 3 from level 3
        // The level 36 level-up never counts (VB's "32 \ 4").
        assertEquals(stats.stat(Stat.INT, 32), stats.stat(Stat.INT, 36))
        assertEquals(1, stats.stat(Stat.CHA, 1, modifier = false) - 7)
        assertEquals(-1, stats.stat(Stat.CHA, 1, modifier = true)) // 8 → (8 - 11) \ 2
        // One level-up different from the rest clears "All Levels".
        stats.setLevelup(3, Stat.STR)
        assertEquals(null, stats.build.levelups[0])
    }

    @Test
    fun supremeTomeRaisesEveryStat() {
        val stats = newStats()
        stats.setTome(Stat.DEX, 5)
        stats.setTome(null, 3)
        assertEquals(listOf(3, 5, 3, 3, 3, 3), stats.build.tomes.toList())
        stats.build.maxLevels = 6
        assertEquals(3, stats.tomeMax())
        assertTrue(stats.tomesLimitedByLevel())
        stats.enforceTomeMax()
        assertEquals(3, stats.build.tomes[Stat.DEX.ordinal])
    }

    @Test
    fun turningOffThePreferredColumnPicksTheNextOne() {
        val stats = newStats()
        stats.setPreferred(BuildPoints.HERO)
        stats.setIncluded(BuildPoints.LEGEND, false)
        stats.setIncluded(BuildPoints.HERO, false)
        assertEquals(BuildPoints.ADVENTURER, stats.build.buildPoints) // Legend is off, so it wraps round
    }

    @Test
    fun drowHasNoChampionColumnAndFewerPoints() {
        val stats = newStats("Drow")
        assertEquals(listOf(BuildPoints.ADVENTURER, BuildPoints.HERO, BuildPoints.LEGEND), stats.preferredChoices())
        assertFalse(stats.canInclude(BuildPoints.CHAMPION))
        stats.setPreferred(BuildPoints.LEGEND)
        assertEquals("32pt", stats.pointsLabel())
    }

    @Test
    fun templatesFillEveryColumn() {
        val stats = newStats()
        val templates = stats.templatesFor(data.findClass("Fighter"))
        assertEquals(listOf("THF"), templates.map { it.caption })
        stats.applyTemplate(templates[0])
        assertEquals(listOf(16, 2, 10, 0, 0, 0), stats.build.statPoints[BuildPoints.ADVENTURER.ordinal].toList())
        assertEquals(listOf(16, 0, 16, 0, 0, 0), stats.build.statPoints[BuildPoints.CHAMPION.ordinal].toList()) // the 32 column
        assertEquals(28, stats.build.statTotals[BuildPoints.ADVENTURER.ordinal])
        assertEquals(Stat.STR, stats.build.levelups[5])
        // Drow: the 28, 28, 30 and 32 spreads.
        val drow = newStats("Drow")
        drow.applyTemplate(templates[0])
        assertEquals(listOf(16, 4, 10, 0, 0, 0), drow.build.statPoints[BuildPoints.HERO.ordinal].toList())
    }

    @Test
    fun clearStats() {
        val stats = newStats()
        stats.applyTemplate(stats.templatesFor(data.findClass("Fighter"))[0])
        stats.setTome(null, 2)
        stats.clearStats(keepTomes = true, preferred = BuildPoints.HERO)
        assertEquals(0, stats.build.statTotals[0])
        assertEquals(null, stats.build.levelups[4])
        assertEquals(BuildPoints.HERO, stats.build.buildPoints)
        assertEquals(2, stats.build.tomes[0])
        stats.clearStats(keepTomes = false, preferred = BuildPoints.ADVENTURER)
        assertFalse(stats.hasTomes())
    }
}
