package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The leveling guide's rules ([GuideRules]) on Kensei Warpriest, from an empty guide; these tests
 * build one.
 */
class GuideRulesTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    /**
     * Kensei Warpriest with an empty guide: its saved guide's steps that the data no longer has load as
     * red rows, so the tests start from the guide's Delete All.
     */
    private fun open(): BuildRules {
        val rules = readBuildFile(SampleBuilds.file("old-data/Kensei Warpriest.build").readBytes(), data, BuildPoints.ADVENTURER).rules
        GuideRules(rules).clear()
        rules.initLevelingGuide()
        return rules
    }

    private fun guideTree(g: GuideRules, display: String): Int = g.treeList(false).first { it.text == display }.number

    @Test
    fun emptyGuide() {
        val rules = open()
        val g = GuideRules(rules)
        assertTrue(rules.build.guide.enhancements.isEmpty())
        val totals = g.totals()
        assertEquals("Level: 1 (Ftr 1)", totals.levelCaption())
        assertEquals("Ftr 2/Clr 1", g.classesAt(3))
        assertEquals("Ftr 12/Clr 8", g.classesAt(30))
        assertEquals("Available: 4 + 0r + 0u", totals.apCaption())
        assertEquals("Spent: 0 + 0r +0u", totals.spentCaption())
        assertEquals("(Commands)", g.treeList(false).last().text)
        assertTrue(g.treeList(true).size >= g.treeList(false).size)
        assertEquals(listOf("Bank 4 AP"), g.commands(4).map { it.text })
    }

    @Test
    fun levelsBanksResetsAndErrors() {
        val rules = open()
        val g = GuideRules(rules)
        val kensei = guideTree(g, "Kensei")
        // Kensei Focus (a selector) at level 1, then Spiritual Bond, which needs 5 AP in the tree.
        g.addAbility(kensei, 0, 0, 1, 1)
        g.addAbility(kensei, 0, 1, 0, 1)
        var steps = rules.guideEnhancements
        assertEquals(1, steps[0].level)
        assertEquals("", steps[0].errorText)
        assertEquals(steps[1].ml, steps[1].level)
        assertEquals("Requires 5 AP spent in tree, only 1 AP spent", steps[1].errorText)
        // Rank 3 of a tier 1 ability adds ranks 1 to 3.
        g.addAbility(kensei, 1, 0, 0, 3)
        steps = rules.guideEnhancements
        assertEquals(5, steps.size)
        assertEquals(listOf(1, 2, 3), listOf(steps[2].rank, steps[3].rank, steps[4].rank))
        assertEquals(" III", steps[4].rankText)
        // Bank what's left at that level; the next step comes a level later.
        g.addCommand(200)
        steps = rules.guideEnhancements
        val bank = steps[5]
        assertEquals(GuideStepStyle.BANK_AP, bank.style)
        assertEquals(bank.level * 4 - bank.spent, bank.bank)
        assertEquals("Bank " + bank.bank + " AP", bank.display)
        // Reset the tree: a level after the step before, and its AP back.
        g.addCommand(100 + rules.guideTrees[kensei - 1].buildGuideTree)
        steps = rules.guideEnhancements
        val reset = steps[6]
        assertEquals(GuideStepStyle.RESET_TREE, reset.style)
        assertEquals(bank.level + 1, reset.level)
        assertEquals("Reset Kensei", reset.display)
        assertEquals(0, reset.spent)
        assertTrue(g.commands(4).map { it.text }.containsAll(listOf("Reset Kensei", "Reset all trees")))
        // The file writes and reads it back the same.
        val saved = writeBuildFile(rules)
        val reread = readBuildFile(saved, data, BuildPoints.ADVENTURER)
        assertEquals(rules.build.guide.enhancements.size, reread.build.guide.enhancements.size)
        assertEquals(steps.map { it.level }, reread.rules.guideEnhancements.map { it.level })
    }

    @Test
    fun rankRequiresTheRankBefore() {
        val rules = open()
        val g = GuideRules(rules)
        val kensei = guideTree(g, "Kensei")
        g.addAbility(kensei, 0, 0, 1, 1)
        g.addAbility(kensei, 1, 0, 0, 2)
        // Move rank 2 above rank 1.
        val moved = g.insertRows(listOf(false, false, true), 2)
        assertEquals(Pair(2, 1), moved)
        rules.initLevelingGuide()
        assertEquals(2, rules.guideEnhancements[1].rank)
        assertEquals("Rank 2 requires first taking Rank 1", rules.guideEnhancements[1].errorText)
        g.deleteRows(listOf(false, true, false))
        rules.initLevelingGuide()
        assertEquals(2, rules.guideEnhancements.size)
        g.deleteToEnd(1)
        rules.initLevelingGuide()
        assertTrue(rules.build.guide.enhancements.isEmpty())
        assertTrue(rules.build.guide.trees.isEmpty())
    }

    @Test
    fun laterRanksKeepTheFirstRanksSelector() {
        val rules = open()
        val g = GuideRules(rules)
        val kensei = guideTree(g, "Kensei")
        val tree = rules.guideTrees[kensei - 1].tree!!
        // The first ability with more than one rank and a choice of selectors.
        var tier = -1
        var ability = -1
        for (t in tree.tiers.indices) {
            for (a in tree.tiers[t].indices) {
                val data = tree.tiers[t][a]
                if (tier == -1 && data.ranks > 1 && data.selectors.size > 1 && g.needsSelector(kensei, t, a)) {
                    tier = t
                    ability = a
                }
            }
        }
        assertTrue(tier >= 0, "a multi-rank ability with selectors")
        // Before rank I is taken, rank II asks for a selector as VB does.
        for (c in g.availableAbilities(kensei, GuideFilter.ALL_ENHANCEMENTS, 20)) {
            if (c.tier == tier && c.ability == ability) {
                assertEquals(0, c.selector)
            }
        }
        g.addAbility(kensei, tier, ability, 2, 1)
        var later = 0
        for (c in g.availableAbilities(kensei, GuideFilter.ALL_ENHANCEMENTS, 20)) {
            if (c.tier == tier && c.ability == ability) {
                assertTrue(c.rank > 1)
                assertEquals(2, c.selector, "rank " + c.rank + " keeps rank I's selector")
                later++
            }
        }
        assertTrue(later > 0, "the later ranks are offered")
    }

    @Test
    fun nothingAvailableSaysWhy() {
        val rules = open()
        val g = GuideRules(rules)
        // The build's first Cleric level is 3, so a Cleric tree has nothing at levels 1 and 2.
        var cleric = 0
        for (t in g.treeList(false)) {
            if (t.number != 0 && rules.guideTrees[t.number - 1].characterClass?.name == "Cleric") {
                cleric = t.number
                break
            }
        }
        assertTrue(cleric > 0, "a Cleric tree")
        assertTrue(g.availableAbilities(cleric, GuideFilter.ALL_AVAILABLE, 1).isEmpty())
        val name = rules.guideTrees[cleric - 1].display
        assertEquals("Nothing in $name at level 1: the earliest is level 3.", g.nothingAvailableMessage(cleric, 1))
    }

    @Test
    fun filters() {
        val rules = open()
        val g = GuideRules(rules)
        val kensei = guideTree(g, "Kensei")
        val all = g.availableAbilities(kensei, GuideFilter.ALL_ENHANCEMENTS, 1)
        // VB quirk: "Selected and Available" lists everything, as "Show All".
        assertEquals(all.map { it.text }, g.availableAbilities(kensei, GuideFilter.SELECTED_AVAILABLE, 1).map { it.text })
        val selected = g.availableAbilities(kensei, GuideFilter.ALL_SELECTED, 1)
        assertTrue(selected.size < all.size)
        assertTrue(selected.any { it.text.startsWith("0: Kensei Focus: ") }) // the build's selector
        val available = g.availableAbilities(kensei, GuideFilter.ALL_AVAILABLE, 1)
        assertTrue(available.size < all.size)
        // A taken rank isn't offered again.
        g.addAbility(kensei, 0, 0, 1, 1)
        assertEquals(all.size - 1, g.availableAbilities(kensei, GuideFilter.ALL_ENHANCEMENTS, 1).size)
        val details = g.details(kensei, 0, 0, 0, 1, 0)!!
        assertEquals("Tier 0: Kensei Focus", details.caption)
        assertEquals("Req: Lvl 1", details.requirement?.text)
        assertEquals("Cost: 1 AP", details.cost)
    }
}
