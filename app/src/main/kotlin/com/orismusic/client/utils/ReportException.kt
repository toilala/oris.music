package com.orismusic.client.utils

import android.content.Context
import android.util.Log
import timber.log.Timber

fun reportException(e: Throwable) {
    Timber.e(e, "Exception reported")
    // In a full implementation, this would send to a crash reporting service like Firebase Crashlytics
}

inline fun <reified T> Context.get(key: androidx.datastore.preferences.core.Preferences.Key<T>): T? =
    dataStore[key]

inline fun <reified T> Context.get(key: androidx.datastore.preferences.core.Preferences.Key<T>, defaultValue: T): T =
    dataStore[key] ?: defaultValue