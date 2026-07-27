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
        ).all.values.map(String::valueOf)
        assertFalse(storedValues.any { it.contains(secret) })
        assertEquals(secret, store.get(profileId))

        store.remove(profileId)
        assertNull(store.get(profileId))
    }
}