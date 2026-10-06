## 2024-05-13 - [Performance Insights]
**Learning:** `PackageManager.getLaunchIntentForPackage` triggers expensive IPC calls. Looping this in rendering code without a cache creates performance bottlenecks.
**Action:** When working with rendering or high-frequency loops in Android, ensure package resolutions are cached (with TTL) rather than called repeatedly. Ensure any Intents fetched are copied before being given out from the cache to avoid accidental mutations.

**Learning:** Rate-limiting disk writes and IPC broadcasts from high-frequency hardware sensors (e.g. `StepCounterService`) via `Handler` debouncing improves performance.
**Action:** Use a Handler to debounce updates (e.g. step counter updates) so the app isn't constantly hitting SharedPreferences or sending IPC broadcasts on every single sensor tick. Use `SystemClock.elapsedRealtime()` instead of `System.currentTimeMillis()` to prevent dropping the final event when the user stops.
