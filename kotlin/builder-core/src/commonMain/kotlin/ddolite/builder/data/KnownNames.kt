package ddolite.builder.data

/**
 * A race or class name VB6 knows about, with its position in the VB enum and the other
 * spellings VB accepts.
 *
 * These tables are ports of `RaceEnum`/`GetRaceID`/`GetRaceName` and `ClassEnum`/`GetClassID`/
 * `GetClassName` in Source/Modules/Build/basEnum.bas. In VB, a race or class missing from these
 * tables can't be loaded at all ("Race not recognized"). Here the tables are only used for the VB
 * order and for alternative spellings: a new race in Races.txt loads without a code change, and the
 * loader warns that VB6 would need one.
 *
 * Since the data format can't change while VB6 is still maintained (plan D-5), the alternative
 * spellings can't move into the data files yet.
 *
 * @property vbIndex the value of the VB enum constant (reHuman = 1, ceBarbarian = 1, …).
 * @property name the name as `GetRaceName`/`GetClassName` return it.
 * @property otherNames other spellings `GetRaceID`/`GetClassID` accept, lower case.
 */
class KnownName(val vbIndex: Int, val name: String, val otherNames: List<String> = emptyList()) {
    /** True if [text] is this name or one of the other spellings, ignoring case. */
    fun matches(text: String): Boolean {
        val lower = text.lowercase()
        return lower == name.lowercase() || otherNames.contains(lower)
    }
}

/** Finds the entry in [table] that [text] names, or null. */
fun findKnownName(table: List<KnownName>, text: String): KnownName? {
    for (known in table) {
        if (known.matches(text)) {
            return known
        }
    }
    return null
}

/** `RaceEnum` (basEnum.bas:28) with `GetRaceID` (:315) spellings and `GetRaceName` (:350) names. */
val KNOWN_RACES: List<KnownName> = listOf(
    KnownName(1, "Human"),
    KnownName(2, "Drow"),
    KnownName(3, "Dwarf"),
    KnownName(4, "Elf"),
    KnownName(5, "Halfling"),
    KnownName(6, "Half-Elf"),
    KnownName(7, "Half-Orc"),
    KnownName(8, "Warforged"),
    KnownName(9, "Bladeforged"),
    KnownName(10, "Purple Dragon Knight"),
    KnownName(11, "Morninglord"),
    KnownName(12, "Shadar-kai"),
    KnownName(13, "Gnome"),
    KnownName(14, "Deep Gnome"),
    KnownName(15, "Dragonborn"),
    KnownName(16, "Aasimar"),
    KnownName(17, "Aasimar Scourge", listOf("scourge", "scourge aasimar")),
    KnownName(18, "Wood Elf"),
    KnownName(19, "Tiefling"),
    KnownName(20, "Tiefling Scoundrel", listOf("scoundrel")),
    KnownName(21, "Shifter"),
    KnownName(22, "Razorclaw Shifter", listOf("razorclaw")),
    KnownName(23, "Tabaxi"),
    KnownName(24, "Tabaxi Trailblazer", listOf("trailblazer")),
    KnownName(25, "Eladrin"),
    KnownName(26, "Eladrin Chaosmancer", listOf("chaosmancer")),
    KnownName(27, "Dhampir"),
    KnownName(28, "Dark Bargainer", listOf("darkbargainer")),
    KnownName(29, "Duergar"),
    KnownName(30, "Duergar Mindcleaver", listOf("duergarmindcleaver", "mindcleaver")),
)

/** `ClassEnum` (basEnum.bas:71) with `GetClassID` (:403) and `GetClassName` (:434). No alternative spellings. */
val KNOWN_CLASSES: List<KnownName> = listOf(
    KnownName(1, "Barbarian"),
    KnownName(2, "Bard"),
    KnownName(3, "Cleric"),
    KnownName(4, "Fighter"),
    KnownName(5, "Paladin"),
    KnownName(6, "Ranger"),
    KnownName(7, "Rogue"),
    KnownName(8, "Sorcerer"),
    KnownName(9, "Wizard"),
    KnownName(10, "Monk"),
    KnownName(11, "Favored Soul"),
    KnownName(12, "Artificer"),
    KnownName(13, "Druid"),
    KnownName(14, "Warlock"),
    KnownName(15, "Alchemist"),
    KnownName(16, "Stormsinger"),
    KnownName(17, "Dark Apostate"),
    KnownName(18, "Blightcaster"),
    KnownName(19, "Sacred Fist"),
    KnownName(20, "Dark Hunter"),
    KnownName(21, "Acolyte"),
    KnownName(22, "Dragon Lord"),
    KnownName(23, "Wild Mage"),
    KnownName(24, "Dragon Disciple"),
    KnownName(25, "Arcane Trickster"),
)
