package ddolite.builder.ui

import androidx.compose.ui.text.PlatformTextStyle

/**
 * How text is drawn, added to every text style in the windows (`platformStyle = TEXT_RENDERING`).
 * On the desktop: as close to Windows' ClearType as Compose can get; null elsewhere (the platform's
 * default). Kotlin only: VB draws text with Windows itself.
 */
internal expect val TEXT_RENDERING: PlatformTextStyle?
