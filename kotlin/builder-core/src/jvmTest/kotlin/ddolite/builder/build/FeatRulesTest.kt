package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The feat rules ([FeatRules]) on the sample builds in Save/, with the real data. */
class FeatRulesTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun open(name: String): BuildRules = readBuildFile(SampleBuilds.file(name).readBytes(), data, BuildPoints.ADVENTURER).rules

    private fun slot(rules: BuildRules, level: Int, source: String): FeatListEntry {
        for (entry in rules.featList) {
            if (entry.level == level && entry.sourceForm == source && entry.actualType != BuildFeatType.GRANTED) {
                return entry
            }
        }
        throw AssertionError("No $source slot at level $level")
    }

    @Test
    fun kenseiSlotsSourcesAndTabs() {
        val rules = open("Kensei Warpriest.build")
        assertEquals("Least Dragonmark: Passage", slot(rules, 1, "Human").display)
        assertEquals("Cleave", slot(rules, 1, "Fighter").display)
        assertEquals("Strength Domain", slot(rules, 5, "Cleric").display)
        // Class slots show the class's third initial, which for these classes is the whole name;
        // the list title uses the same, with the class level.
        assertEquals("Fighter 1", slot(rules, 1, "Fighter").sourceFilter)
        assertEquals("Level 3", slot(rules, 3, "Heroic").sourceFilter)
        assertEquals("Legend 30", slot(rules, 30, "Legendary").sourceFilter)
        assertEquals(FeatTab.CLERIC, slot(rules, 5, "Cleric").tab)
        assertEquals(FeatTab.DEITY, slot(rules, 3, "Deity").tab)
        assertEquals(27, rules.featTabCounts[FeatTab.SELECTED.ordinal])
        assertEquals(1, rules.featTabCounts[FeatTab.CLERIC.ordinal])
        assertEquals(2, rules.featTabCounts[FeatTab.DEITY.ordinal])
        assertEquals("Domain", FeatTab.CLERIC.caption(rules.build.race))
        for (entry in rules.featList) {
            assertTrue(!entry.errorState, entry.display + ": " + entry.errorText)
        }
    }

    @Test
    fun slotChecks() {
        val rules = open("Kensei Warpriest.build")
        val fr = FeatRules(rules)
        val heroic3 = slot(rules, 3, "Heroic").slot
        // A destiny feat can't go in a heroic slot.
        val check = fr.checkFeatSlot(data.findFeat("Epic Mage Armor")!!, heroic3)
        assertEquals(DropState.DEFAULT, check.state)
        assertEquals("Can't be taken as a Heroic feat.", check.error)
        // A selector feat needs a selector, unless the check is only filtering the list.
        val weaponFocus = data.findFeat("Weapon Focus")!!
        val empty = heroic3.copy()
        empty.selector = 0
        assertEquals("A selector is required but wasn't specified.", fr.checkFeatSlot(weaponFocus, empty).error)
        assertTrue(fr.checkFeatSlot(weaponFocus, empty, selectorRequired = false).state != DropState.DEFAULT)
        // Great Cleave needs Cleave and Power Attack, which the build has by level 6.
        val heroic6 = slot(rules, 6, "Heroic").slot
        assertEquals(DropState.CAN_DROP, fr.checkFeatSlot(data.findFeat("Great Cleave")!!, heroic6).state)
    }

    @Test
    fun takenFeats() {
        val rules = open("Kensei Warpriest.build")
        val fr = FeatRules(rules)
        val all = fr.identifyTakenFeats(0)
        assertEquals(1, all.of(data.findFeat("Power Attack")).times)
        val weaponFocus = data.findFeat("Weapon Focus")!!
        assertTrue(all.selectorTaken(weaponFocus, selectorNumber(weaponFocus, "Slashing")))
        // By level 2, Two Handed Fighting (level 15) isn't taken yet.
        assertEquals(0, fr.identifyTakenFeats(2).of(data.findFeat("Two Handed Fighting")).times)
    }

    @Test
    fun availableFeatsForAnEmptyDestinySlot() {
        val rules = open("Kensei Warpriest.build")
        val fr = FeatRules(rules)
        val destiny22 = slot(rules, 22, "Destiny")
        assertNull(destiny22.feat)
        val offered = fr.availableFeats(FeatTab.SELECTED, emptySet(), destiny22, false, destiny22.level, false)
        assertTrue(offered.isNotEmpty())
        for (feat in offered) {
            assertTrue(fr.checkFeatSlot(feat, destiny22.slot, selectorRequired = false).state != DropState.DEFAULT, feat.name)
        }
    }

    @Test
    fun alternatesAndExchanges() {
        val rules = open("Kensei Warpriest.build")
        val fr = FeatRules(rules)
        val before = rules.featList.size
        val heroic1 = slot(rules, 1, "Heroic")
        val position = fr.addSpecialFeat(BuildFeatType.ALTERNATE, heroic1.actualType, heroic1.index, 0)
        assertEquals(before + 1, rules.featList.size)
        assertEquals("Alternate", rules.featList[position].sourceForm)
        assertEquals(1, rules.featList[position].level)
        // Exchanging Power Attack at level 9 is locked: Cleave (level 1) and Great Cleave (6) need it.
        val pa = slot(rules, 1, "Heroic")
        val exchange = fr.addSpecialFeat(BuildFeatType.EXCHANGE, pa.actualType, pa.index, 9)
        val locked = fr.slotLocked(exchange)
        assertNotNull(locked)
        assertTrue(locked.startsWith("Power Attack is locked by "), locked)
        assertTrue(rules.featList[exchange].errorState)
        fr.deleteFeatSlot(BuildFeatType.EXCHANGE, 0)
        assertEquals(before + 1, rules.featList.size)
    }

    @Test
    fun addSpecialFeatMovesBackOnlyOnePlace() {
        // VB quirk: the insertion compares shifted entries, so the new slot moves back at most one place.
        val rules = open("Kensei Warpriest.build")
        val fr = FeatRules(rules)
        val heroic9 = slot(rules, 9, "Heroic")
        val heroic3 = slot(rules, 3, "Heroic")
        val heroic1 = slot(rules, 1, "Heroic")
        // The file has one alternate, at level 30.
        fr.addSpecialFeat(BuildFeatType.ALTERNATE, heroic9.actualType, heroic9.index, 0)
        fr.addSpecialFeat(BuildFeatType.ALTERNATE, heroic3.actualType, heroic3.index, 0)
        fr.addSpecialFeat(BuildFeatType.ALTERNATE, heroic1.actualType, heroic1.index, 0)
        val levels = ArrayList<Int>()
        for (alternate in rules.build.feats(BuildFeatType.ALTERNATE)) {
            levels.add(alternate.level)
        }
        assertEquals(listOf(9, 3, 1, 30), levels)
    }

    @Test
    fun detailsLines() {
        val rules = open("Kensei Warpriest.build")
        val details = FeatRules(rules).details(data.findFeat("Great Cleave"), 0, null)
        assertEquals("Great Cleave", details.caption)
        val texts = details.lines.map { it.text }
        assertTrue(texts.contains("Requires all of:"), texts.toString())
        assertTrue(details.lines.any { it.link != null && it.text.endsWith("(Wiki)") })
        // userDetails drops blank lines at the ends.
        assertTrue(texts.first().isNotEmpty() && texts.last().isNotEmpty())
    }

    @Test
    fun detailsBuilderBullets() {
        val builder = DetailsBuilder()
        builder.addDescrip("First part}{*Second}", "https://example")
        builder.addErrorText("Oops")
        val lines = builder.build()
        assertEquals(listOf("First part", " * Second", " (Wiki)", "", "", "Oops"), lines.map { it.text })
        assertTrue(lines[5].error)
    }
}
