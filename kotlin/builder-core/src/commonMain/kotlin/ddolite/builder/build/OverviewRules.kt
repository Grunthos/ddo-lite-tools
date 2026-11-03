package ddolite.builder.build

import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Race
import ddolite.builder.model.RaceType

/** How a class icon is drawn on the Overview screen. Ported from frmOverview's `ClassStateEnum` (frmOverview.frm:1175). */
enum class ClassIconState {
    /** Can be dragged onto a build class slot. */
    NORMAL,

    /** Greyed out: no allowed alignment in common with the build, or disallowed by a chosen class. */
    DISABLED,

    /** Already one of the build's classes: drawn as the empty icon. */
    EMPTY,
}

/**
 * What the Overview screen offers after its filters run.
 *
 * @property alignments the alignment list, starting with null for VB's blank entry (aleAny).
 */
class OverviewChoices(val alignments: List<Alignment?>, private val allowed: Set<CharacterClass>) {
    /** True if the class passes the alignment (and disallowed-class) filters. */
    fun isAllowed(c: CharacterClass): Boolean = allowed.contains(c)
}

/** One row of the Overview level grid. Ported from frmOverview.InitGrid's `grid(level, 0..5)` (frmOverview.frm:2115). */
class OverviewGridRow(
    val level: Int,
    val className: String,
    /** Class levels so far in each of the three build classes. */
    val classLevels: IntArray,
    val bab: Int,
)

/**
 * The rules behind the Overview screen: race, alignment, max levels, the three build classes and
 * the class at each level. Ported from the build-changing routines in frmOverview
 * (Source/Forms/Build/frmOverview.frm); drawing, focus and the "Are you sure?" questions stay in the
 * screen, which asks [hasLevelingGuide] first (VB's `CheckGuide`, :1293).
 *
 * The screen keeps two flags of VB's form state and passes them in: `mblnInclude32` (Champion
 * points were included when the screen opened) and `mblnIconicClass` (choosing an iconic race
 * also sets the main class).
 */
class OverviewRules(val rules: BuildRules) {
    val build: Build get() = rules.build

    /** txtName_Change (frmOverview.frm:1644). */
    fun setName(name: String) {
        build.buildName = name
    }

    /** txtNotes_Change (frmOverview.frm:1675). */
    fun setNotes(notes: String) {
        build.notes = notes
    }

    /** VB's `CheckGuide` asks before anything that erases a leveling guide with steps. */
    fun hasLevelingGuide(): Boolean = build.guide.enhancements.isNotEmpty()

    // ---------------------------------------------------------------- races

    /**
     * The races in the race list, with a heading row (null race) before each race type.
     * Ported from userRaceCombo.InitRows (Source/Controls/Build/userRaceCombo.ctl:281), GetRaceList
     * (:300) and CompareRace (:325). Only races VB knows (`vbIndex` > 0) are listed, as in VB.
     */
    fun raceRows(): List<RaceRow> {
        val races = ArrayList<Race>()
        for (race in rules.data.races) {
            if (race.vbIndex > 0) {
                races.add(race)
            }
        }
        // VB's insertion sort, which never treats two races as equal.
        for (i in 1 until races.size) {
            val swap = races[i]
            var j = i
            while (j >= 1) {
                if (compareRace(swap, races[j - 1]) == -1) {
                    races[j] = races[j - 1]
                    j--
                } else {
                    break
                }
            }
            races[j] = swap
        }
        val rows = ArrayList<RaceRow>()
        var previous: RaceType? = null
        for (race in races) {
            val current = race.type
            if (current != previous) {
                rows.add(RaceRow(null, raceTypeName(current), current == RaceType.ICONIC))
            }
            previous = current
            var iconic: CharacterClass? = null
            val iconicName = race.iconicClass
            if (iconicName != null) {
                iconic = rules.data.findClass(iconicName)
            }
            rows.add(RaceRow(race, race.name, false, iconic))
        }
        return rows
    }

    /** VB's race type order: unknown (0) first, then Free, Premium, Iconic. */
    private fun raceTypeOrder(type: RaceType?): Int {
        if (type == null) {
            return 0
        }
        return type.ordinal + 1
    }

