package com.darksunTechnologies.justdoit

import android.app.Application
import com.darksunTechnologies.justdoit.datastore.ThemePreferences

/**
 * Custom Application class.
 * Applies the saved theme BEFORE any Activity is created,
 * preventing the "white flash" on cold boot for dark mode users.
 */
class JustDoItApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply user's saved theme before any Activity draws
        val savedMode = ThemePreferences.getSavedThemeMode(this)
        ThemePreferences.applyTheme(savedMode)
    }
}
