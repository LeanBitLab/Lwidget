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

import android.content.Context
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.core.widget.NestedScrollView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

internal fun MainActivity.toggleSectionExpansion(
    header: View,
    content: View,
    chevron: ImageView,
    prefKey: String,
    sectionKey: String? = null
) {
    val nestedScrollView = findViewById<NestedScrollView>(R.id.nested_scroll_view)
    val nowExpanded = content.visibility != View.VISIBLE

    if (!nowExpanded && sectionKey != null) {
        collapseSectionNestedContent(sectionKey)
    }

    val parentViewGroup = (header.parent as? ViewGroup) ?: (nestedScrollView as? ViewGroup)
    if (parentViewGroup != null) {
        TransitionManager.beginDelayedTransition(
            parentViewGroup,
            AutoTransition().apply {
                duration = 220L
                interpolator = FastOutSlowInInterpolator()
            }
        )
    }

    content.visibility = if (nowExpanded) View.VISIBLE else View.GONE
    prefs.edit().putBoolean(prefKey, nowExpanded).apply()

    chevron.animate()
        .rotation(if (nowExpanded) 180f else 0f)
        .setDuration(220L)
        .setInterpolator(FastOutSlowInInterpolator())
        .start()

    if (nowExpanded && nestedScrollView != null) {
        header.postDelayed({
            if (!isDestroyed && !isFinishing) {
                val rect = android.graphics.Rect()
                header.getDrawingRect(rect)
                nestedScrollView.offsetDescendantRectToMyCoords(header, rect)
                val scrollY = nestedScrollView.scrollY
                val viewportHeight = nestedScrollView.height
                val headerTop = rect.top
                if (headerTop < scrollY || headerTop > scrollY + (viewportHeight * 0.65f)) {
                    nestedScrollView.smoothScrollTo(0, headerTop.coerceAtLeast(0))
                }
            }
        }, 100L)
    }
}

