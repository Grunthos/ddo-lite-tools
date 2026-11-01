package ddolite.common.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The theme loader (port of clsConfig.LoadColors and helpers) on made-up theme text. */
class ThemeTest {
    @Test
    fun readsRgbSettings() {
        val result = loadTheme("WrkText=192,192,192\nCtlBackground=60,60,60\n", "Test", ThemeFileKind.SCREEN)
        assertTrue(result.valid)
        assertEquals(argb(192, 192, 192), result.theme.get(ColorGroup.WORKSPACE, ColorValue.TEXT))
        assertEquals(argb(60, 60, 60), result.theme.get(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
    }

    @Test
    fun namesAreCaseInsensitiveAndBackSubIsBackRelated() {
        val result = loadTheme("ctlbacksub = 75, 75, 175\nNAVGRAY=1,2,3", "Test", ThemeFileKind.SCREEN)
        assertEquals(argb(75, 75, 175), result.theme.get(ColorGroup.CONTROLS, ColorValue.BACK_RELATED))
        assertEquals(argb(1, 2, 3), result.theme.get(ColorGroup.NAVIGATION, ColorValue.LIGHT_GRAY))
    }

    @Test
    fun screenFilesDontSetOutputColours() {
        val screen = loadTheme("OutText=1,2,3\nWrkText=4,5,6", "Test", ThemeFileKind.SCREEN)
        assertEquals(argb(4, 5, 6), screen.theme.get(ColorGroup.WORKSPACE, ColorValue.TEXT))
        assertEquals(argb(128, 128, 128), screen.theme.get(ColorGroup.OUTPUT, ColorValue.TEXT))
        val output = loadTheme("OutText=1,2,3\nWrkText=4,5,6", "Test", ThemeFileKind.OUTPUT)
        assertEquals(argb(1, 2, 3), output.theme.get(ColorGroup.OUTPUT, ColorValue.TEXT))
        assertEquals(argb(128, 128, 128), output.theme.get(ColorGroup.WORKSPACE, ColorValue.TEXT))
    }

    @Test
    fun otherValueForms() {
        assertEquals(argb(0x11, 0x22, 0x33), colorToArgb("3351057")) // 3351057 = 0x332211: VB stores BGR
        assertEquals(argb(255, 0, 0), colorToArgb("300,0,0")) // RGB() uses 255 for anything over
        assertNull(colorToArgb("1,2"))
        assertNull(colorToArgb("Button Face"))
        val result = loadTheme("WrkText=Button Face", "Test", ThemeFileKind.SCREEN)
        assertEquals(listOf("WrkText=Button Face"), result.unsupported)
    }

    @Test
    fun fileWithNoColoursIsInvalid() {
        assertFalse(loadTheme("Hello=World\nnot a setting", "Test", ThemeFileKind.SCREEN).valid)
    }
}
