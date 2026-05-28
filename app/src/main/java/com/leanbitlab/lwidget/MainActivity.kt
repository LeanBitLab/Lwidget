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

import android.Manifest
import android.util.Log
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

// Data class for reorderable items
data class ReorderItem(
    val key: String,          // e.g. "show_battery"
    val label: String,        // e.g. "Battery"
    var enabled: Boolean
)

// Adapter for reorder RecyclerView
class ReorderAdapter(
    private val items: MutableList<ReorderItem>,
    private val onOrderChanged: () -> Unit
) : RecyclerView.Adapter<ReorderAdapter.ViewHolder>() {

    class ViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view) {
        val handle: android.widget.ImageView = view.findViewById(R.id.reorder_handle)
        val name: TextView = view.findViewById(R.id.reorder_item_name)
        val enabled: SwitchMaterial = view.findViewById(R.id.reorder_item_enabled)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.settings_reorder_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        holder.enabled.isChecked = item.enabled
    }

    override fun getItemCount() = items.size

    fun moveItem(from: Int, to: Int) {
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
        onOrderChanged()
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val contentSwitches = mutableListOf<SwitchMaterial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

        if (prefs.getBoolean("is_first_launch", true)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        checkAllPermissions()
        setupSections()
        updateLivePreview()
        
        // Advanced Section
        bindSlider(R.id.row_update_interval, "Update Interval (m)", "update_interval", 15f, 1f, 60f, "m")
        
        // Setup Changelog
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        val tvVersion = findViewById<TextView>(R.id.tv_changelog_version)
        tvVersion.text = getString(R.string.changelog_version, versionName)

        findViewById<View>(R.id.tv_github_link).setOnClickListener {
            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://github.com/LeanBitLab/Lwidget"))
        }

        findViewById<View>(R.id.tv_privacy_policy).setOnClickListener {
            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://github.com/LeanBitLab/Lwidget/wiki/Privacy-Policy"))
        }

        
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab_update)
        fab.setOnClickListener {
            updateWidget()
            updateLivePreview()
        }

        // Apply navigation bar insets to FAB so it doesn't overlap gesture nav
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + (24 * resources.displayMetrics.density).toInt()
                rightMargin = insets.right + (24 * resources.displayMetrics.density).toInt()
            }
            windowInsets
        }

        // Handle Collapsing Toolbar Title Fade and Header Fade
        val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar)
        val titleApp = findViewById<TextView>(R.id.title_app)
        val expandedHeader = findViewById<View>(R.id.header_expanded)
        
        appBar.addOnOffsetChangedListener(com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            val totalScrollRange = appBar.totalScrollRange
            val percentage = kotlin.math.abs(verticalOffset).toFloat() / totalScrollRange.toFloat()
            
            // Fade in toolbar title when nearing collapse (e.g. last 20% of scroll)
            val alphaTitle = ((percentage - 0.8f) / 0.2f).coerceIn(0f, 1f)
            titleApp.alpha = alphaTitle

            // Fade out expanded header as we scroll up (first 50% of scroll)
            // Starts dense (1f) and fades to 0f by the time we are halfway collapsed
            val alphaHeader = (1f - (percentage / 0.5f)).coerceIn(0f, 1f)
            expandedHeader.alpha = alphaHeader
            // Optional: Scale down slightly for a nicer effect
            val scale = (1f - (percentage * 0.1f)).coerceIn(0.9f, 1f)
            expandedHeader.scaleX = scale
            expandedHeader.scaleY = scale
        })
    }

    private fun updateLivePreview() {
        val previewContainer = findViewById<android.widget.FrameLayout>(R.id.preview_container)
        try {
            val remoteViews = AwidgetProvider.Companion.buildAppWidgetRemoteViews(this, UpdateMode.FULL)
            val view = remoteViews.apply(this, previewContainer)
            previewContainer.removeAllViews()
            previewContainer.addView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAllPermissions() {
        var widgetNeedsUpdate = false

        // Check Calendar
        val calMissing = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        if (prefs.getBoolean("show_events", false) && calMissing) {
            prefs.edit().putBoolean("show_events", false).apply()
            findViewById<View>(R.id.row_events_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
            findViewById<View>(R.id.row_events_size).visibility = View.GONE
            widgetNeedsUpdate = true
        }

        // Check Tasks
        val tasksMissing = ContextCompat.checkSelfPermission(this, AwidgetProvider.PERMISSION_READ_TASKS_ORG) != PackageManager.PERMISSION_GRANTED
        if (prefs.getBoolean("show_tasks", false) && tasksMissing) {
             prefs.edit().putBoolean("show_tasks", false).apply()
             findViewById<View>(R.id.row_tasks_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
             findViewById<View>(R.id.row_tasks_size).visibility = View.GONE
             widgetNeedsUpdate = true
        }

        // Check Steps
        var stepMissing = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            stepMissing = true
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stepMissing = true
        }
        if (prefs.getBoolean("show_steps", false) && stepMissing) {
             prefs.edit().putBoolean("show_steps", false).apply()
             findViewById<View>(R.id.row_steps_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
             findViewById<View>(R.id.row_steps_size).visibility = View.GONE
             widgetNeedsUpdate = true
        }

        // Check Screen Time
        val usageMissing = !hasUsageStatsPermission()
        if (prefs.getBoolean("show_screen_time", false) && usageMissing) {
            prefs.edit().putBoolean("show_screen_time", false).apply()
            findViewById<View>(R.id.row_screen_time_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
            findViewById<View>(R.id.row_screen_time_size).visibility = View.GONE
            widgetNeedsUpdate = true
        }

        // Check Data Usage
        if (prefs.getBoolean("show_data_usage", false) && usageMissing) {
            prefs.edit().putBoolean("show_data_usage", false).apply()
            findViewById<View>(R.id.row_data_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
            findViewById<View>(R.id.row_data_size).visibility = View.GONE
            widgetNeedsUpdate = true
        }

        // Check Breezy Weather
        val weatherMissing = !isAppInstalled("org.breezyweather") || ContextCompat.checkSelfPermission(this, "org.breezyweather.READ_PROVIDER") != PackageManager.PERMISSION_GRANTED
        if (prefs.getBoolean("show_weather_condition", false) && weatherMissing) {
            prefs.edit().putBoolean("show_weather_condition", false).apply()
            findViewById<View>(R.id.row_weather_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = false
            findViewById<View>(R.id.row_weather_size).visibility = View.GONE
            widgetNeedsUpdate = true
        }

        if (widgetNeedsUpdate) {
            updateWidget()
            updateToggleAvailability()
        }
        
        // Update Permission Toggles
        updatePermissionToggle(R.id.row_perm_calendar, "Calendar Events", !calMissing) {
            if (calMissing) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 100)
            else openAppSettings()
        }
        updatePermissionToggle(R.id.row_perm_tasks, "Tasks", !tasksMissing) {
            if (tasksMissing) ActivityCompat.requestPermissions(this, arrayOf(AwidgetProvider.PERMISSION_READ_TASKS_ORG), 101)
            else openAppSettings()
        }
        updatePermissionToggle(R.id.row_perm_steps, "Step Counter", !stepMissing) {
            if (stepMissing) {
                val neededPermissions = mutableListOf<String>()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 102)
            } else openAppSettings()
        }
        updatePermissionToggle(R.id.row_perm_data_usage, "Data Usage", !usageMissing) {
            try { startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (e: Exception) { Log.w("MainActivity", "Failed to launch usage access settings", e) }
        }
        updatePermissionToggle(R.id.row_perm_screen_time, "Screen Time", !usageMissing) {
            try { startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (e: Exception) { Log.w("MainActivity", "Failed to launch usage access settings", e) }
        }
        updatePermissionToggle(R.id.row_perm_weather, "Weather", !weatherMissing) {
            if (weatherMissing && !isAppInstalled("org.breezyweather")) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.breezyweather")))
                } catch (e: Exception) { Log.w("MainActivity", "Failed to open market link for BreezyWeather", e)
                    try {
                        CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://f-droid.org/packages/org.breezyweather/"))
                    } catch (e2: Exception) { Log.w("MainActivity", "Failed to launch custom tab for BreezyWeather", e2) }
                }
            } else if (weatherMissing) {
                ActivityCompat.requestPermissions(this, arrayOf("org.breezyweather.READ_PROVIDER"), 104)
            } else openAppSettings()
        }
    }
    
    private fun updatePermissionToggle(viewId: Int, label: String, isGranted: Boolean, onClick: () -> Unit) {
        val row = findViewById<View>(viewId)
        if (row != null) {
            row.findViewById<TextView>(R.id.row_label).text = label
            val switchView = row.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch)
            switchView.setOnCheckedChangeListener(null)
            switchView.isChecked = isGranted
            
            row.setOnClickListener { onClick() }
            switchView.setOnClickListener {
                switchView.isChecked = isGranted // Revert instantly
                onClick()
            }
        }
    }
    
    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.parse("package:" + packageName)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning (especially for Data Usage settings)
        checkAllPermissions()
        // Force a full widget update every time the app is opened
        updateWidget()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkAllPermissions()
        if (requestCode == 101) {
             // Task permission result - trigger update
             if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 updateWidget()
             }
        } else if (requestCode == 103) {
             // Breezy Weather permission result
             if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 prefs.edit().putBoolean("show_weather_condition", true).apply()
                 findViewById<View>(R.id.row_weather_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch).isChecked = true
                 findViewById<View>(R.id.row_weather_size).visibility = View.VISIBLE
                 updateWidget()
                 updateToggleAvailability()

                 // Show Gadgetbridge module prompt
                 android.app.AlertDialog.Builder(this)
                     .setTitle("Important Step")
                     .setMessage("If the weather doesn't show up on your widget soon:\n\nOpen Breezy Weather → Settings → External Modules → Enable 'Send Gadgetbridge Data' & toggle on 'Lwidget'.")
                     .setPositiveButton("Got it", null)
                     .show()
             }
        }
    }

    // ===== FOLDED SECTION HELPERS =====

    // Top-level accordion: feature cards (Time, Battery, Appearance, etc.)
    private val accordionViews = mutableMapOf<String, View>()
    private val accordionHeaders = mutableMapOf<String, View>()
    // Nested sections: own accordion among themselves
    private val nestedViews = mutableMapOf<String, View>()
    private val nestedHeaders = mutableMapOf<String, View>()

    private fun collapseAllExcept(exceptKey: String) {
        accordionViews.forEach { (key, view) ->
            if (key != exceptKey && view.visibility == View.VISIBLE) {
                // Extract section name from key like "section_world_clock_expanded"
                val sectionName = key.replace("section_", "").replace("_expanded", "")
                collapseSectionNestedContent(sectionName)
                view.visibility = View.GONE
                prefs.edit().putBoolean(key, false).apply()
                accordionHeaders[key]?.let { resetChevron(it) }
            }
        }
        dismissKeyboard()
    }

    private fun collapseNestedExcept(exceptKey: String) {
        nestedViews.forEach { (key, view) ->
            if (key != exceptKey && view.visibility == View.VISIBLE) {
                view.visibility = View.GONE
                prefs.edit().putBoolean(key, false).apply()
                nestedHeaders[key]?.let { resetChevron(it) }
            }
        }
    }

    private fun resetChevron(header: View) {
        val chevron = header.findViewById<android.widget.ImageView>(R.id.header_chevron)
            ?: header.findViewById<android.widget.ImageView>(R.id.header_chevron_appearance_presets)
            ?: header.findViewById<android.widget.ImageView>(R.id.header_chevron_appearance_outline)
            ?: header.findViewById<android.widget.ImageView>(R.id.header_chevron_appearance_colors)
            ?: header.findViewById<android.widget.ImageView>(R.id.header_chevron_appearance_theme)
            ?: header.findViewById<android.widget.ImageView>(R.id.header_chevron_appearance_font)
            ?: header.findViewById<android.widget.ImageView>(R.id.header_chevron_appearance_transparency)
        chevron?.rotation = 0f
    }

    private fun bindFoldedSection(
        headerId: Int, iconResId: Int?, title: String,
        contentId: Int,
        toggleRowId: Int,
        prefShowKey: String, defShow: Boolean,
        sizeRowId: Int? = null, prefSizeKey: String? = null,
        defSize: Float = 14f, minSize: Float = 10f, maxSize: Float = 72f,
        selectorRowId: Int? = null, selectorOptions: List<String>? = null,
        prefSelectorKey: String? = null, defSelectorIdx: Int = 0,
        isContent: Boolean = false,
        subSettingsContainerId: Int? = null,
        validateToggle: ((Boolean) -> Boolean)? = null,
        onChanged: ((Boolean) -> Unit)? = null
    ): SwitchMaterial {
        val header = findViewById<View>(headerId)
        val chevron = header.findViewById<android.widget.ImageView>(R.id.header_chevron)
        val headerIcon = header.findViewById<android.widget.ImageView>(R.id.header_icon)
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

        // Header click: expand this one and collapse all others
        header.setOnClickListener {
            val nowExpanded = content.visibility != View.VISIBLE
            if (nowExpanded) {
                collapseAllExcept(expandedPrefKey)
                content.visibility = View.VISIBLE
                prefs.edit().putBoolean(expandedPrefKey, true).apply()
            } else {
                // Collapsing - dismiss keyboard and close nested subsections
                collapseSectionNestedContent(sectionKey)
                content.visibility = View.GONE
                prefs.edit().putBoolean(expandedPrefKey, false).apply()
            }
            android.animation.ObjectAnimator.ofFloat(chevron, "rotation", if (nowExpanded) 180f else 0f).apply {
                duration = 300
                start()
            }
        }

        // Toggle row
        val toggleRow = findViewById<View>(toggleRowId)
        val toggleSwitch = toggleRow.findViewById<SwitchMaterial>(R.id.row_switch)
        val toggleLabel = toggleRow.findViewById<TextView>(R.id.row_label)
        val toggleCard = toggleRow.findViewById<com.google.android.material.card.MaterialCardView>(R.id.toggle_row_card)
        toggleLabel.text = "Enable"

        if (isContent) contentSwitches.add(toggleSwitch)

        val isShown = prefs.getBoolean(prefShowKey, defShow)
        toggleSwitch.isChecked = isShown

        // Sub-settings alpha
        val subSettings = subSettingsContainerId?.let { findViewById<View>(it) }
        subSettings?.alpha = if (isShown) 1.0f else 0.4f
        updateToggleCardStyle(toggleCard, isShown)

        // Size row visibility
        val sizeRow = sizeRowId?.let { findViewById<View>(it) }
        sizeRow?.visibility = if (isShown) View.VISIBLE else View.GONE

        // Selector row visibility
        val selectorRow = selectorRowId?.let { findViewById<View>(it) }
        selectorRow?.visibility = if (isShown) View.VISIBLE else View.GONE

        onChanged?.invoke(isShown)

        // Internal listener - ALWAYS handles visibility
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
            sizeRow?.visibility = if (isChecked) View.VISIBLE else View.GONE
            selectorRow?.visibility = if (isChecked) View.VISIBLE else View.GONE
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
            sizeLabel.text = "Size"

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

    private fun updateToggleCardStyle(card: com.google.android.material.card.MaterialCardView?, enabled: Boolean) {
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
    private fun updateFeatureRowVisibility(switch: SwitchMaterial, isChecked: Boolean, sizeRowId: Int? = null) {
        val toggleRow = switch.parent as? View
        val toggleCard = toggleRow?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.toggle_row_card)
        updateToggleCardStyle(toggleCard, isChecked)
        sizeRowId?.let { findViewById<View>(it)?.visibility = if (isChecked) View.VISIBLE else View.GONE }
    }

    private fun bindTimezoneSearch(
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

    // Dismiss keyboard and collapse nested subsections when a parent section collapses
    private fun collapseSectionNestedContent(sectionKey: String) {
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

    private fun dismissKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        currentFocus?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun bindReorderSection() {
        val defaultOrder = listOf(
            ReorderItem("show_battery", getString(R.string.section_battery), prefs.getBoolean("show_battery", true)),
            ReorderItem("show_temp", getString(R.string.section_temp), prefs.getBoolean("show_temp", false)),
            ReorderItem("show_weather_condition", getString(R.string.section_weather_condition), prefs.getBoolean("show_weather_condition", false)),
            ReorderItem("show_data_usage", getString(R.string.section_data_usage), prefs.getBoolean("show_data_usage", false)),
            ReorderItem("show_storage", getString(R.string.section_storage), prefs.getBoolean("show_storage", false)),
            ReorderItem("show_ram", getString(R.string.section_ram), prefs.getBoolean("show_ram", false)),
            ReorderItem("show_steps", getString(R.string.section_steps), prefs.getBoolean("show_steps", false)),
            ReorderItem("show_screen_time", getString(R.string.section_screen_time), prefs.getBoolean("show_screen_time", false))
        )

        val savedOrder = prefs.getString("widget_right_column_order", "")
        val items = if (savedOrder.isNullOrEmpty()) {
            defaultOrder.toMutableList()
        } else {
            val keys = savedOrder.split(",")
            val list = mutableListOf<ReorderItem>()
            keys.forEach { key ->
                val item = defaultOrder.find { it.key == key }
                if (item != null) list.add(item)
                else list.add(ReorderItem(key, key.replace("show_", "").replace("_", " ").capitalize(), prefs.getBoolean(key, false)))
            }
            // Add any new items not in saved order
            defaultOrder.forEach { default ->
                if (!list.any { it.key == default.key }) list.add(default)
            }
            list
        }

        val recyclerView = findViewById<RecyclerView>(R.id.reorder_recycler)
        val adapter = ReorderAdapter(items) {
            // Save order on every move
            val orderStr = items.joinToString(",") { it.key }
            prefs.edit().putString("widget_right_column_order", orderStr).apply()
            updateWidget()
        }
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Intercept touch events so parent NestedScrollView doesn't steal them
        recyclerView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }


        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun bindNestedCard(
        headerId: Int, title: String, contentId: Int, sectionKey: String,
        chevronViewId: Int? = null
    ) {
        val header = findViewById<View>(headerId)
        val content = findViewById<View>(contentId)
        val chevronView = header.findViewById<android.widget.ImageView>(
            chevronViewId ?: R.id.header_chevron
        )

        // Standalone toggle - no accordion, each section independent
        nestedViews[sectionKey] = content
        nestedHeaders[sectionKey] = header

        val isExpanded = prefs.getBoolean(sectionKey, false)
        content.visibility = if (isExpanded) View.VISIBLE else View.GONE
        chevronView.rotation = if (isExpanded) 180f else 0f

        header.setOnClickListener {
            val nowExpanded = content.visibility != View.VISIBLE
            if (nowExpanded) {
                collapseNestedExcept(sectionKey)
                content.visibility = View.VISIBLE
                prefs.edit().putBoolean(sectionKey, true).apply()
            } else {
                content.visibility = View.GONE
                prefs.edit().putBoolean(sectionKey, false).apply()
            }
            android.animation.ObjectAnimator.ofFloat(chevronView, "rotation", if (nowExpanded) 180f else 0f).apply {
                duration = 300
                start()
            }
        }

    }


    private fun bindCategoryFoldable(headerId: Int, contentId: Int, title: String, iconResId: Int, prefKey: String) {
        accordionViews[prefKey] = findViewById(contentId)
        accordionHeaders[prefKey] = findViewById(headerId)
        
        val header = findViewById<View>(headerId)
        header.findViewById<TextView>(R.id.header_title).text = title
        val headerIcon = header.findViewById<android.widget.ImageView>(R.id.header_icon)
        if (iconResId != 0) {
            headerIcon.setImageResource(iconResId)
            headerIcon.visibility = View.VISIBLE
        } else {
            headerIcon.visibility = View.GONE
        }
        
        val content = findViewById<View>(contentId)
        val chevron = header.findViewById<android.widget.ImageView>(R.id.header_chevron)
        val isExpanded = prefs.getBoolean(prefKey, false)
        content.visibility = if (isExpanded) View.VISIBLE else View.GONE
        chevron.rotation = if (isExpanded) 180f else 0f
        
        header.setOnClickListener {
            val nowExpanded = content.visibility != View.VISIBLE
            if (nowExpanded) {
                collapseAllExcept(prefKey)
                content.visibility = View.VISIBLE
                prefs.edit().putBoolean(prefKey, true).apply()
            } else {
                content.visibility = View.GONE
                prefs.edit().putBoolean(prefKey, false).apply()
            }
            android.animation.ObjectAnimator.ofFloat(chevron, "rotation", if (nowExpanded) 180f else 0f).apply {
                duration = 300
                start()
            }
        }
    }

    private fun setupSections() {
        contentSwitches.clear()

        val zoneIds = java.time.ZoneId.getAvailableZoneIds().sorted()
        val dateFormatOptions = listOf(getString(R.string.date_format_full), getString(R.string.date_format_short), getString(R.string.date_format_numeric))
        val timeFormatOptions = listOf(getString(R.string.format_12h), getString(R.string.format_24h))
        val colorOptions = listOf(getString(R.string.color_default), getString(R.string.color_system_accent), getString(R.string.color_custom))

        setupTimeSection(timeFormatOptions)
        setupNextAlarmSection()
        setupWorldClockSection(zoneIds)
        setupDateSection(dateFormatOptions)
        setupBatterySection()
        setupTempSection()
        setupWeatherSection()
        setupDataUsageSection()
        setupStorageSection()
        setupRamSection()
        setupStepsSection()
        setupScreenTimeSection()
        setupKeepAliveSection()
        setupEventsAndTasksSections()
        setupThemeSection(colorOptions)
        
        // System sections
        bindCategoryFoldable(R.id.header_advanced, R.id.content_advanced, "Advanced", 0, "section_advanced_expanded")
        bindCategoryFoldable(R.id.header_permissions, R.id.content_permissions, "Permissions", 0, "section_permissions_expanded")
        bindCategoryFoldable(R.id.header_about, R.id.content_about, "About", 0, "section_about_expanded")
    }

    private fun setupTimeSection(timeFormatOptions: List<String>) {
        // Time
        bindFoldedSection(
            R.id.header_time, R.drawable.ic_time, getString(R.string.section_time),
            R.id.content_time, R.id.row_time_toggle,
            "show_time", true,
            sizeRowId = R.id.row_time_size, prefSizeKey = "size_time", defSize = 56f, minSize = 12f, maxSize = 120f,
            selectorRowId = R.id.row_time_format, selectorOptions = timeFormatOptions, prefSelectorKey = "time_format_idx", defSelectorIdx = 0,
            isContent = true
        )
    }
    private fun setupNextAlarmSection() {
        // Next Alarm
        bindFoldedSection(
            R.id.header_next_alarm, R.drawable.ic_alarm, getString(R.string.section_next_alarm),
            R.id.content_next_alarm, R.id.row_next_alarm_toggle,
            "show_next_alarm", true,
            sizeRowId = R.id.row_next_alarm_size, prefSizeKey = "size_next_alarm", defSize = 14f, minSize = 10f, maxSize = 24f,
            isContent = true
        )
    }
    private fun setupWorldClockSection(zoneIds: List<String>) {
        // World Clock
        bindFoldedSection(
            R.id.header_world_clock, R.drawable.ic_world, getString(R.string.section_world_clock),
            R.id.content_world_clock, R.id.row_world_clock_toggle,
            "show_world_clock", false,
            sizeRowId = R.id.row_world_clock_size, prefSizeKey = "size_world_clock", defSize = 18f, minSize = 10f, maxSize = 32f,
            isContent = true
        )
        bindTimezoneSearch(R.id.row_world_clock_zone, zoneIds, "world_clock_zone_str", "UTC")
    }
    private fun setupDateSection(dateFormatOptions: List<String>) {
        // Date
        bindFoldedSection(
            R.id.header_date, R.drawable.ic_date, getString(R.string.section_date),
            R.id.content_date, R.id.row_date_toggle,
            "show_date", true,
            sizeRowId = R.id.row_date_size, prefSizeKey = "size_date", defSize = 16f, minSize = 10f, maxSize = 24f,
            selectorRowId = R.id.row_date_format, selectorOptions = dateFormatOptions, prefSelectorKey = "date_format_idx", defSelectorIdx = 0,
            isContent = true
        )
    }
    private fun setupBatterySection() {
        // Battery
        bindFoldedSection(
            R.id.header_battery, R.drawable.ic_battery, getString(R.string.section_battery),
            R.id.content_battery, R.id.row_battery_toggle,
            "show_battery", true,
            sizeRowId = R.id.row_battery_size, prefSizeKey = "size_battery", defSize = 32f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "battery" }
        bindToggle(R.id.row_battery_bold, "Bold Text", "bold_battery", true)
    }
    private fun setupTempSection() {
        // Temp
        bindFoldedSection(
            R.id.header_temp, R.drawable.ic_temp, getString(R.string.section_temp),
            R.id.content_temp, R.id.row_temp_toggle,
            "show_temp", false,
            sizeRowId = R.id.row_temp_size, prefSizeKey = "size_temp", defSize = 18f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "temp" }
        bindToggle(R.id.row_temp_bold, "Bold Text", "bold_temp", false)
    }
    private fun setupWeatherSection() {
        // Weather
        val weatherSwitch = bindFoldedSection(
            R.id.header_weather, R.drawable.ic_weather, getString(R.string.section_weather_condition),
            R.id.content_weather, R.id.row_weather_toggle,
            "show_weather_condition", false,
            sizeRowId = R.id.row_weather_size, prefSizeKey = "size_weather", defSize = 18f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "weather_condition" }
        bindToggle(R.id.row_weather_bold, "Bold Text", "bold_weather", false)

        // Override weather listener for Breezy Weather check
        weatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isAppInstalled("org.breezyweather")) {
                    weatherSwitch.isChecked = false
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(R.id.fab_update),
                        "Breezy Weather app (with DataBridge enabled) is required.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction("Install") {
                        try {
                            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://github.com/breezy-weather/breezy-weather/releases"))
                        } catch (e: Exception) {}
                    }.show()
                    return@setOnCheckedChangeListener
                }
                if (ContextCompat.checkSelfPermission(this, "org.breezyweather.READ_PROVIDER") != PackageManager.PERMISSION_GRANTED) {
                    weatherSwitch.isChecked = false
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Permission Clarification")
                        .setMessage("To display the weather, Lwidget needs to read data from Breezy Weather.\n\nAndroid will now ask for 'Location' access. Please note: Lwidget DOES NOT access your location, nor does it have permission to access the internet. This is simply how Android categorizes Breezy Weather's data sharing permission.")
                        .setPositiveButton("Continue") { _, _ ->
                            ActivityCompat.requestPermissions(this, arrayOf("org.breezyweather.READ_PROVIDER"), 103)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@setOnCheckedChangeListener
                }
                if (!checkLimit()) {
                    weatherSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            prefs.edit().putBoolean("show_weather_condition", isChecked).apply()
            updateFeatureRowVisibility(weatherSwitch, isChecked, R.id.row_weather_size)
            updateWidget()
            updateToggleAvailability()
            if (isChecked) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Important Step")
                    .setMessage("If the weather doesn't show up on your widget soon:\n\nOpen Breezy Weather → Settings → External Modules → Enable 'Send Gadgetbridge Data' & toggle on 'Lwidget'.")
                    .setPositiveButton("Got it", null)
                    .show()
            }
        }
    }
    private fun setupDataUsageSection() {
        // Data Usage
        val dataSwitch = bindFoldedSection(
            R.id.header_data, R.drawable.ic_data, getString(R.string.section_data_usage),
            R.id.content_data, R.id.row_data_toggle,
            "show_data_usage", false,
            sizeRowId = R.id.row_data_size, prefSizeKey = "size_data", defSize = 14f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "data" }
        bindToggle(R.id.row_data_bold, "Bold Text", "bold_data_usage", false)

        dataSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    dataSwitch.isChecked = false
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(R.id.fab_update),
                            getString(R.string.perm_usage_access_title),
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {}
                    return@setOnCheckedChangeListener
                }
                if (!checkLimit()) {
                    dataSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            prefs.edit().putBoolean("show_data_usage", isChecked).apply()
            updateFeatureRowVisibility(dataSwitch, isChecked, R.id.row_data_size)
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }
    }
    private fun setupRamSection() {
        // RAM
        bindFoldedSection(
            R.id.header_ram, R.drawable.ic_storage, getString(R.string.section_ram),
            R.id.content_ram, R.id.row_ram_toggle,
            "show_ram", false,
            sizeRowId = R.id.row_ram_size, prefSizeKey = "size_ram", defSize = 14f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "ram" }
        bindToggle(R.id.row_ram_bold, "Bold Text", "bold_ram", false)
    }

    private fun setupStorageSection() {
        // Storage
        bindFoldedSection(
            R.id.header_storage, R.drawable.ic_storage, getString(R.string.section_storage),
            R.id.content_storage, R.id.row_storage_toggle,
            "show_storage", false,
            sizeRowId = R.id.row_storage_size, prefSizeKey = "size_storage", defSize = 14f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "storage" }
        bindToggle(R.id.row_storage_bold, "Bold Text", "bold_storage", false)
    }
    private fun setupStepsSection() {
        // Steps
        val stepsSwitch = bindFoldedSection(
            R.id.header_steps, R.drawable.ic_steps, getString(R.string.section_steps),
            R.id.content_steps, R.id.row_steps_toggle,
            "show_steps", false,
            sizeRowId = R.id.row_steps_size, prefSizeKey = "size_steps", defSize = 14f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "steps" }
        bindToggle(R.id.row_steps_bold, "Bold Text", "bold_steps", false)

        stepsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val neededPermissions = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (neededPermissions.isNotEmpty()) {
                        ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 102)
                        stepsSwitch.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                }
                if (!checkLimit()) {
                    stepsSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            prefs.edit().putBoolean("show_steps", isChecked).apply()
            val keepAlive = prefs.getBoolean("keep_alive", false)
            val serviceIntent = Intent(this, StepCounterService::class.java)
            if (isChecked) {
                val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                } else { true }
                if (hasPermission) { startForegroundService(serviceIntent) }
                else {
                    prefs.edit().putBoolean("show_steps", false).apply()
                    updateWidget()
                    updateToggleAvailability()
                    checkAllPermissions()
                    return@setOnCheckedChangeListener
                }
            } else if (!keepAlive) { stopService(serviceIntent) }
            updateFeatureRowVisibility(stepsSwitch, isChecked, R.id.row_steps_size)
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }
    }
    private fun setupScreenTimeSection() {
        // Screen Time
        val screenTimeSwitch = bindFoldedSection(
            R.id.header_screen_time, R.drawable.ic_time, getString(R.string.section_screen_time),
            R.id.content_screen_time, R.id.row_screen_time_toggle,
            "show_screen_time", false,
            sizeRowId = R.id.row_screen_time_size, prefSizeKey = "size_screen_time", defSize = 14f, minSize = 10f, maxSize = 74f,
            isContent = true
        ).also { it.tag = "screen_time" }
        bindToggle(R.id.row_screen_time_bold, "Bold Text", "bold_screen_time", false)

        screenTimeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    screenTimeSwitch.isChecked = false
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(R.id.fab_update),
                            getString(R.string.perm_usage_access_title),
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) { android.util.Log.e("MainActivity", "Failed to open Usage Access Settings", e) }
                    return@setOnCheckedChangeListener
                }
                if (!checkLimit()) {
                    screenTimeSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            prefs.edit().putBoolean("show_screen_time", isChecked).apply()
            updateFeatureRowVisibility(screenTimeSwitch, isChecked, R.id.row_screen_time_size)
            updateWidget()
            updateToggleAvailability()
            checkAllPermissions()
        }
    }
    private fun setupKeepAliveSection() {
        // Keep Alive (now inside Advanced section, not folded)
        bindToggle(R.id.row_keep_alive_toggle, "Enable Keep Alive", "keep_alive", false) { isChecked ->
            if (isChecked) {
                val neededPermissions = mutableListOf<String>()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (neededPermissions.isNotEmpty()) {
                    val switch = findViewById<View>(R.id.row_keep_alive_toggle).findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.row_switch)
                    switch.isChecked = false
                    ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 105)
                    return@bindToggle
                }
            }
            prefs.edit().putBoolean("keep_alive", isChecked).apply()
            val showSteps = prefs.getBoolean("show_steps", false)
            val serviceIntent = Intent(this, StepCounterService::class.java)
            val hasActivityPerm = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            if ((isChecked || showSteps) && hasActivityPerm) {
                try {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                stopService(serviceIntent)
            }
        }
    }
    private fun setupEventsAndTasksSections() {
        // Events
        val eventsSwitch = bindFoldedSection(
            R.id.header_events, R.drawable.ic_events, getString(R.string.section_events),
            R.id.content_events, R.id.row_events_toggle,
            "show_events", false,
            sizeRowId = R.id.row_events_size, prefSizeKey = "size_events", defSize = 14f, minSize = 10f, maxSize = 18f,
            isContent = true
        )

        // Tasks
        val tasksSwitch = bindFoldedSection(
            R.id.header_tasks, R.drawable.ic_tasks, getString(R.string.section_tasks),
            R.id.content_tasks, R.id.row_tasks_toggle,
            "show_tasks", false,
            sizeRowId = R.id.row_tasks_size, prefSizeKey = "size_tasks", defSize = 14f, minSize = 10f, maxSize = 18f,
            isContent = true
        )

        // Mutual Exclusion: Events vs Tasks
        eventsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 100)
                    eventsSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (checkLimit()) {
                    tasksSwitch.isChecked = false
                    prefs.edit().putBoolean("show_events", true).putBoolean("show_tasks", false).apply()
                    updateFeatureRowVisibility(eventsSwitch, true, R.id.row_events_size)
                    updateWidget()
                    updateToggleAvailability()
                    checkAllPermissions()
                } else { eventsSwitch.isChecked = false }
            } else {
                prefs.edit().putBoolean("show_events", false).apply()
                updateFeatureRowVisibility(eventsSwitch, false, R.id.row_events_size)
                updateWidget()
                updateToggleAvailability()
                checkAllPermissions()
            }
        }
        tasksSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isAppInstalled("org.tasks")) {
                    tasksSwitch.isChecked = false
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(R.id.fab_update),
                        "Tasks.org app is required for this feature.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction("Install") {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.tasks")))
                        } catch (e: Exception) {
                            CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, android.net.Uri.parse("https://play.google.com/store/apps/details?id=org.tasks"))
                        }
                    }.show()
                    return@setOnCheckedChangeListener
                }
                if (ContextCompat.checkSelfPermission(this, AwidgetProvider.PERMISSION_READ_TASKS_ORG) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(AwidgetProvider.PERMISSION_READ_TASKS_ORG), 101)
                }
                if (checkLimit()) {
                    eventsSwitch.isChecked = false
                    prefs.edit().putBoolean("show_tasks", true).putBoolean("show_events", false).apply()
                    updateFeatureRowVisibility(tasksSwitch, true, R.id.row_tasks_size)
                    updateWidget()
                    updateToggleAvailability()
                    checkAllPermissions()
                } else { tasksSwitch.isChecked = false }
            } else {
                prefs.edit().putBoolean("show_tasks", false).apply()
                updateFeatureRowVisibility(tasksSwitch, false, R.id.row_tasks_size)
                updateWidget()
                updateToggleAvailability()
                checkAllPermissions()
            }
        }
    }
    private fun setupThemeSection(colorOptions: List<String>) {
        // ===== THEME =====
        // Use bindFoldedSection for the main card (top-level accordion), not bindNestedCard
        accordionViews["section_appearance_expanded"] = findViewById(R.id.content_appearance)
        accordionHeaders["section_appearance_expanded"] = findViewById(R.id.header_appearance)
        // Set title and icon
        val appearanceHeader = findViewById<View>(R.id.header_appearance)
        appearanceHeader.findViewById<TextView>(R.id.header_title).text = "Theme"
        val appearanceHeaderIcon = appearanceHeader.findViewById<android.widget.ImageView>(R.id.header_icon)
        appearanceHeaderIcon.setImageResource(R.drawable.ic_palette)
        appearanceHeaderIcon.visibility = View.VISIBLE

        val appearanceContent = findViewById<View>(R.id.content_appearance)
        val appearanceChevron = findViewById<View>(R.id.header_appearance).findViewById<android.widget.ImageView>(R.id.header_chevron)
        val appearanceIsExpanded = prefs.getBoolean("section_appearance_expanded", false)
        appearanceContent.visibility = if (appearanceIsExpanded) View.VISIBLE else View.GONE
        appearanceChevron.rotation = if (appearanceIsExpanded) 180f else 0f

        findViewById<View>(R.id.header_appearance).setOnClickListener {
            val nowExpanded = appearanceContent.visibility != View.VISIBLE
            if (nowExpanded) {
                collapseAllExcept("section_appearance_expanded")
                appearanceContent.visibility = View.VISIBLE
                prefs.edit().putBoolean("section_appearance_expanded", true).apply()
            } else {
                appearanceContent.visibility = View.GONE
                prefs.edit().putBoolean("section_appearance_expanded", false).apply()
            }
            android.animation.ObjectAnimator.ofFloat(appearanceChevron, "rotation", if (nowExpanded) 180f else 0f).apply {
                duration = 300
                start()
            }
        }

        // Appearance Subsections (nested cards)
        bindNestedCard(R.id.header_appearance_presets, "PRESETS", R.id.content_appearance_presets, "section_appearance_presets_expanded", R.id.header_chevron_appearance_presets)
        setupPresetsSection()
        bindNestedCard(R.id.header_appearance_outline, "OUTLINE", R.id.content_appearance_outline, "section_appearance_outline_expanded", R.id.header_chevron_appearance_outline)
        bindNestedCard(R.id.header_appearance_colors, "COLORS", R.id.content_appearance_colors, "section_appearance_colors_expanded", R.id.header_chevron_appearance_colors)
        bindNestedCard(R.id.header_appearance_theme, "THEME", R.id.content_appearance_theme, "section_appearance_theme_expanded", R.id.header_chevron_appearance_theme)
        bindNestedCard(R.id.header_appearance_font, "FONT", R.id.content_appearance_font, "section_appearance_font_expanded", R.id.header_chevron_appearance_font)
        bindNestedCard(R.id.header_appearance_transparency, "TRANSPARENCY", R.id.content_appearance_transparency, "section_appearance_transparency_expanded", R.id.header_chevron_appearance_transparency)

        // Reorder section
        bindNestedCard(R.id.header_appearance_reorder, "REORDER", R.id.content_appearance_reorder, "section_appearance_reorder_expanded", R.id.header_chevron_appearance_reorder)
        bindReorderSection()

        // Outline toggle
        bindToggle(R.id.row_outline_toggle, "Show Outline", "show_outline", false) { isChecked ->
            updateWidget()
        }

        // Dynamic Colors toggle
        val rowDynamicColors = findViewById<View>(R.id.row_dynamic_colors_toggle)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            rowDynamicColors.visibility = View.VISIBLE
            bindToggle(R.id.row_dynamic_colors_toggle, "Dynamic Colors", "use_dynamic_colors", true) { isChecked ->
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

        // Theme toggle
        bindToggle(R.id.row_theme_toggle, "Light Theme", "use_system_theme", false) { isChecked ->
            applyTheme()
        }

        // BG Transparency
        bindSlider(R.id.row_bg_transparency, "Background Opacity", "bg_opacity", 85f, 0f, 100f)

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

    private fun setupPresetsSection() {
        data class Preset(
            val key: String,
            val label: String,
            val prefs: Map<String, Any>
        )

        val presets = listOf(
            // Minimal: just time+date, white text, fully transparent, thin font
            Preset("minimal", "Minimal", mapOf(
                "show_time" to true, "size_time" to 58f,
                "show_date" to true, "size_date" to 16f,
                "show_battery" to false, "show_temp" to false,
                "show_storage" to false, "show_ram" to false, "show_data_usage" to false,
                "show_steps" to false, "show_screen_time" to false,
                "show_next_alarm" to false, "show_world_clock" to false,
                "show_events" to false, "show_tasks" to false,
                "show_outline" to false, "bg_opacity" to 0f,
                "font_style" to 9, // Thin
                "use_dynamic_colors" to false,
                "text_color_primary_idx" to 2, "text_color_primary_r" to 255, "text_color_primary_g" to 255, "text_color_primary_b" to 255,
                "text_color_secondary_idx" to 2, "text_color_secondary_r" to 200, "text_color_secondary_g" to 200, "text_color_secondary_b" to 200,
                "date_color_idx" to 2, "date_color_r" to 180, "date_color_g" to 180, "date_color_b" to 190,
                "bold_battery" to false, "bold_temp" to false
            )),
            // Neon: cyan time, magenta date, dark bg, bold condensed font
            Preset("neon", "Neon", mapOf(
                "show_time" to true, "size_time" to 64f,
                "show_date" to true, "size_date" to 14f,
                "show_battery" to true, "size_battery" to 28f, "bold_battery" to true,
                "show_temp" to true, "size_temp" to 18f, "bold_temp" to true,
                "show_storage" to false, "show_ram" to false, "show_data_usage" to false,
                "show_steps" to false, "show_screen_time" to false,
                "show_next_alarm" to true, "size_next_alarm" to 12f,
                "show_world_clock" to false,
                "show_events" to false, "show_tasks" to false,
                "show_outline" to true, "bg_opacity" to 95f,
                "font_style" to 4, // Condensed
                "use_dynamic_colors" to false,
                "text_color_primary_idx" to 2, "text_color_primary_r" to 0, "text_color_primary_g" to 255, "text_color_primary_b" to 255,
                "text_color_secondary_idx" to 2, "text_color_secondary_r" to 0, "text_color_secondary_g" to 200, "text_color_secondary_b" to 200,
                "date_color_idx" to 2, "date_color_r" to 255, "date_color_g" to 0, "date_color_b" to 180,
                "outline_color_idx" to 2, "outline_color_r" to 0, "outline_color_g" to 200, "outline_color_b" to 255,
                "bg_color_idx" to 2, "bg_color_r" to 10, "bg_color_g" to 10, "bg_color_b" to 20,
                "widget_right_column_order" to "show_battery,show_temp,show_weather_condition,show_data_usage,show_storage,show_ram,show_steps,show_screen_time"
            )),
            // Cockpit: green on dark, monospace, info-heavy, terminal look
            Preset("cockpit", "Cockpit", mapOf(
                "show_time" to true, "size_time" to 42f,
                "show_date" to true, "size_date" to 14f,
                "show_battery" to true, "size_battery" to 18f, "bold_battery" to false,
                "show_temp" to true, "size_temp" to 16f, "bold_temp" to false,
                "show_storage" to true, "size_storage" to 14f, "bold_storage" to false, "show_ram" to false, "size_ram" to 14f, "bold_ram" to false,
                "show_data_usage" to true, "size_data" to 14f, "bold_data_usage" to false,
                "show_steps" to false, "show_screen_time" to false,
                "show_next_alarm" to true, "size_next_alarm" to 14f,
                "show_world_clock" to false,
                "show_events" to false, "show_tasks" to false,
                "show_outline" to true, "bg_opacity" to 90f,
                "font_style" to 2, // Monospace
                "use_dynamic_colors" to false,
                "text_color_primary_idx" to 2, "text_color_primary_r" to 0, "text_color_primary_g" to 255, "text_color_primary_b" to 65,
                "text_color_secondary_idx" to 2, "text_color_secondary_r" to 0, "text_color_secondary_g" to 180, "text_color_secondary_b" to 50,
                "date_color_idx" to 2, "date_color_r" to 0, "date_color_g" to 200, "date_color_b" to 80,
                "outline_color_idx" to 2, "outline_color_r" to 0, "outline_color_g" to 120, "outline_color_b" to 40,
                "bg_color_idx" to 2, "bg_color_r" to 5, "bg_color_g" to 15, "bg_color_b" to 5,
                "widget_right_column_order" to "show_battery,show_storage,show_ram,show_data_usage,show_temp,show_weather_condition,show_steps,show_screen_time"
            )),
            // Sunset: warm oranges/gold, serif font, elegant minimal
            Preset("sunset", "Sunset", mapOf(
                "show_time" to true, "size_time" to 54f,
                "show_date" to true, "size_date" to 18f,
                "show_battery" to true, "size_battery" to 24f, "bold_battery" to true,
                "show_temp" to false, "show_storage" to false, "show_ram" to false,
                "show_data_usage" to false, "show_steps" to false,
                "show_screen_time" to false,
                "show_next_alarm" to true, "size_next_alarm" to 14f,
                "show_world_clock" to false,
                "show_events" to false, "show_tasks" to false,
                "show_outline" to false, "bg_opacity" to 70f,
                "font_style" to 1, // Serif
                "use_dynamic_colors" to false,
                "text_color_primary_idx" to 2, "text_color_primary_r" to 255, "text_color_primary_g" to 180, "text_color_primary_b" to 50,
                "text_color_secondary_idx" to 2, "text_color_secondary_r" to 230, "text_color_secondary_g" to 140, "text_color_secondary_b" to 60,
                "date_color_idx" to 2, "date_color_r" to 255, "date_color_g" to 120, "date_color_b" to 50,
                "bg_color_idx" to 2, "bg_color_r" to 30, "bg_color_g" to 15, "bg_color_b" to 8,
                "widget_right_column_order" to "show_battery,show_temp,show_weather_condition,show_data_usage,show_storage,show_ram,show_steps,show_screen_time"
            )),
            // Monochrome: white outline, all white text, medium font, classic layout
            Preset("monochrome", "Monochrome", mapOf(
                "show_time" to true, "size_time" to 48f,
                "show_date" to true, "size_date" to 14f,
                "show_battery" to true, "size_battery" to 22f, "bold_battery" to false,
                "show_temp" to false, "show_storage" to true, "size_storage" to 14f, "show_ram" to false, "size_ram" to 14f,
                "show_data_usage" to false, "show_steps" to false,
                "show_screen_time" to false,
                "show_next_alarm" to true, "size_next_alarm" to 14f,
                "show_world_clock" to false,
                "show_events" to false, "show_tasks" to false,
                "show_outline" to true, "bg_opacity" to 50f,
                "font_style" to 7, // Medium
                "use_dynamic_colors" to false,
                "text_color_primary_idx" to 2, "text_color_primary_r" to 240, "text_color_primary_g" to 240, "text_color_primary_b" to 240,
                "text_color_secondary_idx" to 2, "text_color_secondary_r" to 170, "text_color_secondary_g" to 170, "text_color_secondary_b" to 170,
                "date_color_idx" to 2, "date_color_r" to 200, "date_color_g" to 200, "date_color_b" to 200,
                "outline_color_idx" to 2, "outline_color_r" to 100, "outline_color_g" to 100, "outline_color_b" to 100,
                "bg_color_idx" to 2, "bg_color_r" to 25, "bg_color_g" to 25, "bg_color_b" to 25,
                "widget_right_column_order" to "show_battery,show_storage,show_ram,show_temp,show_weather_condition,show_data_usage,show_steps,show_screen_time"
            )),
            // Snowfall: icy blues, light font, airy feel
            Preset("snowfall", "Snowfall", mapOf(
                "show_time" to true, "size_time" to 60f,
                "show_date" to true, "size_date" to 16f,
                "show_battery" to false, "show_temp" to true, "size_temp" to 20f, "bold_temp" to false,
                "show_storage" to false, "show_ram" to false, "show_data_usage" to false,
                "show_steps" to false, "show_screen_time" to false,
                "show_next_alarm" to false,
                "show_world_clock" to false,
                "show_events" to false, "show_tasks" to false,
                "show_outline" to false, "bg_opacity" to 60f,
                "font_style" to 6, // Light
                "use_dynamic_colors" to false,
                "text_color_primary_idx" to 2, "text_color_primary_r" to 180, "text_color_primary_g" to 220, "text_color_primary_b" to 255,
                "text_color_secondary_idx" to 2, "text_color_secondary_r" to 130, "text_color_secondary_g" to 180, "text_color_secondary_b" to 230,
                "date_color_idx" to 2, "date_color_r" to 100, "date_color_g" to 170, "date_color_b" to 255,
                "bg_color_idx" to 2, "bg_color_r" to 10, "bg_color_g" to 20, "bg_color_b" to 40,
                "widget_right_column_order" to "show_temp,show_battery,show_weather_condition,show_data_usage,show_storage,show_ram,show_steps,show_screen_time"
            ))
        )

        val chipGroup = findViewById<com.google.android.material.chip.ChipGroup>(R.id.preset_chip_group)
        chipGroup.removeAllViews()
        val activePreset = prefs.getString("active_preset", null)

        for (preset in presets) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = preset.label
                isCheckable = true
                isChecked = (preset.key == activePreset)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                checkedIcon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.checkbox_on_background)
                isCheckedIconVisible = true
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

    private fun bindToggle(
        viewId: Int, title: String, prefShowKey: String, defShow: Boolean,
        isContent: Boolean = false,
        onChanged: ((Boolean) -> Unit)? = null
    ) {
        val row = findViewById<View>(viewId)
        val tvTitle = row.findViewById<TextView>(R.id.row_label)
        val switch = row.findViewById<SwitchMaterial>(R.id.row_switch)

        tvTitle.text = title
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


    private fun bindSlider(
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

    private fun bindSelector(
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

    private fun bindColorSliders(viewId: Int, prefPrefix: String): View {
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

    private fun updateColorVisibility(useDynamicColors: Boolean) {
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

    private fun applyTheme() {
        val useSystemTheme = prefs.getBoolean("use_system_theme", false)
        updateWidget()
    }

    private fun checkLimit(): Boolean {
        // Global limit removed per user request

        // Subset Limit: Battery, Weather, Temp, Data, Storage (Max 5 allowed now to fit stack)
        val subsetCount = contentSwitches.count { 
            it.isChecked && (it.tag == "battery" || it.tag == "weather_condition" || it.tag == "temp" || it.tag == "data" || it.tag == "storage" || it.tag == "ram")
        }
        
        if (subsetCount > 6) {
             com.google.android.material.snackbar.Snackbar.make(
                findViewById(R.id.fab_update), 
                getString(R.string.error_max_subset_items), 
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
            return false
        }

        return true
    }
    
    // Check usage stats permission
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val opMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), packageName)
        }
        return opMode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun updateToggleAvailability() {
        // Limit removed
        // Ensure all are enabled
        for (switch in contentSwitches) {
            switch.isEnabled = true
            switch.alpha = 1.0f
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun updateWidget() {
        updateLivePreview()
        // Animation: Subtle Outline Shine
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab_update)
        
        // Get dynamic colors
        // val colorSurface = com.google.android.material.color.MaterialColors.getColor(fab, com.google.android.material.R.attr.colorSurface)
        val colorPrimary = com.google.android.material.color.MaterialColors.getColor(fab, com.google.android.material.R.attr.colorPrimary)
        val colorTransparent = android.graphics.Color.TRANSPARENT

        val strokeAnimator = android.animation.ValueAnimator.ofArgb(colorTransparent, colorPrimary, colorTransparent)
        strokeAnimator.duration = 1000
        strokeAnimator.addUpdateListener { animator ->
            fab.strokeColor = android.content.res.ColorStateList.valueOf(animator.animatedValue as Int)
        }
        strokeAnimator.start()

        // Trigger widget update by sending broadcast
        val intent = Intent(this, AwidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            // Get all IDs
            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, AwidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }
}
