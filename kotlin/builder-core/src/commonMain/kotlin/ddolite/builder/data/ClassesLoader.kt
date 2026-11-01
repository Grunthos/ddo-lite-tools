package ddolite.builder.data

import ddolite.builder.model.Alignment
import ddolite.builder.model.Bab
import ddolite.builder.model.BonusFeatSlot
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.ClassColor
import ddolite.builder.model.GrantedFeat
import ddolite.builder.model.Pact
import ddolite.builder.model.Skill
import ddolite.dataformat.DataSource
import ddolite.dataformat.RawRecord
import ddolite.dataformat.Vb
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

private const val CLASSES_FILE = "Classes.txt"

/** Class levels per class (heroic levels 1-20), the size of VB's per-level class arrays. */
private const val CLASS_LEVELS = 20

/**
 * Loads Classes.txt. Ported from `basDataLoad.LoadClasses` (Build/basDataLoad.bas:213).
 *
 * Returns the classes in VB enum order (see [CharacterClass.vbIndex]), then any classes VB6
 * doesn't know, in file order. A class that appears twice replaces the earlier one, as in VB.
 */
fun loadClasses(source: DataSource, log: LoadLog): List<CharacterClass> {
    val decoded = source.readText(BuilderRecordFile.CLASSES.path)
    if (decoded == null) {
        log.fileNotFound(CLASSES_FILE)
        return emptyList()
    }

    val byName = LinkedHashMap<String, CharacterClass>()
    for (record in splitRecords(decoded.text, BuilderRecordFile.CLASSES.marker).records) {
        // VB only loads records that contain a "BAB: " line.
        if (!record.body.contains("BAB: ")) {
            log.invalidSection(CLASSES_FILE, record.line, "BAB: ", firstLine(record))
            continue
        }
        val loaded = ClassRecordReader(record, log).read()
        byName[loaded.name.lowercase()] = loaded
    }

    val known = ArrayList<CharacterClass>()
    val unknown = ArrayList<CharacterClass>()
    for (c in byName.values) {
        if (c.vbIndex > 0) {
            known.add(c)
        } else {
            unknown.add(c)
        }
    }
    known.sortBy { it.vbIndex }
    known.addAll(unknown)
    return known
}

/**
 * Reads one class record. Ported from `basDataLoad.LoadClass` (Build/basDataLoad.bas:236).
 *
 * A class rather than a function because VB's `LoadClass` keeps a lot of state while it
 * reads (e.g. `SpellSlots` lines can only be read after `MaxSpellLevel`); the VB local
 * variables and `typNew` fields become the properties below.
 *
 * Differences from VB:
 * - A class name VB6 doesn't recognise is still loaded (plan D-7), with a warning; VB skips it.
 * - VB takes the class's display name from `GetClassName` (code), not the data; the same here
 *   for known classes, and the data name otherwise.
 * - `DisallowedClasses: A, B` keeps both classes. VB's loop uses `ReDim` without `Preserve`, so it
 *   keeps only the last; the loader warns when a line lists more than one (no current data does).
 */
private class ClassRecordReader(private val record: RawRecord, private val log: LoadLog) {
    private val dataName = firstLine(record)
    private val known = findKnownName(KNOWN_CLASSES, dataName)
    private val name: String = if (known != null) known.name else dataName

    private var abbreviation = name
    private var initials: List<String> = listOf(name.substring(0, minOf(1, name.length)))
    private var color: ClassColor? = null
    private val alignments = LinkedHashSet<Alignment>()
    private var skillPoints = 0
    private var bab: Bab? = null
    private val bonusFeats = ArrayList<BonusFeatSlot>()
    private val nativeSkills = LinkedHashSet<Skill>()
    private var maxSpellLevel = 0
    private var spellSlots: List<MutableList<Int>> = emptyList()
    private var spellLists: MutableList<List<String>> = ArrayList()
    private var freeSpells: List<String> = emptyList()
    private var mandatorySpells: List<String> = emptyList()
    private val canCastSpell = ArrayList<Int>()
    private var trees: List<String> = emptyList()
    private val grantedFeats = ArrayList<GrantedFeat>()
    private val pacts = ArrayList<Pact>()
    private var disallowedClasses: List<String> = emptyList()
    private var trapper = false

    init {
        for (i in 0 until CLASS_LEVELS) {
            bonusFeats.add(BonusFeatSlot.NONE)
        }
        for (i in 0..9) {
            canCastSpell.add(0)
        }
    }

