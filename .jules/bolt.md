## 2026-10-05 - Rate-limiting high-frequency sensor updates
**Learning:** High-frequency sensors like `Sensor.TYPE_STEP_COUNTER` can trigger `onSensorChanged` multiple times per second, leading to excessive IPC broadcasts (`sendBroadcast`) and disk writes (`SharedPreferences`), causing severe battery drain and CPU usage if not throttled.
**Action:** Always implement a time-based throttle (e.g., 10 seconds) when updating AppWidgets or saving to disk from high-frequency hardware sensors.
