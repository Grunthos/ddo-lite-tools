package ddolite.prototype.skills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.CascadeChange
import ddolite.builder.build.DropState
import ddolite.builder.build.FeatDetails
import ddolite.builder.build.FeatListEntry
import ddolite.builder.build.FeatRules
import ddolite.builder.build.FeatTab
import ddolite.builder.model.Feat
import ddolite.builder.model.FeatGroup
import ddolite.builder.model.SelectorStyle

/** What's being dragged on the Feats screen. */
sealed class FeatDrag {
    /** A feat (and maybe a selector) from the feat or selector list (VB's lstFeat / lstSub OLEDrag). */
    data class FromList(val feat: Feat) : FeatDrag()

    /** A slot's feat, by its row on the tab (usrList's RequestDrag). */
    data class FromSlot(val row: Int) : FeatDrag()
}

/** A feat slot row as a drop target. */
data class FeatSlotKey(val row: Int)

/** The feat list, which takes slots dragged onto it (lstFeat_OLEDragDrop clears the slot). */
object FeatListKey

/** One selector in the selector list (lstSub): its name and number (1-based). */
class SelectorItem(val name: String, val number: Int)

/**
 * The state behind the Feats window: VB's frmFeats module variables and event handlers
 * (Source/Forms/Build/frmFeats.frm). The rules are in builder-core's [FeatRules]; this keeps the
 * window's lists, the slots' drop states and what's chosen, and reports changes to the session.
 *
 * Rows of the slot list are numbered from 1 as VB's usrList; 0 means none.
 */
class FeatsScreenState(private val session: PrototypeState) {
    /** The tab showing (menChannel). */
    var tab by mutableStateOf(FeatTab.SELECTED)
        private set

    /** The header's tabs (LoadChannels). */
    var tabs: List<FeatTab> by mutableStateOf(listOf(FeatTab.SELECTED, FeatTab.GRANTED))
        private set

    /** "Channels": a tab per kind of class bonus feat (cfg.FeatChannels, default off). */
    var channels by mutableStateOf(false)
        private set

    /** The chosen slot's row (mlngActive); 0 = none. */
    var active by mutableIntStateOf(0)
        private set

    /** The row under the mouse, drawn with the highlighted border (usrList.Active). */
    var hoverRow by mutableIntStateOf(0)

    /** Each row's drop state (usrSlot.DropState), index row - 1. */
    var dropStates: List<DropState> by mutableStateOf(emptyList())
        private set

    /** "Show All" (usrchkShowAll). */
    var showAll by mutableStateOf(false)
        private set

    /** The chosen filter groups (lstGroup, multi-select); index 0 is "Show All Feats". */
    var groupSelection: Set<Int> by mutableStateOf(setOf(0))
        private set

    /** The feat list (lstFeat) and the highlighted feat in it (-1 = none). */
    var feats: List<Feat> by mutableStateOf(emptyList())
        private set
    var featIndex by mutableIntStateOf(-1)
        private set

    /** The selector list (lstSub, sorted by name) and the highlighted selector (-1 = none). */
    var selectors: List<SelectorItem> by mutableStateOf(emptyList())
        private set
    var selectorIndex by mutableIntStateOf(-1)
        private set

    /** The feat list's title (lblFeats): "Feats", or the chosen slot's "Level 3", "Ftr 2", …. */
    var listTitle by mutableStateOf("Feats")
        private set

    /** The details box. */
    var details by mutableStateOf(FeatDetails("Details", emptyList()))
        private set

    /** The details box is widened over the filter list (DetailsExpand). */
    var detailsExpanded by mutableStateOf(false)
        private set

    /** The "<<" link that restores the details box (lblRestore.Visible). */
    var restoreLinkShown by mutableStateOf(false)
        private set

    /** The row whose context menu is showing (mnuMain(0)); 0 = none. */
    var menuRow by mutableIntStateOf(0)
        private set

    /** Bumped after each change, so the window redraws. */
    var revision by mutableIntStateOf(0)
        private set

    val drag = DragDropState<FeatDrag>()

    // The button of the last press on a slot or list, so only a left-button drag starts a drag.
    internal var lastButton = PointerButton.PRIMARY

    // mlngSourceIndex: the row a slot drag started from (0 = a drag from the lists).
    private var sourceRow = 0

