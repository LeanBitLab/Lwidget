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

import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.TextView

private data class LanguageOption(val code: String, val displayName: String)

internal fun MainActivity.setupThemeSection(colorOptions: List<String>) {
    // Appearance Sections

    // Appearance Subsections (nested cards)
    bindNestedCard(R.id.header_appearance_presets, getString(R.string.header_presets), R.id.content_appearance_presets, "section_appearance_presets_expanded", R.id.header_chevron_appearance_presets)
    setupPresetsSection()
    bindNestedCard(R.id.header_appearance_outline, getString(R.string.header_outline), R.id.content_appearance_outline, "section_appearance_outline_expanded", R.id.header_chevron_appearance_outline)
    bindNestedCard(R.id.header_appearance_colors, getString(R.string.header_colors), R.id.content_appearance_colors, "section_appearance_colors_expanded", R.id.header_chevron_appearance_colors)
    bindNestedCard(R.id.header_appearance_theme, getString(R.string.header_theme), R.id.content_appearance_theme, "section_appearance_theme_expanded", R.id.header_chevron_appearance_theme)
    bindNestedCard(R.id.header_appearance_font, getString(R.string.header_font), R.id.content_appearance_font, "section_appearance_font_expanded", R.id.header_chevron_appearance_font)
    bindNestedCard(R.id.header_appearance_transparency, getString(R.string.header_transparency), R.id.content_appearance_transparency, "section_appearance_transparency_expanded", R.id.header_chevron_appearance_transparency)
    bindNestedCard(R.id.header_appearance_padding, getString(R.string.header_padding), R.id.content_appearance_padding, "section_appearance_padding_expanded", R.id.header_chevron_appearance_padding)

    // Reorder section
    bindReorderSection()

    // Outline toggle
    bindToggle(R.id.row_outline_toggle, getString(R.string.row_show_outline), "show_outline", false) { _ ->
        updateWidget()
    }

    // Dynamic Colors toggle
    val rowDynamicColors = findViewById<View>(R.id.row_dynamic_colors_toggle)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        rowDynamicColors.visibility = View.VISIBLE
        bindToggle(R.id.row_dynamic_colors_toggle, getString(R.string.row_dynamic_colors), "use_dynamic_colors", true) { isChecked ->
            updateColorVisibility(isChecked)
            if (isChecked) {
                prefs.edit()
                    .putInt("text_color_primary_idx", 0)
                    .putInt("text_color_secondary_idx", 0)
                    .putInt("date_color_idx", 0)
                    .putInt("outline_color_idx", 0)
                    .putInt("bg_color_idx", 0)
                    .apply()
            }
        }
    } else {
        rowDynamicColors.visibility = View.GONE
    }

    // Theme selector (Auto / Light / Dark)
    bindSelector(
        R.id.row_theme_mode, getString(R.string.row_theme_mode), "theme_mode",
        listOf(getString(R.string.theme_auto), getString(R.string.theme_light), getString(R.string.theme_dark)), 0
    ) { _ ->
        applyTheme()
    }

    // BG Transparency
    bindSlider(R.id.row_bg_transparency, getString(R.string.row_bg_opacity), "bg_opacity", 85f, 0f, 100f)

    // Widget Padding
    bindSlider(R.id.row_widget_padding, getString(R.string.row_widget_padding), "widget_padding", 24f, 0f, 48f, suffix = "dp")

    // Background Color
    val bgSliderRow = findViewById<View>(R.id.row_bg_color_custom)
    bindSelector(R.id.row_bg_color, getString(R.string.section_bg_color), "bg_color_idx", colorOptions, 0) { idx ->
        bgSliderRow.visibility = if (idx == 2) View.VISIBLE else View.GONE
        if (idx != 2) updateWidget()
    }
    bindColorSliders(R.id.row_bg_color_custom, "bg_color")
    bgSliderRow.visibility = if (prefs.getInt("bg_color_idx", 0) == 2) View.VISIBLE else View.GONE

    // Text Color Primary
    val primarySliderRow = findViewById<View>(R.id.row_text_color_primary_custom)
    bindSelector(R.id.row_text_color_primary, getString(R.string.section_text_color_primary), "text_color_primary_idx", colorOptions, 0) { idx ->
        primarySliderRow.visibility = if (idx == 2) View.VISIBLE else View.GONE
        if (idx != 2) updateWidget()
    }
    bindColorSliders(R.id.row_text_color_primary_custom, "text_color_primary")
    primarySliderRow.visibility = if (prefs.getInt("text_color_primary_idx", 0) == 2) View.VISIBLE else View.GONE

    // Text Color Secondary
    val secondarySliderRow = findViewById<View>(R.id.row_text_color_secondary_custom)
    bindSelector(R.id.row_text_color_secondary, getString(R.string.section_text_color_secondary), "text_color_secondary_idx", colorOptions, 0) { idx ->
        secondarySliderRow.visibility = if (idx == 2) View.VISIBLE else View.GONE
        if (idx != 2) updateWidget()
    }
    bindColorSliders(R.id.row_text_color_secondary_custom, "text_color_secondary")
    secondarySliderRow.visibility = if (prefs.getInt("text_color_secondary_idx", 0) == 2) View.VISIBLE else View.GONE

    // Date Color
    val dateSliderRow = findViewById<View>(R.id.row_date_color_custom)
    bindSelector(R.id.row_date_color, getString(R.string.section_date_color), "date_color_idx", colorOptions, 0) { idx ->
        dateSliderRow.visibility = if (idx == 2) View.VISIBLE else View.GONE
        if (idx != 2) updateWidget()
    }
    bindColorSliders(R.id.row_date_color_custom, "date_color")
    dateSliderRow.visibility = if (prefs.getInt("date_color_idx", 0) == 2) View.VISIBLE else View.GONE

    // Outline Color
    val outlineSliderRow = findViewById<View>(R.id.row_outline_color_custom)
    bindSelector(R.id.row_outline_color, getString(R.string.section_outline_color), "outline_color_idx", colorOptions, 0) { idx ->
        outlineSliderRow.visibility = if (idx == 2) View.VISIBLE else View.GONE
        if (idx != 2) updateWidget()
    }
    bindColorSliders(R.id.row_outline_color_custom, "outline_color")
    outlineSliderRow.visibility = if (prefs.getInt("outline_color_idx", 0) == 2) View.VISIBLE else View.GONE

    // Apply initial dynamic colors visibility
    updateColorVisibility(prefs.getBoolean("use_dynamic_colors", true))

    // Font selector
    bindSelector(R.id.row_font, getString(R.string.section_font), "font_style", listOf(
        getString(R.string.font_default), getString(R.string.font_serif), getString(R.string.font_monospace), getString(R.string.font_cursive),
        getString(R.string.font_condensed), getString(R.string.font_condensed_light), getString(R.string.font_light), getString(R.string.font_medium),
        getString(R.string.font_black), getString(R.string.font_thin), getString(R.string.font_smallcaps)
    ), 0)

    updateToggleAvailability()
}

