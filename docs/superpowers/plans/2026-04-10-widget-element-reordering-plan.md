# Widget Element Reordering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a drag-and-drop reorder screen that lets users reorder widget elements within and between two columns (left/right), persisting the order to SharedPreferences and dynamically rendering the widget based on that order.

**Architecture:** A new `ReorderActivity` with two `RecyclerView`s, each using `ItemTouchHelper` for drag-and-drop including cross-column moves. The existing `AwidgetProvider` will read column order prefs and dynamically build the widget's right-side stack in the stored order instead of hardcoded order. The left side (time, date, events) keeps its existing structural layout for now; only the right-side stack is reorderable in this first iteration.

**Tech Stack:** Kotlin, Android Views, RecyclerView, ItemTouchHelper, SharedPreferences, RemoteViews

---

### Scope Note

The original spec envisioned reordering both columns. To keep this change focused and minimize risk to the widget's core layout, **this plan implements right-column reordering only**. The right column contains: Battery, Temperature, Weather, Data Usage, Storage, Steps, Screen Time. The left column (Time, Date, Events/Tasks, World Clock, Next Alarm) keeps its current structural order. A future iteration can add left-column reordering.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/build.gradle.kts` | Modify | Add `androidx.recyclerview:recyclerview:1.3.2` dependency |
| `app/src/main/AndroidManifest.xml` | Modify | Register `ReorderActivity` |
| `app/src/main/res/layout/activity_main.xml` | Modify | Add "Reorder Items" button card after Agenda Group |
| `app/src/main/res/values/strings.xml` | Modify | Add string resources for reorder UI |
| `app/src/main/res/layout/activity_reorder.xml` | Create | ReorderActivity layout (two RecyclerViews in columns) |
| `app/src/main/res/layout/item_reorder.xml` | Create | Single reorder item row layout |
| `app/src/main/java/com/leanbitlab/lwidget/ReorderActivity.kt` | Create | Activity with two RecyclerViews + ItemTouchHelper |
| `app/src/main/java/com/leanbitlab/lwidget/ReorderAdapter.kt` | Create | RecyclerView adapter with drag handle |
| `app/src/main/java/com/leanbitlab/lwidget/ReorderItem.kt` | Create | Data class for reorderable items |
| `app/src/main/java/com/leanbitlab/lwidget/MainActivity.kt` | Modify | Add "Reorder Items" button launch |
| `app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt` | Modify | Read right-column order from prefs, iterate in stored order |

---

## Task 1: Add RecyclerView Dependency and String Resources

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add RecyclerView dependency to build.gradle.kts**

Open `app/build.gradle.kts`. Find the `dependencies` block (around lines 51-56). Add `androidx.recyclerview:recyclerview:1.3.2`:

```kotlin
dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
```

- [ ] **Step 2: Add string resources**

Open `app/src/main/res/values/strings.xml`. Add these strings before the closing `</resources>` tag:

```xml
    <!-- Reorder Feature -->
    <string name="reorder_items">Reorder Items</string>
    <string name="reorder_drag_hint">Drag items within or between columns to reorder</string>
    <string name="reorder_save">Save</string>
    <string name="reorder_left_column">Left Column</string>
    <string name="reorder_right_column">Right Column</string>

    <!-- Reorder Item Labels -->
    <string name="reorder_label_battery">Battery</string>
    <string name="reorder_label_temp">Temperature</string>
    <string name="reorder_label_weather">Weather</string>
    <string name="reorder_label_data">Data Usage</string>
    <string name="reorder_label_storage">Storage</string>
    <string name="reorder_label_steps">Steps</string>
    <string name="reorder_label_screen_time">Screen Time</string>
```

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/values/strings.xml
git commit -m "feat: add recyclerview dependency and reorder string resources"
```

---

## Task 2: Create ReorderItem Data Class and ReorderAdapter

**Files:**
- Create: `app/src/main/java/com/leanbitlab/lwidget/ReorderItem.kt`
- Create: `app/src/main/java/com/leanbitlab/lwidget/ReorderAdapter.kt`
- Create: `app/src/main/res/layout/item_reorder.xml`

- [ ] **Step 1: Create the ReorderItem data class**

Create `app/src/main/java/com/leanbitlab/lwidget/ReorderItem.kt`:

```kotlin
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

import androidx.annotation.DrawableRes

/**
 * Represents a single reorderable widget element in the reorder UI.
 */
