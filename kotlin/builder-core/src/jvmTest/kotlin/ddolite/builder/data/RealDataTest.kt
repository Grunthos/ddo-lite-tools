package ddolite.builder.data

import ddolite.builder.model.Bab
import ddolite.builder.model.ClassLevel
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.TreeStyle
import ddolite.builder.model.AbilityTarget
import ddolite.builder.model.Requirement
import ddolite.builder.model.Requirements
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
                // Requirements that never match anything (VB6 resolves them to -1 / 0 without a word):
                // there's no Tier 2 Halfling Guile; the feat is called "Simple Thrown Expertise".
                "Enhancements.txt: Enhancement: Halfling Tier 3: Halfling Guile : requirement's ability not found: Tier 2: Halfling Guile (VB6 doesn't report this)",
                "Enhancements.txt: Enhancement: Vile Chemist Tier 4: Simple Thrown Mastery : requirement's feat not found: Feat: Simple Thrown Weapon Expertise (VB6 doesn't report this)",
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

        // Data/Tomes.txt and NameChange.txt.
        assertEquals(3, data.tomes.racialApMax)
        assertEquals(4, data.tomes.universalApMax)
        assertEquals(27, data.tomes.permDestinyPointMax)
        assertEquals(12, data.tomes.permDestinyPointMin)
        assertEquals(3, data.tomes.fateTomeMax)
        assertEquals(3, data.nameChanges.size)
        assertEquals("Purity of Heart", data.findNameChange("Feat", "Purity of Spirit")?.newName)

        // Every requirement and link resolves, except the two known data problems.
        val unresolved = ArrayList<String>()
        var resolvedCount = 0
        fun check(req: Requirement) {
            if (req.target == null) {
                unresolved.add(req.raw)
            } else {
                resolvedCount++
            }
        }
        fun checkAll(reqs: Requirements) {
            for (list in listOf(reqs.all, reqs.one, reqs.none)) {
                for (req in list) {
                    check(req)
                }
            }
        }
        for (feat in data.feats) {
            checkAll(feat.requirements)
            for (selector in feat.selectors) {
                checkAll(selector.requirements)
            }
        }
        for (tree in data.trees + data.destinies) {
            for (tier in tree.tiers) {
                for (ability in tier) {
                    checkAll(ability.requirements)
                    val parent = ability.parent
                    if (parent != null) {
                        check(parent)
                    }
                    for (sibling in ability.siblings) {
                        check(sibling)
                    }
                    for (detail in ability.rankDetails ?: emptyList()) {
                        checkAll(detail.requirements)
                    }
                    for (selector in ability.selectors) {
                        checkAll(selector.requirements)
                    }
                }
            }
        }
        assertEquals(listOf("Tier 2: Halfling Guile", "Feat: Simple Thrown Weapon Expertise"), unresolved)
        assertTrue(resolvedCount > 1000, "only $resolvedCount resolved")

        // A requirement in another tree, with a rank (Enhancements.txt: "Arcane Archer Tier 1: Energy of the Wild Rank 3").
        var crossTree: AbilityTarget? = null
        for (tree in data.trees) {
            for (tier in tree.tiers) {
                for (ability in tier) {
                    val lists = ArrayList<Requirements>()
                    lists.add(ability.requirements)
                    for (selector in ability.selectors) {
                        lists.add(selector.requirements) // it's in selector sections in the data
                    }
                    for (reqs in lists) {
                        for (req in reqs.none + reqs.one + reqs.all) {
                            if (req.raw == "Arcane Archer Tier 1: Energy of the Wild Rank 3") {
                                crossTree = req.target as AbilityTarget?
                            }
                        }
                    }
                }
            }
        }
        val target = assertNotNull(crossTree)
        assertEquals("Arcane Archer", target.treeName)
        assertEquals(1, target.tier)
        assertEquals("Energy of the Wild", target.abilityName)
        assertEquals(3, target.rank)

        // Every SharedSelector feat ends up with selectors.
        for (feat in data.feats) {
            if (feat.selectorStyle == SelectorStyle.SHARED) {
                assertTrue(feat.selectors.isNotEmpty(), "${feat.name} has no selectors")
            }
        }
    }
}
