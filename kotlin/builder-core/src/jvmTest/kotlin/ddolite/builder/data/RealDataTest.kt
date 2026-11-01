package ddolite.builder.data

import ddolite.builder.model.Bab
import ddolite.builder.model.Stat
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loads the repository's real Races.txt and Classes.txt (Gradle passes the Data folder as
 * ddolite.dataDir). Spot checks use values from the current data; update them if the data changes.
 */
class RealDataTest {
    private val log = LoadLog()
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), log)

    @Test
    fun loadsWithoutErrors() {
        val errors = ArrayList<LoadIssue>()
        for (issue in log.issues) {
            if (!issue.vbDifference) {
                errors.add(issue)
            }
        }
        assertEquals(emptyList(), errors)
    }

    @Test
    fun everyRaceAndClassIsOneVbKnows() {
        assertEquals(30, data.races.size)
        assertEquals(25, data.classes.size)
        for (race in data.races) {
            assertTrue(race.vbIndex > 0, "${race.name} isn't in KNOWN_RACES")
        }
        for (c in data.classes) {
            assertTrue(c.vbIndex > 0, "${c.name} isn't in KNOWN_CLASSES")
        }
    }

    @Test
    fun onlyExpectedVbDifferences() {
        // Warlock lists the Fey pact twice; VB6 keeps both, and so do we.
        val differences = ArrayList<String>()
        for (issue in log.issues) {
            if (issue.vbDifference) {
                differences.add(issue.message)
            }
        }
        assertEquals(listOf("Classes.txt: Warlock has pact Fey more than once (VB6 keeps both)"), differences)
    }

    @Test
    fun spotChecks() {
        val duergar = assertNotNull(data.findRace("Duergar"))
        assertEquals(listOf(9, 8, 9, 8, 8, 8), duergar.stats)
        assertEquals(9, duergar.stat(Stat.STR))

        val alchemist = assertNotNull(data.findClass("Alchemist"))
        assertEquals(Bab.THREE_QUARTERS, alchemist.bab)
        assertEquals(6, alchemist.maxSpellLevel)
        assertEquals(listOf("A", "Al", "Alchemist", "Alc"), alchemist.initials)
        assertEquals(2, alchemist.spellSlots(1, 1))
        assertEquals(15, alchemist.canCastSpell[6]) // SpellSlots6 starts at level 15
        assertEquals(1, alchemist.canCastSpell[0]) // HealingSpell: 1

        // vbIndex order: Barbarian (1) first, Arcane Trickster (25) last.
        assertEquals("Barbarian", data.classes[0].name)
        assertEquals("Arcane Trickster", data.classes[24].name)
    }
}
