package ddolite.dataformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VbTest {
    @Test
    fun trimRemovesSpacesOnly() {
        assertEquals("\ta b\t", Vb.trim("  \ta b\t  "))
    }

    @Test
    fun splitOfEmptyStringIsEmpty() {
        assertEquals(emptyList(), Vb.split("", ","))
        assertEquals(listOf("a", "", "b"), Vb.split("a,,b", ","))
    }

    @Test
    fun isNumeric() {
        for (s in listOf("12", "-3", "+4", "1.5", ".5", "5.", "1e3", "1E-2", "1d6", "&H1F", "&o17", "1,000", " 7 ")) {
            assertTrue(Vb.isNumeric(s), "expected numeric: '$s'")
        }
        for (s in listOf("", " ", "abc", "12a", ".", "-", "&HZ", "1e")) {
            assertFalse(Vb.isNumeric(s), "expected not numeric: '$s'")
        }
    }

    @Test
    fun valReadsLeadingNumber() {
        assertEquals(12.0, Vb.valOf("12abc"))
        assertEquals(123.0, Vb.valOf(" 1 2\t3"))
        assertEquals(-3.5, Vb.valOf("-3.5"))
        assertEquals(1.2, Vb.valOf("1.2.3"))
        assertEquals(1.0, Vb.valOf("1e"))
        assertEquals(1_000_000.0, Vb.valOf("1d6"))
        assertEquals(31.0, Vb.valOf("&H1F"))
        assertEquals(-1.0, Vb.valOf("&HFFFF"))
        assertEquals(15.0, Vb.valOf("&O17"))
        assertEquals(1.0, Vb.valOf("1,000"))
        assertEquals(0.0, Vb.valOf("abc"))
        assertEquals(0.0, Vb.valOf(""))
    }

    @Test
    fun cLngRoundsHalfToEven() {
        assertEquals(2, Vb.cLng(2.5))
        assertEquals(4, Vb.cLng(3.5))
        assertEquals(-2, Vb.cLng(-2.5))
        assertEquals(1, Vb.cLng(0.75))
        assertEquals(0, Vb.cLng(0.5))
        assertFailsWith<ArithmeticException> { Vb.cLng(3e9) }
    }
}