internal fun MainActivity.setupLanguageSection() {
    val languages = listOf(
        LanguageOption("", getString(R.string.lang_system_default)),
        LanguageOption("en", "English"),
        LanguageOption("es", "Español (Spanish)"),
        LanguageOption("zh", "简体中文 (Chinese)"),
        LanguageOption("hi", "हिन्दी (Hindi)"),
        LanguageOption("ar", "العربية (Arabic)"),
        LanguageOption("fr", "Français (French)"),
        LanguageOption("pt", "Português (Portuguese)"),
        LanguageOption("ru", "Русский (Russian)"),
        LanguageOption("de", "Deutsch (German)"),
        LanguageOption("ja", "日本語 (Japanese)"),
        LanguageOption("tr", "Türkçe (Turkish)")
    )
    val languageLabels = languages.map { it.displayName }
    val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.language ?: ""
    val currentIdx = languages.indexOfFirst { it.code.equals(currentTag, ignoreCase = true) }.coerceAtLeast(0)

    val row = findViewById<View>(R.id.row_app_language) ?: return
    val tvTitle = row.findViewById<TextView>(R.id.row_label)
    val autoCompleteTextView = row.findViewById<AutoCompleteTextView>(R.id.row_value)

    tvTitle.text = getString(R.string.section_app_language)
    val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageLabels)
    autoCompleteTextView.setAdapter(adapter)
    autoCompleteTextView.setText(languageLabels[currentIdx], false)

    autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
        val selected = languages.getOrElse(position) { languages[0] }
        val localeList = if (selected.code.isEmpty()) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(selected.code)
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
    }
}