    private fun compareRace(left: Race, right: Race): Int {
        val leftType = raceTypeOrder(left.type)
        val rightType = raceTypeOrder(right.type)
        if (leftType < rightType) {
            return -1
        } else if (leftType > rightType) {
            return 1
        } else if (left.listFirst == right.listFirst) {
            return if (left.name < right.name) -1 else 1
        } else if (left.listFirst) {
            return -1
        }
        return 1
    }

    /** `GetRaceTypeName` (basEnum.bas:394): empty for an unknown type. */
    private fun raceTypeName(type: RaceType?): String {
        if (type == null) {
            return ""
        }
        return type.displayName
    }

    /**
     * A race was chosen in the race list. Ported from usrRace_Click (frmOverview.frm:1544) and
     * RaceChange (:1562), with IconicClass (:1577).
     */
    fun setRace(race: Race?, include32: Boolean, iconicClass: Boolean) {
        build.race = race
        raceChange(include32, loading = false)
        if (iconicClass) {
            var c: CharacterClass? = null
            val name = race?.iconicClass
            if (name != null) {
                c = rules.data.findClass(name)
            }
            // Quirk kept: with a race that has no iconic class this sets the main class back to
            // none, so choosing an iconic race and then another race clears the class it set.
            changeBuildClass(0, c)
            dataChanged()
        }
    }

    /**
     * Drow can't use Champion points (32-point builds). Ported from RaceChange (frmOverview.frm:1562).
     * [include32] is whether Champion points were included when the screen opened.
     */
    private fun raceChange(include32: Boolean, loading: Boolean) {
        val race = build.race
        val champion = BuildPoints.CHAMPION.ordinal
        if (race != null && race.vbIndex == RACE_DROW) {
            build.includePoints[champion] = false
            if (build.buildPoints == BuildPoints.CHAMPION) {
                build.buildPoints = BuildPoints.ADVENTURER
            }
        } else if (!build.includePoints[champion] && include32) {
            build.includePoints[champion] = true
        }
        if (!loading) {
            rules.clearLevelingGuide()
            rules.cascadeChanges(CascadeChange.RACE)
        }
    }

    /** The race's parent race, for the "Sub-Race" link (ShowSubRaceLink, :1586); null if none. */
    fun parentRace(): Race? {
        val name = build.race?.subRace
        if (name == null) {
            return null
        }
        return rules.data.findRace(name)
    }

    /** The Sub-Race link's message. Ported from lnkSubRace_MouseUp (frmOverview.frm:1604). */
    fun subRaceMessage(): String? {
        val race = build.race
        val parent = parentRace()
        if (race == null || parent == null) {
            return null
        }
        return race.name + " is a racial variant of " + parent.name + " and for the purpose of Racial Reincarnation will award " +
            parent.name + " Racial Past Lives."
    }

    // ---------------------------------------------------------------- simple fields

    /** cboAlignment_Click (frmOverview.frm:1651). */
    fun setAlignment(alignment: Alignment?) {
        build.alignment = alignment
        rules.cascadeChanges(CascadeChange.ALIGNMENT)
    }

    /** usrSpinner_Change (frmOverview.frm:1663). */
    fun setMaxLevels(levels: Int) {
        build.maxLevels = levels
        rules.cascadeChanges(CascadeChange.MAX_LEVEL)
    }

    // ---------------------------------------------------------------- build classes

    /**
     * Sets build class [index] (0 = main class, 1 and 2 = splashes) to [newClass] (null = none),
     * moving the levels of the old class to it; levels of a removed class go to the main class.
     * Ported from ChangeBuildClass (frmOverview.frm:1712).
     *
     * With no class chosen yet every level is "none", so choosing the first class gives it all 20.
     */
    fun changeBuildClass(index: Int, newClass: CharacterClass?) {
        val oldClass = build.buildClasses[index]
        build.buildClasses[index] = newClass
        var levelClass = newClass
        if (levelClass == null) {
            levelClass = build.buildClasses[0]
        }
        for (i in 0 until HEROIC_LEVELS) {
            if (build.levelClasses[i] == oldClass) {
                build.levelClasses[i] = levelClass
            }
        }
    }

    /** No class twice, and no gap before a splash. Ported from RemoveDuplicates (frmOverview.frm:1725). */
    fun removeDuplicates() {
        val classes = build.buildClasses
        if (classes[2] == classes[1] || classes[2] == classes[0]) {
            classes[2] = null
        }
        if (classes[1] == classes[0]) {
            classes[1] = null
        }
        if (classes[1] == null && classes[2] != null) {
            classes[1] = classes[2]
        }
    }

