package com.darksunTechnologies.justdoit.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Manages theme preference persistence and application.
 * Values: 0 = System Default, 1 = Light, 2 = Dark
 */
object ThemePreferences {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    fun saveThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    /**
     * Maps our stored integer to the AppCompatDelegate constant.
     * Call this from Application.onCreate() and after user changes the setting.
     */
    fun applyTheme(mode: Int) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
