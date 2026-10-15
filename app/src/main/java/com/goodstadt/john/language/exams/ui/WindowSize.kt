package com.goodstadt.john.language.exams.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * Tablet adaptation (branch V4_tablet).
 *
 * `LocalIsExpanded` is true when the current window is in the Expanded width bucket
 * (`WindowWidthSizeClass.Expanded`, i.e. >= 840.dp). That is the "this is really a tablet /
 * big window" gate — NOT Medium. Android phones have no portrait lock (unlike the iOS targets,
 * which are always `.compact`), so a phone in landscape can report Medium (~640dp); gating tablet
 * layouts on Expanded keeps every phone on the existing phone layout while giving real tablets,
 * foldables (open) and large split-screen windows the wider layout.
 *
 * Window-size-class is window-based, so this also covers split-screen / multi-window / foldables
 * for free, and recomposes when the window is resized.
 *
 * Provided once from [com.goodstadt.john.language.exams.MainActivity]; read anywhere via
 * `LocalIsExpanded.current`. Defaults to `false` (phone layout) if ever read without a provider.
 */
val LocalIsExpanded = compositionLocalOf { false }

/** Maps a [WindowWidthSizeClass] to our single "expanded == tablet layout" flag. */
fun WindowWidthSizeClass.isExpanded(): Boolean = this == WindowWidthSizeClass.Expanded
