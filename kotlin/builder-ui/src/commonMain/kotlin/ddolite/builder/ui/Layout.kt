package ddolite.builder.ui

/*
 * Spacing shared by the windows (pixels at 100%), so that a window's controls are placed relative to
 * one another rather than at numbers copied from the VB form. Windows are moved to these one at a
 * time; the Leveling Guide tab (GuideTab.kt) was the first. Add a value here only once two windows
 * use it, or it obviously belongs with these.
 */

/** From a window's (or tab's) left edge to its first column of controls. */
const val MARGIN = 16

/** Between controls side by side, or a section and the next. */
const val GAP = 15

/** Between a control and one attached to it (a list under its drop-down, a total under its grid). */
const val TIGHT = 3

/** One line of label text, and so the step from one label to the next under it. */
const val LINE = 17

/** Between captions on the same line (e.g. totals). */
const val ITEM_GAP = 24

/** A drop-down list box (ComboBox) and a button. */
const val COMBO_HEIGHT = 21
const val BUTTON_HEIGHT = 25
