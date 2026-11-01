package ddolite.builder.model

/** A warlock pact: its name and the spells it grants (`PactSpells:` in Classes.txt). Ported from `PactType` (basDataDefs.bas:56). */
data class Pact(val name: String, val spells: List<String>)

/**
 * A class from Classes.txt (named `CharacterClass` because `Class` means something else in
 * Kotlin). Ported from `ClassType` (Build/basDataDefs.bas:61).
 *
 * Differences from VB:
 * - Identified by [name], not a `ClassEnum` value (D-7). [vbIndex] keeps the VB enum position,
 *   because VB loops over classes in that order and the output may depend on it.
 * - VB arrays indexed by level or spell level are lists here; use the accessor functions,
 *   which take the same 1-based numbers as VB.
 *
 * @property initials short names for the skills grid header, longest last (exactly 4, or just the
 *   first letter if the data doesn't give them). VB tries them in turn until one is unique.
 * @property alignments alignments the class may have.
 * @property maxSpellLevel 0 for non-casters, else 4, 6 or 9.
 * @property spellSlotsByLevel slots per spell level for each class level: `[classLevel - 1][spellLevel - 1]`.
 * @property spellListsByLevel spells per spell level: `[spellLevel - 1]`.
 * @property canCastSpell first class level with any slots for each spell level, index 0 = healing spells
 *   (`HealingSpell:`); 0 if never. Size 10, as VB's `CanCastSpell(9)`.
 * @property trapper true if the class can search for and disable traps.
 */
data class CharacterClass(
    val name: String,
    val vbIndex: Int,
    val abbreviation: String,
    val initials: List<String>,
    val color: ClassColor?,
    val alignments: Set<Alignment>,
    val skillPoints: Int,
    val bab: Bab?,
    val bonusFeats: List<BonusFeatSlot>,
    val nativeSkills: Set<Skill>,
    val maxSpellLevel: Int,
    val spellSlotsByLevel: List<List<Int>>,
    val spellListsByLevel: List<List<String>>,
    val freeSpells: List<String>,
    val mandatorySpells: List<String>,
    val canCastSpell: List<Int>,
    val trees: List<String>,
    val grantedFeats: List<GrantedFeat>,
    val pacts: List<Pact>,
    val disallowedClasses: List<String>,
    val trapper: Boolean,
) {
    /** What the class grants at [level] (1-20), like VB's `BonusFeat(level)`. */
    fun bonusFeatAt(level: Int): BonusFeatSlot = bonusFeats[level - 1]

    /** Spell slots at [classLevel] (1-20) for [spellLevel] (1..maxSpellLevel), like VB's `SpellSlots(classLevel, spellLevel)`. */
    fun spellSlots(classLevel: Int, spellLevel: Int): Int = spellSlotsByLevel[classLevel - 1][spellLevel - 1]

    /** The spell list for [spellLevel] (1..maxSpellLevel), like VB's `SpellList(spellLevel)`. */
    fun spellList(spellLevel: Int): List<String> = spellListsByLevel[spellLevel - 1]

    /** True if [skill] is a class skill. */
    fun isNativeSkill(skill: Skill): Boolean = nativeSkills.contains(skill)
}