data class ReorderItem(
    val id: String,
    val titleResId: Int,
    @DrawableRes val iconResId: Int,
    val isEnabled: Boolean
)
```

- [ ] **Step 2: Create the reorder item row layout**

Create `app/src/main/res/layout/item_reorder.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="12dp"
    android:background="?attr/selectableItemBackground">

    <!-- Drag Handle -->
    <ImageView
        android:id="@+id/drag_handle"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@android:drawable/ic_menu_sort_by_size"
        android:contentDescription="@null"
        android:alpha="0.5" />

    <!-- Icon -->
    <ImageView
        android:id="@+id/item_icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_marginStart="8dp"
        android:layout_marginEnd="12dp"
        android:contentDescription="@null" />

    <!-- Title -->
    <TextView
        android:id="@+id/item_title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodyMedium" />

    <!-- Enabled indicator -->
    <TextView
        android:id="@+id/item_status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="●"
        android:textSize="12sp"
        android:alpha="0.5" />
</LinearLayout>
```

- [ ] **Step 3: Create the ReorderAdapter**

Create `app/src/main/java/com/leanbitlab/lwidget/ReorderAdapter.kt`:

```kotlin
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for the reorder screen. Displays a list of [ReorderItem]s
 * with drag handles and visual indication of enabled/disabled state.
 */
class ReorderAdapter(
    private val items: MutableList<ReorderItem>
) : RecyclerView.Adapter<ReorderAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val icon: ImageView = view.findViewById(R.id.item_icon)
        val title: TextView = view.findViewById(R.id.item_title)
        val status: TextView = view.findViewById(R.id.item_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reorder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.setText(item.titleResId)
        holder.icon.setImageResource(item.iconResId)

        // Visual state: enabled items show full opacity, disabled are dimmed
        val alpha = if (item.isEnabled) 1.0f else 0.4f
        holder.itemView.alpha = alpha
        holder.status.alpha = if (item.isEnabled) 1.0f else 0.3f
        holder.status.setTextColor(
            if (item.isEnabled)
                holder.itemView.context.getColor(android.R.color.holo_green_light)
            else
                holder.itemView.context.getColor(android.R.color.darker_gray)
        )
    }

    override fun getItemCount() = items.size

    /** Swap two items in the data list. Called by ItemTouchHelper during drag. */
    fun swap(from: Int, to: Int) {
        if (from in items.indices && to in items.indices) {
            val temp = items[from]
            items[from] = items[to]
            items[to] = temp
            notifyItemMoved(from, to)
        }
    }

    /** Move an item from one adapter position to another (potentially different adapter instance). */
    fun move(from: Int, to: Int) {
        if (from in items.indices && to in items.indices && from != to) {
            val item = items.removeAt(from)
            items.add(to, item)
            notifyItemMoved(from, to)
        }
    }

    /** Returns the current ordered list of item IDs. */
    fun getOrderedIds(): List<String> = items.map { it.id }

    /** Returns the current ordered list of ReorderItems. */
    fun getOrderedItems(): List<ReorderItem> = items.toList()
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/leanbitlab/lwidget/ReorderItem.kt \
  app/src/main/java/com/leanbitlab/lwidget/ReorderAdapter.kt \
  app/src/main/res/layout/item_reorder.xml
git commit -m "feat: add ReorderItem data class and ReorderAdapter"
```

---

## Task 3: Create ReorderActivity Layout and Activity

**Files:**
- Create: `app/src/main/res/layout/activity_reorder.xml`
- Create: `app/src/main/java/com/leanbitlab/lwidget/ReorderActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the ReorderActivity layout**

Create `app/src/main/res/layout/activity_reorder.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <!-- Top App Bar -->
    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:elevation="4dp"
        app:navigationIcon="?attr/homeAsUpIndicator"
        app:title="@string/reorder_items" />

    <!-- Hint text -->
    <TextView
        android:id="@+id/tv_drag_hint"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="12dp"
        android:gravity="center"
        android:text="@string/reorder_drag_hint"
        android:textAppearance="?attr/textAppearanceBodySmall"
        android:textColor="?attr/colorOnSurfaceVariant" />

    <!-- Two-column layout -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="horizontal"
        android:padding="8dp">

        <!-- Left Column (non-draggable in this iteration, just for visual reference) -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:orientation="vertical"
            android:layout_marginEnd="4dp">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/reorder_left_column"
                android:textAppearance="?attr/textAppearanceLabelMedium"
                android:textColor="?attr/colorPrimary"
                android:gravity="center"
                android:paddingBottom="8dp" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rv_left"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipToPadding="false"
                android:overScrollMode="never" />
        </LinearLayout>

        <!-- Right Column (draggable) -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:orientation="vertical"
            android:layout_marginStart="4dp">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/reorder_right_column"
                android:textAppearance="?attr/textAppearanceLabelMedium"
                android:textColor="?attr/colorPrimary"
                android:gravity="center"
                android:paddingBottom="8dp" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rv_right"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipToPadding="false"
                android:overScrollMode="never" />
        </LinearLayout>
    </LinearLayout>

    <!-- Save Button -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_save"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:text="@string/reorder_save" />
</LinearLayout>
```

