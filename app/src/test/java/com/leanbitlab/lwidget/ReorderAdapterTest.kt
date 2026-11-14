package com.leanbitlab.lwidget

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReorderAdapterTest {

    private fun items() = mutableListOf(
        ReorderItem("show_battery", "Battery", true),
        ReorderItem("show_steps", "Steps", true),
        ReorderItem("show_ram", "RAM", false),
    )

    @Test
    fun moveItemReordersForward() {
        val list = items()
        ReorderAdapter(list, {}).moveItem(0, 2)

        assertEquals(listOf("show_steps", "show_ram", "show_battery"), list.map { it.key })
    }

    @Test
    fun moveItemReordersBackward() {
        val list = items()
        ReorderAdapter(list, {}).moveItem(2, 0)

        assertEquals(listOf("show_ram", "show_battery", "show_steps"), list.map { it.key })
    }

    @Test
    fun moveItemToSameIndexIsNoOp() {
        val list = items()
        ReorderAdapter(list, {}).moveItem(1, 1)

        assertEquals(listOf("show_battery", "show_steps", "show_ram"), list.map { it.key })
    }

    @Test
    fun itemCountMatchesList() {
        assertEquals(3, ReorderAdapter(items(), {}).itemCount)
    }

    private fun attach(list: MutableList<ReorderItem>, onChanged: () -> Unit): RecyclerView {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val ctx = ContextThemeWrapper(base, R.style.Theme_Lwidget)
        return RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = ReorderAdapter(list, {}, onChanged)
            measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 1000, 2000)
        }
    }

    @Test
    fun accessibilityMoveDownReordersAndNotifies() {
        val list = items()
        var changed = 0
        val rv = attach(list) { changed++ }

        val handled = rv.getChildAt(0).performAccessibilityAction(R.id.a11y_action_move_down, null)

        assertTrue(handled)
        assertEquals(listOf("show_steps", "show_battery", "show_ram"), list.map { it.key })
        assertEquals(1, changed)
    }

    @Test
    fun accessibilityMoveUpOnFirstItemIsRejected() {
        val list = items()
        var changed = 0
        val rv = attach(list) { changed++ }

        val handled = rv.getChildAt(0).performAccessibilityAction(R.id.a11y_action_move_up, null)

        assertFalse(handled)
        assertEquals(listOf("show_battery", "show_steps", "show_ram"), list.map { it.key })
        assertEquals(0, changed)
    }
}
