package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.MAX_PICTURE_BYTES
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.USER_THUMBNAIL_SIZE
import com.cooper.binge.companion.jellyfin.server.userThumbnail
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Tagged user thumbnails, scoped to the server and the signed-in account. Only image bytes go to disk: no tokens or
 * request headers. The bounded cache survives reopening the page, and at most four thumbnails are fetched at a time.
 */
class UserPictures(
    private val connection: Connection,
    private val directory: File,
) {
    private val permits = Semaphore(CONCURRENT_PICTURES)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val memory =
        object : LruCache<String, Bitmap>(MEMORY_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    /** No image tag means no request. An absent, unreadable or failed image stays the caller's initials. */
    suspend fun read(user: ServerUser): Bitmap? =
        withContext(connection.io) {
            val tag = user.imageTag?.takeIf { it.isNotBlank() } ?: return@withContext null
            connection
                .consoleCall(TAG) { session, client ->
                    val key = cacheKey("${client.baseUrl}|${session.userId}|${user.id}|$tag")
                    permits.withPermit {
                        locks.getOrPut(key) { Mutex() }.withLock {
                            memory.get(key) ?: run {
                                val file = File(directory, "$key.image")
                                val bytes =
                                    if (file.isFile && file.length() <= MAX_PICTURE_BYTES) {
                                        file.setLastModified(System.currentTimeMillis())
                                        file.readBytes()
                                    } else {
                                        client.userThumbnail(user.id, tag)?.also { bytes -> attempt { save(file, bytes) } }
                                    }
                                bytes?.let(::decode)?.also { memory.put(key, it) }
                            }
                        }
                    }
                }.getOrNull()
        }

    private fun save(
        file: File,
        bytes: ByteArray,
    ) {
        directory.mkdirs()
        val temporary = File(directory, "${file.name}.tmp")
        try {
            temporary.writeBytes(bytes)
            temporary.renameTo(file)
        } finally {
            temporary.delete()
        }
        var kept = 0L
        directory.listFiles()?.filter { it.extension == "image" }?.sortedByDescending { it.lastModified() }?.forEach {
            kept += it.length()
            if (kept > DISK_BYTES) it.delete()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > USER_THUMBNAIL_SIZE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun cacheKey(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(Locale.ROOT, it) }

    private companion object {
        const val TAG = "JellyfinUserPictures"
        const val CONCURRENT_PICTURES = 4
        const val MEMORY_BYTES = 4 * 1024 * 1024
        const val DISK_BYTES = 20L * 1024 * 1024
    }
}