    // mlngSlotFilter and mblnDropped: the chosen slot when a drag from the lists began, and whether
    // that drag put a feat in a slot (CompleteDrag).
    private var slotFilter = 0
    private var dropped = false

    private val featRules: FeatRules?
        get() {
            val r = session.rules ?: return null
            return FeatRules(r)
        }

    /** The slot list's rows on the current tab (usrList's ItemData), index row - 1. */
    fun rows(): List<FeatListEntry> {
        val r = session.rules ?: return emptyList()
        val result = arrayOfNulls<FeatListEntry>(r.featTabCounts[tab.ordinal])
        for (entry in r.featList) {
            val row = entry.tabRows[tab.ordinal]
            if (row > 0 && featRules!!.isOnTab(entry, tab)) {
                result[row - 1] = entry
            }
        }
        val list = ArrayList<FeatListEntry>()
        for (entry in result) {
            if (entry != null) {
                list.add(entry)
            }
        }
        return list
    }

    private fun entry(row: Int): FeatListEntry? {
        val rows = rows()
        if (row < 1 || row > rows.size) {
            return null
        }
        return rows[row - 1]
    }

    // ---------------------------------------------------------------- form

    /**
     * The window opened, or the build changed elsewhere. Ported from Cascade (frmFeats.frm:312),
     * LoadData (:426) and LoadChannels (:448).
     */
    fun cascade() {
        val r = session.rules ?: return
        groupSelection = setOf(0)
        // VB sets menChannel to fceUnknown, so ChangeTab doesn't restore a details box widened on
        // the Granted tab (quirk kept: it stays wide, without its "<<" link, until lblDetails is clicked).
        tab = FeatTab.SELECTED
        var start = FeatTab.SELECTED
        val list = ArrayList<FeatTab>()
        if (channels) {
            for (t in FeatTab.entries) {
                if (t == FeatTab.SELECTED || t == FeatTab.GRANTED) {
                    continue
                }
                if (r.featTabCounts[t.ordinal] > 0) {
                    list.add(t)
                }
            }
            if (list.size == 1 && list[0] == FeatTab.GENERAL) {
                list.clear()
                list.add(FeatTab.SELECTED)
            } else {
                start = FeatTab.GENERAL
            }
            list.add(FeatTab.GRANTED)
        } else {
            list.add(FeatTab.SELECTED)
            list.add(FeatTab.GRANTED)
        }
        tabs = list
        // ChangeTab skips a tab that's already showing; Cascade first sets menChannel to unknown.
        showTab(start)
        sourceRow = 0
        clearDetails()
        changed()
    }

    /** True if the build has any class bonus feats with their own tab: usrchkChannels is shown (HasChannels, :411). */
    fun hasChannels(): Boolean {
        val r = session.rules ?: return false
        for (t in FeatTab.entries) {
            if (t.ordinal > FeatTab.GENERAL.ordinal && t != FeatTab.GRANTED && r.featTabCounts[t.ordinal] > 0) {
                return true
            }
        }
        return false
    }

    /** usrchkChannels_UserChange (:546). */
    fun changeChannels(value: Boolean) {
        channels = value
        cascade()
    }

    /** usrHeader_Click (:343) → ChangeTab (:364). */
    fun chooseTab(newTab: FeatTab) {
        if (tab == newTab) {
            return
        }
        showTab(newTab)
        changed()
    }

    private fun showTab(newTab: FeatTab) {
        clearDetails()
        if (newTab == FeatTab.GRANTED) {
            detailsExpanded = true
        } else if (tab == FeatTab.GRANTED) {
            detailsExpanded = false
        }
        restoreLinkShown = false
        tab = newTab
        resetDropStates()
        chooseSlot(0)
    }

    private fun resetDropStates() {
        val states = ArrayList<DropState>()
        for (i in rows().indices) {
            states.add(DropState.DEFAULT)
        }
        dropStates = states
    }

    private fun changed() {
        revision++
    }

    /** FeatsChanged (:334): CascadeChanges cceFeat and SetDirty. */
    private fun featsChanged() {
        val r = session.rules ?: return
        r.cascadeChanges(CascadeChange.FEAT)
        session.buildChanged(CascadeChange.FEAT)
        changed()
    }

    // ---------------------------------------------------------------- the chosen slot

