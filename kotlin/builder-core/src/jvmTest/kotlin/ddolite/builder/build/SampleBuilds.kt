package ddolite.builder.build

import java.io.File

/**
 * Where the tests find VB6-saved builds: the repository's Save folder, then kotlin/test-builds
 * (builds kept for the tests after they left Save/, such as TEST.build at level 36).
 */
object SampleBuilds {
    private val folders = listOf(File(System.getProperty("ddolite.saveDir")), File(System.getProperty("ddolite.testBuildsDir")))

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
            val files = folder.listFiles() ?: continue
            for (file in files) {
                if (file.name.endsWith(".build") && !names.contains(file.name)) {
                    names.add(file.name)
                }
            }
        }
        names.sort()
        return names
    }
}
