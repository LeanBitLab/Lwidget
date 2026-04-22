1. **Refactor `resolveColor`:**
   - Move the nested `resolveColor` function inside `AwidgetProvider.updateAppWidget` to the companion object (or an internal class level method) to make it accessible for testing.
   - Update its signature to accept its dependencies (`context`, `prefs`, `useDynamicColors`, `idx`, `isPrimary`, `isLight`, and optionally `sdkInt` for easier testing of branching behavior based on Android version) as arguments.
   - Update the original call sites inside `updateAppWidget` to use the refactored function.

2. **Add Testing Dependencies:**
   - Update `app/build.gradle.kts` to include `testImplementation` dependencies for `junit:junit:4.13.2` and `org.mockito:mockito-core:5.+` (or `org.mockito.kotlin:mockito-kotlin`).

3. **Write Unit Tests:**
   - Create a new file `app/src/test/java/com/leanbitlab/lwidget/AwidgetProviderTest.kt`.
   - Add comprehensive tests covering various scenarios of `resolveColor` including:
     - Dynamic colors behavior for SDK >= S (Android 12) vs older versions.
     - Primary vs secondary text color logic.
     - Light vs dark theme logic.
     - Default color index (`idx == 0`).
     - System accent color index (`idx == 1`).
     - Custom RGB preference colors (`idx == 2`).

4. **Complete pre-commit steps:**
   - Complete pre-commit steps to make sure proper testing, verifications, reviews, and reflections are done.

5. **Submit the change.**
   - Once all tests pass, I will submit the change with a descriptive commit message.
