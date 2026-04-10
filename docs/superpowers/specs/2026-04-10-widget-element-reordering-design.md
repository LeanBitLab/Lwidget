# Widget Element Reordering — Design Spec

## Goal

Allow users to reorder widget elements (time, date, battery, weather, etc.) via drag-and-drop in a two-column layout that mirrors the widget's visual structure. Users can move items within a column and between columns.

## Architecture

### New Components

| Component | Type | Responsibility |
|---|---|---|
| `ReorderActivity` | Activity | Hosts the reorder UI with two RecyclerViews |
| `ReorderAdapter` | RecyclerView.Adapter | Displays reorderable items with drag handles |
| `ReorderItem` | Data class | Represents one reorderable widget element |
| `ReorderTouchCallback` | ItemTouchHelper.Callback | Handles drag-and-drop logic including cross-column moves |

### Existing Components (Modified)

| Component | Changes |
|---|---|
| `MainActivity` | Add "Reorder Items" button in settings, launches `ReorderActivity` |
| `AwidgetProvider` | Replace hardcoded RelativeLayout positioning with dynamic LinearLayout building based on saved column order |
| `strings.xml` | Add string resources for reorder UI |
| `AndroidManifest.xml` | Register `ReorderActivity` |

## Data Model

### ReorderItem

```kotlin
data class ReorderItem(
    val id: String,          // e.g., "time", "battery", "weather_condition"
    val title: String,       // Display name (from strings.xml)
    @DrawableRes val icon: Int, // Icon resource ID
    val isEnabled: Boolean   // Matches current toggle state
)
```

### Item IDs (all reorderable elements)

Left column candidates: `time`, `world_clock`, `next_alarm`, `date`, `events`, `tasks`
Right column candidates: `battery`, `temp`, `weather_condition`, `data_usage`, `storage`, `steps`, `screen_time`

### Persistence

Two SharedPreferences string keys store the column orders:

```
left_column_order = "time,date,events"
right_column_order = "battery,temp,weather_condition,data_usage,storage,steps,screen_time"
```

- Comma-separated list of item IDs
- Order in the string = visual order in the column
- If a key is missing (first run), default to the current hardcoded layout order
- Disabled items are included in the order (position preserved for when re-enabled)

**New preference keys:**
- `left_column_order` (String)
- `right_column_order` (String)

## UI Design

### ReorderActivity Layout

```
┌─────────────────────────────────────────┐
│  ← Back       Reorder Items      ✓ Save │  (TopAppBar)
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────┐  ┌──────────────┐    │
│  │ LEFT COLUMN  │  │ RIGHT COLUMN │    │
│  │              │  │              │    │
│  │ ☰ Time       │  │ ☰ Battery    │    │
│  │ ☰ Date       │  │ ☰ Temp       │    │
│  │ ☰ Events     │  │ ☰ Weather    │    │
│  │              │  │ ☰ Data Usage │    │
│  │              │  │ ☰ Storage    │    │
│  │              │  │ ☰ Steps      │    │
│  │              │  │ ☰ Screen Time│    │
│  └──────────────┘  └──────────────┘    │
│                                         │
│  Drag items to reorder within or        │
│  between columns.                       │
└─────────────────────────────────────────┘
```

### Reorder Item Row

```
┌──────────────────────────────┐
│ ☰  [icon]  Time         ══  │  (drag handle left, icon+title, enabled indicator right)
└──────────────────────────────┘
```

- Drag handle (☰) on the left — grab point for dragging
- Icon + title — identifies the element
- Disabled items render at 50% alpha
- Active drag shows item elevation shadow

### Key Behaviors

1. **Drag within column** — Changes the item's position in that column's order
2. **Drag to opposite column** — Moves the item to the other column (inserted at drop position)
3. **Save button** — Writes both column orders to SharedPreferences, triggers widget update via broadcast, finishes activity
4. **Back button** — Discards changes, returns to MainActivity
5. **Initial load** — Reads existing prefs; if no order prefs exist, populates with default order matching current hardcoded layout

## AwidgetProvider Changes

### Current State

The widget uses `RelativeLayout` with hardcoded `below`/`alignTop` rules. Each element's position is fixed in `widget_layout.xml` and all 10 font-variant layouts.

### New State

Each column becomes a `LinearLayout` (vertical orientation) added programmatically:

```kotlin
// Pseudocode in AwidgetProvider.updateAppWidget()
val leftOrder = prefs.getString("left_column_order", DEFAULT_LEFT_ORDER)!!.split(",")
val rightOrder = prefs.getString("right_column_order", DEFAULT_RIGHT_ORDER)!!.split(",")

// For each column:
// 1. Create a RemoteViews with LinearLayout
// 2. For each enabled item in order, create its RemoteViews and add to the LinearLayout
// 3. Set the column's RemoteViews into the widget's container view
```

**Approach:** Since `RemoteViews` supports `addView()` on `LinearLayout` and `FrameLayout`, the provider will:

1. Inflate the base widget layout (which now contains two empty container `FrameLayout`s: `left_column_container` and `right_column_container`)
2. For each column, build a `RemoteViews` hierarchy:
   - Create a `LinearLayout` RemoteViews
   - For each enabled item, create the item's `RemoteViews` (TextView, TextClock, etc.) and `addView()` it to the LinearLayout
3. `addView()` the column LinearLayout into the container FrameLayout

This eliminates the need for 11 nearly-identical font-variant layout files. Font styling will be applied programmatically via `setTextAppearance()` or typeface setting on each dynamically-created view.

**Font-style layouts:** The existing `widget_layout_*.xml` files each duplicate the full widget structure with a different `fontFamily`. Font style will be applied per-element programmatically using `RemoteViews.setTextViewTextSize()` and typeface selection. The duplicate font-variant layout files are **out of scope** for this change and will be preserved. A future cleanup can remove them.

### Update Mode Compatibility

The existing `UpdateMode` enum (FULL, TICK, CALENDAR_ONLY, TASKS_ONLY, ALARM_ONLY) works independently of ordering — each update targets specific element IDs. The dynamic building logic respects this by only rendering enabled elements and skipping elements not targeted by the current update mode.

## Dependencies

- `androidx.recyclerview:recyclerview` — already in project (check build.gradle)
- `com.google.android.material:material` — for TopAppBar, drag handle styling
- `ItemTouchHelper` — from androidx.recyclerview, no additional dependency needed

## Error Handling

- **Missing/empty order prefs** — Use default order (current hardcoded layout)
- **Order references unknown item ID** — Skip it (defensive parsing)
- **Both Events and Tasks enabled** — Mutual exclusion logic from MainActivity still applies; only the enabled one appears in the reorder list
- **Widget update fails mid-build** — Fall back to full update on next tick

## Testing Considerations

1. Verify default order matches current hardcoded layout on first open
2. Verify drag within column changes order correctly
3. Verify drag between columns moves item correctly
4. Verify save persists order and updates widget
5. Verify back button discards changes
6. Verify disabled items appear dimmed and are still reorderable
7. Verify widget renders correctly after reordering (all font sizes, visibility states)
8. Verify UpdateMode (TICK, CALENDAR_ONLY, etc.) still works with dynamic layout
9. Verify font style changes still apply correctly after reordering

## Migration Notes

- Existing users upgrading will have no order prefs → default to current layout
- No data migration needed; this is additive, not breaking
- Font-variant layout files (`widget_layout_*.xml`) are preserved; cleanup is deferred to a future change
