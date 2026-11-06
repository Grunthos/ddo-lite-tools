package ddolite.builder.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.MAX_LEVELUPS
import ddolite.builder.build.StatRules
import ddolite.builder.model.Stat
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme
import kotlinx.coroutines.delay

/** frmStats' client area, in pixels at 100%. */
private const val STATS_WIDTH = 815
private const val STATS_HEIGHT = 549

/*
 * The Stats tab's layout, from the content corner (WindowFrame): two rows of captioned frames. Base
 * Stats and Levelups on top, Tomes and Templates under them; the right-hand frames end at the right
 * margin. Controls are placed from their frame's corner.
 */
private const val BASE_WIDTH = 529
private const val BASE_HEIGHT = 249
private const val LEVELUPS_LEFT = BASE_WIDTH + GAP
private const val LEVELUPS_HEIGHT = 281
private const val ROW2_TOP = 280
private const val ROW2_HEIGHT = 185
private const val TOMES_WIDTH = 233
private const val TEMPLATES_LEFT = TOMES_WIDTH + GAP

/** userStats' left edges in Base Stats, one per point-buy column (usrStats(0..3)), and their top. */
private const val COLUMNS_LEFT = 24
private const val COLUMN_STEP = 128
private const val COLUMNS_TOP = 72

/** chkTemplate(0..3): the first one's top in Templates, and the step between them. */
private const val TEMPLATE_TOP = 28
private const val TEMPLATE_STEP = 36

/**
 * The Stats window: header (Stats;Schedule | Help), the four groups (Base Stats with the four
 * point-buy columns, Levelups, Tomes, Templates) or the Schedule, and the footer
 * (< Overview, Clear Stats, Skills >), in a [WindowFrame]; the client area is 815 x 549 at 100%.
 * VB's positions are in tools/form-layout's frmStats.md.
 *
 * [onNavigate] gets the footer's "< Overview" and "Skills >".
 */
@Composable
fun StatsWindowContent(state: BuilderSession, onNavigate: (String) -> Unit) {
    val theme = state.skills.theme
    val screen = state.stats
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision // read so this redraws when the build changes
    val rules = screen.rules
    WindowFrame(
        theme, STATS_WIDTH, STATS_HEIGHT,
        header = {
            HeaderBar(
                theme,
                listOf("Stats", "Schedule"),
                if (screen.scheduleTab) 1 else 0,
                listOf("Help"),
                isEnabled = { true },
                onClick = { link ->
                    if (link == "Help") {
                        state.help.show("Stats")
                    } else {
                        screen.showTab(link == "Schedule")
                    }
                },
            )
        },
        footer = {
            // usrFooter_Click (:1003).
            HeaderBar(
                theme,
                listOf("< Overview"),
                null,
                listOf("Skills >"),
                isEnabled = { true },
                onClick = { link ->
                    if (link == "Clear Stats") {
                        screen.clearStats()
                    } else {
                        onNavigate(link)
                    }
                },
                centerLink = "Clear Stats",
            )
        },
    ) { area ->
        if (rules != null) {
            if (screen.scheduleTab) {
                Schedule(rules, theme, 0, 0, area.width, area.height)
            } else {
                StatsGroups(screen, rules, theme, area)
            }
        }
    }
    // tmrWarning (Interval 5000): a template's warning shows for five seconds.
    val warningRevision = screen.warningRevision
    LaunchedEffect(warningRevision) {
        if (screen.warningIndex >= 0) {
            delay(5000)
            screen.warningTimedOut()
        }
    }
}

