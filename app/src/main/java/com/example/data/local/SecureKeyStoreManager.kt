package com.example.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedSecret(
    val cipherTextBase64: String,
    val ivBase64: String
)

interface SecretCipher {
    fun encrypt(plainText: String): EncryptedSecret?
    fun decrypt(cipherTextBase64: String, ivBase64: String): String?
}

class AndroidKeyStoreSecretCipher : SecretCipher {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "smart_ai_auto_reply_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plainText: String): EncryptedSecret? {
        if (plainText.isBlank()) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            EncryptedSecret(
                cipherTextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
        }.getOrNull()
    }

    override fun decrypt(cipherTextBase64: String, ivBase64: String): String? {
        if (cipherTextBase64.isBlank() || ivBase64.isBlank()) return null
        return runCatching {
            val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, ivBytes)
            )
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrNull()
    }
}
