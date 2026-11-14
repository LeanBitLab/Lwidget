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

import java.util.Locale
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

internal fun MainActivity.bindReorderSection() {
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
            else list.add(ReorderItem(key, key.replace("show_", "").replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }, prefs.getBoolean(key, false)))
        }
        // Add any new items not in saved order
        defaultOrder.forEach { default ->
            if (!list.any { it.key == default.key }) list.add(default)
        }
        list
    }

    val recyclerView = findViewById<RecyclerView>(R.id.reorder_recycler)
    var itemTouchHelper: ItemTouchHelper? = null
    var orderHasChanged = false

    val adapter = ReorderAdapter(
        items,
        onStartDrag = { viewHolder ->
            itemTouchHelper?.startDrag(viewHolder)
        }
    )
    recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    recyclerView.adapter = adapter
    (recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false

    val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
        androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
    ) {
        override fun isLongPressDragEnabled(): Boolean = false
        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val fromPos = viewHolder.adapterPosition
            val toPos = target.adapterPosition
            if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                adapter.moveItem(fromPos, toPos)
                orderHasChanged = true
                rv.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.translationZ = 24f
                var parent = recyclerView.parent
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    parent = parent.parent
                }
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.translationZ = 0f
            var parent = recyclerView.parent
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(false)
                parent = parent.parent
            }
            val pos = viewHolder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < items.size) {
                viewHolder.itemView.alpha = if (items[pos].enabled) 1.0f else 0.4f
            }
            if (orderHasChanged) {
                orderHasChanged = false
                val orderStr = items.joinToString(",") { it.key }
                prefs.edit().putString("widget_right_column_order", orderStr).apply()
                updateWidget()
                updateLivePreview()
            }
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long
        ): Int {
            val standardSpeed = super.interpolateOutOfBoundsScroll(
                recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll
            )
            return if (standardSpeed != 0) standardSpeed / 2 else 0
        }
    }
    itemTouchHelper = ItemTouchHelper(callback).also {
        it.attachToRecyclerView(recyclerView)
    }
}
