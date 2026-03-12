package com.ghostpin.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages onboarding completion state via Jetpack DataStore.
 *
 * Uses `DataStore<Preferences>` instead of deprecated SharedPreferences,
 * as recommended for Kotlin 2.x and Jetpack best practices.
 *
 * Call [isComplete] to check if onboarding has been finished,
 * and [markComplete] to persist the completed state.
 */
private val Context.onboardingDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "onboarding")

@Singleton
class OnboardingDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
    }

    /** Whether the onboarding flow has been completed. emits false if not set. */
    val isComplete: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[KEY_COMPLETE] == true }

    /** Mark the onboarding as completed. Persists across app restarts. */
    suspend fun markComplete() {
        context.onboardingDataStore.edit { prefs ->
            prefs[KEY_COMPLETE] = true
        }
    }

    /** Reset onboarding state (for testing/debugging). */
    suspend fun reset() {
        context.onboardingDataStore.edit { prefs ->
            prefs.remove(KEY_COMPLETE)
        }
    }
}
