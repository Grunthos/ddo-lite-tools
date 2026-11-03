package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.DirectoryDataSource
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Spells screen's rules ([SpellRules]) on the sample builds in Save/, with the real data. */
class SpellRulesTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())
    private val saveDir = File(System.getProperty("ddolite.saveDir"))

    private fun open(name: String): BuildRules = readBuildFile(File(saveDir, name).readBytes(), data, BuildPoints.ADVENTURER).rules

    @Test
    fun warlockPactSlotAfterTheWindowOpens() {
        val rules = open("Warlock Blaster.build")
        // As frmSpells.Form_Load: InitBuildSpells again, now that the feats are listed.
        rules.initBuildSpells()
        val sr = SpellRules(rules)
        val warlock = data.findClass("Warlock")!!
        assertEquals(listOf(warlock), sr.spellClasses())
        val level = sr.level(warlock, 1)!!
        val pact = level.slots[level.slots.size - 1]
        assertEquals(SpellSlotType.WARLOCK_PACT, pact.type)
        assertEquals("Pact @ " + pact.level, sr.levelText(pact))
        assertTrue(pact.spell.isNotEmpty())
        // The spells offered leave out the ones already in a slot.
        for (listed in sr.availableSpells(warlock, 1)) {
            assertTrue(!sr.spellTaken(level, listed.name))
        }
    }

    @Test
    fun rareScrollsAreIndented() {
        val rules = open("Bard Acolyte.build")
        val sr = SpellRules(rules)
        val wizard = data.findClass("Wizard")!!
        assertEquals("Acid Fog", sr.cleanSpellName("  Acid Fog*"))
        // A Wizard spell flagged rare in Spells.txt is listed "  Name*" (only when the build has the level).
        assertTrue(data.findSpell("Acid Fog")!!.rare)
        assertTrue(wizard.spellList(6).contains("Acid Fog"))
    }

    @Test
    fun freeSlotsAddAndRemove() {
        val rules = open("Warlock Blaster.build")
        rules.initBuildSpells()
        val sr = SpellRules(rules)
        val warlock = data.findClass("Warlock")!!
        val level = sr.level(warlock, 1)!!
        val before = level.slots.size
        sr.addFreeSpellSlot(warlock, 1)
        sr.addFreeSpellSlot(warlock, 1)
        assertEquals(before + 2, level.slots.size)
        assertEquals(SpellSlotType.FREE, level.slots[0].type)
        assertEquals(2, level.freeSlots)
        // VB quirk: of two empty free slots in a row, one pass removes only the first.
        sr.removeEmptyFreeSlots(warlock, 1)
        assertEquals(before + 1, level.slots.size)
        sr.removeEmptyFreeSlots(warlock, 1)
        assertEquals(before, level.slots.size)
        assertEquals(0, level.freeSlots)
    }

    @Test
    fun clearClassSpellsKeepsPactAndCureSlots() {
        val rules = open("Kensei Warpriest.build")
        rules.initBuildSpells()
        val sr = SpellRules(rules)
        val cleric = data.findClass("Cleric")!!
        sr.clearClassSpells(cleric)
        val level = sr.level(cleric, 1)!!
        for (slot in level.slots) {
            if (slot.type == SpellSlotType.STANDARD || slot.type == SpellSlotType.FREE) {
                assertEquals("", slot.spell)
            }
        }
        assertEquals("Cure Light Wounds", level.slots[0].spell)
    }

    @Test
    fun details() {
        val sr = SpellRules(open("Warlock Blaster.build"))
        val details = sr.details("  Acid Fog*")
        assertEquals("Acid Fog", details.caption)
        assertTrue(details.lines.last().text.endsWith("(Wiki)"))
        assertEquals("Spell not found.", sr.details("No Such Spell").lines[0].text)
        assertEquals("Details", sr.details("").caption)
    }
}