    fun read(): CharacterClass {
        var vbIndex = 0
        if (known != null) {
            vbIndex = known.vbIndex
        } else {
            log.vbDifference(
                CLASSES_FILE, record.line,
                "Class not recognized by VB6: $dataName (VB6 needs ClassEnum, GetClassID and GetClassName updated in basEnum.bas)",
            )
        }

        val lines = record.numberedLines()
        for (i in 1 until lines.size) { // line 0 is the name
            val line = lines[i]
            val parsed = parseLineForLoad(line.text)
            if (parsed is ParsedLine.Blank) {
                continue
            }
            if (parsed is ParsedLine.Field) {
                readField(parsed, line.number, line.text)
            } else {
                invalidLine(line.number, line.text)
            }
        }

        val slots = ArrayList<List<Int>>()
        for (levelSlots in spellSlots) {
            slots.add(ArrayList(levelSlots))
        }
        return CharacterClass(
            name = name,
            vbIndex = vbIndex,
            abbreviation = abbreviation,
            initials = initials,
            color = color,
            alignments = alignments,
            skillPoints = skillPoints,
            bab = bab,
            bonusFeats = bonusFeats,
            nativeSkills = nativeSkills,
            maxSpellLevel = maxSpellLevel,
            spellSlotsByLevel = slots,
            spellListsByLevel = spellLists,
            freeSpells = freeSpells,
            mandatorySpells = mandatorySpells,
            canCastSpell = canCastSpell,
            trees = trees,
            grantedFeats = grantedFeats,
            pacts = pacts,
            disallowedClasses = disallowedClasses,
            trapper = trapper,
        )
    }

    /** The `Select Case strField` block of `LoadClass`. */
    private fun readField(parsed: ParsedLine.Field, lineNumber: Int, lineText: String) {
        when (parsed.field) {
            "abbreviation" -> abbreviation = parsed.item
            "trapper" -> {
                val value = Vb.cBool(parsed.item)
                if (value == null) {
                    // VB stops with a Type mismatch error here.
                    invalidLine(lineNumber, lineText)
                } else {
                    trapper = value
                }
            }
            "initial" -> {
                if (parsed.list.size == 4) {
                    initials = parsed.list
                } else {
                    invalidLine(lineNumber, lineText)
                }
            }
            "color" -> {
                color = ClassColor.find(parsed.item)
                if (color == null) {
                    log.loadError(CLASSES_FILE, lineNumber, "$dataName has invalid color: ${parsed.item}")
                }
            }
            "disallowedclasses" -> {
                // Checked against the loaded classes later, in loadBuilderData.
                // Like VB, each DisallowedClasses line replaces the previous one.
                disallowedClasses = parsed.list
                if (parsed.list.size > 1) {
                    log.vbDifference(
                        CLASSES_FILE, lineNumber,
                        "$dataName: VB6 keeps only the last of several DisallowedClasses on one line; put each on its own line",
                    )
                }
            }
            "alignment" -> {
                for (text in parsed.list) {
                    val alignment = Alignment.find(text)
                    if (alignment != null) {
                        alignments.add(alignment)
                    } else {
                        // "aligment" (sic) is VB's wording.
                        log.loadError(CLASSES_FILE, lineNumber, "$dataName has invalid aligment: $text")
                    }
                }
            }
            "bab" -> {
                bab = null
                for (value in Bab.entries) {
                    if (parsed.item == value.dataValue) {
                        bab = value
                    }
                }
                if (bab == null) {
                    log.loadError(CLASSES_FILE, lineNumber, "$dataName has invalid BAB: ${parsed.item}")
                }
            }
            "skillpoints" -> {
                skillPoints = Vb.cLng(Vb.valOf(parsed.item))
                if (skillPoints != 2 && skillPoints != 4 && skillPoints != 6 && skillPoints != 8) {
                    log.loadError(CLASSES_FILE, lineNumber, "$dataName has invalid skill points: ${parsed.item}")
                }
            }
            "skills" -> {
                for (text in parsed.list) {
                    val skill = Skill.find(text)
                    if (skill != null) {
                        nativeSkills.add(skill)
                    } else {
                        log.loadError(CLASSES_FILE, lineNumber, "$dataName has invalid skill: $text")
                    }
                }
            }
            "bonusfeat" -> readFeatLevels(parsed, BonusFeatSlot.BONUS, lineNumber, lineText)
            "classfeat" -> readFeatLevels(parsed, BonusFeatSlot.CLASS_ONLY, lineNumber, lineText)
            "trees" -> trees = parsed.list
            "maxspelllevel" -> {
                // VB keeps the value even when it reports it.
                maxSpellLevel = Vb.cLng(Vb.valOf(parsed.item))
                if (maxSpellLevel != 4 && maxSpellLevel != 6 && maxSpellLevel != 9) {
                    invalidLine(lineNumber, lineText)
                }
                createSpellTables()
            }
            "healingspell" -> {
                canCastSpell[0] = Vb.cLng(Vb.valOf(parsed.item))
                if (canCastSpell[0] < 1 || canCastSpell[0] > CLASS_LEVELS) {
                    invalidLine(lineNumber, lineText)
                }
            }
            "freespells" -> freeSpells = parsed.list
            "mandatoryspells" -> mandatorySpells = parsed.list
            else -> readOtherField(parsed, lineNumber, lineText)
        }
    }

