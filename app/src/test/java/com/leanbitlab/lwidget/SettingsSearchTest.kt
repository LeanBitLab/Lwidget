package com.leanbitlab.lwidget

import android.content.Context
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings search must find cards by their translated on-screen text, not only English keywords. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsSearchTest {

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            .edit().clear().putBoolean("is_first_launch", false).commit()
    }

    private fun search(query: String): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<EditText>(R.id.search_settings_input).setText(query)
        return activity
    }

    @Test
    @Config(qualifiers = "ru")
    fun findsCardByTranslatedText() {
        val activity = search("событи")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.card_events).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.card_language).visibility)
    }

    @Test
    @Config(qualifiers = "ru")
    fun englishKeywordsStillMatch() {
        val activity = search("calendar")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.card_events).visibility)
    }

    @Test
    fun ignoresCaseAndAccents() {
        val activity = search("LANGUÁGE")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.card_language).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.card_events).visibility)
    }

    @Test
    fun clearingSearchRestoresCards() {
        val activity = search("событи")
        activity.findViewById<EditText>(R.id.search_settings_input).setText("")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.card_language).visibility)
    }
}
