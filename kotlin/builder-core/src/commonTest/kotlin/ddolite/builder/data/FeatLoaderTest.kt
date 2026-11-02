package ddolite.builder.data

import ddolite.builder.model.Alignment
import ddolite.builder.model.ClassLevel
import ddolite.builder.model.Feat
import ddolite.builder.model.FeatChannel
import ddolite.builder.model.FeatGroup
import ddolite.builder.model.PointerStyle
import ddolite.builder.model.RaceRequirementMode
import ddolite.builder.model.Requirement
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatLoaderTest {
    private val racesText = "RaceName: Human\nStats: 8, 8, 8, 8, 8, 8\n\nRaceName: Elf\nStats: 8, 8, 8, 8, 8, 8\n"
    private val classesText =
        "ClassName: Fighter\nBAB: 1\n\nClassName: Monk\nBAB: 0.75\n\nClassName: Favored Soul\nBAB: 0.75\n\n" +
            "ClassName: Artificer\nBAB: 0.75\n\nClassName: Dragon Lord\nBAB: 1\n"

    private fun load(featsText: String): Pair<List<Feat>, LoadLog> {
        val log = LoadLog()
        val source = MapDataSource.ofText("Builder/Races.txt" to racesText, "Builder/Classes.txt" to classesText, "Builder/Feats.txt" to featsText)
        val races = loadRaces(source, log)
        val classes = loadClasses(source, log)
        val feats = processFeatSelectors(loadFeats(source, races, classes, log), log)
        return Pair(feats, log)
    }

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
    fun basicFields() {
        val (feats, log) = load(
            "FeatName: Power Attack\nGroup: Heroic, Melee\nAbbreviation: PA\nSortName: Attack, Power\n" +
                "Descrip: Hit harder\nWikiName: Power Attack (feat)\nBAB: 1\nStat: Strength 13\nSkill: Jump 2\n" +
                "Level: 3\nRepeat: 3\nCanCastSpell: 0\nClass: Fighter 4, Monk 2\nGrantedBy: Favored Soul 3\n" +
                "Race: Required, Human, Elf\nAll: Dodge, Mobility\nNone: Toughness\n",
        )
        assertEquals(emptyList(), messages(log))
        val f = feats[0]
        assertEquals("PA", f.abbreviation)
        // VB quirk: a comma makes ParseLine return a list, leaving the item (and so SortName) empty.
        assertEquals("", f.sortName)
        assertEquals("Power Attack (feat)", f.wiki)
        assertEquals(setOf(FeatGroup.HEROIC, FeatGroup.MELEE), f.groups)
        assertEquals(1, f.bab)
        assertEquals(Stat.STR, f.stat)
        assertEquals(13, f.statValue)
        assertEquals(Skill.JUMP, f.skill)
        assertEquals(3, f.level)
        assertEquals(3, f.times)
        assertTrue(f.canCastSpell)
        assertEquals(0, f.canCastSpellLevel)
        assertEquals(listOf(ClassLevel("Fighter", 4), ClassLevel("Monk", 2)), f.classes)
        assertEquals(ClassLevel("Favored Soul", 3), f.grantedBy)
        assertEquals(RaceRequirementMode.REQUIRED, f.race.mode)
        assertEquals(listOf("Human", "Elf"), f.race.races)
        assertEquals(listOf(Requirement("Feat: Dodge", PointerStyle.FEAT), Requirement("Feat: Mobility", PointerStyle.FEAT)), f.requirements.all)
        assertEquals(listOf(Requirement("Feat: Toughness", PointerStyle.FEAT)), f.requirements.none)
        assertEquals(FeatChannel.GENERAL, f.channel)
        assertTrue(f.selectable)
        assertNull(f.classBonus) // no ClassBonus line
    }

    @Test
    fun recordWithoutGroupIsSkipped() {
        val (feats, log) = load("FeatName: Lost\nDescrip: no group\n\nFeatName: Found\nGroup: Misc\n")
        assertEquals(listOf("Found"), feats.map { it.name })
        assertEquals(listOf("Invalid section in Feats.txt ('Group: ' not found): / Lost"), messages(log))
    }

    @Test
    fun selectorsCopyTheFeatAsItIsAtTheSelectorLine() {
        // ClassBonus comes before Selector, so the selectors get it; Skill comes after, so they don't.
        val (feats, log) = load(
            "FeatName: Weapon Focus\nGroup: Melee\nClassBonus: Artificer, Fighter\nSelector: Slashing, Ranged, All\n" +
                "Skill: Swim 1\nSelectorName: Slashing\nClassBonus: Fighter, Dragon Lord\nDescrip: Swords\n",
        )
        assertEquals(emptyList(), messages(log))
        val f = feats[0]
        assertEquals(SelectorStyle.ROOT, f.selectorStyle)
        assertEquals(listOf("Artificer", "Fighter"), f.classBonus)
        assertEquals(Skill.SWIM, f.skill)
        val slashing = assertNotNull(f.findSelector("Slashing"))
        assertEquals(listOf("Fighter", "Dragon Lord"), slashing.classBonus)
        assertEquals("Swords", slashing.description)
        assertNull(slashing.skill)
        val ranged = assertNotNull(f.findSelector("Ranged"))
        assertEquals(listOf("Artificer", "Fighter"), ranged.classBonus)
        assertTrue(assertNotNull(f.findSelector("All")).all)
        assertFalse(ranged.all)
    }

    @Test
    fun sharedSelectorsAreCopiedFromTheParent() {
        val (feats, log) = load(
            "FeatName: Weapon Focus\nGroup: Melee\nSelector: Slashing, Piercing\n\n" +
                "FeatName: Greater Weapon Focus\nGroup: Melee\nSharedSelector: Weapon Specialization\nClassBonus: Fighter\nSkill: Swim 1\n\n" +
                "FeatName: Weapon Specialization\nGroup: Melee\nSharedSelector: Weapon Focus\n\n" +
                "FeatName: Broken\nGroup: Melee\nSharedSelector: Nothing\n",
        )
        // Greater Weapon Focus → Weapon Specialization (no selectors yet) → Weapon Focus.
        val greater = feats[1]
        assertEquals(SelectorStyle.SHARED, greater.selectorStyle)
        assertEquals(listOf("Slashing", "Piercing"), greater.selectors.map { it.name })
        assertEquals(listOf("Fighter"), greater.selectors[0].classBonus)
        assertEquals(Skill.SWIM, greater.selectors[0].skill)
        assertEquals(listOf("Slashing", "Piercing"), feats[2].selectors.map { it.name })
        assertEquals(listOf("Feat: Broken / Invalid parent selector: Nothing"), messages(log))
    }

    @Test
    fun selectorSectionFields() {
        val (feats, log) = load(
            "FeatName: Follower of\nGroup: Misc\nFlags: Deity\nSelector: Silver Flame, Sovereign Host\n" +
                "SelectorName: Silver Flame\nAlignment: Lawful Good, Neutral Good\nNotClass: Monk\nHide\n" +
                "NotRace: Elf\nRaceOnly: Human\nClassOnly: Favored Soul\nRace: NotAllowed, Elf\nClass: Fighter 1\n" +
                "SelectorName: Missing\nDescrip: no such selector\n",
        )
        val f = feats[0]
        assertEquals(FeatChannel.DEITY, f.channel)
        val silver = assertNotNull(f.findSelector("Silver Flame"))
        assertEquals(listOf(Alignment.LAWFUL_GOOD, Alignment.NEUTRAL_GOOD), silver.alignments)
        assertEquals("Monk", silver.notClass)
        assertEquals(listOf("Elf"), silver.notRaces)
        assertEquals("Human", silver.raceOnly)
        assertEquals("Favored Soul", silver.classOnly)
        assertEquals(RaceRequirementMode.NOT_ALLOWED, silver.race.mode)
        assertEquals(listOf(ClassLevel("Fighter", 1)), silver.classes)
        // "Hide" has no colon, so VB's ParseLine rejects it: hide stays false and the line is reported.
        assertFalse(silver.hide)
        assertNull(assertNotNull(f.findSelector("Sovereign Host")).alignments)
        assertEquals(
            listOf(
                "Feats.txt: Follower of SelectorName: Silver Flame has invalid line: / Hide",
                "Feats.txt: Follower of SelectorName: Missing has invalid line: / SelectorName: Missing",
            ),
            messages(log),
        )
    }

    @Test
    fun invalidValuesAreReported() {
        val (_, log) = load(
            "FeatName: Odd\nGroup: Melee, Bard\nBAB: 21\nRepeat: 4\nStat: Luck 2\nClass: Juggler 3, Fighter 30\n" +
                "RaceBonus: Martian\nFlags: Shiny\nRace: Sometimes, Human\n",
        )
        assertEquals(
            listOf(
                "Feats.txt: Odd has invalid group: Bard",
                "Feats.txt: Odd has invalid line: / BAB: 21",
                "Feats.txt: Odd has invalid line: / Repeat: 4",
                "Feats.txt: Odd has invalid line: / Stat: Luck 2",
                "Feats.txt: Odd has invalid line: / Class: Juggler 3, Fighter 30",
                "Feats.txt: Odd / RaceBonus includes invalid race: Martian",
                "Feats.txt: Odd has invalid line: / Flags: Shiny",
                "Feats.txt: Odd has invalid line: / Race: Sometimes, Human",
            ),
            messages(log),
        )
    }

    @Test
    fun channels() {
        val (feats, _) = load(
            "FeatName: A\nGroup: Misc\nFlags: Domain\n\nFeatName: B\nGroup: Misc\nFlags: Pact\n\n" +
                "FeatName: C\nGroup: Misc\nFlags: RaceOnly\n\nFeatName: D\nGroup: Misc\nFlags: ClassOnly\nClassOnlyClass: Monk\n\n" +
                "FeatName: E\nGroup: Misc\nFlags: ClassOnly\nClassOnlyClass: Favored Soul\nClassOnlyLevel: 2, 5\n\n" +
                "FeatName: F\nGroup: Misc\nFlags: ClassOnly\nClassOnlyClass: Favored Soul\nClassOnlyLevel: 2\n",
        )
        assertEquals(
            listOf(FeatChannel.CLERIC, FeatChannel.WARLOCK, FeatChannel.RACIAL, FeatChannel.MONK, FeatChannel.ENERGY, FeatChannel.FAVORED_SOUL),
            feats.map { it.channel },
        )
        assertEquals(setOf(2, 5), feats[4].classOnlyLevels)
    }
}
