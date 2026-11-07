package ddolite.builder.build

import ddolite.builder.data.BuilderData
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading and writing .build files with small made-up data, one VB behaviour per test. */
class BuildFileTest {
    private fun slots(vararg counts: Int): String = counts.joinToString(", ")

    private val data: BuilderData = loadBuilderData(
        MapDataSource.ofText(
            "Builder/Races.txt" to "RaceName: Human\nStats: 8, 8, 8, 8, 8, 8\nFlags: BonusFeat\n",
            "Builder/Classes.txt" to
                "ClassName: Fighter\nBAB: 1\nSkillPoints: 2\nBonusFeat: 1, 2, 4\nInitial: F, Fi, Ftr, Ftr\n\n" +
                "ClassName: Cleric\nBAB: 0.75\nSkillPoints: 2\nInitial: C, Cl, Clr, Clr\nMaxSpellLevel: 4\n" +
                "SpellSlots1: " + slots(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2) + "\n" +
                "SpellSlots2: " + slots(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) + "\n" +
                "SpellSlots3: " + slots(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) + "\n" +
                "SpellSlots4: " + slots(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) + "\n" +
                "MandatorySpells: Cure Light Wounds, Cure Moderate Wounds, Cure Serious Wounds, Cure Critical Wounds\n" +
                "FreeSpells: Bless\n",
            "Builder/Feats.txt" to
                "FeatName: Toughness\nGroup: Heroic\n\n" +
                "FeatName: Weapon Focus\nGroup: Melee\nClassBonus: Fighter\nSelector: Slashing, Piercing\n\n" +
                "FeatName: Purity of Heart\nGroup: Misc\n",
            "Builder/NameChange.txt" to "NameChangeType: Feat\nOld: Purity of Spirit\nNew: Purity of Heart\n",
            "Builder/Enhancements.txt" to
                "TreeName: Human\nType: Race\nAbbreviation: Hum\n\n" +
                "AbilityName: Versatility\nTier: 0\nSelector: Attack, Defense\n\n" +
                "AbilityName: Inflame: Energy Absorption\nTier: 1\nRanks: 3\n",
        ),
        LoadLog(),
    )

    private fun read(text: String): BuildLoadResult =
        readBuildText(text.replace("\n", "\r\n"), data, BuildPoints.ADVENTURER)

    private fun overview(levels: String): String = "[Overview]\n\nName: Test\nRace: Human\nMaxLevels: 20\n\n$levels\n\n"

    private val fighter = overview("Class: Fighter")

    @Test
    fun featsAreMatchedIgnoringCaseAndRenamed() {
        val result = read(
            fighter + "[Feats]\n\n" +
                "Standard: Heroic 1\ttoughness\n" +
                "Standard: Heroic 3\tPurity of Spirit\n" +
                "Class: Fighter 1\tWeapon Focus: piercing\n" +
                "Class: Fighter 2\tWeapon Focus: Bludgeoning\n" +
                "Standard: Heroic 6\tNo Such Feat\n",
        )
        val build = result.build
        assertEquals("Toughness", build.feats(BuildFeatType.STANDARD)[0].featName)
        assertEquals("Purity of Heart", build.feats(BuildFeatType.STANDARD)[1].featName)
        assertEquals(listOf("Purity of Spirit"), result.deprecations.nameChanges.map { it.oldName })
        val focus = build.feats(BuildFeatType.CLASS1)[0]
        assertEquals("Weapon Focus", focus.featName)
        assertEquals(2, focus.selector)
        // A missing selector and a missing feat leave the slot empty and are listed.
        assertEquals(listOf("Weapon Focus" to "Bludgeoning", "No Such Feat" to ""), result.deprecations.feats.map { it.featName to it.selectorName })
        assertEquals("", build.feats(BuildFeatType.CLASS1)[1].featName)
    }

