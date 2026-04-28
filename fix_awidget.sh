#!/bin/bash
sed -i 's/fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, mode: UpdateMode = UpdateMode.FULL)/fun buildAppWidgetRemoteViews(context: Context, mode: UpdateMode = UpdateMode.FULL): RemoteViews/g' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i '/return tickViews/{n; /return/d}' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i '/return calViews/{n; /return/d}' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i '/return taskViews/{n; /return/d}' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i '/return alarmViews/{n; /return/d}' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i 's/appWidgetManager.partiallyUpdateAppWidget(appWidgetId, tickViews)/return tickViews/g' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i 's/appWidgetManager.partiallyUpdateAppWidget(appWidgetId, calViews)/return calViews/g' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i 's/appWidgetManager.partiallyUpdateAppWidget(appWidgetId, taskViews)/return taskViews/g' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i 's/appWidgetManager.partiallyUpdateAppWidget(appWidgetId, alarmViews)/return alarmViews/g' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
sed -i 's/appWidgetManager.updateAppWidget(appWidgetId, views)/return views\n        }\n\n        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, mode: UpdateMode = UpdateMode.FULL) {\n            val views = buildAppWidgetRemoteViews(context, mode)\n            if (mode == UpdateMode.FULL) {\n                appWidgetManager.updateAppWidget(appWidgetId, views)\n            } else {\n                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)\n            }/g' app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt
