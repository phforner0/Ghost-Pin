package com.ghostpin.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.ghostpin.app.data.ProfileManager
import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.core.model.MovementProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileManagerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun existingProfile_canBeUsedFromManagementScreen() {
        val dao = FakeProfileDao(
            listOf(
                ProfileEntity.fromDomain(
                    profile = MovementProfile.CAR.copy(name = "Scout"),
                    id = "custom-scout",
                    isBuiltIn = false,
                    isCustom = true,
                )
            )
        )
        val viewModel = ProfileManagerViewModel(ProfileManager(dao))
        var usedProfileName: String? = null

        composeRule.setContent {
            GhostPinTheme {
                ProfileManagerScreen(
                    onBack = {},
                    onUseProfile = { usedProfileName = it.name },
                    viewModel = viewModel,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Scout").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Usar")[0].performClick()

        composeRule.runOnIdle {
            assertEquals("Scout", usedProfileName)
        }
    }

    @Test
    fun createProfile_dialogValidatesAndSaves() {
        val dao = FakeProfileDao(emptyList())
        val viewModel = ProfileManagerViewModel(ProfileManager(dao))

        composeRule.setContent {
            GhostPinTheme {
                ProfileManagerScreen(
                    onBack = {},
                    onUseProfile = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithTag("add_profile_button").performClick()
        composeRule.onNodeWithTag("profile_name_field").performTextClearance()
        composeRule.onNodeWithTag("profile_save_button").assertIsNotEnabled()
        assertTrue(composeRule.onAllNodesWithTag("profile_validation_message").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag("profile_name_field").performTextInput("Runner X")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("profile_validation_message").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("profile_save_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            dao.currentProfiles.value.any { it.name == "Runner X" }
        }
        assertTrue(composeRule.onAllNodesWithText("Runner X").fetchSemanticsNodes().isNotEmpty())
    }

    private class FakeProfileDao(initialProfiles: List<ProfileEntity>) : ProfileDao {
        val currentProfiles = MutableStateFlow(initialProfiles)

        override fun observeAll(): Flow<List<ProfileEntity>> =
            currentProfiles.map { profiles ->
                profiles.sortedWith(compareByDescending<ProfileEntity> { it.isBuiltIn }.thenBy { it.name })
            }

        override suspend fun getAll(): List<ProfileEntity> = currentProfiles.value

        override suspend fun getById(id: String): ProfileEntity? = currentProfiles.value.firstOrNull { it.id == id }

        override suspend fun getByName(name: String): ProfileEntity? = currentProfiles.value.firstOrNull { it.name == name }

        override suspend fun countCustom(): Int = currentProfiles.value.count { !it.isBuiltIn }

        override suspend fun insert(profile: ProfileEntity) {
            currentProfiles.value = currentProfiles.value + profile
        }

        override suspend fun insertAll(profiles: List<ProfileEntity>) {
            currentProfiles.value = currentProfiles.value + profiles
        }

        override suspend fun update(profile: ProfileEntity) {
            currentProfiles.value = currentProfiles.value.map { if (it.id == profile.id) profile else it }
        }

        override suspend fun deleteById(id: String) {
            currentProfiles.value = currentProfiles.value.filterNot { it.id == id }
        }
    }
}
