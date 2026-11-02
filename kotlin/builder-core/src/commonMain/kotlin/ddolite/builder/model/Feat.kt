package ddolite.builder.model

/**
 * The filter groups on the Feats screen. Ported from `FilterEnum` (Build/basEnum.bas:164),
 * `GetGroupID` (:602) and `GetFeatGroupName` (:621), in VB order. VB's `feAll` (every feat is in
 * it) isn't a member: a feat's [Feat.groups] holds only the real groups.
 */
enum class FeatGroup(val dataName: String, val displayName: String) {
    MELEE("melee", "Melee"),
    RANGE("range", "Range"),
    SPELLCASTING("spellcasting", "Spellcasting"),
    DEFENSE("defense", "Defense"),
    MISC("misc", "Misc"),
    HEROIC("heroic", "Heroic"),
    ALCHEMY("alchemy", "Alchemy"),
    EPIC("epic", "Epic"),
    DESTINY("destiny", "Destiny"),
    LEGEND("legend", "Legend");

    companion object {
        /** `GetGroupID`: trimmed, case-insensitive; null for an unknown group (VB: feAll). */
        fun find(name: String): FeatGroup? {
            val lower = name.trim().lowercase()
            for (group in entries) {
                if (group.dataName == lower) {
                    return group
                }
            }
            return null
        }
    }
}

/**
 * How a feat's selectors relate to other feats. Ported from `SelectorStyleEnum` (basEnum.bas:233).
 */
enum class SelectorStyle {
    /** No selectors. */
    NONE,

    /** Has its own `Selector:` list. */
    ROOT,

    /** Uses another feat's selectors (`SharedSelector:`), e.g. Greater Weapon Focus uses Weapon Focus's. */
    SHARED,

    /** Each ability must use a different selector (enhancements only). */
    EXCLUSIVE,
}

/**
 * Which feat slots a feat can go in. Ported from `FeatChannelEnum` (basEnum.bas:207) and set by
 * `basDataLoad.SetFeatChannel` from the feat's flags. VB's `fceUnknown`, `fceSelected`,
 * `fceGranted` and `fceChannels` are used by builds, not by loaded feats, and are added with the
 * build model.
 */
enum class FeatChannel {
    GENERAL,
    RACIAL,
    WARLOCK,
    MONK,
    ROGUE,
    FAVORED_ENEMY,
    WILD_SHAPE,
    CLERIC,
    FAVORED_SOUL,
    ENERGY,
    DEITY,
}

/**
 * A feat from Feats.txt. Ported from `FeatType` (Build/basDataDefs.bas:145).
 *
 * Differences from VB:
 * - Classes and races are held by name (plan D-7), checked against the loaded data.
 * - VB's arrays with an "is present" flag in element 0 are nullable lists (null = no such line).
 * - `FeatIndex` and `FeatDisplayIndex` (positions in VB's sorted lookup lists) aren't stored;
 *   lookups and display order are handled by [ddolite.builder.data.BuilderData].
 * - `Alignment()` is never set for a feat by the VB loader (only for selectors), so it's left out.
 *
 * @property groups Feats-screen filter groups (`Group:`).
 * @property parentSelector `SharedSelector:` raw text naming the feat whose selectors it shares.
 * @property selectorOnly `Flags: SelectorOnly`.
 * @property raceBonus races that get it as a racial bonus feat; null if no `RaceBonus:` line.
 * @property classBonus classes that get it as a bonus feat; null if no `ClassBonus:` line.
 * @property classBonusLevel `ClassBonusLevel:` (only Spring Attack, Monk 6).
 * @property grantedBy `GrantedBy:` (only Child of …, Favored Soul 3).
 * @property times how many times it can be taken (`Repeat:` 1, 2, 3 or 99); 0 if not set.
 * @property selectable false for `Flags: Unselectable`.
 * @property bab, level minimum base attack bonus and character level; 0 if none.
 * @property classes `Class:` requirements; null if none.
 * @property canCastSpellLevel the spell level the character must be able to cast; meaningful
 *   only when [canCastSpell] is true (level 0 is allowed).
 * @property raceOnly `Flags: RaceOnly`: only as a racial bonus feat.
 * @property classOnly `Flags: ClassOnly`: only as a class bonus feat, for [classOnlyClasses] at
 *   [classOnlyLevels].
 */
data class Feat(
    val name: String,
    val abbreviation: String,
    val sortName: String,
    val description: String,
    val wiki: String,
    val groups: Set<FeatGroup>,
    val selectorStyle: SelectorStyle,
    val parentSelector: String?,
    val selectors: List<Selector>,
    val selectorOnly: Boolean,
    val raceBonus: List<String>?,
    val classBonus: List<String>?,
    val classBonusLevel: ClassLevel?,
    val grantedBy: ClassLevel?,
    val channel: FeatChannel,
    val deity: Boolean,
    val times: Int,
    val pastLife: Boolean,
    val legend: Boolean,
    val selectable: Boolean,
    val pact: Boolean,
    val domain: Boolean,
    val bab: Int,
    val level: Int,
    val race: RaceRequirement,
    val classes: List<ClassLevel>?,
    val skill: Skill?,
    val skillValue: Int,
    val skillTome: Boolean,
    val stat: Stat?,
    val statValue: Int,
    val canCastSpell: Boolean,
    val canCastSpellLevel: Int,
    val raceOnly: Boolean,
    val classOnly: Boolean,
    val classOnlyClasses: List<String>,
    val classOnlyLevels: Set<Int>,
    val requirements: Requirements,
) {
    /** The selector called exactly [name], or null. */
    fun findSelector(name: String): Selector? {
        for (selector in selectors) {
            if (selector.name == name) {
                return selector
            }
        }
        return null
    }
}
