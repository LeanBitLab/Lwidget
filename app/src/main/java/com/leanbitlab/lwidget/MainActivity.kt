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
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val contentSwitches = mutableListOf<SwitchMaterial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

        setupPermissions()
        setupSections()
        
        findViewById<ExtendedFloatingActionButton>(R.id.fab_update).setOnClickListener {
            updateWidget()
        }
    }

    private fun setupPermissions() {
        val cardPermission = findViewById<MaterialCardView>(R.id.card_permission)
        val btnGrant = findViewById<View>(R.id.btn_grant_permission)

        fun checkPerm() {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                cardPermission.visibility = View.VISIBLE
            } else {
                cardPermission.visibility = View.GONE
            }
        }

        btnGrant.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 100)
        }

        checkPerm()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            setupPermissions() // Re-check visibility
        }
    }

    private fun setupSections() {
        contentSwitches.clear()

        // Time: Def True, 48sp
        bindSection(R.id.section_time, getString(R.string.section_time), "show_time", true, "size_time", 48f, 12f, 120f, isContent = true)
        bindSelector(R.id.section_time_format, getString(R.string.section_time_format), "time_format_idx", listOf(getString(R.string.format_12h), getString(R.string.format_24h)), 0)
        
        // Date: Def True, 14sp
        bindSection(R.id.section_date, getString(R.string.section_date), "show_date", true, "size_date", 14f, 10f, 64f, isContent = true)
        bindSelector(R.id.section_date_format, getString(R.string.section_date_format), "date_format_idx", listOf(getString(R.string.date_format_full), getString(R.string.date_format_short), getString(R.string.date_format_numeric)), 0)
        
        // Battery: Def True, 48sp
        bindSection(R.id.section_battery, getString(R.string.section_battery), "show_battery", true, "size_battery", 48f, 12f, 120f, isContent = true).tag = "battery"
        
        // Temp: Def True, 18sp
        bindSection(R.id.section_temp, getString(R.string.section_temp), "show_temp", true, "size_temp", 18f, 10f, 64f, isContent = true).tag = "temp"

        // Data Usage: Def False, 14sp (New)
        val dataSwitch = bindSection(R.id.section_data, getString(R.string.section_data_usage), "show_data_usage", false, "size_data", 14f, 10f, 48f, isContent = true)
        dataSwitch.tag = "data"
        
        // Intercept Data Usage toggle for permission check
        dataSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    dataSwitch.isChecked = false
                    // Redirect to settings
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(R.id.fab_update), 
                            getString(R.string.perm_usage_access_title), 
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        // Fallback
                    }
                    return@setOnCheckedChangeListener
                }
                
                if (!checkLimit()) {
                    dataSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            
            prefs.edit().putBoolean("show_data_usage", isChecked).apply()
            findViewById<View>(R.id.section_data).findViewById<View>(R.id.size_container).visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWidget()
            updateToggleAvailability()
        }

        // Events: Def True, 14sp
        val eventsSwitch = bindSection(R.id.section_events, getString(R.string.section_events), "show_events", true, "size_events", 14f, 10f, 48f, isContent = true)

        // Tasks: Def False, 14sp (New)
        val tasksSwitch = bindSection(R.id.section_tasks, getString(R.string.section_tasks), "show_tasks", false, "size_tasks", 14f, 10f, 48f, isContent = true)

        // Mutual Exclusion: Events vs Tasks
        eventsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkLimit()) {
                    tasksSwitch.isChecked = false
                    prefs.edit().putBoolean("show_events", true).putBoolean("show_tasks", false).apply()
                    updateWidget()
                    updateToggleAvailability()
                    findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = View.VISIBLE
                    findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = View.GONE
                } else {
                    eventsSwitch.isChecked = false // Revert
                }
            } else {
                 prefs.edit().putBoolean("show_events", false).apply()
                 updateWidget()
                 updateToggleAvailability()
                 findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = View.GONE
            }
        }

        tasksSwitch.setOnCheckedChangeListener { _, isChecked ->
             if (isChecked) {
                if (checkLimit()) {
                    eventsSwitch.isChecked = false
                    prefs.edit().putBoolean("show_tasks", true).putBoolean("show_events", false).apply()
                    updateWidget()
                    updateToggleAvailability()
                     findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = View.VISIBLE
                     findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = View.GONE
                } else {
                    tasksSwitch.isChecked = false // Revert
                }
            } else {
                 prefs.edit().putBoolean("show_tasks", false).apply()
                 updateWidget()
                 updateToggleAvailability()
                 findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = View.GONE
            }
        }
        
        // Initial visibility fix for mutually exclusive items (since generic bindSection logic is overridden above)
        findViewById<View>(R.id.section_events).findViewById<View>(R.id.size_container).visibility = if (eventsSwitch.isChecked) View.VISIBLE else View.GONE
        findViewById<View>(R.id.section_tasks).findViewById<View>(R.id.size_container).visibility = if (tasksSwitch.isChecked) View.VISIBLE else View.GONE


        // Outline: Def False (Renamed from Glow)
        bindToggle(R.id.section_outline, getString(R.string.section_outline), "show_outline", false)

        // Light Theme: Def False
        bindToggle(R.id.section_theme, getString(R.string.section_theme), "use_light_theme", false)

        // Transparent Background: Def False
        bindToggle(R.id.section_transparent, getString(R.string.section_transparent), "transparent_background", false)

        // Font Style: Def "Default" (0)
        bindSelector(R.id.section_font, getString(R.string.section_font), "font_style", listOf(
            getString(R.string.font_default), getString(R.string.font_serif), getString(R.string.font_monospace), getString(R.string.font_cursive), 
            getString(R.string.font_condensed), getString(R.string.font_condensed_light), getString(R.string.font_light), getString(R.string.font_medium), 
            getString(R.string.font_black), getString(R.string.font_thin), getString(R.string.font_smallcaps)
        ), 0)
        
        // Enforce limit initially
        updateToggleAvailability()
    }

    private fun bindSection(
        sectionId: Int, 
        title: String, 
        prefShowKey: String, 
        defShow: Boolean, 
        prefSizeKey: String, 
        defSize: Float,
        minSize: Float,
        maxSize: Float,
        isContent: Boolean = false
    ): SwitchMaterial {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val switch = section.findViewById<SwitchMaterial>(R.id.item_switch)
        val sizeContainer = section.findViewById<View>(R.id.size_container)
        val slider = section.findViewById<Slider>(R.id.item_slider)
        val tvSize = section.findViewById<TextView>(R.id.size_label)

        tvTitle.text = title

        if (isContent) {
            contentSwitches.add(switch)
        }

        // Load Toggle
        val isShown = prefs.getBoolean(prefShowKey, defShow)
        switch.isChecked = isShown
        sizeContainer.visibility = if (isShown) View.VISIBLE else View.GONE

        // Common Listener (can be overridden returned switch)
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkLimit()) {
                switch.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(prefShowKey, isChecked).apply()
            sizeContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWidget()
            if (isContent) updateToggleAvailability()
        }

        // Load Slider
        val currentSize = prefs.getFloat(prefSizeKey, defSize)
        slider.valueFrom = minSize
        slider.valueTo = maxSize
        slider.value = currentSize.coerceIn(minSize, maxSize)
        tvSize.text = "${currentSize.toInt()}"

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                tvSize.text = "${value.toInt()}"
                prefs.edit().putFloat(prefSizeKey, value).apply()
                // Debounce update? For now live is fine
                updateWidget() 
            }
        }
        
        return switch
    }

    private fun bindToggle(
        sectionId: Int,
        title: String,
        prefShowKey: String,
        defShow: Boolean,
        isContent: Boolean = false
    ) {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val switch = section.findViewById<SwitchMaterial>(R.id.item_switch)
        val sizeContainer = section.findViewById<View>(R.id.size_container)

        tvTitle.text = title
        sizeContainer.visibility = View.GONE

        if (isContent) {
            contentSwitches.add(switch)
        }

        // Load Toggle
        val isShown = prefs.getBoolean(prefShowKey, defShow)
        switch.isChecked = isShown

        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkLimit()) {
                switch.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(prefShowKey, isChecked).apply()
            updateWidget()
            if (isContent) updateToggleAvailability()
        }
    }

    private fun bindSelector(
        sectionId: Int,
        title: String,
        prefKey: String,
        options: List<String>,
        defaultIdx: Int
    ) {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val autoCompleteTextView = section.findViewById<android.widget.AutoCompleteTextView>(R.id.item_value)

        tvTitle.text = title
        
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options)
        autoCompleteTextView.setAdapter(adapter)

        val currentIdx = prefs.getInt(prefKey, defaultIdx)
        autoCompleteTextView.setText(options.getOrElse(currentIdx) { options[defaultIdx] }, false)

        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putInt(prefKey, position).apply()
            updateWidget()
        }
    }

    private fun checkLimit(): Boolean {
        val activeCount = contentSwitches.count { it.isChecked }
        
        if (activeCount > 5) {
            com.google.android.material.snackbar.Snackbar.make(
                findViewById(R.id.fab_update), 
                getString(R.string.error_max_items), 
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
            return false
        }

        // Subset Limit: Battery, Temp, Data (Max 2)
        val subsetCount = contentSwitches.count { 
            it.isChecked && (it.tag == "battery" || it.tag == "temp" || it.tag == "data") 
        }
        
        if (subsetCount > 2) {
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
        val mode = appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
            android.os.Process.myUid(), packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun updateToggleAvailability() {
        val activeCount = contentSwitches.count { it.isChecked }
        // Block others only if we reached 5
        val isLimitReached = activeCount >= 5
        
        for (switch in contentSwitches) {
            if (!switch.isChecked) {
                // If limit maxed out, disable remaining
                switch.isEnabled = !isLimitReached
                switch.alpha = if (isLimitReached) 0.5f else 1.0f
            } else {
                switch.isEnabled = true
                switch.alpha = 1.0f
            }
        }
    }

    private fun updateWidget() {
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
