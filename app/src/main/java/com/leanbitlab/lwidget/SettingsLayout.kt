/*
 * Copyright (C) 2026 LeanBitLab
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.leanbitlab.lwidget

import androidx.core.view.doOnLayout
import androidx.core.widget.NestedScrollView

/** Settings content stops growing past this width so tablets/landscape stay readable. */
internal const val MAX_SETTINGS_CONTENT_WIDTH_DP = 640

/** Extra side inset (per side) needed to center content of at most [maxContentPx] in [availablePx]. */
internal fun contentHorizontalInset(availablePx: Int, maxContentPx: Int): Int =
    if (availablePx > maxContentPx) (availablePx - maxContentPx) / 2 else 0

/** Centers the settings column on wide screens by widening the scroll view's side padding. */
internal fun MainActivity.constrainContentWidth() {
    val scroll = findViewById<NestedScrollView>(R.id.nested_scroll_view) ?: return
    val maxPx = (MAX_SETTINGS_CONTENT_WIDTH_DP * resources.displayMetrics.density).toInt()
    val baseStart = scroll.paddingStart
    val baseEnd = scroll.paddingEnd
    scroll.doOnLayout { view ->
        val inset = contentHorizontalInset(view.width - baseStart - baseEnd, maxPx)
        if (inset > 0) {
            view.setPaddingRelative(baseStart + inset, view.paddingTop, baseEnd + inset, view.paddingBottom)
        }
    }
}
