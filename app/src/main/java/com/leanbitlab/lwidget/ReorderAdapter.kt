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

import android.view.MotionEvent
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Data class for reorderable items
data class ReorderItem(
    val key: String,          // e.g. "show_battery"
    val label: String,        // e.g. "Battery"
    var enabled: Boolean
)

// Adapter for reorder RecyclerView
class ReorderAdapter(
    private val items: MutableList<ReorderItem>,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ReorderAdapter.ViewHolder>() {

    class ViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view) {
        val handle: android.widget.ImageView = view.findViewById(R.id.reorder_handle)
        val name: TextView = view.findViewById(R.id.reorder_item_name)
        val status: TextView? = view.findViewById(R.id.reorder_item_status)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.settings_reorder_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        if (item.enabled) {
            holder.view.alpha = 1.0f
            holder.status?.visibility = android.view.View.GONE
        } else {
            holder.view.alpha = 0.4f
            holder.status?.visibility = android.view.View.VISIBLE
        }

        holder.handle.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                onStartDrag(holder)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount() = items.size

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }
}
