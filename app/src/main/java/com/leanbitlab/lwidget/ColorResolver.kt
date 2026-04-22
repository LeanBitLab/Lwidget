package com.leanbitlab.lwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build

object ColorResolver {
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
                 context.getColor(if (isLight) android.R.color.system_accent1_800 else android.R.color.system_accent1_50)
             } else {
                 // Muted neutral for secondary items (temp, data, storage, steps)
                 context.getColor(if (isLight) android.R.color.system_neutral2_600 else android.R.color.system_neutral2_300)
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
