package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.TreeStyle
import ddolite.dataformat.DirectoryDataSource
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The enhancement rules ([EnhancementRules]) on the sample builds in Save/, with the real data. */
class EnhancementRulesTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())
    private val saveDir = File(System.getProperty("ddolite.saveDir"))

    private fun open(name: String): BuildRules = readBuildFile(File(saveDir, name).readBytes(), data, BuildPoints.ADVENTURER).rules

    private fun buildTree(rules: BuildRules, name: String): BuildTree = rules.build.trees[rules.findBuildTree(name) - 1]

    @Test
    fun apTotalsUseRacialAndUniversalPoolsFirst() {
        val rules = open("Warlock Blaster.build")
        val totals = EnhancementRules(rules).apTotals()
        // Tabaxi (racial) 13 AP from the racial pool; Feydark Illusionist (global) 2 from the universal pool.
        assertEquals("Spent: 65 +13r +2u / Max: 80 +18r +2u AP", totals.caption())
        assertTrue(!totals.overspent)
    }

    @Test
    fun spentInTreeIsCumulativeByTier() {
        val rules = open("Warlock Blaster.build")
        val er = EnhancementRules(rules)
        val spend = er.spentInTree(data.findTree("Soul Eater"), buildTree(rules, "Soul Eater"))
        assertEquals(30, spend.total)
        assertEquals(30, spend.byTier[6])
        for (tier in 1..6) {
            assertTrue(spend.byTier[tier] >= spend.byTier[tier - 1])
        }
        assertEquals(30, er.quickSpentInTree(rules.findBuildTree("Soul Eater")))
    }

    @Test
    fun requirementTables() {
        val er = EnhancementRules(open("Warlock Blaster.build"))
        // Class trees: core 4 needs 20 AP spent and 12 class levels; tier 5 needs 30 AP and 12 character levels.
        assertEquals(20, er.spentReq(TreeStyle.CLASS, 0, 4))
        assertEquals(Pair(0, 12), er.levelReqs(TreeStyle.CLASS, 0, 4))
        assertEquals(30, er.spentReq(TreeStyle.CLASS, 5, 1))
        assertEquals(Pair(12, 5), er.levelReqs(TreeStyle.CLASS, 5, 1))
        // Racial trees: tier 4 needs 15 AP; core 2 needs 4 character levels.
        assertEquals(15, er.spentReq(TreeStyle.RACE, 4, 1))
        assertEquals(Pair(4, 0), er.levelReqs(TreeStyle.RACE, 0, 2))
        // Class tree cores count as the tier of their number; the first core as tier 0.
        assertEquals(0, er.spentTier(0, 1, TreeStyle.CLASS))
        assertEquals(3, er.spentTier(0, 3, TreeStyle.CLASS))
        assertEquals(0, er.spentTier(0, 3, TreeStyle.RACE))
    }

    @Test
    fun abilityErrors() {
        // Kensei Warpriest was saved with older data: renamed abilities are dropped when it's read,
        // so the tree no longer has the AP its tier 5 abilities need.
        val rules = open("Kensei Warpriest.build")
        val er = EnhancementRules(rules)
        val tree = data.findTree("Kensei")!!
        val bt = buildTree(rules, "Kensei")
        val spend = er.spentInTree(tree, bt)
        val last = bt.abilities[bt.abilities.size - 1]
        assertEquals(5, last.tier)
        assertEquals("Not enough spent in tree", er.abilityError(tree, bt, last, spend))
        assertNull(er.abilityError(tree, bt, bt.abilities[0], spend))
    }

    @Test
    fun maxTierAndTier5() {
        val rules = open("Warlock Blaster.build")
        val er = EnhancementRules(rules)
        assertEquals(5, er.maxTier(buildTree(rules, "Soul Eater")))
        assertEquals(4, er.maxTier(buildTree(rules, "Tabaxi")))
        // Moving the tier 5 tree removes the old tree's tier 5 abilities.
        val before = buildTree(rules, "Soul Eater").abilities.size
        er.setTier5("Tainted Scholar")
        assertEquals("Tainted Scholar", rules.build.tier5)
        val soulEater = buildTree(rules, "Soul Eater")
        assertTrue(soulEater.abilities.size < before)
        for (a in soulEater.abilities) {
            assertTrue(a.tier < 5)
        }
    }

    @Test
    fun insertionPointKeepsTierOrder() {
        val rules = open("Warlock Blaster.build")
        val er = EnhancementRules(rules)
        val bt = buildTree(rules, "Soul Eater")
        val at = er.insertionPoint(bt, 2, 0)
        for (i in 0 until at) {
            assertTrue(bt.abilities[i].tier <= 2)
        }
        for (i in at until bt.abilities.size) {
            assertTrue(bt.abilities[i].tier >= 2)
        }
        bt.abilities.add(at, BuildAbility(0, BLANK_ABILITY, 0, 0))
        er.removeBlanks(bt)
        for (a in bt.abilities) {
            assertTrue(a.ability != BLANK_ABILITY)
        }
    }

    @Test
    fun availableAbilitiesAreNotTakenAndFit() {
        val rules = open("Warlock Blaster.build")
        val er = EnhancementRules(rules)
        val tree = data.findTree("Soul Eater")!!
        val bt = buildTree(rules, "Soul Eater")
        val offered = er.availableAbilities(tree, bt, showAll = false)
        assertTrue(offered.isNotEmpty())
        val spend = er.spentInTree(tree, bt)
        for (item in offered) {
            assertTrue(!er.abilityTaken(bt, item.first, item.second))
            assertNull(er.abilityError(tree, bt, BuildAbility(item.first, item.second, 0, 0), spend))
        }
        assertTrue(er.availableAbilities(tree, bt, showAll = true).size >= offered.size)
    }

    @Test
    fun details() {
        val rules = open("Warlock Blaster.build")
        val er = EnhancementRules(rules)
        val tree = data.findTree("Soul Eater")!!
        val details = er.details(tree, buildTree(rules, "Soul Eater"), 0, 1, 0, null)
        assertEquals("Tier 0: " + tree.tiers[0][1].name, details.caption)
        assertEquals("Ranks: " + tree.tiers[0][1].ranks, details.ranks)
        assertTrue(details.cost.startsWith("Cost: "))
        assertTrue(details.lines.any { it.text.endsWith("(Wiki)") && it.link!!.endsWith("#Core_abilities") })
    }
}
