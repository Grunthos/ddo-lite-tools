package ddolite.builder.data

import ddolite.builder.model.Bab
import ddolite.builder.model.ClassLevel
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.TreeStyle
import ddolite.builder.model.Stat
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loads the repository's real Builder data files ported so far (Gradle passes the Data folder as
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
        // Data issues VB6 tolerates: Warlock lists the Fey pact twice (VB6 keeps both, and so do we);
        // Spells.txt lists Prismatic Strike twice (VB6 keeps both and may find either).
        val differences = ArrayList<String>()
        for (issue in log.issues) {
            if (issue.vbDifference) {
                differences.add(issue.message)
            }
        }
        assertEquals(
            listOf(
                "Spells.txt: Prismatic Strike is listed more than once (also at line 1371); VB6 keeps both and may use either",
                "Classes.txt: Warlock has pact Fey more than once (VB6 keeps both)",
            ),
            differences,
        )
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

    @Test
    fun spellsAndTemplates() {
        assertEquals(578, data.spells.size) // including Prismatic Strike twice
        for (i in 1 until data.spells.size) {
            assertTrue(data.spells[i - 1].name <= data.spells[i].name, "spells not sorted at $i")
        }
        val acidFog = assertNotNull(data.findSpell("Acid Fog"))
        assertTrue(acidFog.rare)
        assertEquals("Acid Blast (spell)", data.findSpell("Acid Blast")?.wiki)

        assertEquals(51, data.templates.size)
        val first = data.templates[0]
        assertEquals("Barbarian: THF", first.logName)
        assertEquals(Stat.STR, first.levelups)
        assertEquals(16, first.points(0, Stat.STR))
        assertEquals(4, first.points(4, Stat.DEX))
    }

    @Test
    fun feats() {
        assertEquals(417, data.feats.size)
        val focus = assertNotNull(data.findFeat("Weapon Focus"))
        assertEquals(listOf("Bludgeoning", "Piercing", "Ranged", "Slashing", "Thrown"), focus.selectors.map { it.name })
        // Ranged has no section of its own, so it keeps the feat's ClassBonus; Bludgeoning sets its own.
        assertEquals(listOf("Artificer", "Fighter"), focus.findSelector("Ranged")?.classBonus)
        assertEquals(listOf("Fighter", "Dragon Lord"), focus.findSelector("Bludgeoning")?.classBonus)
        assertEquals(listOf("Human", "Purple Dragon Knight"), focus.raceBonus)

        val specialization = assertNotNull(data.findFeat("Weapon Specialization"))
        assertEquals(SelectorStyle.SHARED, specialization.selectorStyle)
        assertEquals(focus.selectors.map { it.name }, specialization.selectors.map { it.name })
        assertEquals(listOf(ClassLevel("Fighter", 4), ClassLevel("Dragon Lord", 4)), specialization.classes)

        assertEquals(438, data.featMap.size) // 439 lines, less the heading
        assertEquals(60, data.autoGrantedFeats.size)

        // Trees and destinies (values read from Enhancements.txt and Destinies.txt).
        assertEquals(94, data.trees.size)
        assertEquals(13, data.destinies.size)
        val kensei = assertNotNull(data.findTree("Kensei"))
        assertEquals(TreeStyle.CLASS, kensei.style)
        assertEquals(listOf("Ken", "Ken"), kensei.initials)
        assertEquals("Kensei enhancements", kensei.wiki)
        assertEquals(6, kensei.tiers[0].size)
        assertEquals(9, kensei.tiers[0][0].selectors.size)
        // Stats: Strength, Dexterity, Wisdom → a "Stat" ability with three selectors at the end of tiers 3 and 4.
        assertEquals(listOf("Strength", "Dexterity", "Wisdom"), kensei.tiers[3].last().selectors.map { it.name })
        var abilities = 0
        for (tree in data.trees) {
            for (tier in tree.tiers) {
                abilities += tier.size
            }
        }
        for (tree in data.destinies) {
            for (tier in tree.tiers) {
                abilities += tier.size
            }
        }
        // 2903 AbilityName lines, plus two automatic stat abilities per class/race-class/global tree with Stats.
        var statTrees = 0
        for (tree in data.trees) {
            if (tree.stats.isNotEmpty() && tree.style != TreeStyle.RACE) {
                statTrees++
            }
        }
        assertEquals(2903 + 2 * statTrees, abilities)

        val crusader = assertNotNull(data.findDestiny("Divine Crusader"))
        assertEquals("Crusader", crusader.abbreviation)
        assertEquals("Divine Crusader", crusader.wiki)

        // The one Rank3Cost line in the data (Protection from Evil and Good).
        var found = false
        for (tree in data.trees) {
            for (tier in tree.tiers) {
                for (ability in tier) {
                    if (ability.name == "Protection from Evil and Good") {
                        assertEquals(2, ability.rankDetails?.get(1)?.cost)
                        found = true
                    }
                }
            }
        }
        assertTrue(found)

        // Every SharedSelector feat ends up with selectors.
        for (feat in data.feats) {
            if (feat.selectorStyle == SelectorStyle.SHARED) {
                assertTrue(feat.selectors.isNotEmpty(), "${feat.name} has no selectors")
            }
        }
    }
}