@Composable
private fun StatsGroups(screen: StatsScreenState, rules: StatRules, theme: Theme, area: ContentArea) {
    val build = rules.build
    val label = labelStyle(theme)
    val control = controlStyle(theme)
    var openList by remember { mutableStateOf("") }

    // ---- Base Stats (fraGroup(0))
    GroupFrame(theme, "Base Stats", 0, 0, BASE_WIDTH, BASE_HEIGHT)
    Label("Preferred Build Points:", label, 24, 34)
    ComboBox(theme, control, build.buildPoints.displayName, 168, 32, 105, 21) { openList = if (openList == "points") "" else "points" }
    Label(rules.pointsLabel(), label, 280, 34)
    for (column in BuildPoints.entries) {
        StatsColumn(screen, rules, theme, column, COLUMNS_LEFT + column.ordinal * COLUMN_STEP, COLUMNS_TOP)
    }

    // ---- Levelups (fraGroup(1)), out to the right margin.
    GroupFrame(theme, "Levelups", LEVELUPS_LEFT, 0, area.width - LEVELUPS_LEFT, LEVELUPS_HEIGHT)
    for (i in 0..MAX_LEVELUPS) {
        if (!rules.levelupShown(i)) {
            continue
        }
        val top = 32 + i * 24
        var caption = "All Levels:"
        if (i > 0) {
            caption = "Level " + (i * 4) + ":"
        }
        Label(caption, label, LEVELUPS_LEFT + 16, top + 2)
        ComboBox(theme, control, build.levelups[i]?.displayName ?: "", LEVELUPS_LEFT + 80, top, 129, 22) {
            openList = if (openList == "levelup$i") "" else "levelup$i"
        }
    }

    // ---- Tomes (fraGroup(2)); hidden while no tome can be used yet.
    GroupFrame(theme, "Tomes", 0, ROW2_TOP, TOMES_WIDTH, ROW2_HEIGHT)
    val tomeMax = rules.tomeMax()
    if (tomeMax > 0) {
        Label("Supreme", label, 24, ROW2_TOP + 32)
        Spinner(
            value = build.tomeAll,
            min = 0,
            max = tomeMax,
            theme = theme,
            onValueChange = { screen.setTome(null, it) },
            modifier = Modifier.offset(128.dp, (ROW2_TOP + 32).dp).size(65.dp, 17.dp),
        )
        for (stat in Stat.entries) {
            val top = ROW2_TOP + 64 + stat.ordinal * 16
            Label(stat.displayName, label, 24, top)
            Spinner(
                value = build.tomes[stat.ordinal],
                min = 0,
                max = tomeMax,
                theme = theme,
                onValueChange = { screen.setTome(stat, it) },
                modifier = Modifier.offset(128.dp, top.dp).size(65.dp, 17.dp),
            )
        }
    }
    if (rules.tomesLimitedByLevel()) {
        Label("Tomes locked to max level", label.copy(color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)), 24, ROW2_TOP + 167)
    }

    // ---- Templates (fraGroup(3)): a frame whose caption is the class link, out to the right margin.
    val templatesWidth = area.width - TEMPLATES_LEFT
    val templatesRight = TEMPLATES_LEFT + templatesWidth
    Frame(theme, TEMPLATES_LEFT, ROW2_TOP + 8, templatesWidth, ROW2_HEIGHT - 8)
    val templates = screen.templates()
    val templateClass = screen.templateClass
    if (templates.isNotEmpty() && templateClass != null) {
        // lnkTemplate drops down the list of classes (mnuTemplate).
        LinkLabel(theme, templateClass.name + " Templates", TEMPLATES_LEFT + 8, ROW2_TOP) { openList = if (openList == "class") "" else "class" }
    }
    // lnkCustomize drops down mnuCustomize (lnkCustomize_MouseDown, :1429), at the frame's right.
    @Suppress("UNUSED_VARIABLE")
    val templatesRevision = screen.templatesRevision
    val customizeLeft = templatesRight - 75
    LinkLabel(theme, "Customize", customizeLeft, ROW2_TOP) { openList = if (openList == "customize") "" else "customize" }
    for (i in templates.indices) {
        val template = templates[i]
        val top = ROW2_TOP + TEMPLATE_TOP + i * TEMPLATE_STEP
        PushButton(theme, template.caption, TEMPLATES_LEFT + 28, top, 89, 29) { screen.applyTemplate(i) }
        var text = template.description
        var color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
        if (screen.warningIndex == i) {
            text = template.warning
            color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
        }
        BasicText(
            text,
            style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = color),
            modifier = Modifier.offset((TEMPLATES_LEFT + 128).dp, (top + 8).dp).size((templatesWidth - 136).dp, 34.dp),
        )
    }

    // ---- Open lists, drawn last so they're on top.
    if (openList == "points") {
        val choices = rules.preferredChoices()
        DropList(theme, control, choices.map { it.displayName }, 168, 53, 105) { index ->
            openList = ""
            screen.choosePreferred(choices[index])
        }
    } else if (openList.startsWith("levelup")) {
        val index = openList.removePrefix("levelup").toInt()
        val names = ArrayList<String>()
        names.add("")
        for (stat in Stat.entries) {
            names.add(stat.displayName)
        }
        DropList(theme, control, names, LEVELUPS_LEFT + 80, 32 + index * 24 + 22, 129) { chosen ->
            openList = ""
            screen.chooseLevelup(index, if (chosen == 0) null else Stat.entries[chosen - 1])
        }
    } else if (openList == "customize") {
        // Under the link (the menu is drawn in the content area, so in its pixels).
        val density = LocalDensity.current.density
        val menuX = customizeLeft * density
        val menuY = (ROW2_TOP + 16) * density
        PopupMenu(
            screen.customizeItems, -1, Offset(menuX, menuY), density,
            onChoose = { index ->
                openList = ""
                screen.customize(index)
            },
            onDismiss = { openList = "" },
            clientWidth = STATS_WIDTH,
            enabled = { screen.customizeEnabled(it) },
        )
    } else if (openList == "class") {
        // InitTemplatesMenu (:1064): every class, with the current one ticked.
        val classes = rules.rules.data.classes
        val names = ArrayList<String>()
        for (c in classes) {
            names.add(if (c == templateClass) "✓ " + c.name else "   " + c.name)
        }
        DropList(theme, control, names, TEMPLATES_LEFT + 8, ROW2_TOP + 16, 180) { chosen ->
            openList = ""
            screen.chooseTemplateClass(classes[chosen])
        }
    }
}

