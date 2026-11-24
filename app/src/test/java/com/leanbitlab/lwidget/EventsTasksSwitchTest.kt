package com.leanbitlab.lwidget

import android.Manifest
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ViewFlipper
import androidx.test.core.app.ApplicationProvider
import com.leanbitlab.lwidget.render.WidgetRenderer
import com.leanbitlab.lwidget.tasks.TasksFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EventsTasksSwitchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val widgetId = 7

    @Before
    fun setUp() {
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            TasksFetcher.PERMISSION_READ_TASKS_ORG
        )
        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS_$widgetId", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun setPrefs(showEvents: Boolean, showTasks: Boolean, page: Int = 0) {
        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE).edit()
            .putBoolean("show_events", showEvents)
            .putBoolean("show_tasks", showTasks)
            .putInt("events_page", page)
            .commit()
    }

    private fun render(mode: UpdateMode = UpdateMode.FULL): View {
        val views = WidgetRenderer.buildRemoteViews(context, widgetId, mode)
        return views.apply(context, FrameLayout(context))
    }

    @Test
    fun selectionShowsOnePageAtATime() {
        assertEquals(true to false, WidgetRenderer.eventsTasksSelection(true, true, 0))
        assertEquals(false to true, WidgetRenderer.eventsTasksSelection(true, true, 1))
        assertEquals(true to false, WidgetRenderer.eventsTasksSelection(true, false, 1))
        assertEquals(false to true, WidgetRenderer.eventsTasksSelection(false, true, 0))
        assertEquals(false to false, WidgetRenderer.eventsTasksSelection(false, false, 0))
    }

    private fun flipper(root: View) = root.findViewById<ViewFlipper>(R.id.events_flipper)

    @Test
    fun switchShowsWhenBothAreOnAndFlipperFollowsTheSavedPage() {
        setPrefs(showEvents = true, showTasks = true, page = 0)
        val onEvents = render()
        assertEquals(View.VISIBLE, onEvents.findViewById<View>(R.id.button_events_switch).visibility)
        assertTrue(onEvents.findViewById<View>(R.id.button_events_switch).hasOnClickListeners())
        assertEquals(0, flipper(onEvents).displayedChild)

        setPrefs(showEvents = true, showTasks = true, page = 1)
        val onTasks = render()
        assertEquals(View.VISIBLE, onTasks.findViewById<View>(R.id.button_events_switch).visibility)
        assertEquals(1, flipper(onTasks).displayedChild)
    }

    @Test
    fun switchIsHiddenAndTheRightPageShowsWhenOnlyOneIsOn() {
        setPrefs(showEvents = true, showTasks = false, page = 1)
        val eventsOnly = render()
        assertEquals(View.GONE, eventsOnly.findViewById<View>(R.id.button_events_switch).visibility)
        assertEquals(0, flipper(eventsOnly).displayedChild)

        setPrefs(showEvents = false, showTasks = true, page = 0)
        val tasksOnly = render()
        assertEquals(View.GONE, tasksOnly.findViewById<View>(R.id.button_events_switch).visibility)
        assertEquals(1, flipper(tasksOnly).displayedChild)
    }

    @Test
    fun eachPageHasItsOwnLines() {
        val root = render()
        val events = root.findViewById<View>(R.id.events_page)
        val tasks = root.findViewById<View>(R.id.tasks_page)
        assertTrue(events.findViewById<View>(R.id.text_event_1) != null)
        assertTrue(events.findViewById<View>(R.id.text_task_1) == null)
        assertTrue(tasks.findViewById<View>(R.id.text_task_1) != null)
        assertTrue(tasks.findViewById<View>(R.id.text_event_1) == null)
        // The switch is pinned to the widget bottom, outside both pages.
        assertTrue(events.findViewById<View>(R.id.button_events_switch) == null)
        assertTrue(tasks.findViewById<View>(R.id.button_events_switch) == null)
        assertTrue(root.findViewById<View>(R.id.button_events_switch) != null)
    }

    @Test
    fun eventsPageUpdateOnlyFlipsThePage() {
        setPrefs(showEvents = true, showTasks = true, page = 1)
        val block = render(UpdateMode.EVENTS_PAGE)
        assertEquals(1, flipper(block).displayedChild)
        assertTrue(block.findViewById<View>(R.id.events_container).hasOnClickListeners())
    }
}