    /**
     * A class chosen in build class combo [index]. Ported from ClassChange (frmOverview.frm:1695):
     * choosing none also clears the splashes after it.
     */
    fun classChange(index: Int, newClass: CharacterClass?) {
        var last = index
        if (newClass == null) {
            last = 2
        }
        for (i in index..last) {
            changeBuildClass(i, newClass)
        }
        removeDuplicates()
        dataChanged()
    }

    /**
     * A class icon dropped on build class slot [index]. Ported from picBuildClass_DragDrop
     * (frmOverview.frm:2013) with DragComplete (:2052). Quirk kept: unlike [classChange], no
     * RemoveDuplicates (the icons of chosen classes can't be dragged, so it isn't needed).
     */
    fun dropClass(index: Int, c: CharacterClass) {
        changeBuildClass(index, c)
        dataChanged()
    }

    /**
     * Build class slot [from] dropped on slot [to]: the two swap places (levels stay as they are).
     * Ported from picBuildClass_DragDrop (frmOverview.frm:2022). Returns false if nothing changed.
     */
    fun swapBuildClasses(from: Int, to: Int): Boolean {
        val classes = build.buildClasses
        if (classes[to] == null || classes[from] == null || to == from) {
            return false
        }
        val swap = classes[to]
        classes[to] = classes[from]
        classes[from] = swap
        rules.initLevelingGuide()
        return true
    }

    /**
     * Build class slot [index] dragged away: it and the slots after it are cleared, their levels
     * going to the main class. Ported from ClearBuildClass (frmOverview.frm:2042).
     */
    fun clearBuildClass(index: Int) {
        for (i in index..2) {
            changeBuildClass(i, null)
        }
        dataChanged()
    }

    /**
     * After the classes change: base attack bonus, an empty leveling guide, and the cascade.
     * Ported from DataChanged (frmOverview.frm:1622); InitGrid is the screen's.
     */
    fun dataChanged() {
        rules.calculateBab()
        rules.clearLevelingGuide()
        rules.cascadeChanges(CascadeChange.CLASS)
    }

    /**
     * Filters the alignments by the build classes, and the classes by the alignment: the chosen
     * one, or else any alignment still possible. If only one alignment is possible it's chosen
     * (without a cascade, as in VB). With [disallowed], classes a build class rules out are
     * filtered too.
     *
     * Ported from DrawIcons (frmOverview.frm:1814, [disallowed] = true) and FilterCombos (:1738,
     * the class combos used when icons are off, which don't check disallowed classes).
     */
    fun filterChoices(disallowed: Boolean): OverviewChoices {
        val alignmentAllowed = HashSet<Alignment>()
        alignmentAllowed.addAll(Alignment.entries)
        var alignments = Alignment.entries.size
        for (c in build.buildClasses) {
            if (c == null) {
                continue // VB's class 0 allows every alignment
            }
            for (alignment in Alignment.entries) {
                if (alignmentAllowed.contains(alignment) && !c.alignments.contains(alignment)) {
                    alignmentAllowed.remove(alignment)
                    alignments--
                }
            }
        }
        if (alignments == 1) {
            for (alignment in Alignment.entries) {
                if (alignmentAllowed.contains(alignment)) {
                    build.alignment = alignment
                    break
                }
            }
        }
        val allowed = HashSet<CharacterClass>()
        val chosen = build.alignment
        for (c in rules.data.classes) {
            if (chosen == null) {
                for (alignment in Alignment.entries) {
                    if (alignmentAllowed.contains(alignment) && c.alignments.contains(alignment)) {
                        allowed.add(c)
                        break
                    }
                }
            } else if (c.alignments.contains(chosen)) {
                allowed.add(c)
            }
        }
        if (disallowed) {
            for (c in build.buildClasses) {
                if (c == null) {
                    continue
                }
                for (name in c.disallowedClasses) {
                    val other = rules.data.findClass(name)
                    if (other != null) {
                        allowed.remove(other)
                    }
                }
            }
        }
        val list = ArrayList<Alignment?>()
        list.add(null)
        for (alignment in Alignment.entries) {
            if (alignmentAllowed.contains(alignment)) {
                list.add(alignment)
            }
        }
        return OverviewChoices(list, allowed)
    }

