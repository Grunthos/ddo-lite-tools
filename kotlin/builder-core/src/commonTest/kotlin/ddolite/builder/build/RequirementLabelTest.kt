package ddolite.builder.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Req: …" label on the Enhancements, Destinies and Leveling Guide status lines. */
class RequirementLabelTest {
    @Test
    fun apAndLevel() {
        assertEquals("Req: 5 AP, Lvl 4", requirementLabel(5, 4)?.text)
        assertFalse(requirementLabel(5, 4)!!.unattainable)
    }

    @Test
    fun levelOnly() {
        assertEquals("Req: Lvl 1", requirementLabel(0, 1)?.text)
    }

    @Test
    fun apOnlyWithNoLevelRequirement() {
        // Destinies: no level requirement.
        assertEquals("Req: 10 AP", requirementLabel(10, null)?.text)
    }

    @Test
    fun nothingNeeded() {
        assertNull(requirementLabel(0, null))
    }

    @Test
    fun unattainable() {
        val label = requirementLabel(5, 0)
        assertEquals("Req: unattainable", label?.text)
        assertTrue(label!!.unattainable)
    }
}
