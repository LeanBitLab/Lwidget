## 2024-05-19 - Removed redundant content descriptions in layouts
**Learning:** In Android XML layouts, purely decorative elements (e.g., `ImageView`s adjacent to descriptive text) should use `android:importantForAccessibility="no"` instead of redundant `contentDescription` values (like 'Icon') to prevent screen reader clutter.
**Action:** Replace redundant `contentDescription` values for decorative icons with `android:importantForAccessibility="no"` to improve the accessibility of the screen reader.
