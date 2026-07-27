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
    fun migrate(profileIds: List<String>, requireAuthentication: Boolean)
    fun setAuthenticationRequired(required: Boolean)
}

@Singleton
class AndroidKeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    @Volatile private var authenticationRequired = false

    override fun put(profileId: String, secret: String) {
        require(profileId.isNotBlank())
        require(secret.isNotBlank())

        val payload = encrypt(secret, authenticationRequired)
        preferences.edit()
            .putString(secretPreferenceKey(profileId), Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    override fun get(profileId: String): String? {
        val encoded = preferences.getString(secretPreferenceKey(profileId), null) ?: return null
        return runCatching {
            decrypt(Base64.decode(encoded, Base64.NO_WRAP))
        }.getOrNull()
    }

    override fun remove(profileId: String) {
        preferences.edit().remove(secretPreferenceKey(profileId)).apply()
    }

    override fun migrate(profileIds: List<String>, requireAuthentication: Boolean) {
        val migrated = profileIds.distinct().mapNotNull { profileId ->
            val encoded = preferences.getString(secretPreferenceKey(profileId), null) ?: return@mapNotNull null
            val plaintext = decrypt(Base64.decode(encoded, Base64.NO_WRAP))
            profileId to Base64.encodeToString(
                encrypt(plaintext, requireAuthentication),
                Base64.NO_WRAP,
            )
        }
        val editor = preferences.edit()
        migrated.forEach { (profileId, encoded) -> editor.putString(secretPreferenceKey(profileId), encoded) }
        check(editor.commit()) { "SECRET_MIGRATION_FAILED" }
        authenticationRequired = requireAuthentication
        deleteKey(if (requireAuthentication) STANDARD_KEY_ALIAS else AUTHENTICATED_KEY_ALIAS)
    }

    override fun setAuthenticationRequired(required: Boolean) {
        authenticationRequired = required
    }

    private fun encrypt(secret: String, requireAuthentication: Boolean): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(requireAuthentication))
        val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return ByteArray(2 + cipher.iv.size + ciphertext.size).also { payload ->
            payload[0] = if (requireAuthentication) PAYLOAD_AUTHENTICATED else PAYLOAD_STANDARD
            payload[1] = cipher.iv.size.toByte()
            cipher.iv.copyInto(payload, destinationOffset = 2)
            ciphertext.copyInto(payload, destinationOffset = 2 + cipher.iv.size)
        }
    }

    private fun decrypt(payload: ByteArray): String {
        val legacy = payload.firstOrNull()?.toInt()?.and(0xFF) in 12..16
        val authenticated = !legacy && payload.firstOrNull() == PAYLOAD_AUTHENTICATED
        val ivOffset = if (legacy) 1 else 2
        val ivSize = payload.getOrNull(if (legacy) 0 else 1)?.toInt()?.and(0xFF) ?: error("INVALID_SECRET")
        require(ivSize in 12..16 && payload.size > ivOffset + ivSize)
        val iv = payload.copyOfRange(ivOffset, ivOffset + ivSize)
        val ciphertext = payload.copyOfRange(ivOffset + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(authenticated), GCMParameterSpec(128, iv))
        authenticationRequired = authenticated
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(requireAuthentication: Boolean): SecretKey {
        val alias = if (requireAuthentication) AUTHENTICATED_KEY_ALIAS else STANDARD_KEY_ALIAS
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
            if (requireAuthentication) {
                builder.setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(KEY_AUTHENTICATION_SECONDS)
            }
            init(builder.build())
            generateKey()
        }
    }

    private fun deleteKey(alias: String) {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }

    private fun secretPreferenceKey(profileId: String) = "profile_secret_$profileId"

    private companion object {
        const val PREFERENCES_NAME = "cpamp_encrypted_secrets_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val STANDARD_KEY_ALIAS = "cpamp_profile_master_v1"
        const val AUTHENTICATED_KEY_ALIAS = "cpamp_profile_authenticated_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_AUTHENTICATION_SECONDS = 3600
        const val PAYLOAD_STANDARD: Byte = 1
        const val PAYLOAD_AUTHENTICATED: Byte = 2
    }
}

