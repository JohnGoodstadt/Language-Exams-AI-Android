package com.goodstadt.john.language.exams.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tablet adaptation (branch V4_tablet) — Phase 1.
 *
 * Caps an element's width when the window is Expanded (a tablet / large window) and is a literal
 * no-op otherwise, so phones are unchanged. The analogue of the iOS `readableWidth()` modifier
 * (700pt -> 700.dp; dp ~= iOS points ~1:1).
 *
 * This only constrains width; horizontal placement is the parent's job, so apply it to content
 * sitting inside a parent that centres horizontally — e.g.
 *
 *     Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
 *         Content(Modifier.fillMaxSize().readableWidth())
 *     }
 *
 * On a phone `readableWidth()` is a no-op so the content fills the width as before; on a tablet it
 * is capped and the parent Box centres it, leaving the background / bars full-bleed.
 *
 * Expanded gate rationale: see [LocalIsExpanded].
 */
fun Modifier.readableWidth(maxWidth: Dp = 700.dp): Modifier = composed {
    if (LocalIsExpanded.current) this.widthIn(max = maxWidth) else this
}
