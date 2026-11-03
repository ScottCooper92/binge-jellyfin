package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The most of a picture this app will read back from the server: far over a 512-pixel JPEG, well under memory. */
private const val MAX_PICTURE_BYTES = 8L * 1024 * 1024

/** The file type the picture is sent as: the app always sends a JPEG, whatever the user picked. */
private const val PICTURE_TYPE = "image/jpeg"

/**
 * A user's picture route. From 10.9 it is `/UserImage?userId=`; before that, `/Users/{id}/Images/Primary`. [newRoute] is
 * [JellyfinServerProfile.hasUserImageRoute].
 */
private fun userImageRoute(
    userId: String,
    newRoute: Boolean,
): Pair<String, Map<String, String>> =
    if (newRoute) "UserImage" to mapOf("userId" to userId) else "Users/$userId/Images/Primary" to emptyMap()

/** [userId]'s picture as the file the server holds, or null when they have none. */
suspend fun JellyfinClient.userImage(
    userId: String,
    newRoute: Boolean,
): ByteArray? {
    val (path, query) = userImageRoute(userId, newRoute)
    return getBytes(path, query, MAX_PICTURE_BYTES)
}

/**
 * Sets [userId]'s picture to [jpeg]. Jellyfin reads the body as base64 text, not as the file's bytes, and takes the file
 * type from the `Content-Type`. The server allows the user themselves and an administrator, and answers 403 to anyone else.
 */
suspend fun JellyfinClient.setUserImage(
    userId: String,
    jpeg: ByteArray,
    newRoute: Boolean,
) {
    val (path, query) = userImageRoute(userId, newRoute)
    postText(path, Base64.getEncoder().encodeToString(jpeg), PICTURE_TYPE, query)
}

/** Removes [userId]'s picture. The server answers the same when there is none. */
suspend fun JellyfinClient.removeUserImage(
    userId: String,
    newRoute: Boolean,
) {
    val (path, query) = userImageRoute(userId, newRoute)
    delete(path, query)
}

private const val HTTP_NOT_FOUND = 404

/**
 * [JellyfinClient.post] with a plain-text body of [contentType]: a user's picture, which Jellyfin reads as base64 text and
 * takes the file type from the header of.
 */
private suspend fun JellyfinClient.postText(
    path: String,
    text: String,
    contentType: String,
    query: Map<String, String>,
): JsonElement = call(Request.Builder().url(url(path, query)).post(text.toRequestBody(contentType.toMediaType())), path)

/**
 * A GET for a file: its bytes, or null when the server has none there (a 404). An answer longer than [maxBytes] is refused,
 * not held. Sent with the header only, so the token never rides in the URL.
 */
private suspend fun JellyfinClient.getBytes(
    path: String,
    query: Map<String, String>,
    maxBytes: Long,
): ByteArray? {
    val request =
        Request
            .Builder()
            .url(url(path, query))
            .get()
            .header("Authorization", authorization())
            .build()
    val call = http.newCall(request)
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) = cont.resumeWithException(e)

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    val outcome =
                        attempt {
                            response.use {
                                if (it.code == HTTP_NOT_FOUND) return@use null
                                if (!it.isSuccessful) throw JellyfinHttpException(it.code, path, it.reasonGiven())
                                val source = it.body.source()
                                if (source.request(maxBytes + 1)) throw IOException("File too large")
                                source.readByteArray()
                            }
                        }
                    outcome.fold(cont::resume, cont::resumeWithException)
                }
            },
        )
    }
}