    /**
     * Chooses slot [row] (0 = none). Ported from the Active property (frmFeats.frm:559): the
     * slot is marked, its feat's details shown, and the feat list refiltered for it.
     */
    fun chooseSlot(row: Int) {
        active = row
        setSelected(row)
        hoverRow = row
        if (row == 0) {
            details = FeatDetails("Details", emptyList())
        } else {
            val e = entry(row)
            showDetails(e?.feat, e?.selector ?: 0)
        }
        if (tab != FeatTab.GRANTED) {
            showAvailable()
        }
        changed()
    }

    /** usrList.Selected = [row]: that row can-drop, every other row default (userList.ctl). */
    private fun setSelected(row: Int) {
        val states = ArrayList<DropState>()
        for (i in 1..rows().size) {
            if (i == row) {
                states.add(DropState.CAN_DROP)
            } else {
                states.add(DropState.DEFAULT)
            }
        }
        dropStates = states
    }

    /**
     * usrList.Selected as VB reads it back: the only can-drop row while every other row is default;
     * 0 if there's none, more than one, or any row in another state (as while related slots or drop
     * targets are shown).
     */
    fun selectedRow(): Int {
        var selected = 0
        for (i in dropStates.indices) {
            when (dropStates[i]) {
                DropState.DEFAULT -> {}
                DropState.CAN_DROP -> {
                    if (selected == 0) {
                        selected = i + 1
                    } else {
                        return 0
                    }
                }
                else -> return 0
            }
        }
        return selected
    }

    /** A slot pressed: usrList_SlotClick (:700). Left toggles the choice; right opens the menu. */
    fun clickSlot(row: Int, button: PointerButton) {
        if (button == PointerButton.PRIMARY) {
            if (active == row) {
                chooseSlot(0)
            } else {
                chooseSlot(row)
                showRelated()
            }
        } else if (button == PointerButton.SECONDARY && tab != FeatTab.GRANTED) {
            chooseSlot(row)
            showRelated()
            if (entry(row) != null) {
                menuRow = row
            }
        }
    }

    /** usrList_SlotDblClick (:714): clears the slot. */
    fun doubleClickSlot(row: Int) {
        if (tab != FeatTab.GRANTED) {
            clearSlot(row)
        }
    }

    /**
     * Marks the chosen slot's alternates and exchanges, or the slot it belongs to, as related.
     * Ported from ShowRelated (frmFeats.frm:718).
     */
    fun showRelated() {
        val home = entry(active) ?: return
        val states = ArrayList(dropStates)
        for (e in session.rules?.featList ?: emptyList()) {
            if (e === home || e.actualType == BuildFeatType.GRANTED) {
                continue
            }
            val related = (e.effectiveType == home.actualType && e.parentIndex == home.index) ||
                (e.actualType == home.effectiveType && e.index == home.parentIndex)
            val row = e.tabRows[tab.ordinal]
            if (related && row > 0 && row <= states.size) {
                states[row - 1] = DropState.SUBORDINATE
            }
        }
        dropStates = states
    }

    // ---------------------------------------------------------------- context menu

    /** The context menu's items for [menuRow]; "-" is a separator. ContextMenu (:749). */
    fun menuItems(): List<String> {
        val e = entry(menuRow) ?: return emptyList()
        val items = arrayListOf("Clear This Feat", "Clear All Feats", "-")
        if (e.actualType == BuildFeatType.ALTERNATE || e.actualType == BuildFeatType.EXCHANGE) {
            items.add("Delete This Slot")
        } else {
            items.add("Add Alternate")
            val maxLevels = session.rules?.build?.maxLevels ?: 0
            if (e.level < maxLevels && e.exchangeIndex < 0) {
                items.add("Exchange Feat")
            }
        }
        return items
    }

    fun closeMenu() {
        menuRow = 0
    }

    /** mnuFeat_Click (:770), by caption. */
    fun chooseMenu(caption: String) {
        menuRow = 0
        if (active == 0) {
            return
        }
        when (caption) {
            "Clear This Feat" -> clearSlot(active)
            "Clear All Feats" -> clearAllSlots()
            "Add Alternate" -> addAlternate()
            "Exchange Feat" -> exchangeFeat()
            "Delete This Slot" -> deleteSlot()
        }
    }

