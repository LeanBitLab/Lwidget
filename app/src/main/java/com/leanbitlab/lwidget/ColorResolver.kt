package com.leanbitlab.lwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import com.leanbitlab.lwidget.render.WidgetColor

object ColorResolver {
    /** Picks the light, dark or "follow system" (night-qualified) variant of a color role. */
    fun themed(light: Int, dark: Int, auto: Int, isLight: Boolean, followSystem: Boolean): Int =
        if (followSystem) auto else if (isLight) light else dark

    /**
     * Like [resolveColor], but also carries a color resource on Android 12+ for theme-dependent
     * choices, so the launcher can follow wallpaper and dark-mode changes on its own.
     */
    fun resolveWidgetColor(
        context: Context,
        prefs: SharedPreferences,
        useDynamicColors: Boolean,
        idx: Int,
        isPrimary: Boolean,
        isLight: Boolean,
        followSystem: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): WidgetColor {
        val argb = resolveColor(context, prefs, useDynamicColors, idx, isPrimary, isLight, sdkInt)
        if (sdkInt < Build.VERSION_CODES.S) return WidgetColor(argb)
        val res = when {
            useDynamicColors -> if (isPrimary) {
                themed(R.color.widget_dyn_primary_light, R.color.widget_dyn_primary_dark, R.color.widget_dyn_primary_auto, isLight, followSystem)
            } else {
                themed(R.color.widget_dyn_secondary_light, R.color.widget_dyn_secondary_dark, R.color.widget_dyn_secondary_auto, isLight, followSystem)
            }
            idx == 0 && followSystem -> if (isPrimary) R.color.widget_text_auto else R.color.widget_text_secondary_auto
            idx == 1 -> android.R.color.system_accent1_500
            else -> 0
        }
        return WidgetColor(argb, res)
    }

    fun resolveColor(
        context: Context,
        prefs: SharedPreferences,
        useDynamicColors: Boolean,
        idx: Int,
        isPrimary: Boolean,
        isLight: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Int {
         // When dynamic colors is on, always use dynamic palette regardless of saved color index
         if (useDynamicColors && sdkInt >= Build.VERSION_CODES.S) {
             return if (isPrimary) {
                 // High contrast accent for time & battery
                 context.getColor(if (isLight) android.R.color.system_accent1_900 else android.R.color.system_accent1_50)
             } else {
                 // High contrast neutral for secondary items (temp, data, storage, steps)
                 context.getColor(if (isLight) android.R.color.system_neutral1_800 else android.R.color.system_neutral2_300)
             }
         }
         return when (idx) {
             0 -> { // Default
                 if (isPrimary) {
                     if (isLight) context.getColor(R.color.widget_text_light) else Color.WHITE
                 } else {
                     if (isLight) context.getColor(R.color.widget_text_secondary_light) else Color.parseColor("#CCFFFFFF")
                 }
             }
             1 -> if (sdkInt >= Build.VERSION_CODES.S) {
                      context.getColor(android.R.color.system_accent1_500)
                  } else {
                      Color.CYAN
                  }
             2 -> {
                 val prefix = if (isPrimary) "text_color_primary" else "text_color_secondary"
                 val r = prefs.getInt("${prefix}_r", 255)
                 val g = prefs.getInt("${prefix}_g", 255)
                 val b = prefs.getInt("${prefix}_b", 255)
                 Color.rgb(r, g, b)
             }
             else -> if (isPrimary) Color.WHITE else Color.parseColor("#CCFFFFFF")
         }
    }
}
