package com.cooper.binge.companion.jellyfin.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts the secrets this app keeps on disk: the access token for the
 * server, and the bearer secret of a sign-in waiting to be approved in another app.
 */
interface SecretCipher {
    fun encrypt(plaintext: String): String

    /** Null when [ciphertext] cannot be decrypted — a lost key or a corrupt blob reads as "no secret". */
    fun decrypt(ciphertext: String): String?
}

/**
 * AES-256-GCM under an Android Keystore key, so the raw key never leaves secure hardware. The
 * per-call 12-byte IV is prepended to the ciphertext and the blob Base64-encoded.
 */
class KeystoreSecretCipher : SecretCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val payload = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + payload, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String): String? =
        try {
            val blob = Base64.decode(ciphertext, Base64.NO_WRAP)
            if (blob.size < IV_LENGTH) {
                Log.e(TAG, "Stored secret is shorter than an IV; treating as absent")
                return null
            }
            val iv = blob.copyOfRange(0, IV_LENGTH)
            val payload = blob.copyOfRange(IV_LENGTH, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // Only the fact is logged: never the ciphertext, the plaintext or the key alias's contents.
            Log.e(TAG, "Stored secret failed to decrypt — key lost or blob corrupt; treating as absent", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Stored secret is not a valid blob; treating as absent", e)
            null
        }

    /** Resolved once so concurrent first callers cannot each generate a key under the alias and clobber the other's. */
    private val secretKey: SecretKey by lazy { loadOrCreateSecretKey() }

    private fun loadOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val TAG = "SecretCipher"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "binge_jellyfin_secret"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
