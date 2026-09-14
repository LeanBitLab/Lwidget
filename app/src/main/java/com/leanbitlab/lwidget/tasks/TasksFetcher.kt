package com.leanbitlab.lwidget.tasks

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import com.leanbitlab.lwidget.MainActivity
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.calendar.CalendarFetcher
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object TasksFetcher {

    const val PERMISSION_READ_TASKS_ORG = "org.tasks.permission.READ_TASKS"
    const val PERMISSION_READ_TASKS_ASTRID = "com.todoroo.astrid.READ"

    data class TaskData(val title: String, val dueMillis: Long)

    fun fetchActiveTasks(context: Context, limit: Int): List<TaskData> {
        val tasks = mutableListOf<TaskData>()
        val taskUri = android.net.Uri.parse("content://org.tasks/tasks")
        val selection = "completed=0 AND deleted=0"
        try {
            context.contentResolver.query(taskUri, null, selection, null, "dueDate ASC")?.use { cursor ->
                val titleIdx = cursor.getColumnIndex("title")
                val compIdx = cursor.getColumnIndex("completed")
                val delIdx = cursor.getColumnIndex("deleted")
                val dueIdx = cursor.getColumnIndex("dueDate")

                if (titleIdx == -1) return emptyList()

                while (cursor.moveToNext() && tasks.size < limit) {
                    val completed = if (compIdx >= 0) cursor.getString(compIdx) else null
                    val deleted = if (delIdx >= 0) cursor.getString(delIdx) else null
                    val dueMillis = if (dueIdx >= 0) cursor.getLong(dueIdx) else 0L

                    val isCompleted = completed != null && completed != "0"
                    val isDeleted = deleted != null && deleted != "0"

                    if (isCompleted || isDeleted) {
                        continue
                    }

                    val title = cursor.getString(titleIdx) ?: "No Title"
                    tasks.add(TaskData(title, dueMillis))
                }
            }
        } catch (e: Exception) {
            // Return empty list on failure
        }
        return tasks
    }

    fun formatDueSuffix(dueMillis: Long): String {
        if (dueMillis <= 0) return ""
        val dueDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueMillis), ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        return if (dueDate.isBefore(today)) {
            " (Overdue)"
        } else if (dueDate.isEqual(today)) {
            " (Today)"
        } else if (dueDate.isEqual(tomorrow)) {
            " (Tomorrow)"
        } else {
            val df = CalendarFetcher.getFormatter("MMM d")
            " (${dueDate.format(df)})"
        }
    }

    fun loadTasks(context: Context, views: RemoteViews, textSizeSp: Float, primaryColor: Int) {
        val eventViews = listOf(
            R.id.text_event_1, R.id.text_event_2, R.id.text_event_3,
            R.id.text_event_4, R.id.text_event_5, R.id.text_event_6,
            R.id.text_event_7, R.id.text_event_8, R.id.text_event_9,
            R.id.text_event_10
        )

        val hasPerm = context.checkSelfPermission(PERMISSION_READ_TASKS_ORG) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(PERMISSION_READ_TASKS_ASTRID) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPerm) {
            views.setTextViewText(eventViews[0], "• Tap to grant Tasks.org permission")
            views.setViewVisibility(eventViews[0], android.view.View.VISIBLE)
            views.setTextViewTextSize(eventViews[0], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)

            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(context, 1002, settingsIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(eventViews[0], pendingIntent)

            for (j in 1 until eventViews.size) {
                views.setViewVisibility(eventViews[j], android.view.View.GONE)
            }
            return
        }

        val tasks = fetchActiveTasks(context, eventViews.size)

        if (tasks.isEmpty()) {
            for (viewId in eventViews) {
                views.setViewVisibility(viewId, android.view.View.GONE)
            }
            return
        }

        for (i in tasks.indices) {
            val task = tasks[i]
            val dueSuffix = formatDueSuffix(task.dueMillis)
            val fullText = "• ${task.title}$dueSuffix"
            val spannable = SpannableString(fullText)
            val accentColor = context.getColor(R.color.widget_outline)
            spannable.setSpan(ForegroundColorSpan(accentColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            views.setTextViewText(eventViews[i], spannable)
            views.setTextColor(eventViews[i], primaryColor)
            views.setTextViewTextSize(eventViews[i], android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            views.setViewVisibility(eventViews[i], android.view.View.VISIBLE)

            val taskIntent = context.packageManager.getLaunchIntentForPackage("org.tasks")
            if (taskIntent != null) {
                val taskPendingIntent = PendingIntent.getActivity(context, 1000 + i, taskIntent, PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(eventViews[i], taskPendingIntent)
            }
        }

        for (j in tasks.size until eventViews.size) {
            views.setViewVisibility(eventViews[j], android.view.View.GONE)
        }
    }
}