    /** ClearSlot (:823). */
    private fun clearSlot(row: Int) {
        val fr = featRules ?: return
        val e = entry(row) ?: return
        fr.setSlot(e, null, 0)
        fr.checkSlotErrors()
        resetDropStates()
        chooseSlot(0)
        featsChanged()
    }

    /** ClearAllSlots (:837), after asking. */
    private fun clearAllSlots() {
        val fr = featRules ?: return
        if (!session.ask("Clear all feats?")) {
            return
        }
        fr.clearAllFeats()
        resetDropStates()
        chooseSlot(0)
        featsChanged()
    }

    /** AddAlternate (:869). */
    private fun addAlternate() {
        val fr = featRules ?: return
        val e = entry(active) ?: return
        val position = fr.addSpecialFeat(BuildFeatType.ALTERNATE, e.actualType, e.index, 0)
        featListChange(position, false)
    }

    /**
     * ExchangeFeat (:881): asks for the level (frmLevel), adds the exchange slot, and if the slot
     * can't be exchanged, shows why ("Fred says:").
     */
    private fun exchangeFeat() {
        val fr = featRules ?: return
        val e = entry(active) ?: return
        val maxLevels = session.rules?.build?.maxLevels ?: return
        val level = session.prompts.chooseLevel(e.level, maxLevels) ?: return
        val position = fr.addSpecialFeat(BuildFeatType.EXCHANGE, e.actualType, e.index, level)
        featListChange(position, true)
        val locked = fr.slotLocked(position)
        if (locked != null) {
            details = FeatDetails("Fred says:", listOf(ddolite.builder.build.DetailLine(locked, null, true)))
        }
    }

    /** DeleteSlot (:905). */
    private fun deleteSlot() {
        val fr = featRules ?: return
        val e = entry(active) ?: return
        fr.deleteFeatSlot(e.actualType, e.index)
        featListChange(-1, false)
    }

    /**
     * After slots were added or removed: relist, choosing the new slot. FeatListChange (:922);
     * [position] is the new slot's place in the feat list (-1 = none).
     */
    private fun featListChange(position: Int, related: Boolean) {
        val r = session.rules ?: return
        resetDropStates()
        if (position >= 0 && position < r.featList.size) {
            val row = r.featList[position].tabRows[tab.ordinal]
            chooseSlot(row)
            if (related) {
                showRelated()
            }
        } else {
            chooseSlot(0)
        }
        featsChanged()
    }

    // ---------------------------------------------------------------- feat and selector lists

    /**
     * Refills the feat list. Ported from ShowAvailable (frmFeats.frm:1266) and FeatListTitle
     * (:1333); the rules are [FeatRules.availableFeats].
     */
    fun showAvailable() {
        val r = session.rules ?: return
        val fr = FeatRules(r)
        selectors = emptyList()
        selectorIndex = -1
        featIndex = -1
        val chosen = entry(active)
        if (tab != FeatTab.GRANTED) {
            listTitle = chosen?.sourceFilter ?: "Feats"
        }
        var level = r.build.maxLevels
        if (chosen != null) {
            level = chosen.level
        }
        feats = fr.availableFeats(tab, chosenGroups(), chosen, showAll, level, isAlternateSelected())
        changed()
    }

    /** The filter groups chosen in lstGroup ("Show All Feats" is every feat's group). */
    private fun chosenGroups(): Set<FeatGroup> {
        val result = HashSet<FeatGroup>()
        for (i in groupSelection) {
            if (i > 0) {
                result.add(FeatGroup.entries[i - 1])
            }
        }
        return result
    }

    /** True if usrList.Selected is an alternate slot (ValidSelectors' blnAlternate). */
    private fun isAlternateSelected(): Boolean {
        val e = entry(selectedRow()) ?: return false
        return e.actualType == BuildFeatType.ALTERNATE
    }

    /** lstGroup's items: "Show All Feats" and the groups (LoadData, GetFeatGroupName). */
    fun groupNames(): List<String> {
        val names = arrayListOf("Show All Feats")
        for (g in FeatGroup.entries) {
            names.add(g.displayName)
        }
        return names
    }

