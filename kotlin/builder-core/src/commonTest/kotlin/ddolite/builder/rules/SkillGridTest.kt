package ddolite.builder.rules

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadClasses
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Skill
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The skills rules (basBuild.InitBuildSkills and the frmSkills editing rules), worked through by
 * hand on a made-up build: Fighter at levels 1-2, Rogue at level 3.
 */
class SkillGridTest {
    private val classes: List<CharacterClass> = loadClasses(
        MapDataSource.ofText(
            "Builder/Classes.txt" to """
                ClassName: Fighter
                BAB: 1
                SkillPoints: 2
                Skills: Intimidate, Jump, Swim

                ClassName: Rogue
                BAB: 0.75
                SkillPoints: 8
                Skills: Disable Device, Jump, Open Lock, Tumble
                Trapper: True
            """.trimIndent(),
        ),
        LoadLog(),
    )
    private val fighter = classes[0] // VB order: Fighter (4) before Rogue (7)
    private val rogue = classes[1]

    private fun grid(intModifier: Int = 0): SkillGrid =
        SkillGrid(listOf(fighter, fighter, rogue), raceSkillPoints = 0, intModifiers = listOf(intModifier, intModifier, intModifier))

    @Test
    fun skillPointsPerLevel() {
        val g = grid()
        assertEquals(8, g.maxPoints(1)) // (2 + 0 + 0) × 4 at level 1
        assertEquals(2, g.maxPoints(2))
        assertEquals(8, g.maxPoints(3)) // Rogue
        // Never less than 1 point (then ×4 at level 1), however low Intelligence is.
        val dim = grid(intModifier = -3)
        assertEquals(4, dim.maxPoints(1))
        assertEquals(1, dim.maxPoints(2))
    }

    @Test
    fun classSkillsAndCaps() {
        val g = grid()
        assertEquals(2, g.native(Skill.JUMP, 1))
        assertEquals(1, g.native(Skill.TUMBLE, 1))
        assertEquals(2, g.native(Skill.TUMBLE, 3))
        assertEquals(8, g.maxRanks(Skill.JUMP, 1)) // (1 + 3) full ranks = 8 half ranks
        assertEquals(4, g.maxRanks(Skill.TUMBLE, 1)) // cross-class: half
        assertEquals(12, g.maxRanks(Skill.TUMBLE, 3)) // native from level 3 on
        assertEquals(12, g.totalMaxRanks(Skill.TUMBLE))
        assertEquals(6, g.totalMaxRanks(Skill.HIDE)) // never native
    }

    @Test
    fun incrementRespectsRankCap() {
        val g = grid()
        val spend = SkillSpend()
        for (i in 1..4) {
            assertTrue(g.increment(spend, Skill.JUMP, 1), "point $i")
        }
        assertFalse(g.increment(spend, Skill.JUMP, 1)) // 4 points = 8 half ranks = the cap
        assertEquals(8, g.cumulativeRanks(spend, Skill.JUMP, 1))
        assertEquals("4", formatRanks(g.totalRanks(spend, Skill.JUMP)))
        assertEquals(RankStatus.FULL, g.cellStatus(spend, Skill.JUMP, 1))
    }

    @Test
    fun incrementRespectsPointsPerLevel() {
        val g = grid()
        val spend = SkillSpend()
        spend.set(Skill.SWIM, 2, 2) // level 2 has only 2 points
        assertFalse(g.increment(spend, Skill.JUMP, 2))
        assertEquals(RankStatus.FULL, g.pointsStatus(spend, 2))
        assertEquals(RankStatus.UNDER, g.pointsStatus(spend, 1))
    }

    @Test
    fun trapperSkillsNeedATrapperLevel() {
        val g = grid()
        val spend = SkillSpend()
        assertFalse(g.increment(spend, Skill.DISABLE_DEVICE, 1))
        assertTrue(g.increment(spend, Skill.DISABLE_DEVICE, 3))
        assertEquals(3, g.firstLevel(Skill.DISABLE_DEVICE))
        assertEquals(1, g.firstLevel(Skill.TUMBLE))
    }

    @Test
    fun decrementAndClear() {
        val g = grid()
        val spend = SkillSpend()
        assertFalse(g.decrement(spend, Skill.JUMP, 1))
        g.increment(spend, Skill.JUMP, 1)
        g.increment(spend, Skill.SWIM, 1)
        assertTrue(g.decrement(spend, Skill.JUMP, 1))
        assertEquals(0, spend.get(Skill.JUMP, 1))
        g.clearLevel(spend, 1)
        assertEquals(0, g.pointsSpent(spend, 1))
    }

    @Test
    fun maxHalfRanksFillsEachLevelToItsCap() {
        val g = grid()
        val spend = SkillSpend()
        g.maxHalfRanks(spend, Skill.TUMBLE)
        // Level 1: cap 4, cross-class → 4 points. Level 2: cap 5 → 1 point.
        // Level 3: cap 12, native → (12 - 5) / 2 = 3 points (6 half ranks), 11 in total.
        assertEquals(4, spend.get(Skill.TUMBLE, 1))
        assertEquals(1, spend.get(Skill.TUMBLE, 2))
        assertEquals(3, spend.get(Skill.TUMBLE, 3))
        assertEquals("5½", formatRanks(g.totalRanks(spend, Skill.TUMBLE)))
        // 11 of 12: under the cap, so dimmed.
        assertEquals(RankStatus.UNDER, g.totalStatus(spend, Skill.TUMBLE))
    }

    @Test
    fun maxFullRanksUsesWholeRanksOnly() {
        val g = grid()
        val spend = SkillSpend()
        g.maxFullRanks(spend, Skill.TUMBLE)
        // Level 1: 4 → 4 points. Level 2: 5 - 4 = 1, odd → 0. Level 3: 12 - 4 = 8 → 4 points.
        assertEquals(4, spend.get(Skill.TUMBLE, 1))
        assertEquals(0, spend.get(Skill.TUMBLE, 2))
        assertEquals(4, spend.get(Skill.TUMBLE, 3))
        assertEquals(RankStatus.FULL, g.totalStatus(spend, Skill.TUMBLE))
    }

    @Test
    fun oddCapCountsAsFullOneHalfRankShort() {
        // Hide is never a class skill here, so its cap is (levels + 3) half ranks.
        // A 2-level build makes that odd: 5 half ranks, which a cross-class skill can't reach exactly.
        val g = SkillGrid(listOf(fighter, fighter), 0, listOf(0, 0))
        val spend = SkillSpend()
        spend.set(Skill.HIDE, 1, 4)
        assertEquals(5, g.totalMaxRanks(Skill.HIDE))
        assertEquals(RankStatus.FULL, g.totalStatus(spend, Skill.HIDE)) // 4 of 5 counts as full
    }

    @Test
    fun formatRanksMatchesVb() {
        assertEquals("", formatRanks(0))
        assertEquals("½", formatRanks(1))
        assertEquals("1", formatRanks(2))
        assertEquals("3½", formatRanks(7))
        assertEquals("23", formatRanks(46))
    }
}
