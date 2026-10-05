## 2024-05-24 - Rate-limit Step Counter Sensor Updates
**Learning:** High-frequency hardware sensors (e.g., `Sensor.TYPE_STEP_COUNTER`) trigger `onSensorChanged` very frequently, causing severe CPU and battery drain if IPC broadcasts (`sendBroadcast`) and disk writes (`SharedPreferences`) are performed on every event without throttling.
**Action:** Implement a time-based throttle (e.g., 10 seconds) in `StepCounterService.kt` to rate-limit these expensive operations.
