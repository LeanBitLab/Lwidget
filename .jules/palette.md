## PALETTE'S JOURNAL
## 2024-11-20 - [Redundant contentDescription on Android Decorative Elements]
**Learning:** In Android XML layouts, purely decorative icons adjacent to text often incorrectly use `android:contentDescription="Icon"`. This clutters the screen reader output unnecessarily.
**Action:** Always replace redundant generic content descriptions on decorative elements with `android:importantForAccessibility="no"` to prevent screen reader noise.
