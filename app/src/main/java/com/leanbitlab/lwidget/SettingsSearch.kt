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
import com.google.android.material.tabs.TabLayout
import com.google.android.material.appbar.AppBarLayout

internal fun MainActivity.setupGlobalSettingsSearch() {
    val searchInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.search_settings_input) ?: return
    val headerNormal = findViewById<View>(R.id.header_normal)
    val headerSearch = findViewById<View>(R.id.header_search_mode)
    val btnSearchToggle = findViewById<View>(R.id.btn_search_toggle)
    val btnSearchClose = findViewById<View>(R.id.btn_search_close)
    val btnSearchClear = findViewById<View>(R.id.btn_search_clear)
    val appBar = findViewById<AppBarLayout>(R.id.app_bar)

    val tabContainers = listOf(
        findViewById<View>(R.id.container_tab_style),
        findViewById<View>(R.id.container_tab_content),
        findViewById<View>(R.id.container_tab_layout),
        findViewById<View>(R.id.container_tab_system)
    )
    val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tab_layout)

    fun closeSearch() {
        searchInput.setText("")
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
        headerSearch?.visibility = View.GONE
        headerNormal?.visibility = View.VISIBLE
    }

    fun openSearch() {
        headerNormal?.visibility = View.GONE
        headerSearch?.visibility = View.VISIBLE
        appBar?.setExpanded(true, true)
        searchInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    btnSearchToggle?.setOnClickListener { openSearch() }
    btnSearchClose?.setOnClickListener { closeSearch() }
    btnSearchClear?.setOnClickListener { searchInput.setText("") }

    onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (headerSearch?.visibility == View.VISIBLE) {
                closeSearch()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    })

    searchInput.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            appBar?.setExpanded(true, true)
        }
    }

    val cardMap = mapOf(
        // Style Tab
        R.id.card_appearance_presets to listOf("presets", "themes", "minimal", "neon", "cockpit", "sunset", "monochrome", "snowfall"),
        R.id.card_appearance_outline to listOf("outline", "border", "stroke", "outline color", "stroke width"),
        R.id.card_appearance_colors to listOf("colors", "dynamic colors", "text color", "background color", "custom colors", "rgb"),
        R.id.card_appearance_theme to listOf("theme", "dark mode", "light mode", "system theme", "mode"),
        R.id.card_appearance_font to listOf("font", "typeface", "typography", "serif", "mono", "monospace", "cursive", "condensed", "thin", "light", "bold"),
        R.id.card_appearance_transparency to listOf("transparency", "opacity", "background opacity", "alpha"),
        R.id.card_appearance_padding to listOf("padding", "margin", "spacing", "inset"),

        // Content Tab
        R.id.card_time to listOf("time", "clock", "hour", "format", "12-hour", "24-hour", "clock app"),
        R.id.card_next_alarm to listOf("alarm", "next alarm", "upcoming alarm"),
        R.id.card_world_clock to listOf("world clock", "timezone", "time zone", "utc"),
        R.id.card_date to listOf("date", "calendar", "day", "month"),
        R.id.card_battery to listOf("battery", "percentage", "charging", "battery level"),
        R.id.card_temp to listOf("temperature", "temp", "battery temp", "celsius", "fahrenheit"),
        R.id.card_weather to listOf("weather", "forecast", "breezy", "condition", "breezy weather"),
        R.id.card_data to listOf("data", "data usage", "cellular", "wifi", "network"),
        R.id.card_storage to listOf("storage", "internal storage", "disk", "free space"),
        R.id.card_ram to listOf("ram", "memory", "free ram", "ram usage"),
        R.id.card_steps to listOf("steps", "step counter", "activity", "fitness", "pedometer", "walking"),
        R.id.card_screen_time to listOf("screen time", "usage", "digital wellbeing"),
        R.id.card_events to listOf("events", "calendar", "agenda", "calendar app", "schedule"),
        R.id.card_tasks to listOf("tasks", "tasks.org", "todo", "to-do", "task list"),

        // Layout Tab
        R.id.card_appearance_reorder to listOf("reorder", "order", "column", "layout", "arrange", "drag", "stack"),

        // System Tab
        R.id.card_language to listOf("language", "locale", "translation", "languages"),
        R.id.card_advanced to listOf("advanced", "keep alive", "battery optimization", "foreground service", "service", "backup", "export", "import"),
        R.id.card_permissions to listOf("permissions", "calendar permission", "usage stats", "activity recognition", "notifications"),
        R.id.card_about to listOf("about", "version", "github", "source code", "developer", "license", "privacy", "author"),
        R.id.card_changelog to listOf("changelog", "whats new", "updates", "history")
    )

    val headerLabels: List<View> = listOfNotNull(
        findViewById(R.id.label_category_content),
        findViewById(R.id.label_category_stats)
    )

    searchInput.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            val query = s?.toString()?.trim()?.lowercase() ?: ""
            val isSearching = query.isNotEmpty()
            btnSearchClear?.visibility = if (isSearching) View.VISIBLE else View.GONE

            if (isSearching) {
                // Make all tab containers visible so search shows matches across all categories
                tabContainers.forEach { container -> container?.visibility = View.VISIBLE }
                tabLayout?.visibility = View.GONE

                for ((cardId, keywords) in cardMap) {
                    val card = findViewById<View>(cardId) ?: continue
                    val matches = keywords.any { it.contains(query) }
                    card.visibility = if (matches) View.VISIBLE else View.GONE
                }
                headerLabels.forEach { label ->
                    label.visibility = View.GONE
                }
            } else {
                // Restore tabs and active container
                tabLayout?.visibility = View.VISIBLE
                val selectedTab = tabLayout?.selectedTabPosition?.coerceIn(0, 3) ?: 0
                tabContainers.forEachIndexed { index, container ->
                    container?.visibility = if (index == selectedTab) View.VISIBLE else View.GONE
                }
                for ((cardId, _) in cardMap) {
                    findViewById<View>(cardId)?.visibility = View.VISIBLE
                }
                headerLabels.forEach { label ->
                    label.visibility = View.VISIBLE
                }
            }
        }
    })
}
