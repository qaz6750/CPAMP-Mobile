package com.cpamp.mobile.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretStoreTest {
    @Test
    fun encryptedPreferenceNeverContainsPlaintextAndDeletionRemovesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AndroidKeystoreSecretStore(context)
        val profileId = UUID.randomUUID().toString()
        val secret = "admin-${UUID.randomUUID()}"

        store.put(profileId, secret)

        val storedValues = context.getSharedPreferences(
            "cpamp_encrypted_secrets_v1",
            Context.MODE_PRIVATE,
        ).all.values.map { value -> value.toString() }
        assertFalse(storedValues.any { it.contains(secret) })
        assertEquals(secret, store.get(profileId))

        store.remove(profileId)
        assertNull(store.get(profileId))
    }

    @Test
    fun encryptedSecretsCannotBeSwappedBetweenProfiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AndroidKeystoreSecretStore(context)
        val firstProfileId = UUID.randomUUID().toString()
        val secondProfileId = UUID.randomUUID().toString()
        val preferences = context.getSharedPreferences(
            "cpamp_encrypted_secrets_v1",
            Context.MODE_PRIVATE,
        )
        val firstKey = "profile_secret_$firstProfileId"
        val secondKey = "profile_secret_$secondProfileId"

        store.put(firstProfileId, "admin-${UUID.randomUUID()}")
        store.put(secondProfileId, "admin-${UUID.randomUUID()}")
        val firstPayload = requireNotNull(preferences.getString(firstKey, null))
        val secondPayload = requireNotNull(preferences.getString(secondKey, null))
        preferences.edit()
            .putString(firstKey, secondPayload)
            .putString(secondKey, firstPayload)
            .commit()

        assertNull(store.get(firstProfileId))
        assertNull(store.get(secondProfileId))

        store.remove(firstProfileId)
        store.remove(secondProfileId)
    }
}
