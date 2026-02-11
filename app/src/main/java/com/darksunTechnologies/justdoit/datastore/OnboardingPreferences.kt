package com.darksunTechnologies.justdoit.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey

object OnboardingPreferences {
    val IS_FIRST_TIME = booleanPreferencesKey("is_first_time")
}