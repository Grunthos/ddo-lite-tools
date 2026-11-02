package ddolite.tools.formlayout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormLayoutTest {
    private val sample = """
        VERSION 5.00
        Begin VB.Form frmTest
           Caption         =   "Test ""form"" x"
           ScaleHeight     =   3000
           ScaleWidth      =   4500
           BeginProperty Font
              Name            =   "Verdana"
           EndProperty
           Begin VB.Frame fraOne
              Caption         =   "One"
              Height          =   1500
              Left            =   150
              Top             =   150
              Width           =   3000
              Begin VB.Label lblA
                 Caption         =   "A"
                 Height          =   300
                 Left            =   150
                 Top             =   300
                 Width           =   600
              End
              Begin VB.Label lblB
                 Height          =   300
                 Left            =   165
                 Top             =   750
                 Width           =   2400
              End
              Begin VB.TextBox txtC
                 Height          =   300
                 Left            =   900
                 Top             =   300
                 Visible         =   0   'False
                 Width           =   600
              End
           End
           Begin VB.Timer tmrX
              Interval        =   300
              Left            =   0
              Top             =   0
           End
           Begin VB.Menu mnuMain
              Caption         =   "Main"
              Begin VB.Menu mnuItem
                 Caption         =   "Item"
                 Index           =   2
              End
           End
        End
        Attribute VB_Name = "frmTest"
        Private Sub Form_Resize()
            Me.fraOne.Move 0, 0  ' moved
            With Me.lblA
                .Left = 10
            End With
        End Sub
    """.trimIndent()

    @Test
    fun parsesTreeAndProperties() {
        val file = parseFrm(sample)
        assertEquals("frmTest", file.form.name)
        assertEquals("Test \"form\" x", file.form.caption)
        assertEquals(3, file.form.children.size) // frame, timer, menu
        val frame = file.form.children[0]
        assertEquals(3, frame.children.size)
        assertFalse(frame.children[2].startsVisible)
        assertTrue(file.form.children[1].isNonVisual)
        assertEquals("mnuItem(2)", file.form.children[2].children[0].displayName)
        assertEquals("Private Sub Form_Resize()", file.codeLines[0])
    }

    @Test
    fun placesAndGroupsIntoRowsAndColumns() {
        val root = placeControls(parseFrm(sample).form)
        assertEquals(300, root.bounds.width)
        assertEquals(1, root.children.size) // timer and menu left out
        val frame = root.children[0]
        val a = frame.children[0]
        val b = frame.children[1]
        val c = frame.children[2]
        assertEquals(20, a.bounds.left) // 150 + 150 twips
        assertEquals(30, a.bounds.top)
        // lblB's left (21 px) is within the tolerance of lblA's (20 px): same column.
        assertEquals(listOf(20, 70), frame.grid!!.cols)
        assertEquals(0, b.col)
        assertEquals(2, b.colSpan) // 160 px wide, so it runs under txtC's column too
        assertEquals(1, c.col)
        assertEquals(0, c.row)
        assertEquals(1, b.row)
        assertEquals(1, frame.grid!!.nearMisses.size)
    }

    @Test
    fun clustersNearbyEdges() {
        assertEquals(listOf(10, 50), clusterEdges(listOf(12, 10, 10, 50, 52)))
    }

    @Test
    fun findsRuntimeMoves() {
        val moves = findRuntimeMoves(parseFrm(sample))
        assertEquals(2, moves.size)
        assertEquals("fraOne", moves[0].target)
        assertEquals("lblA", moves[1].target)
    }
}
