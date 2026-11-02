package ddolite.common.resources

/** Resource type of bitmaps in a Windows .res file (`RT_BITMAP`). */
const val RT_BITMAP = 2

/**
 * One resource from a Windows .res file.
 *
 * @property type the numeric type (e.g. [RT_BITMAP]), or -1 if the type is a name.
 * @property name the resource's name ("CLSFIGHTER"), or "#<id>" for a numbered one.
 */
class Win32Resource(val type: Int, val name: String, val data: ByteArray)

/**
 * Reads the resources in a compiled Windows resource file (.res), such as
 * Source/CharacterBuilderLite.RES, which VB6 builds into the .exe and reads with `LoadResPicture`.
 *
 * New code (VB gets this from Windows). The format: each resource is a header (data size, header
 * size, type, name, then version, flags, language and characteristics), padded to 4 bytes,
 * followed by its data, padded to 4 bytes. Types and names are a 0xFFFF word plus a number, or a
 * zero-terminated UTF-16 string. The first entry is an empty marker and is skipped.
 */
fun readResFile(bytes: ByteArray): List<Win32Resource> {
    val result = ArrayList<Win32Resource>()
    var pos = 0
    while (pos + 8 <= bytes.size) {
        val dataSize = readInt(bytes, pos)
        val headerSize = readInt(bytes, pos + 4)
        if (headerSize < 8 || pos + headerSize + dataSize > bytes.size) {
            break
        }
        var at = pos + 8
        var type = -1
        var typeName = ""
        if (readShort(bytes, at) == 0xFFFF) {
            type = readShort(bytes, at + 2)
            at += 4
        } else {
            val text = StringBuilder()
            at = readName(bytes, at, text)
            typeName = text.toString()
        }
        var name: String
        if (readShort(bytes, at) == 0xFFFF) {
            name = "#" + readShort(bytes, at + 2)
        } else {
            val text = StringBuilder()
            readName(bytes, at, text)
            name = text.toString()
        }
        if (dataSize > 0 || type != 0 || typeName.isNotEmpty()) {
            val start = pos + headerSize
            result.add(Win32Resource(type, name, bytes.copyOfRange(start, start + dataSize)))
        }
        pos += headerSize + dataSize
        pos = (pos + 3) and 3.inv()
    }
    return result
}

/** The bitmap called [name] (any case), as the bytes of a .bmp file; null if there isn't one. */
fun findBitmap(resources: List<Win32Resource>, name: String): ByteArray? {
    for (resource in resources) {
        if (resource.type == RT_BITMAP && resource.name.equals(name, ignoreCase = true)) {
            return bitmapFile(resource.data)
        }
    }
    return null
}

/**
 * A bitmap resource is a BMP file without its 14-byte file header; this puts the header back so
 * ordinary image decoders can read it. The pixels start after the info header, the bit masks
 * (BI_BITFIELDS) and the palette.
 */
fun bitmapFile(dib: ByteArray): ByteArray {
    val infoSize = readInt(dib, 0)
    var bitCount = 0
    var compression = 0
    var colorsUsed = 0
    if (infoSize >= 40) {
        bitCount = readShort(dib, 14)
        compression = readInt(dib, 16)
        colorsUsed = readInt(dib, 32)
    } else {
        bitCount = readShort(dib, 10) // BITMAPCOREHEADER
    }
    var paletteEntries = colorsUsed
    if (paletteEntries == 0 && bitCount <= 8) {
        paletteEntries = 1 shl bitCount
    }
    var entrySize = 4
    if (infoSize < 40) {
        entrySize = 3
    }
    var offset = 14 + infoSize + paletteEntries * entrySize
    if (infoSize == 40 && compression == 3) {
        offset += 12
    }
    val file = ByteArray(14 + dib.size)
    file[0] = 'B'.code.toByte()
    file[1] = 'M'.code.toByte()
    writeInt(file, 2, file.size)
    writeInt(file, 10, offset)
    dib.copyInto(file, 14)
    return file
}

private fun readName(bytes: ByteArray, start: Int, into: StringBuilder): Int {
    var at = start
    while (at + 1 < bytes.size) {
        val c = readShort(bytes, at)
        at += 2
        if (c == 0) {
            break
        }
        into.append(c.toChar())
    }
    // The name follows the type directly; the padding after the name is skipped by using the header size.
    return at
}

private fun readShort(bytes: ByteArray, at: Int): Int = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

private fun readInt(bytes: ByteArray, at: Int): Int = readShort(bytes, at) or (readShort(bytes, at + 2) shl 16)

private fun writeInt(bytes: ByteArray, at: Int, value: Int) {
    bytes[at] = value.toByte()
    bytes[at + 1] = (value shr 8).toByte()
    bytes[at + 2] = (value shr 16).toByte()
    bytes[at + 3] = (value shr 24).toByte()
}
