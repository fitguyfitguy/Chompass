package com.fitguy.nofud.data

import com.fitguy.nofud.models.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Single source of truth for [UserProfile]. Thin wrapper over [PreferencesStore]
 * that exposes reactive reads and suspend writes.
 */
class ProfileRepository(private val prefs: PreferencesStore) {
    val profile: Flow<UserProfile?> = prefs.userProfile

    suspend fun save(profile: UserProfile) = prefs.setUserProfile(profile)

    /** Current snapshot. Suspends until DataStore emits the first value. */
    suspend fun current(): UserProfile? = prefs.userProfile.first()
}
