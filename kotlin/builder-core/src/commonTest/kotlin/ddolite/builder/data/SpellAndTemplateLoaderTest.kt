package ddolite.builder.data

import ddolite.builder.model.Stat
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpellAndTemplateLoaderTest {
    private fun messages(log: LoadLog): List<String> {
        val result = ArrayList<String>()
        for (issue in log.issues) {
            var text = issue.message
            if (issue.detail != null) {
                text = text + " / " + issue.detail
            }
            result.add(text)
        }
        return result
    }

    @Test
    fun spellsAreReadAndSortedOrdinally() {
        val log = LoadLog()
        val spells = loadSpells(
            MapDataSource.ofText(
                "Builder/Spells.txt" to
                    "﻿SpellName: Zap\nDescrip: Zaps\n\n" +
                    "SpellName: acid\nWikiName: Acid (spell)\nFlags: Rare\n\n" +
                    "SpellName: Bolt\nFlags: Shiny\nNonsense\n",
            ),
            log,
        )
        // Ordinal order: capitals before lower case, as VB's Option Compare Binary.
        assertEquals(listOf("Bolt", "Zap", "acid"), spells.map { it.name })
        val acid = spells[2]
        assertEquals("Acid (spell)", acid.wiki)
        assertTrue(acid.rare)
        assertEquals("Zaps", spells[1].description)
        assertEquals("Zap", spells[1].wiki) // the wiki name defaults to the spell name
        assertEquals(
            listOf("Spells.txt: Bolt has invalid Flag: Shiny", "Spells.txt: Bolt has invalid line: / Nonsense"),
            messages(log),
        )
    }

    @Test
    fun duplicateSpellIsAWarning() {
        val log = LoadLog()
        val spells = loadSpells(MapDataSource.ofText("Builder/Spells.txt" to "SpellName: A\n\nSpellName: A\n"), log)
        assertEquals(2, spells.size)
        assertEquals(1, log.issues.size)
        assertTrue(log.issues[0].vbDifference)
    }

    @Test
    fun templateFixedWidthColumns() {
        val log = LoadLog()
        val templates = loadTemplates(
            MapDataSource.ofText(
                "Builder/Templates.txt" to
                    "Class: Barbarian\nFlags: Traps, Always\nCaption: THF\nDescrip: Strength build\n" +
                    "Levelups: Strength\nPTS: 28 30 32 34 36\nSTR: 16 16 16 16 16\nDEX:  2  4     2  4\n" +
                    "CON: 10 10 16 16 16\n",
            ),
            log,
        )
        assertEquals(emptyList(), messages(log))
        val t = templates[0]
        assertEquals("Barbarian", t.className)
        assertTrue(t.trapping)
        assertTrue(t.always)
        assertEquals("THF", t.caption)
        assertEquals(Stat.STR, t.levelups)
        assertEquals(listOf(2, 4, 0, 2, 4), (0 until 5).map { t.points(it, Stat.DEX) })
        assertEquals(16, t.points(4, Stat.CON))
        assertEquals(0, t.points(0, Stat.WIS))
        assertEquals("Barbarian: THF (Traps)", t.logName)
    }

    @Test
    fun templateErrorsUseTheCaptionOnceKnown() {
        val log = LoadLog()
        loadTemplates(
            MapDataSource.ofText(
                "Builder/Templates.txt" to "Class: Fighter\nFlags: Sometimes\nCaption: TWF\nLevelups: Luck\n",
            ),
            log,
        )
        assertEquals(
            listOf(
                "Templates.txt: Fighter has invalid line: / Flags: Sometimes",
                "Templates.txt: Fighter: TWF has invalid line: / Levelups: Luck",
            ),
            messages(log),
        )
    }

    @Test
    fun templateTotalsAndValuesAreChecked() {
        val log = LoadLog()
        val templates = loadTemplates(
            MapDataSource.ofText(
                "Builder/Templates.txt" to
                    "Class: Fighter\nCaption: Odd\nSTR: 16 16 16 16 16\nDEX:  7 14 16 16 20\n",
            ),
            log,
        )
        checkTemplates(templates, log)
        val found = messages(log)
        assertTrue(found.contains("Templates.txt: Fighter: Odd has invalid stat point value / 28pt Dexterity: 7"))
        assertTrue(found.contains("Templates.txt: Fighter: Odd has invalid points total / 28pt build totals 23"))
        // 34 points: 16 + 16 = 32, both valid values, wrong total.
        assertTrue(found.contains("Templates.txt: Fighter: Odd has invalid points total / 34pt build totals 32"))
        assertFalse(found.contains("Templates.txt: Fighter: Odd has invalid stat point value / 34pt Dexterity: 16"))
    }

    @Test
    fun classSpellsMustExist() {
        val log = LoadLog()
        val data = loadBuilderData(
            MapDataSource.ofText(
                "Builder/Classes.txt" to "ClassName: Wizard\nBAB: 0.5\nFreeSpells: Zap, Missing\n",
                "Builder/Spells.txt" to "SpellName: Zap\n",
                "Builder/Templates.txt" to "Class: Juggler\nSTR:  8  8  8  8  8\nDEX:  8  8  8  8  8\nCON:  6  8 10 10 10\nINT:  6  6  6  8 10\n",
            ),
            log,
        )
        val found = messages(log)
        assertTrue(found.contains("Classes.txt: Wizard has invalid Free Spell: / Missing"), found.toString())
        assertTrue(found.contains("Templates.txt: Class not recognized: Juggler"), found.toString())
        assertEquals("Zap", data.findSpell("Zap")?.name)
        assertNull(data.findSpell("zap")) // exact match, as SeekSpell
    }
}
