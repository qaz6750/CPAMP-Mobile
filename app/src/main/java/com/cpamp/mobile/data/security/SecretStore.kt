package com.cpamp.mobile.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface SecretStore {
    fun put(profileId: String, secret: String)
    fun get(profileId: String): String?
    fun remove(profileId: String)
}

@Singleton
class AndroidKeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun put(profileId: String, secret: String) {
        require(profileId.isNotBlank())
        require(secret.isNotBlank())

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(1 + cipher.iv.size + ciphertext.size)
        payload[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(payload, destinationOffset = 1)
        ciphertext.copyInto(payload, destinationOffset = 1 + cipher.iv.size)
        preferences.edit()
            .putString(secretPreferenceKey(profileId), Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    override fun get(profileId: String): String? {
        val encoded = preferences.getString(secretPreferenceKey(profileId), null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return null
            require(ivSize in 12..16 && payload.size > 1 + ivSize)
            val iv = payload.copyOfRange(1, 1 + ivSize)
            val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override fun remove(profileId: String) {
        preferences.edit().remove(secretPreferenceKey(profileId)).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun secretPreferenceKey(profileId: String) = "profile_secret_$profileId"

    private companion object {
        const val PREFERENCES_NAME = "cpamp_encrypted_secrets_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "cpamp_profile_master_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

