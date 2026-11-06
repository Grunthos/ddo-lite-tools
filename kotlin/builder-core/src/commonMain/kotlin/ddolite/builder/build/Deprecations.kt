package ddolite.builder.build

import ddolite.builder.model.NameChange

/**
 * A feat in a build file that no longer matches the data: the feat isn't found, or it needs a
 * selector that isn't found. Ported from `DeprecateFeatType` (basDeprecate.bas:6); the Current/Old
 * display fields belong to the binary format and the Deprecated screen.
 *
 * @property index 0-based slot in [type]'s list.
 */
data class DeprecatedFeat(
    val type: BuildFeatType,
    val index: Int,
    val featName: String,
    val selectorName: String,
    val childType: BuildFeatType?,
    val childIndex: Int,
)

/**
 * An ability line that didn't resolve. Ported from `DeprecateAbilityType` (basDeprecate.bas:18).
 *
 * @property raw the text as parsed (for tree lines, the build tree's name, a space, and the line's value).
 * @property parsed false if the text couldn't even be split into tier, ability and rank.
 */
data class DeprecatedAbility(
    val isDestiny: Boolean,
    val raw: String,
    val treeName: String,
    val tier: Int,
    val abilityName: String,
    val selectorName: String,
    val rank: Int,
    val guideTreeDisplay: String,
    val parsed: Boolean,
)

/**
 * What loading a build couldn't match to the current data, for the Deprecated screen
 * (frmDeprecate) to show. Ported from `DeprecateType` (basDeprecate.bas:36), as filled by
 * `LoadFileLite`; the binary-format parts (WarSoul, DivineMight, …) aren't needed for .build files.
 *
 * @property nameChanges renames from NameChange.txt that were applied, each once.
 * @property guide the guide steps that didn't resolve, kept as red rows or dropped (Kotlin only: VB
 *   keeps only the first and ignores the rest of the guide after it).
 */
class Deprecations {
    val feats = ArrayList<DeprecatedFeat>()
    val nameChanges = ArrayList<NameChange>()
    val enhancements = ArrayList<DeprecatedAbility>()
    val destinies = ArrayList<DeprecatedAbility>()
    val guide = ArrayList<GuideDeprecation>()
    val reducedRanks = ArrayList<ReducedRank>()

    /** VB's `gtypDeprecate.Deprecated`: anything to show. */
    val any: Boolean
        get() = feats.isNotEmpty() || nameChanges.isNotEmpty() || enhancements.isNotEmpty() ||
            destinies.isNotEmpty() || guide.isNotEmpty() || reducedRanks.isNotEmpty()

    /** Records a rename once. Ported from `DeprecateNameChange` (basDeprecate.bas:69). */
    fun addNameChange(change: NameChange) {
        if (!nameChanges.contains(change)) {
            nameChanges.add(change)
        }
    }
}

/**
 * A leveling guide step that didn't resolve: [text] is the file's line. [kept] when its tree exists
 * (the ability doesn't): the guide keeps it as a red row. Otherwise its tree is missing and it was
 * dropped. Kotlin only (see [Deprecations.guide]).
 */
class GuideDeprecation(val ability: DeprecatedAbility, val text: String, val kept: Boolean)

/**
 * A tree's ability whose rank in the file the data no longer has: kept at [ranks], its highest.
 * Kotlin only (VB drops the ability).
 */
class ReducedRank(val ability: DeprecatedAbility, val ranks: Int)
