package com.ghostpin.app.data

import com.ghostpin.app.data.db.ProfileDao
import com.ghostpin.app.data.db.ProfileEntity
import com.ghostpin.core.model.MovementProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileManagerTest {
    @Test
    fun `create rejects duplicate name ignoring case`() =
        runTest {
            val dao =
                FakeProfileDao(
                    listOf(
                        ProfileEntity.fromDomain(
                            profile = MovementProfile.CAR.copy(name = "Scout"),
                            id = "custom-scout",
                            isBuiltIn = false,
                            isCustom = true,
                        )
                    )
                )
            val manager = ProfileManager(dao)

            val error =
                runCatching {
                    manager.create(MovementProfile.PEDESTRIAN.copy(name = "scout"))
                }.exceptionOrNull()

            assertEquals("Já existe um perfil com esse nome.", error?.message)
        }

    @Test
    fun `update allows keeping same name for same profile`() =
        runTest {
            val existing =
                ProfileEntity.fromDomain(
                    profile = MovementProfile.CAR.copy(name = "Scout"),
                    id = "custom-scout",
                    isBuiltIn = false,
                    isCustom = true,
                )
            val dao = FakeProfileDao(listOf(existing))
            val manager = ProfileManager(dao)

            manager.update("custom-scout", MovementProfile.CAR.copy(name = "Scout"))

            assertEquals(
                "Scout",
                dao.currentProfiles.value
                    .first()
                    .name
            )
        }

    @Test
    fun `clone rejects duplicate target name`() =
        runTest {
            val dao =
                FakeProfileDao(
                    listOf(
                        ProfileEntity.fromDomain(
                            profile = MovementProfile.CAR.copy(name = "Scout"),
                            id = "custom-scout",
                            isBuiltIn = false,
                            isCustom = true,
                        ),
                        ProfileEntity.fromDomain(
                            profile = MovementProfile.PEDESTRIAN.copy(name = "Scout Copy"),
                            id = "custom-copy",
                            isBuiltIn = false,
                            isCustom = true,
                        )
                    )
                )
            val manager = ProfileManager(dao)

            val error =
                runCatching {
                    manager.clone("custom-scout", "Scout Copy")
                }.exceptionOrNull()

            assertEquals("Já existe um perfil com esse nome.", error?.message)
        }

    private class FakeProfileDao(
        initialProfiles: List<ProfileEntity>
    ) : ProfileDao {
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