    /**
     * A click on filter group [index]: on its own, or with Ctrl added or removed (an Extended
     * multi-select ListBox). lstGroup_Click (:1378).
     */
    fun clickGroup(index: Int, toggle: Boolean) {
        if (toggle) {
            val set = HashSet(groupSelection)
            if (!set.remove(index)) {
                set.add(index)
            }
            groupSelection = set
        } else {
            groupSelection = setOf(index)
        }
        showAvailable()
    }

    /** usrchkShowAll_UserChange (:1374). */
    fun changeShowAll(value: Boolean) {
        showAll = value
        showAvailable()
    }

    /**
     * A feat in the list pressed: lstFeat_MouseDown (:997) and ListFeatClick (:1032). Shows its
     * details and selectors. Returns true if it has no selectors, so it can be dragged at once.
     */
    fun clickFeat(index: Int): Boolean {
        val r = session.rules ?: return false
        if (index < 0 || index >= feats.size) {
            return false
        }
        // lstFeat_MouseDown resets the drop states to the chosen slot.
        setSelected(active)
        featIndex = index
        val feat = feats[index]
        showDetails(feat, 0)
        selectors = emptyList()
        selectorIndex = -1
        if (feat.selectorStyle == SelectorStyle.NONE) {
            changed()
            return true
        }
        // ShowSelectors (:1093): the valid selectors at the build's max level, not hidden, sorted (lstSub.Sorted).
        val fr = FeatRules(r)
        val valid = fr.validSelectors(feat, r.build.maxLevels, isAlternateSelected(), showAll)
        val list = ArrayList<SelectorItem>()
        for (i in 1..feat.selectors.size) {
            if (valid[i] && !feat.selectors[i - 1].hide) {
                list.add(SelectorItem(feat.selectors[i - 1].name, i))
            }
        }
        list.sortWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
        selectors = list
        changed()
        return false
    }

    /** lstSub_Click / lstSub_MouseDown (:1208, :1220). */
    fun clickSelector(index: Int) {
        if (index < 0 || index >= selectors.size) {
            return
        }
        selectorIndex = index
        val feat = feats.getOrNull(featIndex) ?: return
        showDetails(feat, selectors[index].number)
        changed()
    }

    /** GetUserChoices (:579): the feat and selector chosen in the lists, or null if a selector is still needed. */
    private fun userChoice(): Pair<Feat, Int>? {
        val feat = feats.getOrNull(featIndex) ?: return null
        var selector = 0
        if (selectorIndex >= 0 && selectorIndex < selectors.size) {
            selector = selectors[selectorIndex].number
        }
        if (selector != 0 || selectors.isEmpty()) {
            return Pair(feat, selector)
        }
        return null
    }

    /**
     * A double-click in the feat or selector list: puts the feat in the chosen slot, or with none
     * chosen, the first empty slot it can go in. Ported from DoubleClick (frmFeats.frm:587).
     */
    fun doubleClickList() {
        val fr = featRules ?: return
        val choice = userChoice() ?: return
        val rows = rows()
        var row = active
        if (row < 1 || row > rows.size) {
            row = 0
            for (i in rows.indices) {
                val e = rows[i]
                if (e.feat != null) { // VB: FeatID = 0, which includes a name the data doesn't have
                    continue
                }
                val check = e.slot.copy()
                check.selector = choice.second
                val state = fr.checkFeatSlot(choice.first, check).state
                if (state == DropState.CAN_DROP || state == DropState.CAN_DROP_ERROR) {
                    row = i + 1
                    break
                }
            }
            if (row == 0) {
                return
            }
        }
        val e = rows[row - 1]
        fr.setSlot(e, choice.first, choice.second)
        fr.checkSlotErrors()
        resetDropStates()
        active = 0
        hoverRow = row
        showAvailable()
        featsChanged()
    }

    // ---------------------------------------------------------------- dragging

    /**
     * A drag is starting. From a slot: usrList_RequestDrag (:781), only off the Granted tab and
     * when no other slot drag is under way. From the lists: lstFeat_MouseMove / lstSub_MouseMove
     * (:1007, :1231), only once a feat (and selector, if it has them) is chosen. Shows where the feat
     * can go (ShowDropSlots, :650). Returns false to refuse the drag.
     */
    fun startDrag(item: FeatDrag): Boolean {
        if (item is FeatDrag.FromSlot) {
            if (tab == FeatTab.GRANTED || sourceRow != 0) {
                return false
            }
            val e = entry(item.row) ?: return false
            chooseSlot(item.row)
            sourceRow = item.row
            if (e.feat == null) {
                // VB allows the drag but ShowDropSlots shows no targets.
                return true
            }
            showDropSlots(e.feat!!, e.selector)
            return true
        }
        val choice = userChoice() ?: return false
        slotFilter = selectedRow()
        showDropSlots(choice.first, choice.second)
        return true
    }

