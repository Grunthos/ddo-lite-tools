package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.Windows1252
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opens each build in the repository's Save folder (saved by VB6) with the real data, saves it
 * again and compares the text line by line. Differences are expected only where the current VB6
 * would write the file differently from the version that saved it; each is listed and explained.
 */
class BuildRoundTripTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())
    private val saveDir = File(System.getProperty("ddolite.saveDir"))

    private fun roundTrip(name: String): Pair<BuildLoadResult, List<String>> {
        val bytes = File(saveDir, name).readBytes()
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

    private fun sampleBuilds(): List<String> {
        val names = ArrayList<String>()
        for (file in saveDir.listFiles()!!) {
            if (file.name.endsWith(".build")) {
                names.add(file.name)
            }
        }
        names.sort()
        return names
    }

    /**
     * The samples were saved by older versions against older data, so they don't come back
     * byte for byte. Every difference must be one of: the Levelup line for level 36 (MAX_LEVELUPS
     * went from 8 to 9), a line for something the current data no longer has (each one recorded as
     * deprecated), or a feat renamed by NameChange.txt.
     */
    @Test
    fun differencesFromVbAreExplained() {
        assertEquals(listOf("Bard Acolyte.build", "Kensei Warpriest.build", "TEST.build", "Warlock Blaster.build"), sampleBuilds())
        for (name in sampleBuilds()) {
            val (loaded, differences) = roundTrip(name)
            assertEquals(emptyList(), loaded.problems, name)
            val deprecations = loaded.deprecations
            var removedAbilities = 0
            var removedFeats = 0
            var removedGuide = 0
            val added = ArrayList<String>()
            for (line in differences) {
                if (line.startsWith("-Ability: ")) {
                    removedAbilities++
                } else if (line.startsWith("-Guide: ")) {
                    removedGuide++
                } else if (line.startsWith("-Standard: ")) {
                    removedFeats++
                } else if (line.startsWith("+")) {
                    added.add(line)
                } else {
                    assertEquals("-", line, "$name: unexpected difference") // a blank line around the removed guide
                }
            }
            assertEquals(deprecations.enhancements.size + deprecations.destinies.size, removedAbilities, "$name abilities")
            // A renamed feat is written back under its new name in the same slot.
            assertEquals(deprecations.feats.size + deprecations.nameChanges.size, removedFeats, "$name feats")
            val expectedAdded = ArrayList<String>()
            if (name != "TEST.build") {
                expectedAdded.add("+Levelup: 36\t") // TEST.build was saved by a version that already writes it
            }
            for (change in deprecations.nameChanges) {
                assertEquals("Epic Pact Dice", change.newName)
                expectedAdded.add("+Standard: Destiny 22\tEpic Pact Dice")
            }
            assertEquals(expectedAdded, added, name)
            if (removedGuide > 0) {
                // VB ignores the rest of the guide after the first step that doesn't resolve.
                assertTrue(deprecations.levelingGuide != null, name)
            }
        }
    }

    /**
     * What the Kotlin writer saves, it reads back with nothing deprecated and saves again unchanged.
     *
     * One exception, as in VB: when the leveling guide's first step doesn't resolve, its tree has
     * already been added to the guide's trees, so the first save writes an empty [LevelingGuide]
     * heading; reading that back gives no trees, and the next save leaves the heading out.
     */
    @Test
    fun saveLoadSaveIsStable() {
        for (name in sampleBuilds()) {
            val original = readBuildFile(File(saveDir, name).readBytes(), data, BuildPoints.ADVENTURER)
            val first = writeBuildFile(original.rules)
            val reloaded = readBuildFile(first, data, BuildPoints.ADVENTURER)
            assertEquals(emptyList(), reloaded.problems, name)
            assertTrue(!reloaded.deprecations.any, "$name: ${reloaded.deprecations.feats} ${reloaded.deprecations.enhancements}")
            val second = writeBuildFile(reloaded.rules)
            val differences = lineDifferences(Windows1252.decode(first), Windows1252.decode(second))
            if (original.deprecations.levelingGuide != null && original.build.guide.enhancements.isEmpty()) {
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
        // Fighter 13 levels (full BAB), Cleric 7 (3/4): 13 + 5 at level 20.
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
