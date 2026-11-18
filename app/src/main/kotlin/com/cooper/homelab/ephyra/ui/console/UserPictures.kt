package com.cooper.homelab.ephyra.ui.console

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.MAX_PICTURE_BYTES
import com.cooper.homelab.ephyra.server.ServerUser
import com.cooper.homelab.ephyra.server.USER_THUMBNAIL_SIZE
import com.cooper.homelab.ephyra.server.userThumbnail
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Where a user's picture comes from, so a screen can show one without a token or a network dependency. */
interface UserPictureSource {
    suspend fun read(user: ServerUser): Bitmap?

    /** The picture if it is already in memory, so an avatar can draw it on its first frame; null where it is not, or the source cannot say. */
    fun cached(user: ServerUser): Bitmap? = null
}

/** The pictures the app can show, or null where it has none (a preview, a test): every avatar is then its initials. */
internal val LocalUserPictures = staticCompositionLocalOf<UserPictureSource?> { null }

/** A thumbnail's bytes as a bitmap no larger than [USER_THUMBNAIL_SIZE] on its long side, or null when they are not an image. */
internal fun decodeThumbnail(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > USER_THUMBNAIL_SIZE) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * Tagged user thumbnails, scoped to the server and the signed-in account. Only image bytes go to disk: no tokens or
 * request headers. The bounded cache survives reopening the page, and at most four thumbnails are fetched at a time.
 */
class UserPictures(
    private val connection: Connection,
    private val directory: File,
) : UserPictureSource {
    private val permits = Semaphore(CONCURRENT_PICTURES)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Counts the times the cache has been cleared. A read notes it before it asks the server, and keeps what comes back only if
     * it is unchanged, so a fetch already on the wire when the session ends cannot put its picture back (#518).
     */
    @Volatile private var generation = 0

    /** Held by [clear] and by the moment a read keeps a picture, so the one cannot land half-way through the other. */
    private val keeping = Any()
    private val memory =
        object : LruCache<String, Bitmap>(MEMORY_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    /** No image tag means no request. An absent, unreadable or failed image stays the caller's initials. */
    override suspend fun read(user: ServerUser): Bitmap? =
        withContext(connection.io) {
            val tag = user.imageTag?.takeIf { it.isNotBlank() } ?: return@withContext null
            connection
                .consoleCall(TAG) { session, client ->
                    val key = cacheKey("${client.baseUrl}|${session.userId}|${user.id}|$tag")
                    val started = generation
                    permits.withPermit {
                        locks.getOrPut(key) { Mutex() }.withLock {
                            memory.get(key) ?: run {
                                val file = File(directory, "$key.image")
                                val onDisk = file.isFile && file.length() <= MAX_PICTURE_BYTES
                                val bytes =
                                    if (onDisk) {
                                        file.setLastModified(System.currentTimeMillis())
                                        file.readBytes()
                                    } else {
                                        client.userThumbnail(user.id, tag)
                                    }
                                bytes?.let(::decodeThumbnail)?.takeIf { keep(started, key, file, bytes.takeUnless { onDisk }, it) }
                            }
                        }
                    }
                }.getOrNull()
        }

    /** Forgets every picture, in memory and on disk: they are the signed-in account's, and stay no longer than its session. */
    fun clear() {
        synchronized(keeping) {
            generation++
            memory.evictAll()
            locks.clear()
            directory.deleteRecursively()
        }
    }

    /**
     * Puts a picture in the memory cache, and on disk when it was fetched, unless the cache was cleared since the read began:
     * then it belongs to a session that is gone, and the read answers nothing.
     */
    private fun keep(
        started: Int,
        key: String,
        file: File,
        fetched: ByteArray?,
        bitmap: Bitmap,
    ): Boolean =
        synchronized(keeping) {
            if (generation != started) return false
            fetched?.let { attempt { save(file, it) } }
            memory.put(key, bitmap)
            true
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

    private fun cacheKey(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(Locale.ROOT, it) }

    private companion object {
        const val TAG = "JellyfinUserPictures"
        const val CONCURRENT_PICTURES = 4
        const val MEMORY_BYTES = 4 * 1024 * 1024
        const val DISK_BYTES = 20L * 1024 * 1024
    }
}
