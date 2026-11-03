package ddolite.builder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.BuildSpellLevel
import ddolite.builder.build.BuildSpellSlot
import ddolite.builder.build.DropState
import ddolite.builder.build.FeatDetails
import ddolite.builder.build.ListedSpell
import ddolite.builder.build.SpellRules
import ddolite.builder.build.SpellSlotType
import ddolite.builder.model.CharacterClass

/** What's being dragged on the Spells window: a spell from the list, or a slot's spell by its row. */
sealed class SpellDrag {
    data class FromList(val display: String) : SpellDrag()
    data class FromSlot(val row: Int) : SpellDrag()
}

/** A spell slot row as a drop target. */
data class SpellSlotKey(val row: Int)

/** The spell list, which takes slots dragged off (lstSpell_OLEDragDrop clears them). */
object SpellListKey

/**
 * The state behind the Spells window: VB's frmSpells (Source/Forms/Build/frmSpells.frm). The
 * rules are in builder-core's [SpellRules]; this edits `build.Spell` directly, as VB does, and
 * only marks the build dirty (VB's SetDirty; no cascade).
 *
 * Rows are numbered from 1 as VB's userList; 0 means none.
 */
class SpellsScreenState(private val session: BuilderSession) {
    /** The class shown (menClass) and spell level (mlngSpellLevel). */
    var spellClass: CharacterClass? by mutableStateOf(null)
        private set
    var spellLevel by mutableIntStateOf(1)
        private set

    /** The class list (cboClass); hidden with only one class. */
    var classes: List<CharacterClass> by mutableStateOf(emptyList())
        private set

    /** The slots' drop states (usrList), index row - 1. */
    var dropStates: List<DropState> by mutableStateOf(emptyList())
        private set

    /** The slot under the mouse (usrList.Active). */
    var hoverRow by mutableIntStateOf(0)

    /** The spell list (lstSpell) and its highlighted row (-1 = none). */
    var spells: List<ListedSpell> by mutableStateOf(emptyList())
        private set
    var listIndex by mutableIntStateOf(-1)
        private set

    /** The details box. */
    var details by mutableStateOf(FeatDetails("Details", emptyList()))
        private set

    /** The slot whose menu is showing; 0 = none. */
    var menuRow by mutableIntStateOf(0)
        private set

    /** Bumped after each change, so the window redraws. */
    var revision by mutableIntStateOf(0)
        private set

    val drag = DragDropState<SpellDrag>()

    // mlngSourceIndex and mlngLeaveHighlighted.
    private var sourceRow = 0
    private var leaveHighlighted = 0

    private val rules: SpellRules?
        get() {
            val r = session.rules ?: return null
            return SpellRules(r)
        }

    private fun changed() {
        revision++
    }

    /** SetDirty after a change to the spells. */
    private fun spellsChanged() {
        session.markDirty()
        changed()
    }

    /** The slots of the level shown. */
    fun level(): BuildSpellLevel? {
        val c = spellClass ?: return null
        return rules?.level(c, spellLevel)
    }

    fun rows(): List<BuildSpellSlot> = level()?.slots ?: emptyList()

    /** The highest spell level of the class shown (the spinner's maximum). */
    fun maxSpellLevel(): Int {
        val c = spellClass ?: return 1
        return session.rules?.build?.spellsFor(c)?.maxSpellLevel ?: 1
    }

    /** lblRare: "* Rare scrolls are indented", or for Wizard levels 8-9 "* All level N Wizard scrolls are rare"; null if none is rare. */
    fun rareNote(): String? {
        var any = false
        for (s in spells) {
            if (s.rare) {
                any = true
            }
        }
        if (!any) {
            return null
        }
        if (spellClass?.name == "Wizard" && spellLevel > 7) {
            return "* All level $spellLevel Wizard scrolls are rare"
        }
        return "* Rare scrolls are indented"
    }

    // ---------------------------------------------------------------- form

    /** Form_Load (:195): sets up the spell slots (InitBuildSpells), then Cascade. */
    fun opened() {
        hoverRow = 0
        menuRow = 0
        session.rules?.initBuildSpells()
        cascade()
    }

    /** Cascade (:211) → LoadData (:258): keeps the class and level if it can. */
    fun cascade() {
        val r = rules ?: return
        sourceRow = 0
        leaveHighlighted = 0
        val list = r.spellClasses()
        classes = list
        var level = spellLevel
        var chosen: CharacterClass? = null
        for (c in list) {
            if (c == spellClass) {
                chosen = c
            }
        }
        if (chosen == null) {
            chosen = list.firstOrNull()
            level = 1
        }
        spellClass = chosen
        val max = maxSpellLevel()
        if (level > max) {
            level = max
        }
        spellLevel = level
        initSlots()
        showAvailable()
        details = FeatDetails("Details", emptyList())
        changed()
    }

