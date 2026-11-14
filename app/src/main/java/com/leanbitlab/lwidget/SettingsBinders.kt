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
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

internal fun MainActivity.bindToggle(
    viewId: Int, title: String, prefShowKey: String, defShow: Boolean,
    isContent: Boolean = false,
    onChanged: ((Boolean) -> Unit)? = null
) {
    val row = findViewById<View>(viewId)
    val tvTitle = row.findViewById<TextView>(R.id.row_label)
    val switch = row.findViewById<SwitchMaterial>(R.id.row_switch)

    tvTitle.text = title
    switch.tag = prefShowKey
    if (isContent) contentSwitches.add(switch)

    val isShown = prefs.getBoolean(prefShowKey, defShow)
    switch.isChecked = isShown
    onChanged?.invoke(isShown)

    switch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !checkLimit()) {
            switch.isChecked = false
            return@setOnCheckedChangeListener
        }
        prefs.edit().putBoolean(prefShowKey, isChecked).apply()
        onChanged?.invoke(isChecked)
        updateWidget()
        if (isContent) updateToggleAvailability()
    }
}

internal fun MainActivity.bindSlider(
    viewId: Int, title: String, prefKey: String, defValue: Float,
    minValue: Float, maxValue: Float, suffix: String = "%"
) {
    val row = findViewById<View>(viewId)
    val tvTitle = row.findViewById<TextView>(R.id.row_label)
    val slider = row.findViewById<Slider>(R.id.row_slider)
    val tvValue = row.findViewById<TextView>(R.id.row_value)

    tvTitle.text = title

    val currentValue = prefs.getFloat(prefKey, defValue)
    slider.valueFrom = minValue
    slider.valueTo = maxValue
    slider.value = currentValue.coerceIn(minValue, maxValue)
    tvValue.text = "${currentValue.toInt()}$suffix"

    slider.addOnChangeListener { _, value, fromUser ->
        if (fromUser) {
            tvValue.text = "${value.toInt()}$suffix"
            prefs.edit().putFloat(prefKey, value).apply()
            updateWidget()
        }
    }
}

internal fun MainActivity.bindSelector(
    viewId: Int, title: String, prefKey: String, options: List<String>,
    defaultIdx: Int, onSelectionChanged: ((Int) -> Unit)? = null
) {
    val row = findViewById<View>(viewId)
    val tvTitle = row.findViewById<TextView>(R.id.row_label)
    val autoCompleteTextView = row.findViewById<AutoCompleteTextView>(R.id.row_value)

    tvTitle.text = title

    val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options)
    autoCompleteTextView.setAdapter(adapter)

    if (prefKey == "world_clock_zone_str") {
        val currentVal = prefs.getString(prefKey, "UTC") ?: "UTC"
        autoCompleteTextView.setText(currentVal, false)
        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selected = options.getOrElse(position) { "UTC" }
            prefs.edit().putString(prefKey, selected).apply()
            updateWidget()
        }
    } else if (prefKey == "clock_app_package" || prefKey == "calendar_app_package") {
        val appPackages = if (prefKey == "clock_app_package") clockAppPackages else calendarAppPackages
        val currentVal = prefs.getString(prefKey, "default") ?: "default"
        val currentIdx = appPackages.indexOf(currentVal).coerceAtLeast(0)
        autoCompleteTextView.setText(options.getOrElse(currentIdx) { options[0] }, false)
        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selected = appPackages.getOrElse(position) { "default" }
            prefs.edit().putString(prefKey, selected).apply()
            updateWidget()
            onSelectionChanged?.invoke(position)
            row.requestFocus()
            autoCompleteTextView.clearFocus()
        }
    } else {
        val currentIdx = prefs.getInt(prefKey, defaultIdx)
        autoCompleteTextView.setText(options.getOrElse(currentIdx) { options[defaultIdx] }, false)
        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putInt(prefKey, position).apply()
            updateWidget()
            onSelectionChanged?.invoke(position)
            row.requestFocus()
            autoCompleteTextView.clearFocus()
        }
    }
}