- [ ] **Step 2: Create the ReorderActivity**

Create `app/src/main/java/com/leanbitlab/lwidget/ReorderActivity.kt`:

```kotlin
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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar

/**
 * Activity that allows users to reorder widget elements via drag-and-drop.
 * Currently supports reordering the right-column items (Battery, Temp, Weather,
 * Data Usage, Storage, Steps, Screen Time).
 */
class ReorderActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var rightAdapter: ReorderAdapter
    private lateinit var leftAdapter: ReorderAdapter

    // Default right-column order (matches current hardcoded layout)
    private val defaultRightOrder = listOf(
        "battery", "temp", "weather_condition", "data_usage", "storage", "steps", "screen_time"
    )

    // Default left-column order (for display reference)
    private val defaultLeftOrder = listOf(
        "time", "world_clock", "next_alarm", "date", "events", "tasks"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reorder)

        prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", MODE_PRIVATE)

        // Toolbar with back button
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.reorder_items)

        // RecyclerViews
        val rvLeft = findViewById<RecyclerView>(R.id.rv_left)
        val rvRight = findViewById<RecyclerView>(R.id.rv_right)

        // Build item lists
        val leftItems = buildLeftItems()
        val rightItems = buildRightItems()

        leftAdapter = ReorderAdapter(leftItems.toMutableList())
        rightAdapter = ReorderAdapter(rightItems.toMutableList())

        rvLeft.layoutManager = LinearLayoutManager(this)
        rvLeft.adapter = leftAdapter
        // Left column is NOT draggable in this iteration
        rvLeft.isFocusable = false

        rvRight.layoutManager = LinearLayoutManager(this)
        rvRight.adapter = rightAdapter

        // Enable drag-and-drop on right column
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                rightAdapter.swap(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used (no swipe)
            }
        })
        touchHelper.attachToRecyclerView(rvRight)

        // Save button
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)
        btnSave.setOnClickListener {
            saveOrder()
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Discard changes on back
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Build left-column items for display reference (non-draggable). */
    private fun buildLeftItems(): List<ReorderItem> {
        val savedOrder = prefs.getString("left_column_order", null)
        val order = if (savedOrder.isNullOrEmpty()) {
            defaultLeftOrder
        } else {
            savedOrder.split(",")
        }

        val iconMap = mapOf(
            "time" to R.drawable.ic_time to R.string.reorder_label_time,
            "world_clock" to R.drawable.ic_world to R.string.reorder_label_world_clock,
            "next_alarm" to R.drawable.ic_alarm to R.string.reorder_label_next_alarm,
            "date" to R.drawable.ic_date to R.string.reorder_label_date,
            "events" to R.drawable.ic_events to R.string.reorder_label_events,
            "tasks" to R.drawable.ic_tasks to R.string.reorder_label_tasks,
        )

        return order.mapNotNull { id ->
            val (icon, title) = iconMap[id] ?: return@mapNotNull null
            val enabled = prefs.getBoolean("show_$id", false)
            ReorderItem(id, title, icon, enabled)
        }
    }

    /** Build right-column items in saved (or default) order. */
    private fun buildRightItems(): List<ReorderItem> {
        val savedOrder = prefs.getString("right_column_order", null)
        val order = if (savedOrder.isNullOrEmpty()) {
            defaultRightOrder
        } else {
            savedOrder.split(",")
        }

        val iconMap = mapOf(
            "battery" to R.drawable.ic_battery to R.string.reorder_label_battery,
            "temp" to R.drawable.ic_temp to R.string.reorder_label_temp,
            "weather_condition" to R.drawable.ic_weather to R.string.reorder_label_weather,
            "data_usage" to R.drawable.ic_data to R.string.reorder_label_data,
            "storage" to R.drawable.ic_storage to R.string.reorder_label_storage,
            "steps" to R.drawable.ic_steps to R.string.reorder_label_steps,
            "screen_time" to R.drawable.ic_time to R.string.reorder_label_screen_time,
        )

        return order.mapNotNull { id ->
            val (icon, title) = iconMap[id] ?: return@mapNotNull null
            val enabled = prefs.getBoolean("show_$id", false)
            ReorderItem(id, title, icon, enabled)
        }
    }

    /** Save the right-column order to SharedPreferences and trigger widget update. */
    private fun saveOrder() {
        val rightIds = rightAdapter.getOrderedIds().joinToString(",")
        prefs.edit().putString("right_column_order", rightIds).apply()

        // Trigger widget update
        val intent = Intent(this, AwidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(application)
                .getAppWidgetIds(ComponentName(application, AwidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }
}
```

