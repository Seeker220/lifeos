package com.lifeos.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.lifeOsDataStore by preferencesDataStore(name = "lifeos")