    @Test
    fun alternatesAndExchangesRoundTrip() {
        val text = fighter + "[Feats]\n\n" +
            "Standard: Heroic 1\tToughness\n\n" +
            "Alternate: Heroic 1\tPurity of Heart\n\n" +
            "Exchange: Heroic 1\t7\tWeapon Focus: Slashing\n"
        val result = read(text)
        val alternate = result.build.feats(BuildFeatType.ALTERNATE)[0]
        assertEquals(BuildFeatType.STANDARD, alternate.childType)
        assertEquals(0, alternate.child)
        assertEquals(1, alternate.level)
        assertEquals(7, result.build.feats(BuildFeatType.EXCHANGE)[0].level)
        val saved = writeBuildText(result.rules)
        assertTrue(saved.contains("Alternate: Heroic 1\tPurity of Heart\r\n"), saved)
        assertTrue(saved.contains("Exchange: Heroic 1\t7\tWeapon Focus: Slashing\r\n"), saved)
    }

    @Test
    fun deityFeatsAreSavedByLevelButReadByClassLevel() {
        // Cleric from level 4: deity slots at class levels 1 and 6 are character levels 4 and 9.
        val levels = StringBuilder("Class: Fighter\nClass: Cleric\n\n")
        for (level in 1..20) {
            levels.append("Level: ").append(level).append('\t').append(if (level <= 3) "Fighter" else "Cleric").append('\n')
        }
        val result = read(overview(levels.toString()) + "[Feats]\n\nDeity: Deity 1\tToughness\nDeity: Deity 6\tPurity of Heart\n")
        val deity = result.build.feats(BuildFeatType.DEITY)
        assertEquals(listOf(4, 9), deity.map { it.level })
        assertEquals(listOf("Toughness", "Purity of Heart"), deity.map { it.featName })
        val saved = writeBuildText(result.rules)
        assertTrue(saved.contains("Deity: Deity 4\tToughness\r\n"), saved)
        // Reading that back finds no deity slot at class level 4 or 9: both feats are lost, as in VB.
        val reread = read(saved.replace("\r\n", "\n"))
        assertEquals(listOf("", ""), reread.build.feats(BuildFeatType.DEITY).map { it.featName })
    }

    @Test
    fun spellsFillSlotsAndFreeSpellsAddOne() {
        val result = read(
            overview("Class: Cleric") + "[Spells]\n\n" +
                "Spell: Cleric 1\tCure Light Wounds\nSpell: Cleric 1\tBless\nSpell: Cleric 1\tShield of Faith\n" +
                "Spell: Cleric 1\tDivine Favor\n",
        )
        val level = result.build.spellsFor(data.findClass("Cleric")!!)!!.level(1)
        // Two slots plus the cure, plus one free slot for Bless; after loading, the free slot comes first.
        assertEquals(listOf(SpellSlotType.FREE, SpellSlotType.STANDARD, SpellSlotType.STANDARD, SpellSlotType.STANDARD), level.slots.map { it.type })
        assertEquals(1, level.freeSlots)
        assertEquals(emptyList(), result.problems)
        // A fifth spell is one more than there are slots: VB's "Subscript out of range".
        val tooMany = read(
            overview("Class: Cleric") + "[Spells]\n\n" +
                "Spell: Cleric 1\ta\nSpell: Cleric 1\tb\nSpell: Cleric 1\tc\nSpell: Cleric 1\td\n",
        )
        assertEquals(listOf("Subscript out of range"), tooMany.problems.map { it.message })
        assertEquals(14, tooMany.problems[0].lineNumber)
    }

    @Test
    fun treeAbilitiesResolveOrAreDeprecated() {
        val result = read(
            fighter + "[Enhancements]\n\nTree: Human\nType: Race\n" +
                "Ability: Tier 0: Versatility: Defense\n" +
                "Ability: Tier 1: Inflame: Energy Absorption (Rank 2)\n" +
                "Ability: Tier 0: Versatility: Speed\n",
        )
        val tree = result.build.trees[0]
        assertEquals(listOf(BuildAbility(0, 0, 1, 2), BuildAbility(1, 0, 2, 0)), tree.abilities)
        assertEquals(listOf("Speed"), result.deprecations.enhancements.map { it.selectorName })
        val saved = writeBuildText(result.rules)
        assertTrue(saved.contains("Ability: Tier 1: Inflame: Energy Absorption (Rank 2)\r\n"), saved)
    }

