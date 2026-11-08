package ddolite.builder.build

import java.io.File

/**
 * Where the tests find VB6-saved builds: the repository's Save folder, then kotlin/test-builds
 * (builds kept for the tests after they left Save/, such as TEST.build at level 36), then
 * kotlin/test-builds/old-data, named "old-data/<name>" (Save/'s samples as they were before 4.15
 * renamed some of their feats and abilities, kept for the tests of what an outdated build does).
 */
object SampleBuilds {
    private val testBuilds = File(System.getProperty("ddolite.testBuildsDir"))
    private val folders = listOf(File(System.getProperty("ddolite.saveDir")), testBuilds)

    /** The named build, from whichever folder has it. */
    fun file(name: String): File {
        for (folder in folders) {
            val file = File(folder, name)
            if (file.isFile) {
                return file
            }
        }
        throw IllegalArgumentException("No sample build named $name")
    }

    /** The names of every .build in the folders, sorted; a name in both is listed once (Save/ wins). */
    fun names(): List<String> {
        val names = ArrayList<String>()
        for (folder in folders) {
            addNames(folder, "", names)
        }
        addNames(File(testBuilds, "old-data"), "old-data/", names)
        names.sort()
        return names
    }

    private fun addNames(folder: File, prefix: String, names: MutableList<String>) {
        val files = folder.listFiles() ?: return
        for (file in files) {
            if (file.name.endsWith(".build") && !names.contains(prefix + file.name)) {
                names.add(prefix + file.name)
            }
        }
    }
}
