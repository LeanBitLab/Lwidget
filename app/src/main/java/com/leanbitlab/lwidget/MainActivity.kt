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
        // Time: Def True, 48sp
        // Time: Def True, 48sp, Max 120sp
        bindSection(R.id.section_time, "Time", "show_time", true, "size_time", 48f, 12f, 120f)
        
        // Date: Def True, 14sp, Max 64sp
        bindSection(R.id.section_date, "Date", "show_date", true, "size_date", 14f, 10f, 64f)
        
        // Battery: Def True, 48sp, Max 120sp
        bindSection(R.id.section_battery, "Battery", "show_battery", true, "size_battery", 48f, 12f, 120f)
        
        // Temp: Def True, 18sp, Max 64sp
        bindSection(R.id.section_temp, "Temperature", "show_temp", true, "size_temp", 18f, 10f, 64f)

        // Events: Def True, 14sp, Max 48sp
        bindSection(R.id.section_events, "Events", "show_events", true, "size_events", 14f, 10f, 48f)

        // Outline Glow: Def False
        bindToggle(R.id.section_outline, "Outline Glow", "show_outline", false)

        // Light Theme: Def False
        bindToggle(R.id.section_theme, "Light Theme", "use_light_theme", false)
    }

    private fun bindSection(
        sectionId: Int, 
        title: String, 
        prefShowKey: String, 
        defShow: Boolean, 
        prefSizeKey: String, 
        defSize: Float,
        minSize: Float,
        maxSize: Float
    ) {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val switch = section.findViewById<SwitchMaterial>(R.id.item_switch)
        val slider = section.findViewById<Slider>(R.id.item_slider)
        val tvSize = section.findViewById<TextView>(R.id.size_label)
        val sizeContainer = section.findViewById<View>(R.id.size_container)

        tvTitle.text = title

        // Load Toggle
        val isShown = prefs.getBoolean(prefShowKey, defShow)
        switch.isChecked = isShown
        sizeContainer.visibility = if (isShown) View.VISIBLE else View.GONE

        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefShowKey, isChecked).apply()
            sizeContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWidget() // Live update
        }

        // Load Slider
        val currentSize = prefs.getFloat(prefSizeKey, defSize)
        slider.valueFrom = minSize
        slider.valueTo = maxSize
        slider.value = currentSize.coerceIn(minSize, maxSize)
        tvSize.text = "Size: ${currentSize.toInt()}sp"

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                tvSize.text = "Size: ${value.toInt()}sp"
                prefs.edit().putFloat(prefSizeKey, value).apply()
                updateWidget() // Live update
            }
        }
    }

    private fun bindToggle(
        sectionId: Int,
        title: String,
        prefShowKey: String,
        defShow: Boolean
    ) {
        val section = findViewById<View>(sectionId)
        val tvTitle = section.findViewById<TextView>(R.id.item_title)
        val switch = section.findViewById<SwitchMaterial>(R.id.item_switch)
        val sizeContainer = section.findViewById<View>(R.id.size_container)

        tvTitle.text = title
        sizeContainer.visibility = View.GONE

        // Load Toggle
        val isShown = prefs.getBoolean(prefShowKey, defShow)
        switch.isChecked = isShown

        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefShowKey, isChecked).apply()
            updateWidget() // Live update
        }
    }

    private fun updateWidget() {
        // Animation: Subtle Outline Shine
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab_update)
        
        // Get dynamic colors
        val colorSurface = com.google.android.material.color.MaterialColors.getColor(fab, com.google.android.material.R.attr.colorSurface)
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