    @Test
    fun guideStepsUseTreeAbbreviations() {
        val result = read(
            fighter + "[Enhancements]\n\nTree: Human\nType: Race\n\n[LevelingGuide]\n\n" +
                "Guide: Hum Tier 0: Versatility: Attack\nGuide: Bank AP\nGuide: Reset Tree: Hum\n",
        )
        assertEquals(listOf(1, 200, 101), result.build.guide.enhancements.map { it.id })
        assertTrue(result.deprecations.guide.isEmpty())
        val saved = writeBuildText(result.rules)
        assertTrue(saved.contains("[LevelingGuide]\r\n\r\nGuide: Hum Tier 0: Versatility: Attack\r\nGuide: Bank AP\r\nGuide: Reset Tree: Hum\r\n"), saved)
    }

    @Test
    fun treeKeepsMissingAbilitiesAsPlaceholders() {
        // A line the data hasn't stays in its tree as a placeholder (not an ability), and is saved back
        // where it was (Kotlin only: VB drops it).
        val result = read(
            fighter + "[Enhancements]\n\nTree: Human\nType: Race\n" +
                "Ability: Tier 0: Versatility: Attack\nAbility: Tier 0: No Such Ability\nAbility: Tier 1: Inflame: Energy Absorption (Rank 2)\n\n",
        )
        val human = result.build.trees[0]
        assertEquals(2, human.abilities.size)
        assertEquals(1, human.missing.size)
        assertEquals("No Such Ability", human.missing[0].caption)
        assertEquals(0, human.missing[0].tier)
        assertTrue(result.deprecations.enhancements[0].kept)
        val saved = writeBuildText(result.rules)
        assertTrue(saved.contains("Ability: Tier 0: Versatility: Attack\r\nAbility: Tier 0: No Such Ability\r\nAbility: Tier 1: Inflame: Energy Absorption (Rank 2)\r\n"), saved)
        // Resetting the tree clears it too.
        TreeEdits(result.rules, destinies = false).resetAbilities(human)
        assertTrue(human.missing.isEmpty())

        // One read before an ability of its own tier stays before it (in the slot list and the file).
        val before = read(
            fighter + "[Enhancements]\n\nTree: Human\nType: Race\n" +
                "Ability: Tier 0: No Such Ability\nAbility: Tier 0: Versatility: Attack\n\n",
        )
        val slots = before.build.trees[0].slots()
        assertTrue(slots[0].placeholder)
        assertFalse(slots[1].placeholder)
        assertTrue(writeBuildText(before.rules).contains("Ability: Tier 0: No Such Ability\r\nAbility: Tier 0: Versatility: Attack\r\n"))
    }

    @Test
    fun guideKeepsMissingAbilitiesAndDropsMissingTrees() {
        // A missing ability in a known tree is kept (saved back as read); a missing tree's step is
        // dropped; the steps after both still load (Kotlin only: VB stops at the first).
        val result = read(
            fighter + "[Enhancements]\n\nTree: Human\nType: Race\n\n[LevelingGuide]\n\n" +
                "Guide: Hum Tier 0: No Such Ability\nGuide: Nowhere Tier 1: Something\nGuide: Bank AP\n" +
                "Guide: Hum Tier 0: Versatility: Attack\n",
        )
        val steps = result.build.guide.enhancements
        assertEquals(listOf(0, 200, 1), steps.map { it.id })
        assertEquals("Hum Tier 0: No Such Ability", steps[0].missing)
        val guide = result.deprecations.guide
        assertEquals(2, guide.size)
        assertTrue(guide[0].kept)
        assertFalse(guide[1].kept)
        assertEquals("Nowhere Tier 1: Something", guide[1].text)
        val saved = writeBuildText(result.rules)
        assertTrue(saved.contains("Guide: Hum Tier 0: No Such Ability\r\nGuide: Bank AP\r\nGuide: Hum Tier 0: Versatility: Attack\r\n"), saved)
        assertFalse(saved.contains("Nowhere"), saved)
    }

