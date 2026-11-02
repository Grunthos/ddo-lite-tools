package ddolite.builder.data

import ddolite.builder.model.Alignment
import ddolite.builder.model.Bab
import ddolite.builder.model.BonusFeatSlot
import ddolite.builder.model.ClassColor
import ddolite.builder.model.GrantedFeat
import ddolite.builder.model.RaceType
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loader behaviour on small made-up files. Expected messages follow VB's wording
 * (basErrorLog.bas), so they can later be compared with a VB6 error.log.
 */
class RaceAndClassLoaderTest {
    private fun races(text: String) = MapDataSource.ofText("Builder/Races.txt" to text)

    private fun classes(text: String) = MapDataSource.ofText("Builder/Classes.txt" to text)

    private fun messages(log: LoadLog): List<String> {
        val result = ArrayList<String>()
        for (issue in log.issues) {
            if (issue.detail == null) {
                result.add(issue.message)
            } else {
                result.add(issue.message + " " + issue.detail)
            }
        }
        return result
    }

    @Test
    fun readsARace() {
        val log = LoadLog()
        val loaded = loadRaces(
            races(
                """
                RaceName: Aasimar
                Type: Premium
                Stats: 8, 8, 8, 8, 10, 8
                Flags: BonusFeat
                GrantedFeats1: Healing Hands
                GrantedFeats4: Toughness, Dodge
                Trees: Aasimar
                """.trimIndent(),
            ),
            log,
        )
        assertEquals(emptyList(), messages(log))
        val race = loaded[0]
        assertEquals("Aasimar", race.name)
        assertEquals(16, race.vbIndex)
        assertEquals(RaceType.PREMIUM, race.type)
        assertEquals(10, race.stat(Stat.WIS))
        assertTrue(race.bonusFeat)
        assertEquals(
            listOf(GrantedFeat(1, "Healing Hands"), GrantedFeat(4, "Toughness"), GrantedFeat(4, "Dodge")),
            race.grantedFeats,
        )
        assertEquals(listOf("Aasimar"), race.trees)
    }

    @Test
    fun raceErrorsUseVbWording() {
        val log = LoadLog()
        loadRaces(
            races(
                """
                RaceName: Human
                Type: Expensive
                Stats: 8, 8, 8, 8, 8, 7
                Flags: Shiny
                Colour: Blue
                GrantedFeats99: Toughness

                RaceName: Nobody
                Type: Free
                """.trimIndent(),
            ),
            log,
        )
        assertEquals(
            listOf(
                "Races.txt: Human has invalid Type: Expensive",
                "Races.txt: Human has invalid line: Stats: 8, 8, 8, 8, 8, 7",
                "Races.txt: Human has invalid Flag: Shiny",
                "Races.txt: Human has invalid line: Colour: Blue",
                "Races.txt: Human has invalid line: GrantedFeats99: Toughness",
                "Invalid section in Races.txt ('Stats: ' not found): Nobody",
            ),
            messages(log),
        )
        assertEquals(2, log.issues[0].line) // "Type: Expensive" is line 2 of the file
    }

    @Test
    fun unknownRaceStillLoadsWithWarning() {
        val log = LoadLog()
        val loaded = loadRaces(races("RaceName: Kobold\nType: Free\nStats: 8, 8, 8, 8, 8, 8\n"), log)
        assertEquals(1, loaded.size)
        assertEquals(0, loaded[0].vbIndex)
        assertEquals(1, log.issues.size)
        assertTrue(log.issues[0].vbDifference)
    }

    @Test
    fun racesComeOutInVbOrder() {
        val log = LoadLog()
        val loaded = loadRaces(
            races("RaceName: Elf\nStats: 8, 10, 6, 8, 8, 8\n\nRaceName: Human\nStats: 8, 8, 8, 8, 8, 8\n"),
            log,
        )
        assertEquals("Human", loaded[0].name) // reHuman = 1 comes before reElf = 4
        assertEquals("Elf", loaded[1].name)
    }

