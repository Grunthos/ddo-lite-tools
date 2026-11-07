package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.Windows1252
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opens each build in the repository's Save folder and kotlin/test-builds (saved by VB6) with the real data, saves it
 * again and compares the text line by line. Differences are expected only where the current VB6
 * would write the file differently from the version that saved it; each is listed and explained.
 */
class BuildRoundTripTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun roundTrip(name: String): Pair<BuildLoadResult, List<String>> {
        val bytes = SampleBuilds.file(name).readBytes()
        val loaded = readBuildFile(bytes, data, BuildPoints.ADVENTURER)
        val saved = writeBuildFile(loaded.rules)
        return Pair(loaded, lineDifferences(Windows1252.decode(bytes), Windows1252.decode(saved)))
    }

    /** "-old" / "+new" lines where the two texts differ (a simple LCS diff). */
    private fun lineDifferences(before: String, after: String): List<String> {
        val a = before.split("\r\n")
        val b = after.split("\r\n")
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val result = ArrayList<String>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            if (i < a.size && j < b.size && a[i] == b[j]) {
                i++
                j++
            } else if (j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j])) {
                result.add("+" + b[j])
                j++
            } else {
                result.add("-" + a[i])
                i++
            }
        }
        return result
    }

    private fun sampleBuilds(): List<String> = SampleBuilds.names()

    /**
     * The samples were saved by older versions against older data, so they don't come back
     * byte for byte. Every difference must be one of: the Levelup line for level 36 (MAX_LEVELUPS
     * went from 8 to 9), a line for something the current data no longer has (each one recorded as
     * deprecated), a feat renamed by NameChange.txt, a selector dropped from a feat that no longer
     * has selectors, or the DestinyTier5 line (which VB doesn't read).
     */
    @Test
    fun differencesFromVbAreExplained() {
        // The repository's samples, plus any others saved there.
        assertTrue(sampleBuilds().containsAll(listOf("Bard Acolyte.build", "Kensei Warpriest.build", "TEST.build", "Warlock Blaster.build")))
        for (name in sampleBuilds()) {
            val (loaded, differences) = roundTrip(name)
            val originalText = Windows1252.decode(SampleBuilds.file(name).readBytes())
            assertEquals(emptyList(), loaded.problems, name)
            val deprecations = loaded.deprecations
            var removedAbilities = 0
            var removedFeats = 0
            var removedGuide = 0
            val added = ArrayList<String>()
            // A selector on a feat that no longer has selectors is dropped without a message
            // (FindFeat, basFormatLite.bas:970): "-Standard: …\tFeat: Selector" with "+Standard: …\tFeat".
            val remaining = ArrayList(differences)
            var reduced = 0
            for (line in differences) {
                val tab = line.indexOf('\t')
                val colon = line.indexOf(':', tab + 1)
                if (line.startsWith("-Standard: ") && tab > 0 && colon > 0) {
                    val without = "+" + line.substring(1, colon)
                    if (remaining.contains(without)) {
                        remaining.remove(without)
                        remaining.remove(line)
                    }
                }
                // An ability kept at a lower rank (Kotlin only) is written at that rank: "-Ability: …
                // (Rank 3)" with "+Ability: …" (one rank) or "+Ability: … (Rank 2)".
                if (line.startsWith("-Ability: ") && line.endsWith(")") && line.contains(" (Rank ")) {
                    val name = line.substring(1, line.lastIndexOf(" (Rank "))
                    for (other in ArrayList(remaining)) {
                        if (other == "+$name" || (other.startsWith("+$name (Rank ") && other != "+" + line.substring(1))) {
                            remaining.remove(other)
                            remaining.remove(line)
                            reduced++
                            break
                        }
                    }
                }
                // A guide step for rank 1 of an ability that now has one rank is written without
                // " (Rank 1)" (GetGuideLine adds it only for abilities with ranks).
                if (line.startsWith("-Guide: ") && line.endsWith(" (Rank 1)")) {
                    val without = "+" + line.substring(1, line.length - " (Rank 1)".length)
                    if (remaining.contains(without)) {
                        remaining.remove(without)
                        remaining.remove(line)
                    }
                }
            }
            for (line in remaining) {
                if (line.startsWith("-Ability: ")) {
                    removedAbilities++
                } else if (line.startsWith("-Guide: ")) {
                    removedGuide++
                } else if (line.startsWith("-Standard: ")) {
                    removedFeats++
                } else if (line == "-[LevelingGuide]" && !originalText.contains("\r\nGuide: ")) {
                    // An empty guide heading: saved after a guide whose first step didn't resolve (its tree
                    // was already added), and not written again (see saveLoadSaveIsStable; VB-BUGS 23a).
                } else if (line.startsWith("-DestinyTier5: ")) {
                    // VB writes DestinyTier5 but LoadDestinyText never reads it (VB-BUGS 18f).
                } else if (line.startsWith("+")) {
                    added.add(line)
                } else {
                    assertEquals("-", line, "$name: unexpected difference") // a blank line around the removed guide
                }
            }
            // Abilities kept as placeholder slots are saved back; only the others leave the file.
            var droppedAbilities = deprecations.destinies.size
            for (e in deprecations.enhancements) {
                if (!e.kept) {
                    droppedAbilities++
                }
            }
            assertEquals(droppedAbilities, removedAbilities, "$name abilities")
            assertEquals(deprecations.reducedRanks.size, reduced, "$name reduced ranks")
            // A renamed feat is written back under its new name in the same slot; a missing one is
            // kept as a placeholder and written back as read.
            var droppedFeats = deprecations.nameChanges.size
            for (f in deprecations.feats) {
                if (!f.kept) {
                    droppedFeats++
                }
            }
            assertEquals(droppedFeats, removedFeats, "$name feats")
            val expectedAdded = ArrayList<String>()
            // Versions before MAX_LEVELUPS = 9 don't write the level 36 line (TEST.build and newer saves do).
            val original = Windows1252.decode(SampleBuilds.file(name).readBytes())
            if (!original.contains("\r\nLevelup: 36\t")) {
                expectedAdded.add("+Levelup: 36\t")
            }
            for (change in deprecations.nameChanges) {
                assertEquals("Epic Pact Dice", change.newName)
                expectedAdded.add("+Standard: Destiny 22\tEpic Pact Dice")
            }
            assertEquals(expectedAdded, added, name)
            // Only a missing tree's steps leave the file; a missing ability is saved back as read.
            var dropped = 0
            for (g in deprecations.guide) {
                if (!g.kept) {
                    dropped++
                }
            }
            assertEquals(dropped, removedGuide, "$name guide")
        }
    }

    /**
     * What the Kotlin writer saves, it reads back with nothing deprecated (but the guide's kept red
     * rows, which stay until deleted) and saves again unchanged.
     *
     * One exception, as in VB: when the leveling guide's first step doesn't resolve, its tree has
     * already been added to the guide's trees, so the first save writes an empty [LevelingGuide]
     * heading; reading that back gives no trees, and the next save leaves the heading out.
     */
    @Test
    fun saveLoadSaveIsStable() {
        for (name in sampleBuilds()) {
            val original = readBuildFile(SampleBuilds.file(name).readBytes(), data, BuildPoints.ADVENTURER)
            val first = writeBuildFile(original.rules)
            val reloaded = readBuildFile(first, data, BuildPoints.ADVENTURER)
            assertEquals(emptyList(), reloaded.problems, name)
            val d = reloaded.deprecations
            assertTrue(d.nameChanges.isEmpty() && d.destinies.isEmpty(), name)
            for (f in d.feats) {
                assertTrue(f.kept, "$name: $f") // placeholders come back as placeholders
            }
            for (e in d.enhancements) {
                assertTrue(e.kept, "$name: $e") // placeholders come back as placeholders
            }
            for (g in d.guide) {
                assertTrue(g.kept, "$name: ${g.text}")
            }
            val second = writeBuildFile(reloaded.rules)
            val differences = lineDifferences(Windows1252.decode(first), Windows1252.decode(second))
            if (original.build.guide.trees.isNotEmpty() && original.build.guide.enhancements.isEmpty()) {
                assertEquals(listOf("-[LevelingGuide]", "-", "-"), differences, name)
            } else {
                assertEquals(emptyList(), differences, name)
            }
            val third = writeBuildFile(readBuildFile(second, data, BuildPoints.ADVENTURER).rules)
            assertEquals(Windows1252.decode(second), Windows1252.decode(third), name)
        }
    }

    @Test
    fun kenseiWarpriestSpotChecks() {
        val (loaded, _) = roundTrip("Kensei Warpriest.build")
        val build = loaded.build
        assertEquals("Kensei Warpriest", build.buildName)
        assertEquals("Human", build.race?.name)
        assertEquals(30, build.maxLevels)
        assertEquals(listOf("Fighter", "Cleric", null), build.buildClasses.map { it?.name })
        assertEquals("Cleric", build.levelClass(3)?.name)
        assertEquals(listOf(10, 0, 6, 4, 2, 6), build.statPoints[BuildPoints.ADVENTURER.ordinal].toList())
        assertEquals(28, build.statTotals[BuildPoints.ADVENTURER.ordinal])
        // Fighter 12 levels (full BAB), Cleric 8 (3/4): 12 + 6 at level 20.
        assertEquals(18, build.bab[19])
        val legend = build.feats(BuildFeatType.LEGEND)
        assertEquals(15, legend.size) // VB's shared index: the Legend slot is the 15th entry
        assertEquals("Scion of", legend[14].featName)
        assertEquals("Cleave", build.feats(BuildFeatType.CLASS1)[0].featName)
        assertEquals("Domain", build.feats(BuildFeatType.CLASS2)[0].featName)
        val cleric = build.spellsFor(loaded.rules.data.findClass("Cleric")!!)!!
        assertEquals(4, cleric.maxSpellLevel)
        assertEquals("Cure Light Wounds", cleric.level(1).slots[0].spell)
        assertEquals(SpellSlotType.CLERIC_CURE, cleric.level(1).slots[0].type)
        assertEquals(listOf("Kensei", "Warpriest", "Stalwart Defender", "Human"), build.trees.map { it.treeName })
        assertEquals(listOf("Legendary Dreadnought", "Unyielding Sentinel", "Fury of the Wild"), build.destinies.map { it.treeName })
        assertEquals(12, build.permDestinyPoints)
    }
}
