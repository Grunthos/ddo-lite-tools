package ddolite.builder.ui

/** A popup menu item: its caption ("-" for a separator) and whether it can be chosen. */
class MenuChoice(val caption: String, val enabled: Boolean)

/** A line of text that shows in the error colour when [error]. */
class Caption(val text: String, val error: Boolean)

/** A build tree row's Class, Tier and AP columns (usrTree). */
class TreeColumns(val className: String, val tier: String, val ap: String)