    @Test
    fun readsAClass() {
        val log = LoadLog()
        val loaded = loadClasses(
            classes(
                """
                ClassName: Warlock
                Initial: W, Wa, Warlock, Wlk
                Color: Purple
                Alignment: Lawful Neutral, Chaotic Good
                BAB: 0.75
                SkillPoints: 2
                Skills: Bluff, Concentration, UMD
                BonusFeat: 4
                ClassFeat: 2, 3
                MaxSpellLevel: 6
                SpellSlots0: 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0
                SpellSlots1: 0, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5
                SpellList1: Burning Hands, Charm Person
                PactSpells: Fey, Obscuring Mist, Blindness
                Trapper: True
                """.trimIndent(),
            ),
            log,
        )
        assertEquals(emptyList(), messages(log))
        val c = loaded[0]
        assertEquals("Warlock", c.name)
        assertEquals(14, c.vbIndex)
        assertEquals(listOf("W", "Wa", "Warlock", "Wlk"), c.initials)
        assertEquals(ClassColor.PURPLE, c.color)
        assertEquals(setOf(Alignment.LAWFUL_NEUTRAL, Alignment.CHAOTIC_GOOD), c.alignments)
        assertEquals(Bab.THREE_QUARTERS, c.bab)
        assertEquals(setOf(Skill.BLUFF, Skill.CONCENTRATION, Skill.UMD), c.nativeSkills)
        assertEquals(BonusFeatSlot.BONUS, c.bonusFeatAt(4))
        assertEquals(BonusFeatSlot.CLASS_ONLY, c.bonusFeatAt(3))
        assertEquals(BonusFeatSlot.NONE, c.bonusFeatAt(1))
        assertEquals(6, c.maxSpellLevel)
        assertEquals(0, c.spellSlots(1, 1))
        assertEquals(2, c.spellSlots(2, 1))
        assertEquals(2, c.canCastSpell[1]) // first level with a level-1 slot
        assertEquals(0, c.canCastSpell[2])
        assertEquals(listOf("Burning Hands", "Charm Person"), c.spellList(1))
        assertEquals("Fey", c.pacts[0].name)
        assertEquals(listOf("Obscuring Mist", "Blindness"), c.pacts[0].spells)
        assertTrue(c.trapper)
    }

    @Test
    fun classErrorsUseVbWording() {
        val log = LoadLog()
        loadClasses(
            classes(
                """
                ClassName: Fighter
                BAB: 2
                Color: Mauve
                Alignment: Chaotic Stupid
                SkillPoints: 3
                Skills: Juggling
                Initial: F, Fi
                SpellSlots1: 1, 2
                """.trimIndent(),
            ),
            log,
        )
        assertEquals(
            listOf(
                "Classes.txt: Fighter has invalid BAB: 2",
                "Classes.txt: Fighter has invalid color: Mauve",
                "Classes.txt: Fighter has invalid aligment: Chaotic Stupid",
                "Classes.txt: Fighter has invalid skill points: 3",
                "Classes.txt: Fighter has invalid skill: Juggling",
                "Classes.txt: Fighter has invalid line: Initial: F, Fi",
                "Classes.txt: Fighter has SpellSlots1 line but MaxSpellLevel is 0",
            ),
            messages(log),
        )
    }

    @Test
    fun crossReferencesAreChecked() {
        val source = MapDataSource.ofText(
            "Builder/Races.txt" to "RaceName: Purple Dragon Knight\nStats: 8, 8, 8, 8, 8, 8\nIconicClass: Juggler\nSubRace: Martian\n",
            "Builder/Classes.txt" to "ClassName: Bard\nBAB: 0.75\nDisallowedClasses: Mime\n",
            "Builder/Spells.txt" to "",
            "Builder/Templates.txt" to "",
        )
        val log = LoadLog()
        val data = loadBuilderData(source, log)
        assertEquals(
            listOf(
                "Races.txt: Purple Dragon Knight has invalid IconicClass: Juggler",
                "Races.txt: Purple Dragon Knight has unknown SubRace: Martian (VB6 ignores it)",
                "Classes.txt: Bard has invalid DisallowedClass: Mime",
            ),
            messages(log),
        )
        assertNotNull(data.findClass("bard"))
        assertFalse(log.issues[0].vbDifference)
        assertTrue(log.issues[1].vbDifference)
    }

    @Test
    fun findRaceAcceptsVbAlternativeSpellings() {
        val log = LoadLog()
        val data = loadBuilderData(
            MapDataSource.ofText("Builder/Races.txt" to "RaceName: Duergar Mindcleaver\nStats: 9, 8, 9, 8, 8, 8\n"),
            log,
        )
        assertEquals("Duergar Mindcleaver", data.findRace("mindcleaver")?.name)
        assertEquals("Duergar Mindcleaver", data.findRace("DUERGAR MINDCLEAVER")?.name)
    }
}
