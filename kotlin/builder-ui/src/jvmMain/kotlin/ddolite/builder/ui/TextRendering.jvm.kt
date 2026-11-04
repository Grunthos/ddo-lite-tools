package ddolite.builder.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle

/**
 * Compose for Desktop's default is grey smoothing, light hinting and letters at fractional
 * positions, which looks soft at the windows' small text size. This asks for colour (subpixel)
 * smoothing, full hinting and letters on whole pixels, as Windows' ClearType does.
 */
@OptIn(ExperimentalTextApi::class)
internal actual val TEXT_RENDERING: PlatformTextStyle? = PlatformTextStyle(
    null,
    PlatformParagraphStyle(
        FontRasterizationSettings(
            smoothing = FontSmoothing.SubpixelAntiAlias,
            hinting = FontHinting.Full,
            subpixelPositioning = false,
            autoHintingForced = false,
        ),
    ),
)
