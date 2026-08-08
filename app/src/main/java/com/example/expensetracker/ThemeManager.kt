package com.example.expensetracker

import android.app.Activity
import android.content.Context

/**
 * Persists and applies the user's chosen app theme (Luminous / Onyx). setSelected() only
 * writes the preference — the caller (SettingsFragment) still has to call Activity.recreate()
 * to actually re-inflate every view under the new theme, since there's no live way to swap a
 * theme on an already-created Activity.
 */
object ThemeManager {

    private const val PREF_NAME = "theme_prefs"
    private const val KEY_SELECTED = "selected_theme"

    enum class AppTheme(val styleRes: Int, val label: String) {
        LUMINOUS(R.style.Theme_Finora_Luminous, "Luminous"),
        ONYX(R.style.Theme_Finora_Onyx, "Onyx")
    }

    fun getSelected(context: Context): AppTheme {
        val name = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, AppTheme.ONYX.name)
        return AppTheme.entries.find { it.name == name } ?: AppTheme.ONYX
    }

    fun setSelected(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED, theme.name).apply()
    }

    /** Call before super.onCreate()/setContentView() in every Activity. */
    fun applyTheme(activity: Activity) {
        activity.setTheme(getSelected(activity).styleRes)
    }
}
