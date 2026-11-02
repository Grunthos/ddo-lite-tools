package ddolite.builder.data

import ddolite.builder.model.NameChange
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TomesAndNameChangesTest {
    @Test
    fun defaultsWhenThereIsNoFile() {
        val log = LoadLog()
        val tomes = loadTomes(MapDataSource.ofText(), log)
        assertEquals(8, tomes.stat.max)
        assertEquals(listOf(1, 1, 3, 7, 11, 15, 19, 22), tomes.stat.levels)
        assertEquals(3, tomes.stat.level(3))
        assertEquals(5, tomes.skill.max)
        assertEquals(2, tomes.racialApMax)
        assertEquals(1, tomes.universalApMax)
        assertEquals(0, tomes.permDestinyPointMin)
        assertEquals(18, tomes.permDestinyPointMax)
        assertEquals(emptyList(), log.issues)
    }

    @Test
    fun fileOverridesDefaults() {
        val log = LoadLog()
        val tomes = loadTomes(
            MapDataSource.ofText(
                TOMES_PATH to "Stat: 3, 1, 5\nRacialAPMax: 3\nPowerMax: 0\nPRR/MRRMax: x\nFateTomeMax: 3 3\nSomething: 9\nno colon\nDestinyTomeMax: 3 ",
            ),
            log,
        )
        assertEquals(listOf(1, 5, 0), tomes.stat.levels) // a missing level is 0
        assertEquals(3, tomes.racialApMax)
        assertEquals(4, tomes.powerMax) // 0 leaves the default
        assertEquals(4, tomes.rrMax) // so does text
        assertEquals(33, tomes.fateTomeMax) // VB's Val ignores the space
        assertEquals(3, tomes.destinyTomeMax)
        assertEquals(1, log.issues.size)
        assertTrue(log.issues[0].vbDifference)
        assertTrue(log.issues[0].message.contains("read as 33"))
    }

    @Test
    fun nameChanges() {
        val log = LoadLog()
        val changes = loadNameChanges(
            MapDataSource.ofText(
                "Builder/NameChange.txt" to
                    "NameChangeType: Feat\nOld: Zeal\nNew: Zealotry\n\n" +
                    "NameChangeType: Feat\nOld: Alpha\nNew: Beta\nAssignSelector: One\n\n" +
                    "NameChangeType: Feat\nOld: Only old\n\n" +
                    "NameChangeType: Spell\nOld: A\nNew: B\n",
            ),
            log,
        )
        assertEquals(
            listOf(NameChange("Feat", "Alpha", "Beta", "One"), NameChange("Feat", "Zeal", "Zealotry", "")),
            changes,
        )
        assertEquals(listOf("NameChange.txt: Invalid Type: Spell"), log.issues.map { it.message })
    }
}