    /** How class [c]'s icon is drawn. From DrawIcons (frmOverview.frm:1889). */
    fun iconState(c: CharacterClass, choices: OverviewChoices): ClassIconState {
        for (chosen in build.buildClasses) {
            if (chosen == c) {
                return ClassIconState.EMPTY
            }
        }
        if (choices.isAllowed(c)) {
            return ClassIconState.NORMAL
        }
        return ClassIconState.DISABLED
    }

    /** A build class slot can't take a drop while the slot before it is empty (DrawIconHeader, :1964). */
    fun isSlotEnabled(index: Int): Boolean = index == 0 || build.buildClasses[index - 1] != null

    // ---------------------------------------------------------------- level grid

    /**
     * The level grid's rows. Ported from InitGrid (frmOverview.frm:2111).
     *
     * VB's class column shows the name of the build class slot the level's class is in. A level
     * with no class shows the last empty slot's name, which is empty. Quirk not copied: with all
     * three slots filled, a level with no class makes VB fail ("Subscript out of range"); here
     * its name is empty.
     */
    fun gridRows(): List<OverviewGridRow> {
        val rows = ArrayList<OverviewGridRow>()
        val counts = HashMap<CharacterClass?, Int>()
        for (level in 1..rules.heroicLevels()) {
            val c = build.levelClass(level)
            counts[c] = (counts[c] ?: 0) + 1
            var name = ""
            if (c != null) {
                name = c.name
            }
            val classLevels = IntArray(3)
            for (i in 0..2) {
                classLevels[i] = counts[build.buildClasses[i]] ?: 0
            }
            rows.add(OverviewGridRow(level, name, classLevels, build.bab[level - 1]))
        }
        return rows
    }

    /**
     * 0-based build class slot of the class at [level], or -1 if the level has no class or its class isn't
     * one of the build classes. VB's `mlngBuildClass(build.Class(level)) - 1`.
     */
    fun slotOfLevel(level: Int): Int {
        val c = build.levelClass(level)
        if (c == null) {
            return -1
        }
        for (i in 0..2) {
            if (build.buildClasses[i] == c) {
                return i
            }
        }
        return -1
    }

    /**
     * A click in build class column [slot] at [level]: that level becomes that class. Ported from
     * ChangeLevelSplit (frmOverview.frm:2271). Returns false if nothing changed; the screen asks
     * about the leveling guide only when it would change ([canChangeLevelSplit]).
     */
    fun changeLevelSplit(level: Int, slot: Int): Boolean {
        if (!canChangeLevelSplit(level, slot)) {
            return false
        }
        build.levelClasses[level - 1] = build.buildClasses[slot]
        dataChanged()
        return true
    }

    fun canChangeLevelSplit(level: Int, slot: Int): Boolean {
        val c = build.buildClasses[slot]
        return c != null && build.levelClass(level) != c
    }

    /**
     * Whether the class of level [from] can be dragged onto level [to] in the class column
     * (picGrid_OLEDragOver, frmOverview.frm:2289): only onto a level with a different class.
     */
    fun canSwapLevels(from: Int, to: Int): Boolean = build.levelClass(to) != build.levelClass(from)

    /** True if dragging from [from] has anywhere to go (DragClass, :2254). */
    fun canDragLevel(from: Int): Boolean {
        for (level in 1..rules.heroicLevels()) {
            if (build.levelClass(level) != build.levelClass(from)) {
                return true
            }
        }
        return false
    }

    /**
     * The class of level [from] dropped on level [to]: the two levels swap classes. Ported from
     * picGrid_OLEDragDrop (frmOverview.frm:2303) and picGrid_OLECompleteDrag (:2318).
     */
    fun swapLevels(from: Int, to: Int): Boolean {
        if (!canSwapLevels(from, to)) {
            return false
        }
        val swap = build.levelClasses[to - 1]
        build.levelClasses[to - 1] = build.levelClasses[from - 1]
        build.levelClasses[from - 1] = swap
        dataChanged()
        return true
    }
}

/**
 * A row of the race list: a race, or a heading (null [race]) with the race type's name.
 *
 * @property classHeader the heading of the Iconic races, which also labels the class column.
 * @property iconicClass the class an iconic race starts as.
 */
class RaceRow(val race: Race?, val caption: String, val classHeader: Boolean, val iconicClass: CharacterClass? = null)
