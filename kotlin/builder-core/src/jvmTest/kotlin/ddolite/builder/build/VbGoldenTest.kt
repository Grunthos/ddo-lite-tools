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
 * Compares Kotlin with what VB wrote for the golden builds (test-builds/golden/vb-<version>, see
 * CAPTURE.md). For each "Golden <name>.build" there, opening <name>.build (from test-builds/golden,
 * or Save/ and test-builds via [SampleBuilds]) and saving it must give VB's file byte for byte.
 */
class VbGoldenTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())
    private val golden = File(System.getProperty("ddolite.testBuildsDir"), "golden")

    @Test
    fun saveMatchesVb() {
        val failures = ArrayList<String>()
        var compared = 0
        for (snapshot in snapshots()) {
            val files = snapshot.listFiles() ?: continue
            files.sortBy { it.name }
            for (file in files) {
                if (!file.name.startsWith("Golden ") || !file.name.endsWith(".build")) {
                    continue
                }
                val name = file.name.substring("Golden ".length)
                val input = if (File(golden, name).isFile) File(golden, name) else SampleBuilds.file(name)
                val loaded = readBuildFile(input.readBytes(), data, BuildPoints.ADVENTURER)
                val difference = firstDifference(Windows1252.decode(file.readBytes()), Windows1252.decode(writeBuildFile(loaded.rules)))
                if (difference != null) {
                    failures.add("${snapshot.name}/${file.name}: $difference")
                }
                compared++
            }
        }
        assertTrue(compared >= 4, "golden builds found: $compared")
        assertEquals(emptyList(), failures)
    }

    /** The vb-<version> folders. */
    private fun snapshots(): List<File> {
        val result = ArrayList<File>()
        val files = golden.listFiles() ?: return result
        for (file in files) {
            if (file.isDirectory && file.name.startsWith("vb-")) {
                result.add(file)
            }
        }
        return result
    }

    /** The first line where [expected] and [actual] differ (line endings included), or null. */
    private fun firstDifference(expected: String, actual: String): String? {
        if (expected == actual) {
            return null
        }
        val a = expected.split("\n")
        val b = actual.split("\n")
        for (i in 0 until maxOf(a.size, b.size)) {
            if (a.getOrNull(i) != b.getOrNull(i)) {
                return "line ${i + 1}: VB \"${a.getOrNull(i)}\", Kotlin \"${b.getOrNull(i)}\""
            }
        }
        return "same lines, different bytes"
    }
}