    /** InitSlots (:313): one row per slot, none chosen. */
    private fun initSlots() {
        val states = ArrayList<DropState>()
        for (i in rows().indices) {
            states.add(DropState.DEFAULT)
        }
        dropStates = states
    }

    private fun selectRow(row: Int) {
        val states = ArrayList<DropState>()
        for (i in 1..rows().size) {
            states.add(if (i == row) DropState.CAN_DROP else DropState.DEFAULT)
        }
        dropStates = states
    }

    private fun selectedRow(): Int {
        var selected = 0
        for (i in dropStates.indices) {
            when (dropStates[i]) {
                DropState.DEFAULT -> {}
                DropState.CAN_DROP -> {
                    if (selected != 0) {
                        return 0
                    }
                    selected = i + 1
                }
                else -> return 0
            }
        }
        return selected
    }

    /** ShowAvailable (:330); keeps the list position if it can, but nothing highlighted. */
    private fun showAvailable() {
        val r = rules ?: return
        val c = spellClass
        if (c == null) {
            spells = emptyList()
            return
        }
        spells = r.availableSpells(c, spellLevel)
        listIndex = -1
    }

    /** cboClass_Click (:711) → PopulateSpellLevels (:718): back to level 1. */
    fun chooseClass(c: CharacterClass) {
        spellClass = c
        spellLevel = 1
        spellLevelChange()
        session.saveBackup()
    }

    /** usrSpinner_Change (:729) → SpellLevelChange (:735). */
    fun chooseLevel(level: Int) {
        spellLevel = level
        spellLevelChange()
        session.saveBackup()
    }

    private fun spellLevelChange() {
        initSlots()
        showAvailable()
        details = FeatDetails("Details", emptyList())
        changed()
    }

    private fun showDetails(display: String) {
        val r = rules ?: return
        details = r.details(display)
    }

    // ---------------------------------------------------------------- slots

    /** usrList_SlotClick (:456): right opens the menu; left toggles the choice. */
    fun clickSlot(row: Int, button: PointerButton) {
        listIndex = -1
        val slot = rows().getOrNull(row - 1) ?: return
        if (button == PointerButton.SECONDARY) {
            selectRow(row)
            hoverRow = row
            showDetails(slot.spell)
            menuRow = row
        } else if (selectedRow() == row) {
            noSelection()
        } else {
            selectRow(row)
            showDetails(slot.spell)
        }
        changed()
    }

    /**
     * The slot menu (ContextMenu, :471): "Clear this spell" (only for standard and free slots),
     * "Clear Class spells" (with more than one class), "Clear all spells".
     */
    fun menuItems(): List<Pair<String, Boolean>> {
        val slot = rows().getOrNull(menuRow - 1) ?: return emptyList()
        val items = ArrayList<Pair<String, Boolean>>()
        items.add(Pair("Clear this spell", slot.type == SpellSlotType.STANDARD || slot.type == SpellSlotType.FREE))
        if (classes.size > 1) {
            items.add(Pair("Clear " + (spellClass?.name ?: "") + " spells", true))
        }
        items.add(Pair("Clear all spells", true))
        return items
    }

    fun closeMenu() {
        menuRow = 0
    }

    /** mnuSpell_Click (:487), by caption. */
    fun chooseMenu(caption: String) {
        menuRow = 0
        val r = rules ?: return
        val c = spellClass ?: return
        if (caption == "Clear this spell") {
            val slot = rows().getOrNull(selectedRow() - 1) ?: return
            if (slot.type != SpellSlotType.CLERIC_CURE && slot.type != SpellSlotType.WARLOCK_PACT) {
                clearSlot(selectedRow())
            }
        } else if (caption == "Clear all spells") {
            if (classes.size > 1) {
                if (!session.ask("Clear all spells for all classes and levels?")) {
                    return
                }
                r.clearAllSpells()
            } else {
                if (!session.ask("Clear all spells for all levels?")) {
                    return
                }
                r.clearClassSpells(c)
            }
            afterClearClass()
        } else {
            if (!session.ask("Clear all " + c.name + " spells for all levels?")) {
                return
            }
            r.clearClassSpells(c)
            afterClearClass()
        }
    }

    private fun afterClearClass() {
        initSlots()
        showAvailable()
        spellsChanged()
    }

    /** usrList_SlotDblClick (:483). */
    fun doubleClickSlot(row: Int) {
        clearSlot(row)
    }

    /** ClearSlot (:511): only standard and free slots; an emptied free slot is removed. */
    private fun clearSlot(row: Int) {
        val r = rules ?: return
        val c = spellClass ?: return
        val slot = rows().getOrNull(row - 1) ?: return
        r.clearSlot(c, spellLevel, slot)
        initSlots()
        showAvailable()
        details = FeatDetails("Details", emptyList())
        spellsChanged()
    }

    // ---------------------------------------------------------------- the spell list