internal fun MainActivity.setupPresetsSection() {
    data class Preset(
        val key: String,
        val label: String,
        val prefs: Map<String, Any>
    )

    val presets = listOf(
        // Minimal: just time+date, white text, fully transparent, thin font
        Preset("minimal", getString(R.string.preset_minimal), mapOf(
            "show_outline" to false, "bg_opacity" to 0f,
            "font_style" to 9, // Thin
            "use_dynamic_colors" to false,
            "text_color_primary_idx" to 2, "text_color_primary_r" to 255, "text_color_primary_g" to 255, "text_color_primary_b" to 255,
            "text_color_secondary_idx" to 2, "text_color_secondary_r" to 200, "text_color_secondary_g" to 200, "text_color_secondary_b" to 200,
            "date_color_idx" to 2, "date_color_r" to 180, "date_color_g" to 180, "date_color_b" to 190,
            "bold_battery" to false, "bold_temp" to false
        )),
        // Neon: cyan time, magenta date, dark bg, bold condensed font
        Preset("neon", getString(R.string.preset_neon), mapOf(
            "bold_battery" to true, "bold_temp" to true,
            "show_outline" to true, "bg_opacity" to 95f,
            "font_style" to 4, // Condensed
            "use_dynamic_colors" to false,
            "text_color_primary_idx" to 2, "text_color_primary_r" to 0, "text_color_primary_g" to 255, "text_color_primary_b" to 255,
            "text_color_secondary_idx" to 2, "text_color_secondary_r" to 0, "text_color_secondary_g" to 200, "text_color_secondary_b" to 200,
            "date_color_idx" to 2, "date_color_r" to 255, "date_color_g" to 0, "date_color_b" to 180,
            "outline_color_idx" to 2, "outline_color_r" to 0, "outline_color_g" to 200, "outline_color_b" to 255,
            "bg_color_idx" to 2, "bg_color_r" to 10, "bg_color_g" to 10, "bg_color_b" to 20
        )),
        // Cockpit: green on dark, monospace, info-heavy, terminal look
        Preset("cockpit", getString(R.string.preset_cockpit), mapOf(
            "bold_battery" to false, "bold_temp" to false,
            "bold_storage" to false, "bold_ram" to false, "bold_data_usage" to false,
            "show_outline" to true, "bg_opacity" to 90f,
            "font_style" to 2, // Monospace
            "use_dynamic_colors" to false,
            "text_color_primary_idx" to 2, "text_color_primary_r" to 0, "text_color_primary_g" to 255, "text_color_primary_b" to 65,
            "text_color_secondary_idx" to 2, "text_color_secondary_r" to 0, "text_color_secondary_g" to 180, "text_color_secondary_b" to 50,
            "date_color_idx" to 2, "date_color_r" to 0, "date_color_g" to 200, "date_color_b" to 80,
            "outline_color_idx" to 2, "outline_color_r" to 0, "outline_color_g" to 120, "outline_color_b" to 40,
            "bg_color_idx" to 2, "bg_color_r" to 5, "bg_color_g" to 15, "bg_color_b" to 5
        )),
        // Sunset: warm oranges/gold, serif font, elegant minimal
        Preset("sunset", getString(R.string.preset_sunset), mapOf(
            "bold_battery" to true,
            "show_outline" to false, "bg_opacity" to 70f,
            "font_style" to 1, // Serif
            "use_dynamic_colors" to false,
            "text_color_primary_idx" to 2, "text_color_primary_r" to 255, "text_color_primary_g" to 180, "text_color_primary_b" to 50,
            "text_color_secondary_idx" to 2, "text_color_secondary_r" to 230, "text_color_secondary_g" to 140, "text_color_secondary_b" to 60,
            "date_color_idx" to 2, "date_color_r" to 255, "date_color_g" to 120, "date_color_b" to 50,
            "bg_color_idx" to 2, "bg_color_r" to 30, "bg_color_g" to 15, "bg_color_b" to 8
        )),
        // Monochrome: white outline, all white text, medium font, classic layout
        Preset("monochrome", getString(R.string.preset_monochrome), mapOf(
            "bold_battery" to false,
            "show_outline" to true, "bg_opacity" to 50f,
            "font_style" to 7, // Medium
            "use_dynamic_colors" to false,
            "text_color_primary_idx" to 2, "text_color_primary_r" to 240, "text_color_primary_g" to 240, "text_color_primary_b" to 240,
            "text_color_secondary_idx" to 2, "text_color_secondary_r" to 170, "text_color_secondary_g" to 170, "text_color_secondary_b" to 170,
            "date_color_idx" to 2, "date_color_r" to 200, "date_color_g" to 200, "date_color_b" to 200,
            "outline_color_idx" to 2, "outline_color_r" to 100, "outline_color_g" to 100, "outline_color_b" to 100,
            "bg_color_idx" to 2, "bg_color_r" to 25, "bg_color_g" to 25, "bg_color_b" to 25
        )),
        // Snowfall: icy blues, light font, airy feel
        Preset("snowfall", getString(R.string.preset_snowfall), mapOf(
            "bold_temp" to false,
            "show_outline" to false, "bg_opacity" to 60f,
            "font_style" to 6, // Light
            "use_dynamic_colors" to false,
            "text_color_primary_idx" to 2, "text_color_primary_r" to 180, "text_color_primary_g" to 220, "text_color_primary_b" to 255,
            "text_color_secondary_idx" to 2, "text_color_secondary_r" to 130, "text_color_secondary_g" to 180, "text_color_secondary_b" to 230,
            "date_color_idx" to 2, "date_color_r" to 100, "date_color_g" to 170, "date_color_b" to 255,
            "bg_color_idx" to 2, "bg_color_r" to 10, "bg_color_g" to 20, "bg_color_b" to 40
        ))
    )

    val chipGroup = findViewById<com.google.android.material.chip.ChipGroup>(R.id.preset_chip_group)
    chipGroup.removeAllViews()
    val activePreset = prefs.getString("active_preset", null)

    val primaryContainer = com.google.android.material.color.MaterialColors.getColor(
        chipGroup, com.google.android.material.R.attr.colorPrimaryContainer
    )
    val surfaceContainer = com.google.android.material.color.MaterialColors.getColor(
        chipGroup, com.google.android.material.R.attr.colorSurfaceContainerHighest
    )
    val onPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(
        chipGroup, com.google.android.material.R.attr.colorOnPrimaryContainer
    )
    val onSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(
        chipGroup, com.google.android.material.R.attr.colorOnSurfaceVariant
    )

    val chipBgStateList = android.content.res.ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(
            primaryContainer,
            surfaceContainer
        )
    )

    val chipTextStateList = android.content.res.ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(
            onPrimaryContainer,
            onSurfaceVariant
        )
    )

    val density = resources.displayMetrics.density

    for (preset in presets) {
        val chip = com.google.android.material.chip.Chip(this).apply {
            text = preset.label
            isCheckable = true
            isChecked = (preset.key == activePreset)
            isCheckedIconVisible = false
            shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(14f * density).build()
            chipBackgroundColor = chipBgStateList
            setTextColor(chipTextStateList)
            chipStrokeWidth = 0f
            setEnsureMinTouchTargetSize(false)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener {
                val editor = prefs.edit()
                for ((k, v) in preset.prefs) {
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Float -> editor.putFloat(k, v)
                        is Int -> editor.putInt(k, v)
                        is String -> editor.putString(k, v)
                    }
                }
                editor.putString("active_preset", preset.key)
                editor.apply()
                updateWidget()
                recreate()
            }
        }
        chipGroup.addView(chip)
    }
}

internal fun MainActivity.applyTheme() {
    updateWidget()
}
