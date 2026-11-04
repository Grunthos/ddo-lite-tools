package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ddolite.builder.model.Skill
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** frmSkills' client width, the bottom of usrHeader and the top of usrFooter, in pixels at 100%. */
private const val CLIENT_WIDTH = 870
private const val HEADER_HEIGHT = 26
private const val FOOTER_TOP = 492

/** One frame on the Skill Tomes tab (fraSkills(0..5)): its caption, where it is, and its skills from the top. */
private class TomeFrame(val caption: String, val left: Int, val top: Int, val skills: List<Skill>)

/** The Skill Tomes frames, from frmSkills.frm's positions (tools/form-layout's frmSkills.md). */
private val TOME_FRAMES = listOf(
    TomeFrame("Athletics", 156, 64, listOf(Skill.BALANCE, Skill.JUMP, Skill.SWIM, Skill.TUMBLE)),
    TomeFrame("Spellcasting", 400, 64, listOf(Skill.CONCENTRATION, Skill.SPELLCRAFT, Skill.HEAL, Skill.REPAIR)),
    TomeFrame("Social", 156, 196, listOf(Skill.BLUFF, Skill.DIPLOMACY, Skill.INTIMIDATE)),
    TomeFrame("Trapping", 400, 196, listOf(Skill.SEARCH, Skill.SPOT, Skill.DISABLE_DEVICE, Skill.OPEN_LOCK)),
    TomeFrame("Miscellaneous", 156, 328, listOf(Skill.UMD, Skill.PERFORM, Skill.HAGGLE, Skill.LISTEN)),
    TomeFrame("Stealth", 400, 328, listOf(Skill.HIDE, Skill.MOVE_SILENTLY)),
)

/** The Contrast menu (mnuContrast(0..3)), in [Contrast] order. */
private val CONTRAST_ITEMS = listOf("None", "Low", "Medium", "High")

/**
 * The Skills window (Source/Forms/Build/frmSkills.frm): the header (tabs "Skill Ranks" and
 * "Skill Tomes"; "Contrast" and "Help"), the skills grid or the skill tome spinners, and the
 * footer ("< Stats", "Clear Skills" or "Clear Tomes", "Feats >"). The client area is 870 x 517
 * at 100%.
 *
 * [classIcons] are drawn above the levels when the build has more than one class (VB's default,
 * `cfg.UseIcons And cfg.IconSkills`); without them, as on Android, the classes' initials are.
 * [onNavigate] gets the footer's "< Stats" and "Feats >".
 */
@Composable
fun SkillsWindowContent(state: BuilderSession, classIcons: Map<String, ImageBitmap>, onNavigate: (String) -> Unit) {
    val skills = state.skills
    val theme = skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision // read so this redraws when the build changes
    val density = LocalDensity.current.density
    // Where the pointer last was, in pixels: VB's PopupMenu shows the menu there.
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var contrastMenuAt by remember { mutableStateOf<Offset?>(null) }
    var skillMenuAt by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pointer = event.changes[0].position
                    }
                }
            },
    ) {
        if (state.opened != null && skills.hasLevels()) {
            if (skills.tomesTab) {
                SkillTomes(skills, theme)
            } else {
                val textMeasurer = rememberTextMeasurer()
                val classRow = skills.isMulticlass()
                val icons = classIcons.isNotEmpty()
                val place = remember(textMeasurer, classRow, icons, density) {
                    windowGridLayout(textMeasurer, classRow, icons, CLIENT_WIDTH * density, HEADER_HEIGHT * density, FOOTER_TOP * density)
                }
                SkillsGrid(
                    skills,
                    place.layout,
                    classIcons,
                    Modifier.offset((place.left / density).dp, (place.top / density).dp),
                ) { at -> skillMenuAt = Offset(place.left + at.x, place.top + at.y) }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            // usrHeader_Click (frmSkills.frm:1186).
            var tab = 0
            if (skills.tomesTab) {
                tab = 1
            }
            HeaderBar(
                theme,
                listOf("Skill Ranks", "Skill Tomes"),
                tab,
                listOf("Contrast", "Help"),
                isEnabled = { true },
                onClick = { link ->
                    if (link == "Help") {
                        state.help.show("Skills")
                    } else if (link == "Skill Ranks") {
                        skills.tomesTab = false
                    } else if (link == "Skill Tomes") {
                        skills.tomesTab = true
                    } else if (link == "Contrast") {
                        contrastMenuAt = pointer
                    }
                },
            )
        }
        Box(modifier = Modifier.offset(0.dp, FOOTER_TOP.dp).size(CLIENT_WIDTH.dp, HEADER_HEIGHT.dp)) {
            // usrFooter_Click (:1203). VB clears the tomes from a timer so the spinners lose focus first.
            var center = "Clear Skills"
            if (skills.tomesTab) {
                center = "Clear Tomes"
            }
            HeaderBar(
                theme,
                listOf("< Stats"),
                null,
                listOf("Feats >"),
                isEnabled = { true },
                onClick = { link ->
                    if (link == "Clear Skills") {
                        if (state.ask("Clear all skills?")) {
                            skills.clearSkills()
                        }
                    } else if (link == "Clear Tomes") {
                        if (state.ask("Clear all skill tomes?")) {
                            skills.clearTomes()
                        }
                    } else {
                        onNavigate(link)
                    }
                },
                centerLink = center,
            )
        }

        // ---- Pop-up menus, over everything.
        val skillAt = skillMenuAt
        if (skillAt != null && skills.menuRow != 0) {
            PopupMenu(skills.menuItems, -1, skillAt, density, onChoose = { index ->
                skillMenuAt = null
                skills.chooseMenu(index)
            }, onDismiss = {
                skillMenuAt = null
                skills.menuRow = 0
            })
        }
        val contrastAt = contrastMenuAt
        if (contrastAt != null) {
            // ContrastMenu (:1237) ticks the current setting; mnuContrast_Click (:1246) redraws.
            PopupMenu(CONTRAST_ITEMS, skills.contrast.ordinal, contrastAt, density, onChoose = { index ->
                contrastMenuAt = null
                skills.contrast = Contrast.entries[index]
            }, onDismiss = { contrastMenuAt = null })
        }
    }
}

