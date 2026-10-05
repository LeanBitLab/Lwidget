## 2026-10-05 - [Remove redundant content descriptions from decorative layout icons]
**Learning:** Android XML layouts often assign redundant 'contentDescription="Icon"' attributes to decorative ImageViews placed next to descriptive TextViews, which clutters the screen reader experience.
**Action:** Use 'android:importantForAccessibility="no"' for purely decorative icons in Android layouts to ensure screen readers focus on the meaningful adjacent text labels instead.