internal fun MainActivity.bindFoldedSection(
    headerId: Int, iconResId: Int?, title: String,
    contentId: Int,
    toggleRowId: Int,
    prefShowKey: String, defShow: Boolean,
    sizeRowId: Int? = null, prefSizeKey: String? = null,
    defSize: Float = 14f, minSize: Float = 10f, maxSize: Float = 72f,
    selectorRowId: Int? = null, selectorOptions: List<String>? = null,
    prefSelectorKey: String? = null, defSelectorIdx: Int = 0,
    boldRowId: Int? = null,
    isContent: Boolean = false,
    subSettingsContainerId: Int? = null,
    validateToggle: ((Boolean) -> Boolean)? = null,
    onChanged: ((Boolean) -> Unit)? = null
): SwitchMaterial {
    val header = findViewById<View>(headerId)
    val chevron = header.findViewById<ImageView>(R.id.header_chevron)
    val headerIcon = header.findViewById<ImageView>(R.id.header_icon)
    val headerTitle = header.findViewById<TextView>(R.id.header_title)
    val content = findViewById<View>(contentId)

    val sectionKey = prefShowKey.replace("show_", "")
    val expandedPrefKey = "section_${sectionKey}_expanded"
    accordionViews[expandedPrefKey] = content
    accordionHeaders[expandedPrefKey] = header

    headerTitle.text = title
    if (iconResId != null) {
        headerIcon.setImageResource(iconResId)
        headerIcon.visibility = View.VISIBLE
    } else {
        headerIcon.visibility = View.GONE
    }

    // Expand/collapse - read from prefs, apply visibility
    val isExpandedFromPrefs = prefs.getBoolean(expandedPrefKey, false)
    content.visibility = if (isExpandedFromPrefs) View.VISIBLE else View.GONE
    chevron.rotation = if (isExpandedFromPrefs) 180f else 0f

    // Header click: smooth expand/collapse with auto-scroll
    header.setOnClickListener {
        toggleSectionExpansion(header, content, chevron, expandedPrefKey, sectionKey)
    }

    // Toggle row
    val toggleRow = findViewById<View>(toggleRowId)
    val toggleSwitch = toggleRow.findViewById<SwitchMaterial>(R.id.row_switch)
    val toggleLabel = toggleRow.findViewById<TextView>(R.id.row_label)
    val toggleCard = toggleRow.findViewById<MaterialCardView>(R.id.toggle_row_card)
    toggleLabel.setText(R.string.label_enable)

    toggleSwitch.tag = prefShowKey
    if (isContent) contentSwitches.add(toggleSwitch)

    val isShown = prefs.getBoolean(prefShowKey, defShow)
    toggleSwitch.isChecked = isShown

    // Sub-settings alpha
    val subSettings = subSettingsContainerId?.let { findViewById<View>(it) }
    subSettings?.alpha = if (isShown) 1.0f else 0.4f
    updateToggleCardStyle(toggleCard, isShown)

    // Child rows visibility
    val sizeRow = sizeRowId?.let { findViewById<View>(it) }
    sizeRow?.visibility = if (isShown) View.VISIBLE else View.GONE

    val selectorRow = selectorRowId?.let { findViewById<View>(it) }
    selectorRow?.visibility = if (isShown) View.VISIBLE else View.GONE

    val boldRow = boldRowId?.let { findViewById<View>(it) }
    boldRow?.visibility = if (isShown) View.VISIBLE else View.GONE

    onChanged?.invoke(isShown)

    // Internal listener - handles visibility with animation
    toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !checkLimit()) {
            toggleSwitch.isChecked = false
            return@setOnCheckedChangeListener
        }
        if (validateToggle?.invoke(isChecked) == false) {
            toggleSwitch.isChecked = !isChecked
            return@setOnCheckedChangeListener
        }
        prefs.edit().putBoolean(prefShowKey, isChecked).apply()
        subSettings?.alpha = if (isChecked) 1.0f else 0.4f
        updateToggleCardStyle(toggleCard, isChecked)

        val parentViewGroup = (toggleRow.parent as? ViewGroup)
        if (parentViewGroup != null) {
            TransitionManager.beginDelayedTransition(
                parentViewGroup,
                AutoTransition().apply {
                    duration = 180L
                }
            )
        }

        sizeRow?.visibility = if (isChecked) View.VISIBLE else View.GONE
        selectorRow?.visibility = if (isChecked) View.VISIBLE else View.GONE
        boldRow?.visibility = if (isChecked) View.VISIBLE else View.GONE
        onChanged?.invoke(isChecked)
        updateWidget()
        if (isContent) updateToggleAvailability()
    }

    // Size row setup
    if (sizeRowId != null && prefSizeKey != null) {
        val sizeRowInner = findViewById<View>(sizeRowId)
        val slider = sizeRowInner.findViewById<Slider>(R.id.row_slider)
        val valueLabel = sizeRowInner.findViewById<TextView>(R.id.row_value)
        val sizeLabel = sizeRowInner.findViewById<TextView>(R.id.row_label)
        sizeLabel.setText(R.string.label_size)

        val currentSize = prefs.getFloat(prefSizeKey, defSize)
        slider.valueFrom = minSize
        slider.valueTo = maxSize
        slider.value = currentSize.coerceIn(minSize, maxSize)
        valueLabel.text = "${currentSize.toInt()}"

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                valueLabel.text = "${value.toInt()}"
                prefs.edit().putFloat(prefSizeKey, value).apply()
                updateWidget()
            }
        }
    }

    // Selector row setup (skip world clock timezone - uses custom search layout)
    if (selectorRowId != null && selectorOptions != null && prefSelectorKey != null && prefSelectorKey != "world_clock_zone_str") {
        val selectorRowInner = findViewById<View>(selectorRowId)
        val autoCompleteTextView = selectorRowInner.findViewById<AutoCompleteTextView>(R.id.row_value)
        val selectorLabel = selectorRowInner.findViewById<TextView>(R.id.row_label)
        selectorLabel.text = title

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, selectorOptions)
        autoCompleteTextView.setAdapter(adapter)

        if (prefSelectorKey == "world_clock_zone_str") {
            val currentVal = prefs.getString(prefSelectorKey, "UTC") ?: "UTC"
            autoCompleteTextView.setText(currentVal, false)
            autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
                val selected = selectorOptions.getOrElse(position) { "UTC" }
                prefs.edit().putString(prefSelectorKey, selected).apply()
                updateWidget()
                selectorRowInner.clearFocus()
                autoCompleteTextView.clearFocus()
            }
        } else {
            val currentIdx = prefs.getInt(prefSelectorKey, defSelectorIdx)
            autoCompleteTextView.setText(selectorOptions.getOrElse(currentIdx) { selectorOptions[defSelectorIdx] }, false)
            autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
                prefs.edit().putInt(prefSelectorKey, position).apply()
                updateWidget()
                selectorRowInner.clearFocus()
                autoCompleteTextView.clearFocus()
            }
        }

        // Clear focus/dropdown shade when dismissed
        autoCompleteTextView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                autoCompleteTextView.clearFocus()
            }
        }
    }

    return toggleSwitch
}

