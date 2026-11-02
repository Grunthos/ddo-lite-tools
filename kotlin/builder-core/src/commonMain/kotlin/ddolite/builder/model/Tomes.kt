package ddolite.builder.model

/**
 * When tomes of one kind (stat or skill) can be used: up to [max] tomes, the n-th usable from
 * character level `level(n)`. Ported from `TomeScheduleType` (Source/Modules/basTomes.bas).
 */
data class TomeSchedule(val max: Int, val levels: List<Int>) {
    /** The character level from which tome [tome] (1..max) can be used; VB's `Level(tome)`. */
    fun level(tome: Int): Int = levels[tome - 1]
}

/**
 * Tome limits from Data/Tomes.txt (or VB's built-in defaults). Ported from `TomesType`
 * (Source/Modules/basTomes.bas), which the Builder and the Compendium share; it moves to the
 * `common` module when the Compendium is ported.
 */
data class Tomes(
    val stat: TomeSchedule,
    val skill: TomeSchedule,
    val racialApMax: Int,
    val universalApMax: Int,
    val powerMax: Int,
    val rrMax: Int,
    val permDestinyPointMin: Int,
    val permDestinyPointMax: Int,
    val fateTomeMax: Int,
    val destinyTomeMax: Int,
)

/**
 * A rename for old builds: a feat that was called [oldName] is now [newName], optionally with the
 * selector [assignSelector]. Ported from `NameChangeType` (Build/basDataDefs.bas:262); VB's
 * `Selector` index is worked out when it's used.
 *
 * @property type always "Feat" so far (VB rejects anything else).
 */
data class NameChange(val type: String, val oldName: String, val newName: String, val assignSelector: String)