/**
 * The Skill Tomes tab (picTomes): a spinner for each skill, in six frames. LoadData (:1376) and
 * usrspnSkill_Change (:1390).
 */
@Composable
private fun SkillTomes(skills: SkillsScreenState, theme: Theme) {
    @Suppress("UNUSED_VARIABLE")
    val revision = skills.revision // the tomes are in the build, not Compose state
    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT), textAlign = TextAlign.End)
    for (frame in TOME_FRAMES) {
        GroupFrame(theme, frame.caption, frame.left, frame.top, 225, 105)
        for (i in frame.skills.indices) {
            val skill = frame.skills[i]
            val top = frame.top + 24 + i * 16
            // lblSkill: right-aligned, ending 11 pixels before the spinner.
            BasicText(skill.displayName, style = label, modifier = Modifier.offset((frame.left + 8).dp, (top + 1).dp).size(121.dp, 16.dp))
            Spinner(
                value = skills.tome(skill),
                min = 0,
                max = skills.tomeMax,
                theme = theme,
                onValueChange = { skills.setTome(skill, it) },
                modifier = Modifier.offset((frame.left + 140).dp, top.dp).size(65.dp, 17.dp),
            )
        }
    }
}

/** Windows' menu colours (VB's pop-up menus are system menus, whatever the theme). */
private val MENU_BACKGROUND = Color(0xFFF2F2F2)
private val MENU_BORDER = Color(0xFFCCCCCC)
private val MENU_DISABLED = Color(0xFF6D6D6D)

/**
 * A pop-up menu at [at] (pixels in the window), like VB's PopupMenu: "-" is a separator, item
 * [checked] has a tick (-1 = none). An item is chosen when pressed (not released, so the right-click
 * that opened the menu can't choose one); a press anywhere else closes it. An item [enabled] says no to is
 * grey and does nothing.
 */
@Composable
internal fun PopupMenu(
    items: List<String>,
    checked: Int,
    at: Offset,
    density: Float,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
    clientWidth: Int = CLIENT_WIDTH,
    enabled: (Int) -> Boolean = { true },
) {
    val width = 190
    var height = 4
    for (item in items) {
        if (item == "-") {
            height += 9
        } else {
            height += 22
        }
    }
    // Inside the window, as Windows places a menu: leftward from the pointer if it would pass the
    // right edge, upward if it would pass the bottom (the window's size when known, else clientWidth).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        var windowWidth = clientWidth
        if (constraints.hasBoundedWidth) {
            windowWidth = (constraints.maxWidth / density).toInt()
        }
        var windowHeight = Int.MAX_VALUE
        if (constraints.hasBoundedHeight) {
            windowHeight = (constraints.maxHeight / density).toInt()
        }
        val x = (at.x / density).toInt()
        val y = (at.y / density).toInt()
        var left = x
        if (left + width > windowWidth) {
            left = x - width
        }
        if (left < 0) {
            left = 0
        }
        var top = y
        if (top + height > windowHeight) {
            top = y - height
        }
        if (top < 0) {
            top = 0
        }
        MenuBody(items, checked, left, top, width, height, onChoose, onDismiss, enabled)
    }
}

/** The [PopupMenu] at [left], [top]: the press-catcher behind it, and the items. */
@Composable
private fun MenuBody(
    items: List<String>,
    checked: Int,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
    enabled: (Int) -> Boolean,
) {
    var hover by remember { mutableIntStateOf(-1) }
    // A press outside the menu closes it, and goes no further (as a Windows menu).
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press) {
                        event.changes[0].consume()
                        onDismiss()
                    }
                }
            }
        },
    )
    Column(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .background(MENU_BACKGROUND)
            .border(1.dp, MENU_BORDER),
    ) {
        Box(modifier = Modifier.size(width.dp, 2.dp))
        for (i in items.indices) {
            if (items[i] == "-") {
                Box(modifier = Modifier.size(width.dp, 9.dp)) {
                    Box(modifier = Modifier.offset(28.dp, 4.dp).size((width - 32).dp, 1.dp).background(MENU_BORDER))
                }
                continue
            }
            val itemEnabled = enabled(i)
            var style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = if (itemEnabled) Color.Black else MENU_DISABLED)
            var back = Color.Transparent
            if (i == hover && itemEnabled) {
                style = style.copy(color = HIGHLIGHT_TEXT)
                back = HIGHLIGHT
            }
            Box(
                modifier = Modifier.size(width.dp, 22.dp).background(back)
                    .pointerInput(i) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                                    hover = i
                                } else if (event.type == PointerEventType.Exit) {
                                    hover = -1
                                } else if (event.type == PointerEventType.Press) {
                                    event.changes[0].consume()
                                    if (itemEnabled) {
                                        onChoose(i)
                                    }
                                }
                            }
                        }
                    },
            ) {
                if (i == checked) {
                    BasicText("✓", style = style, modifier = Modifier.offset(8.dp, 3.dp))
                }
                BasicText(items[i], style = style, modifier = Modifier.offset(28.dp, 3.dp))
            }
        }
    }
}
