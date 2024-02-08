package com.alphasync

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

class AlphaSyncApp : Application() {
    val dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
}