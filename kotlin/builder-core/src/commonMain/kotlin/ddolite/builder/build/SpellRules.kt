package ddolite.builder.build

import ddolite.builder.model.CharacterClass

/**
 * A spell offered in the Spells screen's list: the name as listed (rare Wizard and Artificer
 * scrolls indented and starred, "  Name*") and the spell's own name.
 */
class ListedSpell(val display: String, val name: String, val rare: Boolean)

/**
 * The Spells screen's rules: which classes have spells, the spells offered, free spell slots and
 * clearing. Ported from basBuild.bas (`AddFreeSpellSlot` :2142, `RemoveEmptyFreeSlots` :2158) and
 * frmSpells (Source/Forms/Build/frmSpells.frm). The slots themselves come from
 * [BuildRules.initBuildSpells].
 */
class SpellRules(val rules: BuildRules) {
    val build: Build get() = rules.build
    private val data = rules.data

    /**
     * The classes in the class list: the build's classes (most levels first) that can cast level 1
     * spells and have level 1 slots at their levels. Ported from frmSpells.LoadData (frmSpells.frm:258,
     * the cboClass part).
     */
    fun spellClasses(): List<CharacterClass> {
        val result = ArrayList<CharacterClass>()
        for (split in rules.classSplit()) {
            val c = split.characterClass
            if (c.canCastSpell[1] != 0 && c.maxSpellLevel > 0 && c.spellSlots(split.levels, 1) != 0) {
                result.add(c)
            }
        }
        return result
    }

    /** The slots of [c]'s spell level [spellLevel] (1-based); null if there are none. */
    fun level(c: CharacterClass, spellLevel: Int): BuildSpellLevel? {
        val spells = build.spellsFor(c) ?: return null
        if (spellLevel < 1 || spellLevel > spells.levels.size) {
            return null
        }
        return spells.level(spellLevel)
    }

    /** The Acquired column: "Level 7", or "Pact @ 7" for a warlock pact slot. frmSpells.GetLevelText (:420). */
    fun levelText(slot: BuildSpellSlot): String {
        if (slot.type == SpellSlotType.WARLOCK_PACT) {
            return "Pact @ " + slot.level
        }
        return "Level " + slot.level
    }

    /** frmSpells.SpellTaken (:362): the spell is in one of this level's slots (exact name). */
    fun spellTaken(level: BuildSpellLevel, spell: String): Boolean {
        for (slot in level.slots) {
            if (slot.spell == spell) {
                return true
            }
        }
        return false
    }

    /**
     * The spells offered: the class's list for the level, without those already in a slot. For
     * Wizards and Artificers, rare scrolls are shown "  Name*". Ported from frmSpells.ShowAvailable (:330)
     * and MakeSpellName (:596).
     */
    fun availableSpells(c: CharacterClass, spellLevel: Int): List<ListedSpell> {
        val result = ArrayList<ListedSpell>()
        val level = level(c, spellLevel) ?: return result
        for (name in c.spellList(spellLevel)) {
            if (spellTaken(level, name)) {
                continue
            }
            var display = name
            var rare = false
            if (c.name == "Wizard" || c.name == "Artificer") {
                val spell = data.findSpell(name)
                if (spell != null && spell.rare) {
                    display = "  $name*"
                    rare = true
                }
            }
            result.add(ListedSpell(display, name, rare))
        }
        return result
    }

    /** frmSpells.CleanSpellName (:607): the list's text without the indent and "*". */
    fun cleanSpellName(display: String): String {
        var name = display.trim()
        if (name.endsWith("*")) {
            name = name.substring(0, name.length - 1)
        }
        return name
    }

    /**
     * Adds an empty free slot at the top of the level. Ported from `AddFreeSpellSlot` (basBuild.bas:2142).
     *
     * VB quirk kept: the new slot's level is the class level of the first slot, not converted to a
     * character level as [BuildRules.initBuildSpells] does, so it can show a different "Level" until
     * the slots are set up again (e.g. when the build is reopened).
     */
    fun addFreeSpellSlot(c: CharacterClass, spellLevel: Int) {
        val spells = build.spellsFor(c) ?: return
        val level = level(c, spellLevel) ?: return
        level.freeSlots++
        val slot = BuildSpellSlot()
        slot.type = SpellSlotType.FREE
        slot.spell = ""
        var classLevel = 0
        for (cl in 1..spells.classLevels) {
            if (c.spellSlots(cl, spellLevel) >= 1) {
                classLevel = cl
                break
            }
        }
        slot.level = classLevel
        level.slots.add(0, slot)
    }

    /**
     * Removes empty free slots. Ported from `RemoveEmptyFreeSlots` (basBuild.bas:2158).
     *
     * VB quirk kept: after removing a slot the loop moves on without looking at the slot that moved
     * into its place, so of two empty free slots in a row only the first goes.
     */
    fun removeEmptyFreeSlots(c: CharacterClass, spellLevel: Int) {
        val level = level(c, spellLevel) ?: return
        var i = 0
        while (i < level.slots.size) {
            val slot = level.slots[i]
            if (slot.type == SpellSlotType.FREE && slot.spell.isEmpty()) {
                level.slots.removeAt(i)
                level.freeSlots--
            }
            i++
        }
    }

    /** Empties every standard and free slot of [c], removing empty free slots. frmSpells.ClearClassSpells (:525). */
    fun clearClassSpells(c: CharacterClass) {
        val spells = build.spellsFor(c) ?: return
        for (spellLevel in 1..spells.maxSpellLevel) {
            val level = spells.level(spellLevel)
            for (slot in level.slots) {
                if (slot.type == SpellSlotType.STANDARD || slot.type == SpellSlotType.FREE) {
                    slot.spell = ""
                }
            }
            removeEmptyFreeSlots(c, spellLevel)
        }
    }

    /** frmSpells.ClearAllSpells (:545): every class with spell levels. */
    fun clearAllSpells() {
        for (spells in build.classSpells) {
            if (spells.maxSpellLevel > 0) {
                clearClassSpells(spells.characterClass)
            }
        }
    }

    /** True if [spell] is one of the class's free spells (a free slot is added for it). `CheckFree` (basBuild.bas:2188). */
    fun isFree(c: CharacterClass, spell: String): Boolean = rules.isFreeSpell(c, spell)

    /**
     * The details box for a spell: its description and wiki link, or "Spell not found.". Ported from
     * frmSpells.ShowDetails (:748). Returns the caption and lines; an empty name gives "Details" and nothing.
     */
    fun details(display: String): FeatDetails {
        val name = cleanSpellName(display)
        if (name.isEmpty()) {
            return FeatDetails("Details", emptyList())
        }
        val out = DetailsBuilder()
        val spell = data.findSpell(name)
        if (spell == null) {
            out.addText("Spell not found.")
        } else {
            out.addDescrip(spell.description, makeWiki(spell.wiki))
        }
        return FeatDetails(name, out.build())
    }
}
