package com.leanbitlab.lwidget

import org.junit.Assert.assertEquals
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
        ReorderAdapter(list) {}.moveItem(0, 2)

        assertEquals(listOf("show_steps", "show_ram", "show_battery"), list.map { it.key })
    }

    @Test
    fun moveItemReordersBackward() {
        val list = items()
        ReorderAdapter(list) {}.moveItem(2, 0)

        assertEquals(listOf("show_ram", "show_battery", "show_steps"), list.map { it.key })
    }

    @Test
    fun moveItemToSameIndexIsNoOp() {
        val list = items()
        ReorderAdapter(list) {}.moveItem(1, 1)

        assertEquals(listOf("show_battery", "show_steps", "show_ram"), list.map { it.key })
    }

    @Test
    fun itemCountMatchesList() {
        assertEquals(3, ReorderAdapter(items()) {}.itemCount)
    }
}