// Dismiss keyboard and collapse nested subsections when a parent section collapses
internal fun MainActivity.collapseSectionNestedContent(sectionKey: String) {
    // World clock timezone search
    if (sectionKey == "world_clock") {
        val worldClockZoneRow = findViewById<View>(R.id.row_world_clock_zone)
        val searchEdit = worldClockZoneRow?.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.zone_search_edit)
        val listView = worldClockZoneRow?.findViewById<ListView>(R.id.zone_search_list)
        searchEdit?.clearFocus()
        listView?.visibility = View.GONE
    }
    // Appearance reorder section
    if (sectionKey == "appearance") {
        // collapse reorder too
    }
    // Appearance subsections
    if (sectionKey == "appearance") {
        val presetsContent = findViewById<View>(R.id.content_appearance_presets)
        val outlineContent = findViewById<View>(R.id.content_appearance_outline)
        val colorsContent = findViewById<View>(R.id.content_appearance_colors)
        val themeContent = findViewById<View>(R.id.content_appearance_theme)
        val fontContent = findViewById<View>(R.id.content_appearance_font)
        val transparencyContent = findViewById<View>(R.id.content_appearance_transparency)
        presetsContent?.visibility = View.GONE
        outlineContent?.visibility = View.GONE
        colorsContent?.visibility = View.GONE
        themeContent?.visibility = View.GONE
        fontContent?.visibility = View.GONE
        transparencyContent?.visibility = View.GONE
        prefs.edit()
            .putBoolean("section_appearance_presets_expanded", false)
            .putBoolean("section_appearance_outline_expanded", false)
            .putBoolean("section_appearance_colors_expanded", false)
            .putBoolean("section_appearance_theme_expanded", false)
            .putBoolean("section_appearance_font_expanded", false)
            .putBoolean("section_appearance_transparency_expanded", false)
            .apply()
        // Reset nested chevrons
        listOf(
            R.id.header_chevron_appearance_presets,
            R.id.header_chevron_appearance_outline,
            R.id.header_chevron_appearance_colors,
            R.id.header_chevron_appearance_theme,
            R.id.header_chevron_appearance_font,
            R.id.header_chevron_appearance_transparency
        ).forEach { id ->
            findViewById<android.widget.ImageView>(id)?.rotation = 0f
        }
    }
    dismissKeyboard()
}

internal fun MainActivity.dismissKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
    currentFocus?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
}

internal fun MainActivity.bindNestedCard(
    headerId: Int, title: String, contentId: Int, sectionKey: String,
    chevronViewId: Int? = null
) {
    val header = findViewById<View>(headerId)
    val content = findViewById<View>(contentId)
    val chevronView = header.findViewById<ImageView>(
        chevronViewId ?: R.id.header_chevron
    )

    nestedViews[sectionKey] = content
    nestedHeaders[sectionKey] = header

    val isExpanded = prefs.getBoolean(sectionKey, false)
    content.visibility = if (isExpanded) View.VISIBLE else View.GONE
    chevronView.rotation = if (isExpanded) 180f else 0f

    header.setOnClickListener {
        toggleSectionExpansion(header, content, chevronView, sectionKey)
    }
}

internal fun MainActivity.bindCategoryFoldable(headerId: Int, contentId: Int, title: String, iconResId: Int, prefKey: String) {
    accordionViews[prefKey] = findViewById(contentId)
    accordionHeaders[prefKey] = findViewById(headerId)
    
    val header = findViewById<View>(headerId)
    header.findViewById<TextView>(R.id.header_title).text = title
    val headerIcon = header.findViewById<ImageView>(R.id.header_icon)
    if (iconResId != 0) {
        headerIcon.setImageResource(iconResId)
        headerIcon.visibility = View.VISIBLE
    } else {
        headerIcon.visibility = View.GONE
    }
    
    val content = findViewById<View>(contentId)
    val chevron = header.findViewById<ImageView>(R.id.header_chevron)
    val isExpanded = prefs.getBoolean(prefKey, false)
    content.visibility = if (isExpanded) View.VISIBLE else View.GONE
    chevron.rotation = if (isExpanded) 180f else 0f
    
    header.setOnClickListener {
        toggleSectionExpansion(header, content, chevron, prefKey)
    }
}

internal fun MainActivity.bindTimezoneSearch(
    rowId: Int, zoneIds: List<String>, prefKey: String, defaultVal: String
) {
    val row = findViewById<View>(rowId)
    val searchEdit = row.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.zone_search_edit)
    val listView = row.findViewById<ListView>(R.id.zone_search_list)

    val currentVal = prefs.getString(prefKey, defaultVal) ?: defaultVal
    searchEdit.setText(currentVal)

    var filteredList: MutableList<String> = mutableListOf()
    val filteredAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, filteredList)

    // Text watcher for filtering
    searchEdit.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            val query = s?.toString()?.lowercase() ?: ""
            if (query.isEmpty()) {
                filteredList.clear()
                listView.visibility = View.GONE
                return
            }
            filteredList.clear()
            filteredList.addAll(zoneIds.filter { it.lowercase().contains(query) })
            filteredAdapter.notifyDataSetChanged()
            listView.visibility = if (filteredList.isEmpty()) View.GONE else View.VISIBLE
        }
    })

    listView.adapter = filteredAdapter
    listView.setOnItemClickListener { _, _, position, _ ->
        val selected = filteredList[position]
        searchEdit.setText(selected)
        listView.visibility = View.GONE
        searchEdit.clearFocus()
        prefs.edit().putString(prefKey, selected).apply()
        updateWidget()
    }

    // Intercept touch events so parent NestedScrollView doesn't steal them
    listView.setOnTouchListener { v, event ->
        v.parent.requestDisallowInterceptTouchEvent(true)
        false
    }

    // Show list on focus
    searchEdit.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            val query = searchEdit.text?.toString()?.lowercase() ?: ""
            if (query.isEmpty()) {
                filteredList.clear()
                filteredList.addAll(zoneIds)
                filteredAdapter.notifyDataSetChanged()
                listView.visibility = View.VISIBLE
            }
        }
    }
}
