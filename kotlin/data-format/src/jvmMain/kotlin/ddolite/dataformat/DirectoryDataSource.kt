package ddolite.dataformat

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads data files from a folder on disk: the Data folder next to the app, as VB6 does
 * (Build/basDataLoad.bas:24, `DataPath`). Desktop (JVM) only.
 */
class DirectoryDataSource(val root: Path) : DataSource {
    override fun read(path: String): ByteArray? {
        val file = root.resolve(path)
        if (!Files.isRegularFile(file)) {
            return null
        }
        return Files.readAllBytes(file)
    }

    override fun list(directory: String): List<String> {
        var dir = root
        var prefix = ""
        if (directory.isNotEmpty()) {
            dir = root.resolve(directory)
            prefix = directory.trimEnd('/') + "/"
        }
        val result = ArrayList<String>()
        if (!Files.isDirectory(dir)) {
            return result
        }
        Files.newDirectoryStream(dir).use { entries ->
            for (entry in entries) {
                if (Files.isRegularFile(entry)) {
                    result.add(prefix + entry.fileName.toString())
                }
            }
        }
        result.sort()
        return result
    }
}