internal fun MainActivity.bindColorSliders(viewId: Int, prefPrefix: String): View {
    val row = findViewById<View>(viewId)
    val sliderRed = row.findViewById<Slider>(R.id.slider_red)
    val sliderGreen = row.findViewById<Slider>(R.id.slider_green)
    val sliderBlue = row.findViewById<Slider>(R.id.slider_blue)
    val valRed = row.findViewById<TextView>(R.id.val_red)
    val valGreen = row.findViewById<TextView>(R.id.val_green)
    val valBlue = row.findViewById<TextView>(R.id.val_blue)
    val preview = row.findViewById<View>(R.id.color_preview)

    val r = prefs.getInt("${prefPrefix}_r", 255)
    val g = prefs.getInt("${prefPrefix}_g", 255)
    val b = prefs.getInt("${prefPrefix}_b", 255)

    fun updatePreview() {
        val color = android.graphics.Color.rgb(sliderRed.value.toInt(), sliderGreen.value.toInt(), sliderBlue.value.toInt())
        preview.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        valRed.text = sliderRed.value.toInt().toString()
        valGreen.text = sliderGreen.value.toInt().toString()
        valBlue.text = sliderBlue.value.toInt().toString()
    }

    sliderRed.value = r.toFloat()
    sliderGreen.value = g.toFloat()
    sliderBlue.value = b.toFloat()
    updatePreview()

    val listener = Slider.OnChangeListener { _, _, fromUser ->
        if (fromUser) {
            updatePreview()
            prefs.edit()
                .putInt("${prefPrefix}_r", sliderRed.value.toInt())
                .putInt("${prefPrefix}_g", sliderGreen.value.toInt())
                .putInt("${prefPrefix}_b", sliderBlue.value.toInt())
                .apply()
            updateWidget()
        }
    }

    sliderRed.addOnChangeListener(listener)
    sliderGreen.addOnChangeListener(listener)
    sliderBlue.addOnChangeListener(listener)

    return row
}

internal fun MainActivity.updateColorVisibility(useDynamicColors: Boolean) {
    val manualColorIds = listOf(
        R.id.row_bg_color, R.id.row_bg_color_custom,
        R.id.row_text_color_primary, R.id.row_text_color_primary_custom,
        R.id.row_text_color_secondary, R.id.row_text_color_secondary_custom,
        R.id.row_date_color, R.id.row_date_color_custom,
        R.id.row_outline_color, R.id.row_outline_color_custom
    )
    manualColorIds.forEach { id ->
        findViewById<View>(id).visibility = if (useDynamicColors) View.GONE else View.VISIBLE
    }
}

internal fun MainActivity.updateToggleCardStyle(card: MaterialCardView?, enabled: Boolean) {
    if (card == null) return
    if (enabled) {
        card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        card.strokeWidth = (1f * resources.displayMetrics.density).toInt()
        card.setStrokeColor(android.content.res.ColorStateList.valueOf(
            com.google.android.material.color.MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimary)
        ))
    } else {
        card.setCardBackgroundColor(
            com.google.android.material.color.MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerLow)
        )
        card.setStrokeColor(android.content.res.ColorStateList.valueOf(
            com.google.android.material.color.MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
        ))
    }
}

// Helper for callers who override the toggle listener to update row visibility
internal fun MainActivity.updateFeatureRowVisibility(switch: SwitchMaterial, isChecked: Boolean, vararg rowIds: Int) {
    val toggleRow = switch.parent as? View
    val toggleCard = toggleRow?.findViewById<MaterialCardView>(R.id.toggle_row_card)
    updateToggleCardStyle(toggleCard, isChecked)

    val parentViewGroup = (toggleRow?.parent as? ViewGroup)
    if (parentViewGroup != null) {
        TransitionManager.beginDelayedTransition(
            parentViewGroup,
            AutoTransition().apply {
                duration = 180L
            }
        )
    }

    rowIds.forEach { id ->
        findViewById<View>(id)?.visibility = if (isChecked) View.VISIBLE else View.GONE
    }
}

internal fun MainActivity.checkLimit(): Boolean {
    // Subset Limit: items in the right-side widget column
    val subsetCount = contentSwitches.count { 
        it.isChecked && (it.tag in listOf("battery", "weather_condition", "temp", "data", "storage", "ram", "steps", "screen_time"))
    }
    
    if (subsetCount > 8) {
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(R.id.fab_update), 
            getString(R.string.error_max_subset_items), 
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
        return false
    }

    return true
}

internal fun MainActivity.updateToggleAvailability() {
    // Limit removed
    // Ensure all are enabled
    for (switch in contentSwitches) {
        switch.isEnabled = true
        switch.alpha = 1.0f
    }
}
