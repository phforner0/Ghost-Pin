package com.ghostpin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.ProfileManager
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.core.model.MovementProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel exposing [ProfileManager] operations to the Profile Management UI.
 *
 * Sprint 4 — Task 15.
 *
 * The existing [SimulationViewModel] retains the in-session selected profile.
 * This ViewModel handles the separate "Manage Profiles" screen where the user
 * can create, clone, edit, and delete custom profiles.
 */
@HiltViewModel
class ProfileManagerViewModel
    @Inject
    constructor(
        private val profileManager: ProfileManager,
    ) : ViewModel() {
        /** All profiles (built-in first, then custom alphabetically), observed live. */
        val profiles: StateFlow<List<ProfileEntity>> =
            profileManager
                .observeAllEntities()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        // ── CRUD ─────────────────────────────────────────────────────────────────

        fun create(
            profile: MovementProfile,
            onResult: (String?) -> Unit = {},
            onError: (String) -> Unit = {}
        ) {
            viewModelScope.launch {
                runCatching { profileManager.create(profile) }
                    .onSuccess { id -> onResult(id) }
                    .onFailure { error -> onError(error.message ?: "Create failed") }
            }
        }

        /**
         * Clone an existing profile under a new name.
         *
         * @param sourceId Room entity ID of the profile to clone.
         * @param newName Name for the cloned profile.
         * @param onResult Callback with the new profile's ID (or null on failure).
         */
        fun clone(
            sourceId: String,
            newName: String,
            onResult: (String?) -> Unit = {}
        ) {
            viewModelScope.launch {
                val id = profileManager.clone(sourceId, newName)
                onResult(id)
            }
        }

        /**
         * Update a custom profile's parameters.
         *
         * Bumps the patch semver version automatically.
         * Silently fails with a log if the profile is built-in.
         *
         * @param id Room entity ID of the profile to update.
         * @param updated New domain model values.
         */
        fun update(
            id: String,
            updated: MovementProfile,
            onError: (String) -> Unit = {}
        ) {
            viewModelScope.launch {
                runCatching { profileManager.update(id, updated) }
                    .onFailure { e -> onError(e.message ?: "Update failed") }
            }
        }

        /**
         * Delete a custom profile.
         *
         * Silently fails (with a log) if the profile is built-in.
         *
         * @param id Room entity ID of the profile to delete.
         */
        fun delete(
            id: String,
            onError: (String) -> Unit = {}
        ) {
            viewModelScope.launch {
                runCatching { profileManager.delete(id) }
                    .onFailure { e -> onError(e.message ?: "Delete failed") }
            }
        }
    }