    /** The `Case Else` branch of `LoadClass`: fields whose names end in a number. */
    private fun readOtherField(parsed: ParsedLine.Field, lineNumber: Int, lineText: String) {
        val field = parsed.field
        if (field.startsWith("spellslots")) {
            readSpellSlots(parsed, digitAfter(field, "spellslots"), lineNumber, lineText)
        } else if (field.startsWith("spelllist")) {
            readSpellList(parsed, digitAfter(field, "spelllist"), lineNumber, lineText)
        } else if (field.startsWith("grantedfeats")) {
            readGrantedFeats(parsed, grantedFeats, CLASSES_FILE, lineNumber, dataName, lineText, log)
        } else if (field.startsWith("pactspells")) {
            // "PactSpells: <pact name>, <spell>, <spell>, …"
            if (parsed.list.size < 2) {
                // VB stops with a "Subscript out of range" error here.
                invalidLine(lineNumber, lineText)
                return
            }
            val pactName = parsed.list[0]
            for (existing in pacts) {
                if (existing.name == pactName) {
                    log.vbDifference(CLASSES_FILE, lineNumber, "$dataName has pact $pactName more than once (VB6 keeps both)")
                }
            }
            pacts.add(Pact(pactName, parsed.list.subList(1, parsed.list.size)))
        } else {
            invalidLine(lineNumber, lineText)
        }
    }

    /** `BonusFeat:`/`ClassFeat:` lines: a list of class levels (1-20). */
    private fun readFeatLevels(parsed: ParsedLine.Field, slot: BonusFeatSlot, lineNumber: Int, lineText: String) {
        for (text in parsed.list) {
            val level = Vb.cLng(Vb.valOf(text))
            if (level in 1..CLASS_LEVELS) {
                bonusFeats[level - 1] = slot
            } else {
                invalidLine(lineNumber, lineText)
            }
        }
    }

    /** VB's `ReDim .SpellList(1 To max)` and `ReDim .SpellSlots(1 To 20, 1 To max)`, done when MaxSpellLevel is read. */
    private fun createSpellTables() {
        if (maxSpellLevel <= 0) {
            return
        }
        val slots = ArrayList<MutableList<Int>>()
        for (level in 0 until CLASS_LEVELS) {
            val row = ArrayList<Int>()
            for (spellLevel in 0 until maxSpellLevel) {
                row.add(0)
            }
            slots.add(row)
        }
        spellSlots = slots
        spellLists = ArrayList()
        for (spellLevel in 0 until maxSpellLevel) {
            spellLists.add(emptyList())
        }
    }

    /** `SpellSlots<n>: <20 values>`, one value per class level. `SpellSlots0` is a header row and ignored. */
    private fun readSpellSlots(parsed: ParsedLine.Field, spellLevel: Int, lineNumber: Int, lineText: String) {
        if (spellLevel == 0) {
            return
        }
        if (spellLevel > maxSpellLevel) {
            log.loadError(
                CLASSES_FILE, lineNumber,
                "$dataName has SpellSlots$spellLevel line but MaxSpellLevel is $maxSpellLevel",
            )
            return
        }
        if (parsed.list.size != CLASS_LEVELS) {
            invalidLine(lineNumber, lineText)
            return
        }
        for (i in 0 until CLASS_LEVELS) {
            val value = Vb.valOf(parsed.list[i])
            spellSlots[i][spellLevel - 1] = Vb.cLng(value)
            // The first class level with any slots is when the class can cast that spell level.
            if (canCastSpell[spellLevel] == 0 && value != 0.0) {
                canCastSpell[spellLevel] = i + 1
            }
        }
    }

    /** `SpellList<n>: spell, spell, …` */
    private fun readSpellList(parsed: ParsedLine.Field, spellLevel: Int, lineNumber: Int, lineText: String) {
        if (spellLevel < 1) {
            invalidLine(lineNumber, lineText)
            return
        }
        if (spellLevel > maxSpellLevel) {
            log.loadError(
                CLASSES_FILE, lineNumber,
                "$dataName has SpellList$spellLevel line but MaxSpellLevel is $maxSpellLevel",
            )
            return
        }
        spellLists[spellLevel - 1] = parsed.list
    }

    /** VB's `Val(Mid$(strField, n, 1))`: the single character after [prefix], as a number (0 if none). */
    private fun digitAfter(field: String, prefix: String): Int {
        if (field.length <= prefix.length) {
            return 0
        }
        return Vb.cLng(Vb.valOf(field.substring(prefix.length, prefix.length + 1)))
    }

    /** VB's `LogError` here names the class by `GetClassName` (log.LoadItem = .ClassName). */
    private fun invalidLine(lineNumber: Int, lineText: String) {
        log.invalidLine(CLASSES_FILE, lineNumber, name, lineText)
    }
}