/**
 * One point-buy column: its Include box, and when included the six stats with their spinners,
 * the cost of the next point and the points spent. Ported from the userStats control
 * (Source/Controls/userStats.ctl; ShowPoints :453, ShowCost :478, ShowSpent :487).
 */
@Composable
private fun StatsColumn(screen: StatsScreenState, rules: StatRules, theme: Theme, column: BuildPoints, left: Int, top: Int) {
    val build = rules.build
    val included = build.includePoints[column.ordinal]
    CheckBox(theme, column.displayName, included, rules.canInclude(column), left + 12, top, 97) { screen.setIncluded(column, !included) }
    if (!included) {
        return
    }
    val label = labelStyle(theme)
    val cost = TextStyle(platformStyle = TEXT_RENDERING, fontSize = 10.sp, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM), textAlign = TextAlign.Center)
    for (stat in Stat.entries) {
        val rowTop = top + 24 + stat.ordinal * 16
        Label(stat.abbreviation, label, left, rowTop)
        val value = rules.columnStat(column, stat)
        Spinner(
            value = value,
            min = 6,
            max = 20,
            theme = theme,
            onValueChange = { newValue -> screen.changeStat(column, stat, newValue > value) },
            modifier = Modifier.offset((left + 32).dp, rowTop.dp).size(65.dp, 17.dp),
        )
        val next = rules.nextCost(column, stat)
        BasicText(if (next == 0) "-" else "+$next", style = cost, modifier = Modifier.offset((left + 98).dp, (rowTop + 2).dp).size(18.dp, 13.dp))
    }
    // picSpent: the points spent, in a box.
    Box(
        modifier = Modifier.offset((left + 48).dp, (top + 128).dp).size(33.dp, 17.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
    ) {
        BasicText(
            build.statTotals[column.ordinal].toString(),
            style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT), textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A Frame with its caption on the border (lblGroup over shpGroup). */
@Composable
internal fun GroupFrame(theme: Theme, caption: String, left: Int, top: Int, width: Int, height: Int) {
    Frame(theme, left, top + 8, width, height - 8)
    BasicText(
        caption,
        style = labelStyle(theme),
        modifier = Modifier.offset((left + 8).dp, top.dp).background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)),
    )
}

/** A label drawn as a link (VB's lnk… labels). */
@Composable
private fun LinkLabel(theme: Theme, text: String, left: Int, top: Int, onClick: () -> Unit) {
    BasicText(
        text,
        style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_LINK), textDecoration = TextDecoration.Underline),
        modifier = Modifier.offset(left.dp, top.dp).background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)).clickable(onClick = onClick),
    )
}