    /** lstSpell_MouseDown (:654): highlights the spell and shows it. */
    fun pressSpell(index: Int) {
        val spell = spells.getOrNull(index) ?: return
        listIndex = index
        selectRow(0)
        hoverRow = 0
        showDetails(spell.display)
        changed()
    }

    /**
     * lstSpell_DblClick (:623) and Space/Enter (lstSpell_KeyDown, :701) → ChooseSpell (:627): a free
     * spell gets a new free slot; anything else goes in the first empty slot.
     */
    fun chooseSpell() {
        val r = rules ?: return
        val c = spellClass ?: return
        val listed = spells.getOrNull(listIndex) ?: return
        val name = r.cleanSpellName(listed.display)
        var row = 0
        if (r.isFree(c, name)) {
            r.addFreeSpellSlot(c, spellLevel)
            row = 1
        } else {
            val slots = rows()
            for (i in slots.indices) {
                if (slots[i].spell.isEmpty()) {
                    row = i + 1
                    break
                }
            }
        }
        if (row == 0) {
            return
        }
        (rules ?: return).setSpell(rows()[row - 1], name)
        initSlots()
        showAvailable()
        selectRow(row)
        hoverRow = row
        showDetails(name)
        spellsChanged()
    }

    // ---------------------------------------------------------------- dragging

    /**
     * A drag is starting. From the list (lstSpell_MouseMove, :663) or a standard or free slot
     * (usrList_RequestDrag, :553); shows where it can go (ShowDropSlots, :375): free spells to free
     * slots and others to standard ones (a free spell from the list gets a new free slot first).
     */
    fun startDrag(item: SpellDrag): Boolean {
        val r = rules ?: return false
        val c = spellClass ?: return false
        var sourceFree: Boolean
        if (item is SpellDrag.FromSlot) {
            val slot = rows().getOrNull(item.row - 1) ?: return false
            selectRow(0)
            if (slot.type != SpellSlotType.STANDARD && slot.type != SpellSlotType.FREE) {
                return false
            }
            leaveHighlighted = item.row
            sourceRow = item.row
            sourceFree = slot.type == SpellSlotType.FREE
            showDetails(slot.spell)
        } else {
            if (listIndex == -1) {
                return false
            }
            sourceRow = 0
            // VB checks the list's text as shown, so a rare (starred) free spell isn't seen as free.
            sourceFree = r.isFree(c, spells[listIndex].display)
            if (sourceFree) {
                r.addFreeSpellSlot(c, spellLevel)
            }
        }
        val slots = rows()
        val states = ArrayList<DropState>()
        for (i in slots.indices) {
            val slot = slots[i]
            var state = DropState.DEFAULT
            if (slot.type != SpellSlotType.CLERIC_CURE && slot.type != SpellSlotType.WARLOCK_PACT) {
                val destFree = slot.type == SpellSlotType.FREE
                if (sourceFree == destFree && i + 1 != sourceRow) {
                    state = DropState.CAN_DROP
                }
            }
            states.add(state)
        }
        dropStates = states
        changed()
        return true
    }

    /**
     * A drag ended on [target]: usrList_OLEDragDrop (:565) fills or swaps the slot, lstSpell_OLEDragDrop
     * (:688) clears a slot dragged onto the list; then CompleteDrag (:444) removes an unused free slot.
     *
     * VB bug kept: a spell dropped from the list is stored as the list shows it, so a rare Wizard or
     * Artificer scroll is saved as "  Name*" (ChooseSpell, by double-click, stores the plain name).
     */
    fun dropped(item: SpellDrag, target: Any?) {
        val r = rules
        val c = spellClass
        if (r != null && c != null) {
            if (target is SpellSlotKey && dropStates.getOrNull(target.row - 1) == DropState.CAN_DROP) {
                val slots = rows()
                if (item is SpellDrag.FromList) {
                    r.setSpell(slots[target.row - 1], item.display)
                    showAvailable()
                    showDetails(item.display)
                    hoverRow = target.row
                    spellsChanged()
                } else if (item is SpellDrag.FromSlot && sourceRow != 0) {
                    r.swapSpells(slots[target.row - 1], slots[sourceRow - 1])
                    showAvailable()
                    spellsChanged()
                }
                leaveHighlighted = target.row
            } else if (target == SpellListKey && item is SpellDrag.FromSlot && sourceRow != 0) {
                val from = sourceRow
                sourceRow = 0
                clearSlot(from)
                leaveHighlighted = 0
            }
            sourceRow = 0
            r.removeEmptyFreeSlots(c, spellLevel)
        }
        initSlots()
        selectRow(leaveHighlighted)
        leaveHighlighted = 0
        changed()
    }

    // ---------------------------------------------------------------- details

    /** NoSelection (:782). */
    private fun noSelection() {
        listIndex = -1
        selectRow(0)
        details = FeatDetails("Details", emptyList())
    }
}
