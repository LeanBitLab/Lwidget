#!/bin/bash

# Update AwidgetProvider.kt
cat << 'PATCH1' > awidget_patch.diff
--- app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
+++ app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
@@ -377,10 +377,12 @@
             val primaryColor = resolveColor(textColorPrimaryIdx, true, useLightTheme)
             val secondaryColor = resolveColor(textColorSecondaryIdx, false, useLightTheme)

+            val dateColorIdx = prefs.getInt("date_color_idx", 0)
+
             // Slightly distinct colors for date and next alarm
             val dateColor = if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                 // Warm accent for date
                 context.getColor(if (useLightTheme) android.R.color.system_accent2_700 else android.R.color.system_accent2_100)
             } else {
-                if (useLightTheme) android.graphics.Color.parseColor("#AA555544") else android.graphics.Color.parseColor("#BBDDDDCC")
+                when (dateColorIdx) {
+                    2 -> android.graphics.Color.rgb(prefs.getInt("date_color_r", 255), prefs.getInt("date_color_g", 255), prefs.getInt("date_color_b", 255))
+                    1 -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) context.getColor(android.R.color.system_accent2_500) else android.graphics.Color.YELLOW
+                    else -> if (useLightTheme) android.graphics.Color.parseColor("#AA555544") else android.graphics.Color.parseColor("#BBDDDDCC")
+                }
             }
             val alarmColor = if (useDynamicColors && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
PATCH1

# Update MainActivity.kt
cat << 'PATCH2' > main_patch.diff
--- app/src/main/java/com/leanbitlab/lwidget/MainActivity.kt
+++ app/src/main/java/com/leanbitlab/lwidget/MainActivity.kt
@@ -1187,6 +1187,7 @@
                     prefs.edit()
                         .putInt("text_color_primary_idx", 0)
                         .putInt("text_color_secondary_idx", 0)
+                        .putInt("date_color_idx", 0)
                         .putInt("outline_color_idx", 0)
                         .putInt("bg_color_idx", 0)
                         .apply()
@@ -1226,6 +1227,15 @@
         bindColorSliders(R.id.row_text_color_secondary_custom, "text_color_secondary")
         secondarySliderRow.visibility = if (prefs.getInt("text_color_secondary_idx", 0) == 2) View.VISIBLE else View.GONE

+        // Date Color
+        val dateSliderRow = findViewById<View>(R.id.row_date_color_custom)
+        bindSelector(R.id.row_date_color, getString(R.string.section_date_color), "date_color_idx", colorOptions, 0) { idx ->
+            dateSliderRow.visibility = if (idx == 2) View.VISIBLE else View.GONE
+            if (idx != 2) updateWidget()
+        }
+        bindColorSliders(R.id.row_date_color_custom, "date_color")
+        dateSliderRow.visibility = if (prefs.getInt("date_color_idx", 0) == 2) View.VISIBLE else View.GONE
+
         // Outline Color
         val outlineSliderRow = findViewById<View>(R.id.row_outline_color_custom)
         bindSelector(R.id.row_outline_color, getString(R.string.section_outline_color), "outline_color_idx", colorOptions, 0) { idx ->
@@ -1400,6 +1410,7 @@
             R.id.row_bg_color, R.id.row_bg_color_custom,
             R.id.row_text_color_primary, R.id.row_text_color_primary_custom,
             R.id.row_text_color_secondary, R.id.row_text_color_secondary_custom,
+            R.id.row_date_color, R.id.row_date_color_custom,
             R.id.row_outline_color, R.id.row_outline_color_custom
         )
         manualColorIds.forEach { id ->
PATCH2

# Update strings.xml
cat << 'PATCH3' > strings_patch.diff
--- app/src/main/res/values/strings.xml
+++ app/src/main/res/values/strings.xml
@@ -25,6 +25,7 @@
     <string name="section_bg_transparency">Transparency</string>
     <string name="section_text_color_primary">Text Color</string>
     <string name="section_text_color_secondary">Text Color 2</string>
+    <string name="section_date_color">Date Color</string>
     <string name="section_outline_color">Outline Color</string>
     <string name="section_font">Font Style</string>
     <string name="section_bg_color">Background Color</string>
PATCH3

# Update activity_main.xml
cat << 'PATCH4' > activity_main_patch.diff
--- app/src/main/res/layout/activity_main.xml
+++ app/src/main/res/layout/activity_main.xml
@@ -1246,6 +1246,16 @@
                                         android:layout_height="wrap_content"/>

                                     <include layout="@layout/settings_selector_row"
+                                        android:id="@+id/row_date_color"
+                                        android:layout_width="match_parent"
+                                        android:layout_height="wrap_content"/>
+
+                                    <include layout="@layout/settings_color_row"
+                                        android:id="@+id/row_date_color_custom"
+                                        android:layout_width="match_parent"
+                                        android:layout_height="wrap_content"/>
+
+                                    <include layout="@layout/settings_selector_row"
                                         android:id="@+id/row_outline_color"
                                         android:layout_width="match_parent"
                                         android:layout_height="wrap_content"/>
PATCH4

patch -p0 < awidget_patch.diff
patch -p0 < main_patch.diff
patch -p0 < strings_patch.diff
patch -p0 < activity_main_patch.diff
