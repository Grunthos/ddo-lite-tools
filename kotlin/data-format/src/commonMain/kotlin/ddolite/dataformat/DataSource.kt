package ddolite.dataformat

/**
 * Where game data files come from. Replaces VB6's `DataPath() & "Feats.txt"` file paths
 * (e.g. basDataLoad.DataPath, Build/basDataLoad.bas:24), so the loaders don't depend on a
 * folder on disk.
 *
 * Paths are relative and use '/' separators, e.g. "Builder/Feats.txt". Desktop reads the
 * Data folder next to the app (as VB6 does), a mobile build will read files bundled with
 * the app, and tests use [MapDataSource].
 */
interface DataSource {
    /** The file's bytes, or null if it doesn't exist. */
    fun read(path: String): ByteArray?

    /** All file paths in [directory] (not its subfolders), sorted. "" means the top level. */
    fun list(directory: String): List<String>
}

/** Reads and decodes a text file (see [decodeText]), or returns null if it doesn't exist. */
fun DataSource.readText(path: String): DecodedText? {
    val bytes = read(path)
    if (bytes == null) {
        return null
    }
    return decodeText(bytes)
}

/** A [DataSource] held in memory: used by tests, and later for data bundled into mobile apps. */
class MapDataSource(private val files: Map<String, ByteArray>) : DataSource {
    override fun read(path: String): ByteArray? = files[path]

    override fun list(directory: String): List<String> {
        var prefix = ""
        if (directory.isNotEmpty()) {
            prefix = directory.trimEnd('/') + "/"
        }
        val result = ArrayList<String>()
        for (path in files.keys) {
            // In the folder itself, not a subfolder of it.
            if (path.startsWith(prefix) && !path.substring(prefix.length).contains('/')) {
                result.add(path)
            }
        }
        result.sort()
        return result
    }

    companion object {
        /** Convenience for tests: files given as text, stored as UTF-8. */
        fun ofText(vararg files: Pair<String, String>): MapDataSource {
            val map = HashMap<String, ByteArray>()
            for (file in files) {
                map[file.first] = file.second.encodeToByteArray()
            }
            return MapDataSource(map)
        }
    }
}
