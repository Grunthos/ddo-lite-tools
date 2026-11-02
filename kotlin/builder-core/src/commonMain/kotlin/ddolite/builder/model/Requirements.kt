package ddolite.builder.model

/**
 * What a requirement or link points at. Ported from `PointerEnum` (Build/basEnum.bas:151).
 * VB's `peError` (0, unset) is represented by a null where it can occur.
 */
enum class PointerStyle { FEAT, ENHANCEMENT, DESTINY }

/**
 * A requirement as written in the data, e.g. "Feat: Power Attack" or an enhancement path.
 * Ported from the load-time part of `PointerType` (Build/basDataDefs.bas:10): VB stores only
 * `Raw` and `Style` while loading, and fills in the rest (feat, tree, tier, ability, selector,
 * rank) in `basProcess.ProcessPointers` once every file is loaded. That resolved form is ported
 * with ProcessPointers.
 */
data class Requirement(val raw: String, val style: PointerStyle)

/**
 * The three requirement lists of a feat, selector or ability: all of [all], at least one of
 * [one], and none of [none]. Ported from `Req(1 To 3) As ReqListType` (1 = All, 2 = One,
 * 3 = None; `ReqGroupEnum`, basEnum.bas:158).
 */
data class Requirements(
    val all: List<Requirement>,
    val one: List<Requirement>,
    val none: List<Requirement>,
) {
    /** The list for a group, by its data-file name: "all", "one" or "none" (VB `GetReqGroupID`). */
    fun withGroup(group: String, list: List<Requirement>): Requirements {
        if (group == "all") {
            return Requirements(list, one, none)
        }
        if (group == "one") {
            return Requirements(all, list, none)
        }
        if (group == "none") {
            return Requirements(all, one, list)
        }
        throw IllegalArgumentException("Not a requirement group: $group")
    }

    companion object {
        val EMPTY = Requirements(emptyList(), emptyList(), emptyList())
    }
}

/**
 * How [RaceRequirement.races] is to be read. Ported from `RaceReqEnum` (basEnum.bas:273) and
 * `GetRaceReqID` (:701). The comments on `FeatType.Race` in basDataDefs.bas explain the meanings.
 */
enum class RaceRequirementMode(val dataName: String) {
    /** No race filter (VB: rreAny, also what an unknown mode gives). */
    ANY(""),

    /** Only the listed races may take it. */
    REQUIRED("required"),

    /** The listed races may not take it. */
    NOT_ALLOWED("notallowed"),

    /** Standard (non-iconic) races may take it, except those listed. */
    STANDARD("standard"),

    /** Iconic races may take it, except those listed. */
    ICONIC("iconic");

    companion object {
        /** `GetRaceReqID`: case-insensitive; null for anything else (VB returns rreAny and the loader reports it). */
        fun find(name: String): RaceRequirementMode? {
            val lower = name.lowercase()
            for (mode in entries) {
                if (mode != ANY && mode.dataName == lower) {
                    return mode
                }
            }
            return null
        }
    }
}

/**
 * A race restriction: "Race: Required, Human, Elf". Ported from VB's `Race() As Long` array,
 * where `Race(0)` holds the [mode] and `Race(race) = 1` marks each listed race.
 * Races are held by name (plan D-7).
 */
data class RaceRequirement(val mode: RaceRequirementMode, val races: List<String>) {
    companion object {
        val NONE = RaceRequirement(RaceRequirementMode.ANY, emptyList())
    }
}

/**
 * A class and a class level, e.g. "Fighter 4" in `Class: Fighter 4, Paladin 4`. Ported from
 * `ClassLevelType` (basDataDefs.bas:105) and the `Class()`/`ClassLevel()` array pairs.
 */
data class ClassLevel(val className: String, val level: Int)
