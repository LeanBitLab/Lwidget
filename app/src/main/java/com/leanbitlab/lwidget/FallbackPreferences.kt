package com.leanbitlab.lwidget

import android.content.SharedPreferences

class FallbackPreferences(
    private val widgetPrefs: SharedPreferences,
    private val globalPrefs: SharedPreferences
) : SharedPreferences {

    override fun getAll(): Map<String, *> {
        val merged = HashMap<String, Any?>()
        merged.putAll(globalPrefs.all)
        merged.putAll(widgetPrefs.all)
        return merged
    }

    override fun getString(key: String, defValue: String?): String? {
        return if (widgetPrefs.contains(key)) {
            widgetPrefs.getString(key, defValue)
        } else {
            globalPrefs.getString(key, defValue)
        }
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        return if (widgetPrefs.contains(key)) {
            widgetPrefs.getStringSet(key, defValues)
        } else {
            globalPrefs.getStringSet(key, defValues)
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        return if (widgetPrefs.contains(key)) {
            widgetPrefs.getInt(key, defValue)
        } else {
            globalPrefs.getInt(key, defValue)
        }
    }

    override fun getLong(key: String, defValue: Long): Long {
        return if (widgetPrefs.contains(key)) {
            widgetPrefs.getLong(key, defValue)
        } else {
            globalPrefs.getLong(key, defValue)
        }
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return if (widgetPrefs.contains(key)) {
            widgetPrefs.getFloat(key, defValue)
        } else {
            globalPrefs.getFloat(key, defValue)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return if (widgetPrefs.contains(key)) {
            widgetPrefs.getBoolean(key, defValue)
        } else {
            globalPrefs.getBoolean(key, defValue)
        }
    }

    override fun contains(key: String): Boolean {
        return widgetPrefs.contains(key) || globalPrefs.contains(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return FallbackEditor(widgetPrefs.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        widgetPrefs.registerOnSharedPreferenceChangeListener(listener)
        globalPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        widgetPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        globalPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    class FallbackEditor(private val editor: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { editor.putStringSet(key, values) }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { editor.putLong(key, value) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { editor.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
        override fun remove(key: String): SharedPreferences.Editor = apply { editor.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { editor.clear() }
        override fun commit(): Boolean = editor.commit()
        override fun apply() = editor.apply()
    }
}
