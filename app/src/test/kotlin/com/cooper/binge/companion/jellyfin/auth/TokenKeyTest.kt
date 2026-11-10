package com.cooper.binge.companion.jellyfin.auth

import android.util.Base64
import com.binge.companion.kit.KeystoreSecretCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * The access token is encrypted by the kit's cipher under the alias this app has always used, so a session saved before the move
 * still reads. [oldEncrypt] and [oldDecrypt] are the app's own cipher as it was before: its algorithm, its blob format.
 */
@RunWith(RobolectricTestRunner::class)
class TokenKeyTest {
    @Before
    fun install() {
        FakeAndroidKeyStore.keys.clear()
        Security.removeProvider("AndroidKeyStore")
        Security.addProvider(FakeAndroidKeyStore.provider())
    }

    @After
    fun remove() {
        Security.removeProvider("AndroidKeyStore")
    }

    private fun key() = FakeAndroidKeyStore.keys.getValue(TOKEN_KEY_ALIAS)

    private fun oldEncrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun oldDecrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    @Test
    fun `the token's key alias is the one the app has always used`() {
        assertEquals("binge_jellyfin_secret", TOKEN_KEY_ALIAS)
    }

    @Test
    fun `a token the old cipher saved still decrypts, on the key under the same alias`() {
        val cipher = KeystoreSecretCipher(TOKEN_KEY_ALIAS)
        // The key under the alias is made on first use; the old cipher's blob is then written under that same key.
        cipher.encrypt("warm up")
        val saved = oldEncrypt("tok-en/with+base64=chars")
        assertEquals("tok-en/with+base64=chars", cipher.decrypt(saved))
    }

    @Test
    fun `a token the kit saves is one the old format reads`() {
        val cipher = KeystoreSecretCipher(TOKEN_KEY_ALIAS)
        val saved = cipher.encrypt("access-token")
        assertEquals("access-token", oldDecrypt(saved))
    }

    @Test
    fun `another alias is another key, so a changed alias reads as signed out`() {
        val saved = KeystoreSecretCipher(TOKEN_KEY_ALIAS).encrypt("access-token")
        assertNull(KeystoreSecretCipher("another_alias").decrypt(saved))
    }
}
