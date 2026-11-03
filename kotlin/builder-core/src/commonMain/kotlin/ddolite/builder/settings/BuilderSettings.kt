package ddolite.builder.settings

import ddolite.builder.build.BuildPoints
import ddolite.builder.output.FeatOrderOutput
import ddolite.builder.output.ListTags
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.SkillOrderOutput
import ddolite.builder.output.Tags
import ddolite.dataformat.Vb

/** A window's place and size in twips (VB's `CoordsType`); a width or height of 0 means "keep the size". */
data class Coords(val left: Int = 0, val top: Int = 0, val width: Int = 0, val height: Int = 0)

/** How the main window opens (`WindowSizeEnum`, clsConfig.cls:204), in VB's order. */
enum class WindowSize { MAXIMIZED, FILL_DESKTOP, REMEMBER }

/**
 * The settings VB keeps in Settings.txt next to the program, for the Character Builder: its own
 * section, the output format, and the shared colour settings. Ported from clsConfig.cls:
 * `SetDefaults` (:428), `LoadSettings` (:544), `LoadCharBuild` (:603), `LoadShared` (:786),
 * `ParseOutputData` (:816), and `SaveSettings` (:1123) with `MakeCharBuildSettings` (:1179),
 * `MakeSharedSettings` (:1311) and `MakeOutputSettings` (:1323).
 *
 * The other programs' sections (Compendium, Paths) are kept as they were read and written back
 * unchanged, so a settings file shared by the tools loses nothing. The Colors section is kept as
 * text: the window applies it as the colours ([colorsText]).
 */
class BuilderSettings {
    // Section: Character Builder Lite
    var windowSize = WindowSize.REMEMBER
    var maximized = true
    var windowCoords = Coords()
    var dialogCoords = Coords()
    var optionsCoords = Coords()
    var convertCoords = Coords()
    var convertOnStartup = false
    var childWindows = true
    var showErrors = true
    var useIcons = true
    var iconOverview = true
    var iconSkills = true
    var mruCount = 9
    val mruList = ArrayList<String>()
    var buildPoints = BuildPoints.ADVENTURER
    var skillOrderScreen = 0
    var skillOrderOutput = SkillOrderOutput.RANKS_SPENT
    var featOrder = 0
    var featChannels = false
    var featOrderOutput = FeatOrderOutput.CHANNEL
    var featShowAll = false
    var bbCodes = "Default"
    var outputMargin = 0
    var confirm = true
    var contrast = 2 // ceMedium
    var version = ""
    var processTime = 0

    // Section: Shared
    var colorCoords = Coords()
    var screenColors = "Black"
    var outputColors = "Default"
    var colorVersion = 0

    // Section: Output
    var output: OutputFormat = OutputFormat.DEFAULT

    /** Section: Colors, as text ("WrkBackground=0,0,0" …); null if the file had none. */
    var colorsText: String? = null

    /** Sections this program doesn't use, by title, as read. */
    private val otherSections = LinkedHashMap<String, String>()

    companion object {
        /** The defaults (clsConfig.SetDefaults). */
        fun defaults(): BuilderSettings = BuilderSettings()

        /**
         * Reads a settings file: the text is split at "Section: " and each section by its title
         * line, as LoadSettings does. Unknown lines are ignored; values are read as VB reads them.
         */
        fun parse(text: String): BuilderSettings {
            val settings = BuilderSettings()
            val parts = text.split("Section: ")
            for (i in 1 until parts.size) {
                val part = parts[i]
                val end = part.indexOf("\r\n").let { if (it < 0) part.indexOf('\n') else it }
                if (end < 0) {
                    continue
                }
                val title = part.substring(0, end)
                val body = part.substring(end)
                when (title) {
                    "Character Builder Lite" -> settings.loadCharBuild(body)
                    "Shared" -> settings.loadShared(body)
                    "Output" -> settings.output = OutputFormat.parse(body, OutputFormat.DEFAULT.copy())
                    "Colors" -> settings.colorsText = body
                    else -> settings.otherSections[title] = body
                }
            }
            return settings
        }

        private fun bool(value: String): Boolean = value.lowercase() == "true"

        private fun number(value: String): Int = Vb.valOf(value).toInt()

        /** ParseCoords (:749): four tab-separated numbers, else unchanged. */
        private fun coords(value: String, old: Coords): Coords {
            val parts = value.split('\t')
            if (parts.size != 4) {
                return old
            }
            return Coords(number(parts[0]), number(parts[1]), number(parts[2]), number(parts[3]))
        }

        private fun vbBool(b: Boolean): String = if (b) "True" else "False"
    }

