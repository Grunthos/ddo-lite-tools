package ddolite.builder.data

import ddolite.builder.data.ParsedLine.Field
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Expected results worked out by hand from basDataLoad.ParseLine (Build/basDataLoad.bas:2126).
 * Examples are real lines from Data/Builder unless marked otherwise.
 */
class ParseLineTest {
    @Test
    fun blankAndMissingColon() {
        assertEquals(ParsedLine.Blank, parseLine(""))
        assertEquals(ParsedLine.Blank, parseLine("  \t "))
        assertEquals(ParsedLine.MissingColon("no colon here"), parseLine("no colon here"))
    }

    @Test
    fun singleNumber() {
        assertEquals(Field("tier", "0", 0, listOf("0")), parseLine("Tier: 0"))
        assertEquals(Field("ranks", "3", 3, listOf("3")), parseLine("Ranks: 3"))
    }

    @Test
    fun trailingNumberIsSplitOff() {
        assertEquals(Field("class", "Monk", 12, listOf("Monk 12")), parseLine("Class: Monk 12"))
    }

    @Test
    fun textWithoutNumber() {
        assertEquals(
            Field("selector", "Strength", 0, listOf("Strength")),
            parseLine("Selector: Strength"),
        )
        assertEquals(
            Field("all", "Feat: Bond: Bond of the Protector", 0, listOf("Feat: Bond: Bond of the Protector")),
            parseLine("All: Feat: Bond: Bond of the Protector"),
        )
    }

    @Test
    fun commaMakesAList() {
        assertEquals(
            Field("class", "", 0, listOf("Alchemist 8", "Artificer 8")),
            parseLine("Class: Alchemist 8, Artificer 8"),
        )
        assertEquals(
            Field("selector", "", 0, listOf("Strength", "Wisdom", "Charisma")),
            parseLine("Selector: Strength, Wisdom, Charisma"),
        )
    }

    @Test
    fun descriptionsAreNeverSplit() {
        assertEquals(
            Field("descrip", "+1 to Strength, Wisdom, or Charisma.", 0, emptyList()),
            parseLine("Descrip: +1 to Strength, Wisdom, or Charisma."),
        )
        assertEquals(
            Field("wikiname", "Foo, Bar 2", 0, emptyList()), // made-up example
            parseLine("WikiName: Foo, Bar 2"),
        )
    }

    @Test
    fun keyIsLowerCasedAndTabsDeleted() {
        assertEquals(Field("grantedfeats1", "Healing Hands", 0, listOf("Healing Hands")), parseLine("GrantedFeats1: Healing Hands"))
        assertEquals(Field("group", "HeroicMisc", 0, listOf("HeroicMisc")), parseLine("\tGroup:\tHeroic\tMisc")) // made-up
    }

    @Test
    fun vbNumericQuirks() {
        // A Double assigned to a VB Long rounds half to even.
        assertEquals(Field("bab", "0.75", 1, listOf("0.75")), parseLine("BAB: 0.75"))
        assertEquals(Field("level", "2.5", 2, listOf("2.5")), parseLine("Level: 2.5")) // made-up
        // IsNumeric accepts a D exponent, so "1d6" is the number 1000000. Made-up example.
        assertEquals(Field("dice", "Damage", 1_000_000, listOf("Damage 1d6")), parseLine("Dice: Damage 1d6"))
    }
}
