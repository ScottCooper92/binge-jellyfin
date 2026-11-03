package com.cooper.binge.companion.jellyfin.server

import java.util.Base64

private const val SPLASHSCREEN = "Branding/Splashscreen"

/**
 * The login splash image the server serves, or null when it serves none: the splash screen is off (before 10.11 even an
 * administrator gets a 404 then) or there is neither a custom image nor a generated one. Anyone may read it.
 */
suspend fun JellyfinClient.splashscreenImage(): ByteArray? = getBytes(SPLASHSCREEN, emptyMap(), MAX_PICTURE_BYTES)

/**
 * Sets the login splash image to [jpeg]. Admin only, from 10.9. The body is base64 text typed by its `Content-Type`, as a
 * user's picture is. The server keeps the file and writes its own path into the branding configuration
 * (`SplashscreenLocation`), so a page holding that configuration must read it again.
 */
suspend fun JellyfinClient.uploadSplashscreen(jpeg: ByteArray) {
    postText(SPLASHSCREEN, Base64.getEncoder().encodeToString(jpeg), PICTURE_TYPE, emptyMap())
}

/** Removes the custom splash image, back to Jellyfin's own. Admin only, from 10.9. The server answers the same when there is none. */
suspend fun JellyfinClient.removeSplashscreen() {
    delete(SPLASHSCREEN)
}