    private fun showDropSlots(feat: Feat, selector: Int) {
        val fr = featRules ?: return
        val states = ArrayList<DropState>()
        for (e in rows()) {
            val check = e.slot.copy()
            check.selector = selector
            states.add(fr.checkFeatSlot(feat, check).state)
        }
        dropStates = states
        changed()
    }

    /**
     * A drag ended on [target] (null = nowhere). The drop handlers usrList_OLEDragDrop (:791),
     * lstFeat_OLEDragDrop (:1085), then CompleteDrag (:632).
     */
    fun dropped(item: FeatDrag, target: Any?) {
        if (tab != FeatTab.GRANTED) {
            if (item is FeatDrag.FromList && target is FeatSlotKey) {
                slotDrop(target.row)
                dropped = true
            } else if (item is FeatDrag.FromSlot && target is FeatSlotKey && target.row != sourceRow) {
                swapSlots(sourceRow, target.row)
            } else if (item is FeatDrag.FromSlot && target == FeatListKey) {
                clearSlot(sourceRow)
            }
        }
        completeDrag()
    }

    /** CompleteDrag (:632). */
    private fun completeDrag() {
        sourceRow = 0
        val states = ArrayList<DropState>()
        for (i in rows().indices) {
            states.add(DropState.DEFAULT)
        }
        if (slotFilter != 0 && !dropped && slotFilter <= states.size) {
            states[slotFilter - 1] = DropState.CAN_DROP
        } else {
            dropped = false
            slotFilter = 0
        }
        dropStates = states
        changed()
    }

    /** SlotDrop (:942): a feat from the lists dropped on slot [row]. */
    private fun slotDrop(row: Int) {
        val fr = featRules ?: return
        val state = dropStates.getOrNull(row - 1) ?: return
        if (state != DropState.CAN_DROP && state != DropState.CAN_DROP_ERROR) {
            return
        }
        val choice = userChoice() ?: return
        val e = entry(row) ?: return
        val keepSelectors = selectors.size > 1
        fr.setSlot(e, choice.first, choice.second)
        fr.checkSlotErrors()
        active = 0
        showAvailable()
        if (keepSelectors) {
            // VB reselects the feat, which shows its remaining selectors.
            val index = feats.indexOf(choice.first)
            if (index >= 0) {
                clickFeat(index)
            }
        }
        featsChanged()
    }

    /** SwapSlots (:966). */
    private fun swapSlots(from: Int, to: Int) {
        val fr = featRules ?: return
        val one = entry(from) ?: return
        val two = entry(to) ?: return
        val feat = one.feat
        val selector = one.selector
        fr.setSlot(one, two.feat, two.selector)
        fr.setSlot(two, feat, selector)
        fr.checkSlotErrors()
        active = 0
        showAvailable()
        featsChanged()
    }

    // ---------------------------------------------------------------- details

    private fun clearDetails() {
        details = FeatDetails("Details", emptyList())
    }

    /** ShowDetails (:1489) for [feat]; the chosen slot's error shows when no feat is highlighted in the list. */
    private fun showDetails(feat: Feat?, selector: Int) {
        val fr = featRules ?: return
        var errorSlot: FeatListEntry? = null
        if (active != 0 && featIndex == -1) {
            errorSlot = entry(active)
        }
        details = fr.details(feat, selector, errorSlot)
    }

    /** lblDetails_MouseDown, lblExpand_MouseDown, lblRestore_MouseDown (:1685-:1726). */
    fun toggleDetails() {
        if (tab == FeatTab.GRANTED) {
            return
        }
        detailsExpanded = !detailsExpanded
        restoreLinkShown = detailsExpanded
    }

    /**
     * The window opened (Form_Load, frmFeats.frm:285): a new form, so the details box starts
     * normal size, then Cascade.
     */
    fun opened() {
        detailsExpanded = false
        restoreLinkShown = false
        cascade()
    }
}
