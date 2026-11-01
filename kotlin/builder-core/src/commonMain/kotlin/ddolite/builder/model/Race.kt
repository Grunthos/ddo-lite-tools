package ddolite.builder.model

/**
 * A feat granted automatically at a level, as written in a data file ("GrantedFeats3: Toughness").
 *
 * VB stores these as `PointerType` with `.Tier` = level and `.Raw` = "Feat: <name>", and
 * resolves the name to a feat later (basBuild/basProcess). Here only the name is kept until the
 * feat loader is ported.
 */
data class GrantedFeat(val level: Int, val featName: String)

/**
 * A playable race from Races.txt. Ported from `RaceType` (Build/basDataDefs.bas:22).
 *
 * Differences from VB:
 * - Identified by [name], not by a `RaceEnum` value (D-7). [vbIndex] keeps the VB enum
 *   position, because VB loops over races in that order and the output may depend on it.
 * - [iconicClass] and [subRace] are names rather than enum values.
 *
 * @property stats starting ability scores, STR..CHA (always 6 values).
 * @property skillPoints extra skill points per level (1 with `Flags: BonusSkill`, else 0).
 * @property trees enhancement tree names, as listed.
 */
data class Race(
    val name: String,
    val vbIndex: Int,
    val abbreviation: String,
    val type: RaceType?,
    val iconicClass: String?,
    val subRace: String?,
    val listFirst: Boolean,
    val bonusFeat: Boolean,
    val stats: List<Int>,
    val skillPoints: Int,
    val grantedFeats: List<GrantedFeat>,
    val trees: List<String>,
) {
    /** The starting score for one stat. */
    fun stat(stat: Stat): Int = stats[stat.ordinal]
}
