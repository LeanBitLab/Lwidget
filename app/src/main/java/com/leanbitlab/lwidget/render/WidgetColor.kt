package com.leanbitlab.lwidget.render

import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes

/**
 * A widget color: the resolved [argb] value, plus an optional color resource [res].
 *
 * On Android 12+ a non-zero [res] is handed to the launcher, which resolves it every time it
 * draws the widget. Wallpaper (Material You) and dark-mode changes then apply immediately,
 * without waiting for the next widget update. Older versions, or [res] == 0, use [argb].
 */
class WidgetColor(@ColorInt val argb: Int, @ColorRes val res: Int = 0) {
    private val useRes get() = res != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun applyTextColor(views: RemoteViews, viewId: Int) {
        if (useRes) views.setColor(viewId, "setTextColor", res) else views.setTextColor(viewId, argb)
    }

    fun applyColorFilter(views: RemoteViews, viewId: Int) {
        if (useRes) views.setColor(viewId, "setColorFilter", res) else views.setInt(viewId, "setColorFilter", argb)
    }

    fun applyImageTint(views: RemoteViews, viewId: Int) {
        when {
            useRes -> views.setColorStateList(viewId, "setImageTintList", res)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                views.setColorStateList(viewId, "setImageTintList", ColorStateList.valueOf(argb))
            else -> views.setInt(viewId, "setColorFilter", argb)
        }
    }
}