    /** The lines of a section: "Field=value", as ParseLine (:1105) splits them. */
    private fun fields(body: String): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>()
        for (line in body.split("\r\n", "\n")) {
            val equals = line.indexOf('=')
            if (line.isEmpty() || equals < 0) {
                continue
            }
            result.add(Pair(Vb.trim(line.substring(0, equals)).lowercase(), Vb.trim(line.substring(equals + 1))))
        }
        return result
    }

    /** LoadCharBuild (:603). */
    private fun loadCharBuild(body: String) {
        var mruRaw = ""
        for ((field, value) in fields(body)) {
            when (field) {
                "windowsize" -> windowSize = WindowSize.entries.getOrElse(number(value)) { WindowSize.REMEMBER }
                "maximized" -> maximized = bool(value)
                "windowcoords" -> windowCoords = coords(value, windowCoords)
                "dialogcoords" -> dialogCoords = coords(value, dialogCoords)
                "optionscoords" -> optionsCoords = coords(value, optionsCoords)
                "convertcoords" -> convertCoords = coords(value, convertCoords)
                "convertonstartup" -> convertOnStartup = bool(value)
                "childwindows" -> childWindows = bool(value)
                "showerrorsonstartup" -> showErrors = bool(value)
                "iconoverview" -> iconOverview = bool(value)
                "iconskills" -> iconSkills = bool(value)
                "mrulist" -> mruRaw = value
                "mrucount" -> mruCount = number(value)
                "buildpoints" -> buildPoints = BuildPoints.entries.getOrElse(number(value)) { BuildPoints.ADVENTURER }
                "skillorderscreen" -> skillOrderScreen = number(value)
                "skillorderoutput" -> skillOrderOutput = SkillOrderOutput.entries.getOrElse(number(value)) { SkillOrderOutput.RANKS_SPENT }
                "featorder" -> featOrder = number(value)
                "featchannels" -> featChannels = bool(value)
                "featorderoutput" -> featOrderOutput = FeatOrderOutput.entries.getOrElse(number(value)) { FeatOrderOutput.CHANNEL }
                "featshowall" -> featShowAll = bool(value)
                "useicons" -> useIcons = bool(value)
                "bbcodes" -> bbCodes = value
                "outputmargin" -> outputMargin = number(value)
                "confirm" -> confirm = bool(value)
                "version" -> version = value
                "processtime" -> processTime = number(value)
                "contrast" -> contrast = number(value)
            }
        }
        // SplitMRU (:660): the list is read after the count, whichever line came first.
        if (mruCount > 9) {
            mruCount = 9
        }
        mruList.clear()
        if (mruRaw.isNotEmpty()) {
            val names = mruRaw.split('\t')
            for (i in 0 until minOf(names.size, mruCount)) {
                mruList.add(names[i])
            }
        }
    }

    /** LoadShared (:786). */
    private fun loadShared(body: String) {
        for ((field, value) in fields(body)) {
            when (field) {
                "colorcoords" -> colorCoords = coords(value, colorCoords)
                "screencolors" -> screenColors = value
                "outputcolors" -> outputColors = value
                "colorversion" -> colorVersion = number(value)
            }
        }
    }

    /**
     * The Recent Files Menu count: 0 empties the list; more than 9 is 9; the list is cut to fit.
     * `cfg.MRUCount` (clsConfig.cls:2690).
     */
    fun changeMruCount(count: Int) {
        mruCount = if (count < 1) 0 else minOf(count, 9)
        while (mruList.size > mruCount) {
            mruList.removeAt(mruList.size - 1)
        }
    }

    /**
     * Puts [file] (a full path) at the top of the recent files, dropping any other with the same name
     * (whatever its folder), keeping [mruCount]. Only .build files are added, unless already listed.
     * Ported from `cfg.AddMRU` (clsConfig.cls:2742).
     */
    fun addRecent(file: String) {
        if (file.isEmpty() || mruCount <= 0) {
            return
        }
        val found = mruList.contains(file)
        if (!found && !file.lowercase().endsWith(".build")) {
            return
        }
        val display = nameOf(file)
        val old = ArrayList(mruList)
        mruList.clear()
        mruList.add(file)
        for (entry in old) {
            if (entry.isNotEmpty() && nameOf(entry) != display) {
                if (mruList.size >= mruCount) {
                    break
                }
                mruList.add(entry)
            }
        }
    }

    /** GetNameFromFilespec: the file name without its folder or extension. */
    private fun nameOf(path: String): String {
        var name = path.substring(maxOf(path.lastIndexOf('\\'), path.lastIndexOf('/')) + 1)
        val dot = name.lastIndexOf('.')
        if (dot >= 0) {
            name = name.substring(0, dot)
        }
        return name
    }

    /** The file's text: the sections in VB's order, CRLF line ends. SaveSettings (:1123). */
    fun write(): String {
        val out = StringBuilder()
        out.append(section("Character Builder Lite", charBuildLines()))
        otherSections["Compendium"]?.let { out.append("Section: Compendium").append(it) }
        otherSections["Paths"]?.let { out.append("Section: Paths").append(it) }
        out.append("Section: Output\r\n\r\n").append(section(null, outputLines()))
        out.append(section("Shared", sharedLines()))
        val colors = colorsText
        if (colors != null) {
            out.append("Section: Colors").append(colors)
        }
        for ((title, body) in otherSections) {
            if (title != "Compendium" && title != "Paths") {
                out.append("Section: ").append(title).append(body)
            }
        }
        return out.toString()
    }

    /** InitString/AddString/GetString: the title with a blank line after it, the lines, two blank lines. */
    private fun section(title: String?, lines: List<String>): String {
        val all = ArrayList<String>()
        if (title != null) {
            all.add("Section: $title")
            all.add("")
        }
        all.addAll(lines)
        all.add("")
        all.add("")
        all.add("")
        return all.joinToString("\r\n")
    }

    private fun coordsText(c: Coords): String = c.left.toString() + "\t" + c.top + "\t" + c.width + "\t" + c.height

    /** MakeCharBuildSettings (:1179). */
    private fun charBuildLines(): List<String> = listOf(
        "WindowSize=" + windowSize.ordinal,
        "Maximized=" + vbBool(maximized),
        "WindowCoords=" + coordsText(windowCoords),
        "DialogCoords=" + coordsText(dialogCoords),
        "OptionsCoords=" + coordsText(optionsCoords),
        "ConvertCoords=" + coordsText(convertCoords),
        "ConvertOnStartup=" + vbBool(convertOnStartup),
        "ChildWindows=" + vbBool(childWindows),
        "ShowErrorsOnStartup=" + vbBool(showErrors),
        "UseIcons=" + vbBool(useIcons),
        "IconOverview=" + vbBool(iconOverview),
        "IconSkills=" + vbBool(iconSkills),
        "MRUCount=$mruCount",
        "MRUList=" + mruList.joinToString("\t"),
        "BuildPoints=" + buildPoints.ordinal,
        "SkillOrderScreen=$skillOrderScreen",
        "SkillOrderOutput=" + skillOrderOutput.ordinal,
        "FeatOrder=$featOrder",
        "FeatChannels=" + vbBool(featChannels),
        "FeatOrderOutput=" + featOrderOutput.ordinal,
        "FeatShowAll=" + vbBool(featShowAll),
        "BBCodes=$bbCodes",
        "OutputMargin=$outputMargin",
        "Confirm=" + vbBool(confirm),
        "Contrast=$contrast",
        "Version=$version",
        "ProcessTime=$processTime",
    )

    /** MakeOutputSettings (:1323). */
    private fun outputLines(): List<String> = output.toLines()

    /** MakeSharedSettings (:1311). */
    private fun sharedLines(): List<String> = listOf(
        "ColorCoords=" + coordsText(colorCoords),
        "ScreenColors=$screenColors",
        "OutputColors=$outputColors",
        "ColorVersion=$colorVersion",
    )
}