    @Test
    fun replacingOrClearingAPlaceholderUpdatesItsGuideRows() {
        val text = fighter + "[Enhancements]\n\nTree: Human\nType: Race\nAbility: Tier 1: Old Name (Rank 3)\n\n" +
            "[LevelingGuide]\n\nGuide: Hum Tier 1: Old Name (Rank 1)\nGuide: Bank AP\nGuide: Hum Tier 1: Old Name (Rank 2)\n" +
            "Guide: Hum Tier 1: Old Name (Rank 3)\nGuide: Hum Tier 1: Other Name\n"

        // Replaced by Inflame: Energy Absorption (tier 1, ability 0), here with two ranks: rows for
        // ranks 1 and 2 become it, rank 3 stays red, and another missing ability's row is untouched.
        val replaced = read(text)
        val human = replaced.build.trees[0]
        TreeEdits(replaced.rules, destinies = false).replacePlaceholder(human, 0, 1, 0, 0, 2)
        assertTrue(human.missing.isEmpty())
        val steps = replaced.build.guide.enhancements
        assertEquals(listOf(1, 200, 1, 0, 0), steps.map { it.id })
        assertEquals(listOf(1, 0, 2), listOf(steps[0].rank, steps[1].rank, steps[2].rank))
        assertEquals("Hum Tier 1: Old Name (Rank 3)", steps[3].missing)
        assertTrue(writeBuildText(replaced.rules).contains("Guide: Hum Tier 1: Inflame: Energy Absorption (Rank 2)\r\n"))

        // Cleared: its rows go too.
        val cleared = read(text)
        TreeEdits(cleared.rules, destinies = false).clearPlaceholder(cleared.build.trees[0], 0)
        val left = cleared.build.guide.enhancements
        assertEquals(listOf(200, 0), left.map { it.id })
        assertEquals("Hum Tier 1: Other Name", left[1].missing)
    }

    @Test
    fun vbRunTimeErrorsBecomeProblems() {
        val result = read(
            "[Overview]\n\nName: Test\nRace: Human\nMaxLevels: 300\nClass: Fighter\n\n" +
                "[Feats]\n\nExchange: Heroic 1\tToughness\n\n" +
                "[Destiny]\n\nAbility: Tier 0: Anything\n",
        )
        assertEquals(listOf(5 to "Overflow", 10 to "Subscript out of range", 14 to "Subscript out of range"), result.problems.map { it.lineNumber to it.message })
        assertEquals(MAX_LEVEL, result.build.maxLevels) // the bad value isn't stored
    }

    @Test
    fun unknownSectionIsReadAsSkills() {
        val result = read(fighter + "[Something]\n\nJump   :  4   2\n")
        assertEquals(4, result.build.skills[ddolite.builder.model.Skill.JUMP.ordinal][0])
        assertEquals(2, result.build.skills[ddolite.builder.model.Skill.JUMP.ordinal][1])
    }

    @Test
    fun writerLayout() {
        val result = read(overview("Class: Fighter\nNotes: first\nNotes: second\nNotes:  ") + "[Stats]\n\nPreferred: Hero\nChampion: No\n")
        val saved = writeBuildText(result.rules)
        assertEquals(
            "[Overview]\r\n\r\nName: Test\r\nRace: Human\r\nMaxLevels: 20\r\nMaxLevels: 20\r\n\r\n" +
                "Notes: first\r\nNotes: second\r\n\r\nClass: Fighter\r\n\r\n\r\n" +
                "[Stats]\r\n\r\nPreferred: Hero\r\n\r\nChampion: No\r\n\r\n\r\n" +
                // InitBuildTrees always adds the race tree.
                "[Enhancements]\r\n\r\nTree: Human\r\nType: Race\r\nClassLevels: 20\r\n\r\n",
            saved,
        )
    }
}
