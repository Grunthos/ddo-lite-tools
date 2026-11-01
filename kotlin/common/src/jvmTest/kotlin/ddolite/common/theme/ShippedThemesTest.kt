package ddolite.common.theme

import ddolite.dataformat.decodeText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every .screen file in the repository's Settings folder loads and sets every non-output colour. */
class ShippedThemesTest {
    @Test
    fun allShippedThemesLoad() {
        val settings = Path.of(System.getProperty("ddolite.dataDir")).resolveSibling("Settings")
        var count = 0
        Files.newDirectoryStream(settings, "*.screen").use { files ->
            for (file in files) {
                val text = decodeText(Files.readAllBytes(file)).text
                val result = loadTheme(text, file.fileName.toString(), ThemeFileKind.SCREEN)
                assertTrue(result.valid, "$file")
                assertEquals(emptyList(), result.unsupported, "$file")
                count++
            }
        }
        assertTrue(count >= 10, "expected the shipped .screen files, found $count")
    }

    @Test
    fun blackThemeValues() {
        val file = Path.of(System.getProperty("ddolite.dataDir")).resolveSibling("Settings").resolve("Black.screen")
        val theme = loadTheme(decodeText(Files.readAllBytes(file)).text, "Black", ThemeFileKind.SCREEN).theme
        assertEquals(argb(0, 0, 0), theme.get(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))
        assertEquals(argb(235, 235, 235), theme.get(ColorGroup.CONTROLS, ColorValue.TEXT))
        assertEquals(argb(255, 167, 38), theme.get(ColorGroup.DROP_SLOTS, ColorValue.ORANGE))
    }
}
