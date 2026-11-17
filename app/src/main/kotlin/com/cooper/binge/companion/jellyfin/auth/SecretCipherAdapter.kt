package com.cooper.binge.companion.jellyfin.auth

import com.binge.companion.kit.SecretCipher
import com.cooper.homelab.core.server.connection.SecretCipher as LibrarySecretCipher

/**
 * This app's Keystore cipher as the shared library's. Both write the same blob (the library's is the kit's, moved), so a secret one
 * wrote the other reads, and the Keystore alias does not change.
 */
internal fun SecretCipher.forLibrary(): LibrarySecretCipher =
    object : LibrarySecretCipher {
        override fun encrypt(plaintext: String): String = this@forLibrary.encrypt(plaintext)

        override fun decrypt(ciphertext: String): String? = this@forLibrary.decrypt(ciphertext)
    }
