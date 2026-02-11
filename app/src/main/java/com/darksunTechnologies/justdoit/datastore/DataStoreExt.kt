package com.darksunTechnologies.justdoit.datastore

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(
    name = "tasks_datastore"
)