package ddolite.common.resources

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Reads the class icons out of the VB Builder's resource file, as frmOverview does with LoadResPicture. */
class ResFileTest {
    @Test
    fun classIconsAreBitmaps() {
        val res = Path.of(System.getProperty("ddolite.dataDir")).resolveSibling("Source").resolve("CharacterBuilderLite.RES")
        val resources = readResFile(Files.readAllBytes(res))
        val names = ArrayList<String>()
        for (resource in resources) {
            if (resource.type == RT_BITMAP && resource.name.startsWith("CLS")) {
                names.add(resource.name)
            }
        }
        assertEquals(26, names.size, names.toString()) // 25 classes and CLSEMPTY
        val fighter = assertNotNull(findBitmap(resources, "clsfighter"))
        assertEquals('B'.code.toByte(), fighter[0])
        // The pixel offset points inside the file, and the decoder in the JDK can read it.
        val image = javax.imageio.ImageIO.read(fighter.inputStream())
        assertNotNull(image)
        assertTrue(image.width > 16 && image.height > 16, "${image.width} x ${image.height}")
    }
}
