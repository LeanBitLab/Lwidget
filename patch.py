import sys

def main():
    with open('app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt', 'r') as f:
        content = f.read()

    search = """<<<<<<< HEAD
        private fun updateScreenTime(context: Context, views: RemoteViews) {
            val now = System.currentTimeMillis()
            if (now - lastUsageStatsCheckTime > 60000) {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                } else {
                    appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                }
                cachedUsageStatsPermission = (mode == android.app.AppOpsManager.MODE_ALLOWED)
                lastUsageStatsCheckTime = now
=======
        private fun updateScreenTime(context: Context, views: RemoteViews, prefs: android.content.SharedPreferences) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                 appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                 appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
>>>>>>> origin/master
            }"""

    replace = """        private fun updateScreenTime(context: Context, views: RemoteViews, prefs: android.content.SharedPreferences) {
            val now = System.currentTimeMillis()
            if (now - lastUsageStatsCheckTime > 60000) {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                } else {
                    appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                }
                cachedUsageStatsPermission = (mode == android.app.AppOpsManager.MODE_ALLOWED)
                lastUsageStatsCheckTime = now
            }"""

    if search in content:
        content = content.replace(search, replace)
        with open('app/src/main/java/com/leanbitlab/lwidget/AwidgetProvider.kt', 'w') as f:
            f.write(content)
        print("Success")
    else:
        print("Search string not found")

if __name__ == "__main__":
    main()
