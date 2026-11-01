package ddolite.builder.model

// Fixed game concepts that stay as enums (plan decision D-7: only races and classes become
// data-driven). Each is ported from an enum in Source/Modules/Build/basEnum.bas, together with
// its GetXxxID (name → value) and GetXxxName (value → name) functions.
//
// VB enums reserve 0 for "any/none" (aeAny, seAny, …). Kotlin uses null for that instead, so
// `find` returns null where VB's GetXxxID returns 0.

/**
 * The six ability scores. Ported from `StatEnum` (basEnum.bas:8), `GetStatID` (:561) and
 * `GetStatName` (:572). Order matters: race stat lines and builds list them STR..CHA.
 */
enum class Stat(val displayName: String) {
    STR("Strength"),
    DEX("Dexterity"),
    CON("Constitution"),
    INT("Intelligence"),
    WIS("Wisdom"),
    CHA("Charisma");

    /** `GetStatName(stat, True)`: the first three letters. */
    val abbreviation: String get() = displayName.substring(0, 3)

    companion object {
        /** `GetStatID`: full name or three-letter abbreviation, any case. */
        fun find(name: String): Stat? {
            val lower = name.lowercase()
            for (stat in entries) {
                if (lower == stat.displayName.lowercase() || lower == stat.abbreviation.lowercase()) {
                    return stat
                }
            }
            return null
        }
    }
}

/**
 * Alignments. Ported from `AlignmentEnum` (basEnum.bas:18), `GetAlignmentID` (:293)
 * and `GetAlignmentName` (:304).
 */
enum class Alignment(val displayName: String) {
    TRUE_NEUTRAL("True Neutral"),
    NEUTRAL_GOOD("Neutral Good"),
    LAWFUL_NEUTRAL("Lawful Neutral"),
    LAWFUL_GOOD("Lawful Good"),
    CHAOTIC_NEUTRAL("Chaotic Neutral"),
    CHAOTIC_GOOD("Chaotic Good");

    companion object {
        /** `GetAlignmentID`: exact name, any case. */
        fun find(name: String): Alignment? {
            for (alignment in entries) {
                if (name.equals(alignment.displayName, ignoreCase = true)) {
                    return alignment
                }
            }
            return null
        }
    }
}

/**
 * The 21 skills, in VB order (the order of the skills grid rows). Ported from `SkillsEnum`
 * (basEnum.bas:102), `GetSkillID` (:509) and `GetSkillName` (:535).
 *
 * @property abbreviation the short name used where space is tight (`GetSkillName(skill, True)`).
 * @property otherNames extra spellings `GetSkillID` accepts.
 */
enum class Skill(val displayName: String, val abbreviation: String, val otherNames: List<String> = emptyList()) {
    BALANCE("Balance", "Balance"),
    BLUFF("Bluff", "Bluff"),
    CONCENTRATION("Concentration", "Concent"),
    DIPLOMACY("Diplomacy", "Diplo"),
    DISABLE_DEVICE("Disable Device", "Disable", listOf("disabledevice")),
    HAGGLE("Haggle", "Haggle"),
    HEAL("Heal", "Heal"),
    HIDE("Hide", "Hide"),
    INTIMIDATE("Intimidate", "Intim"),
    JUMP("Jump", "Jump"),
    LISTEN("Listen", "Listen"),
    MOVE_SILENTLY("Move Silently", "Move Si", listOf("movesilently")),
    OPEN_LOCK("Open Lock", "Open Lo", listOf("openlock")),
    PERFORM("Perform", "Perform"),
    REPAIR("Repair", "Repair"),
    SEARCH("Search", "Search"),
    SPELLCRAFT("Spellcraft", "Spellcr"),
    SPOT("Spot", "Spot"),
    SWIM("Swim", "Swim"),
    TUMBLE("Tumble", "Tumble"),
    UMD("Use Magic Device", "UMD", listOf("umd"));

    companion object {
        /** `GetSkillID`: the full name or one of [otherNames], any case. */
        fun find(name: String): Skill? {
            val lower = name.lowercase()
            for (skill in entries) {
                if (lower == skill.displayName.lowercase() || skill.otherNames.contains(lower)) {
                    return skill
                }
            }
            return null
        }
    }
}

/** Race types. Ported from `RaceTypeEnum` (basEnum.bas:63), `GetRaceTypeID` (:386) and `GetRaceTypeName` (:394). */
enum class RaceType(val dataName: String, val displayName: String) {
    FREE("free", "Free to Play"),
    PREMIUM("premium", "Premium"),
    ICONIC("iconic", "Iconic");

    companion object {
        /** `GetRaceTypeID`: the word used in Races.txt ("Free", "Premium", "Iconic"), any case. */
        fun find(name: String): RaceType? {
            val lower = name.lowercase()
            for (type in entries) {
                if (lower == type.dataName) {
                    return type
                }
            }
            return null
        }
    }
}

/** Base attack bonus progression. Ported from `BABEnum` (basEnum.bas:128); values as written in Classes.txt. */
enum class Bab(val dataValue: String) {
    FULL("1"),
    THREE_QUARTERS("0.75"),
    HALF("0.5"),
}

/**
 * The colour a class is drawn in (e.g. the leveling order). VB stores it as one of the
 * theme colours in clsConfig's `ColorValueEnum` (cveRed … cveOrange), so the actual colour
 * comes from the current theme. Values as written in Classes.txt.
 */
enum class ClassColor {
    RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE;

    companion object {
        /** The `Color:` value in Classes.txt, any case (basDataLoad.LoadClass, :284). */
        fun find(name: String): ClassColor? {
            for (color in entries) {
                if (name.equals(color.name, ignoreCase = true)) {
                    return color
                }
            }
            return null
        }
    }
}

/**
 * What a class grants at a level besides normal feats. VB stores `BonusFeat(level)` as a
 * `BuildFeatSourceEnum` value: 0 for none, `bfsClass` for a bonus feat slot (`BonusFeat:` in
 * Classes.txt), `bfsClassOnly` for a class-only feat slot (`ClassFeat:`).
 */
enum class BonusFeatSlot { NONE, BONUS, CLASS_ONLY }