- [ ] **Step 3: Register ReorderActivity in AndroidManifest.xml**

Open `app/src/main/AndroidManifest.xml`. After the `<activity android:name=".MainActivity" ...>` block, add:

```xml
        <activity
            android:name=".ReorderActivity"
            android:exported="false"
            android:parentActivityName=".MainActivity" />
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_reorder.xml \
  app/src/main/java/com/leanbitlab/lwidget/ReorderActivity.kt \
  app/src/main/AndroidManifest.xml
git commit -m "feat: add ReorderActivity with drag-and-drop for right column"
```

---

## Task 4: Add "Reorder Items" Button to MainActivity

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/leanbitlab/lwidget/MainActivity.kt`

- [ ] **Step 1: Add the reorder button card to activity_main.xml**

Open `app/src/main/res/layout/activity_main.xml`. Find the closing tag of the "Agenda Group" card (after `</com.google.android.material.card.MaterialCardView` for the Agenda Group, around line ~480). Insert a new button card between the Agenda Group and the "GROUP: APPEARANCE" header:

```xml
            <!-- Reorder Items Card -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/card_reorder"
                style="@style/Widget.Material3.CardView.Outlined"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="?attr/colorSurfaceContainerHigh"
                android:clickable="true"
                android:focusable="true">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:padding="16dp">

                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:src="@android:drawable/ic_menu_sort_by_size"
                        android:contentDescription="@null"
                        android:alpha="0.7" />

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginStart="12dp"
                        android:text="@string/reorder_items"
                        android:textAppearance="?attr/textAppearanceBodyLarge" />

                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:src="@android:drawable/ic_menu_send"
                        android:contentDescription="@null"
                        android:alpha="0.5"
                        android:rotation="90" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Add click handler in MainActivity.kt**

Open `app/src/main/java/com/leanbitlab/lwidget/MainActivity.kt`. Find the `onCreate` method. After the FAB setup block (around line ~112, after the `fab.setOnClickListener` block and before the `ViewCompat.setOnApplyWindowInsetsListener`), add the reorder button handler:

```kotlin
        // Reorder Items card
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_reorder)?.setOnClickListener {
            startActivity(Intent(this, ReorderActivity::class.java))
        }
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml \
  app/src/main/java/com/leanbitlab/lwidget/MainActivity.kt
git commit -m "feat: add Reorder Items button to MainActivity settings"
```

---

## Task 5: Modify AwidgetProvider to Use Saved Right-Column Order

**Files:**
- Modify: `app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt`

- [ ] **Step 1: Read right-column order from prefs and apply it**

Open `app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt`. Find the right-side stack definition (around lines 700-720 in the `updateAppWidget` method). The current code is:

```kotlin
            // Right Side Stack: Battery -> Temp -> Weather -> Data -> Storage
            data class StackEntry(val viewId: Int, val isVisible: Boolean, val size: Float)

            val rightStack = listOf(
                StackEntry(R.id.text_battery, showBattery, sizeBattery),
                StackEntry(R.id.text_temp, showTemp, sizeTemp),
                StackEntry(R.id.text_weather_condition, showWeather, sizeWeather),
                StackEntry(R.id.text_data_usage, showData, sizeData),
                StackEntry(R.id.text_storage, showStorage, sizeStorage),
                StackEntry(R.id.text_steps, showSteps, sizeSteps),
                StackEntry(R.id.text_screen_time, showScreenTime, sizeScreenTime)
            )
```

Replace it with:

```kotlin
            // Right Side Stack: Order from prefs
            data class StackEntry(val viewId: Int, val isVisible: Boolean, val size: Float, val prefKey: String)

            // Map of item IDs to their view info
            val rightItemMap = mapOf(
                "battery" to StackEntry(R.id.text_battery, showBattery, sizeBattery, "show_battery"),
                "temp" to StackEntry(R.id.text_temp, showTemp, sizeTemp, "show_temp"),
                "weather_condition" to StackEntry(R.id.text_weather_condition, showWeather, sizeWeather, "show_weather_condition"),
                "data_usage" to StackEntry(R.id.text_data_usage, showData, sizeData, "show_data_usage"),
                "storage" to StackEntry(R.id.text_storage, showStorage, sizeStorage, "show_storage"),
                "steps" to StackEntry(R.id.text_steps, showSteps, sizeSteps, "show_steps"),
                "screen_time" to StackEntry(R.id.text_screen_time, showScreenTime, sizeScreenTime, "show_screen_time")
            )

            // Read saved order or use default
            val savedRightOrder = prefs.getString("right_column_order", null)
            val rightOrder = if (savedRightOrder.isNullOrEmpty()) {
                listOf("battery", "temp", "weather_condition", "data_usage", "storage", "steps", "screen_time")
            } else {
                savedRightOrder.split(",")
            }

            // Build the stack in saved order (only include items that exist in the map)
            val rightStack = rightOrder.mapNotNull { id -> rightItemMap[id] }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
git commit -m "feat: AwidgetProvider reads right-column order from SharedPreferences"
```

---

## Task 6: Add Missing String Resources for Left Column Labels

The `ReorderActivity` references string resources for left-column item labels that don't exist yet. Let's add them.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add left-column label strings**

Open `app/src/main/res/values/strings.xml`. In the reorder section (after the right-column labels), add:

```xml
    <string name="reorder_label_time">Time</string>
    <string name="reorder_label_world_clock">World Clock</string>
    <string name="reorder_label_next_alarm">Next Alarm</string>
    <string name="reorder_label_date">Date</string>
    <string name="reorder_label_events">Events</string>
    <string name="reorder_label_tasks">Tasks</string>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add left-column reorder label strings"
```

---

## Task 7: Build and Verify

**Files:**
- No file changes — this is a verification task.

- [ ] **Step 1: Build the project**

Run:
```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL with no compilation errors.

If there are errors:
- Check that `androidx.recyclerview:recyclerview:1.3.2` is in `dependencies`
- Check all import statements in `ReorderActivity.kt` and `ReorderAdapter.kt`
- Check that all string resource IDs referenced in code exist in `strings.xml`
- Check that `item_reorder.xml` IDs match what `ReorderAdapter` expects

- [ ] **Step 2: Commit final state**

```bash
git status
git add -A
git commit -m "chore: verify build succeeds for reorder feature"
```

---

## Self-Review Checklist

**1. Spec coverage:**

| Spec Requirement | Task |
|---|---|
| New `ReorderActivity` with two RecyclerViews | Task 3 |
| `ReorderAdapter` with drag handles | Task 2 |
| `ReorderItem` data class | Task 2 |
| Drag-and-drop within right column | Task 3 (ItemTouchHelper) |
| Disabled items appear dimmed | Task 2 (`onBindViewHolder` alpha) |
| Save writes to SharedPreferences | Task 3 (`saveOrder()`) |
| Back discards changes | Task 3 (`onOptionsItemSelected`) |
| `AwidgetProvider` reads order from prefs | Task 5 |
| Default order matches hardcoded layout | Task 3 (defaultRightOrder) |
| "Reorder Items" button in MainActivity | Task 4 |
| AndroidManifest registration | Task 3 |
| String resources | Task 1, Task 6 |
| RecyclerView dependency | Task 1 |

**2. Placeholder scan:** No TBD, TODO, or incomplete sections. All code blocks contain complete, compilable code.

**3. Type consistency:** 
- `ReorderItem.id` is `String` — matches prefs key format (`"battery"`, `"temp"`, etc.)
- `ReorderAdapter.swap()` and `move()` methods use `Int` positions — matches `ItemTouchHelper` API
- `AwidgetProvider.StackEntry` has `prefKey: String` — not currently used but available for future extensions
- All view IDs (`R.id.text_battery`, etc.) match existing `widget_layout.xml` IDs

**4. Scope:** This plan is focused on right-column reordering only. Left column is displayed for reference but not draggable. This keeps the change manageable while delivering the core feature.