/** The userCheckBox control: a box with a tick, and its caption. */
@Composable
internal fun CheckBox(theme: Theme, caption: String, checked: Boolean, enabled: Boolean, left: Int, top: Int, width: Int, onClick: () -> Unit) {
    var textColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (!enabled) {
        textColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)
    }
    var modifier = Modifier.offset(left.dp, top.dp).size(width.dp, 17.dp)
    if (enabled) {
        modifier = modifier.clickable(onClick = onClick)
    }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.offset(0.dp, 2.dp).size(13.dp, 13.dp)
                .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
                .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
        ) {
            if (checked) {
                BasicText("✓", style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = 11.sp, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)), modifier = Modifier.offset(1.dp, (-2).dp))
            }
        }
        BasicText(caption, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = textColor), modifier = Modifier.offset(18.dp, 0.dp))
    }
}

/**
 * The Schedule tab: each stat's score at every level 0-30, with rows for its tome and level-ups
 * when it has them; a value that hasn't changed since the level before is dim. Ported from
 * frmStats.ShowSchedule (:1502), InitGrid (:1522) and the Draw…Row routines (:1544-:1650).
 *
 * Quirk not copied: VB's level-up row runs to level 36 (MAX_LEVEL) but its grid only has
 * columns 0-30, so on VB that row would fail at level 31 ("Subscript out of range"); here it
 * stops at 30 like the other rows.
 */
@Composable
private fun Schedule(rules: StatRules, theme: Theme, left: Int, top: Int, width: Int, height: Int) {
    val measurer = rememberTextMeasurer()
    val text = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    val dim = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)
    val style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE)
    val build = rules.build
    Canvas(modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)) {
        fun textWidth(value: String): Int = measurer.measure(value, style).size.width
        val headerWidth = textWidth("Constitution ")
        val cellWidth = (size.width.toInt() - headerWidth) / 31
        val offsetX1 = (cellWidth - textWidth("9")) / 2
        val offsetX2 = (cellWidth - textWidth("99")) / 2
        val textHeight = measurer.measure("Q", style).size.height
        val rowHeight = textHeight + 4
        val offsetY = (rowHeight - textHeight) / 2

        fun cellText(value: Int, column: Int, rowTop: Int, color: androidx.compose.ui.graphics.Color) {
            var x = headerWidth + column * cellWidth
            x += if (value < 10) offsetX1 else offsetX2
            drawText(measurer, value.toString(), Offset(x.toFloat(), (rowTop + offsetY).toFloat()), style.copy(color = color))
        }

        // Header row: the levels.
        for (i in 0..30) {
            cellText(i, i, 0, text)
        }
        var rowTop = rowHeight * 2
        for (stat in Stat.entries) {
            drawText(measurer, stat.displayName, Offset(0f, (rowTop + offsetY).toFloat()), style.copy(color = text))
            var old = 0
            for (i in 0..30) {
                val value = rules.stat(stat, i, fred = i != 0)
                cellText(value, i, rowTop, if (value != old) text else dim)
                old = value
            }
            rowTop += rowHeight
            if (build.tomes[stat.ordinal] != 0) {
                drawText(measurer, "Tome", Offset(0f, (rowTop + offsetY).toFloat()), style.copy(color = text))
                old = 0
                for (i in 0..30) {
                    val value = rules.tome(stat, i, i != 0)
                    cellText(value, i, rowTop, if (value != old) text else dim)
                    old = value
                }
                rowTop += rowHeight
            }
            var hasLevelup = false
            for (i in 1..MAX_LEVELUPS) {
                if (build.levelups[i] == stat) {
                    hasLevelup = true
                }
            }
            if (hasLevelup) {
                drawText(measurer, "Levelups", Offset(0f, (rowTop + offsetY).toFloat()), style.copy(color = text))
                old = 0
                for (i in 0..30) {
                    val value = rules.levelups(stat, i)
                    cellText(value, i, rowTop, if (value != old) text else dim)
                    old = value
                }
                rowTop += rowHeight
            }
            rowTop += rowHeight
        }
    }
}
