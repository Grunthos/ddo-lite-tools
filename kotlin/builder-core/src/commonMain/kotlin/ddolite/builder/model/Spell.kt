package ddolite.builder.model

/**
 * A spell from Spells.txt. Ported from `SpellType` (Build/basDataDefs.bas:40).
 *
 * VB's `Cost`, `Cooldown` and `Crawled` fields are never set by the loader (they belonged to the
 * old wiki crawler), so they're left out.
 *
 * @property name `SpellName`: the key that class spell lists and builds refer to (exact match).
 * @property wiki `WikiName`: the DDO wiki page name; the spell name unless the file says otherwise.
 * @property description `Descrip`.
 * @property rare `Flags: Rare`.
 */
data class Spell(
    val name: String,
    val wiki: String,
    val description: String,
    val rare: Boolean,
)
