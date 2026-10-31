package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A Jellyfin answer other than 2xx. 401 is a broken session; 404 an item the server does not have. */
class JellyfinHttpException(
    val code: Int,
    path: String,
) : IOException("Jellyfin answered $code for $path")

/** Who this app is to the server: the `Authorization: MediaBrowser …` header's fields. */
data class ClientIdentity(
    val client: String,
    val device: String,
    val deviceId: String,
    val version: String,
)

/**
 * The transport to one Jellyfin server: the header that names this app (and carries the token once
 * signed in), and JSON over HTTP on [io]. The calls themselves are extensions in `JellyfinAuth.kt`
 * (sign-in) and `JellyfinLibraryApi.kt` (the library and playback). Their shapes are the server's own
 * OpenAPI document (`/api-docs/openapi.json`).
 */
class JellyfinClient(
    private val http: OkHttpClient,
    val baseUrl: HttpUrl,
    private val identity: ClientIdentity,
    private val io: CoroutineDispatcher,
    private val token: String? = null,
) {
    fun withToken(token: String): JellyfinClient = JellyfinClient(http, baseUrl, identity, io, token)

    /**
     * The header the server reads who we are from, with the token once signed in. Each field is
     * percent-encoded, as Jellyfin's own clients do and the server decodes: a device model can carry
     * characters a header may not (non-ASCII), or ones the header's syntax splits on (`,` and `"`).
     */
    fun authorization(): String =
        buildString {
            append("MediaBrowser ")
            append("Client=\"").append(identity.client.encoded()).append("\", ")
            append("Device=\"").append(identity.device.encoded()).append("\", ")
            append("DeviceId=\"").append(identity.deviceId.encoded()).append("\", ")
            append("Version=\"").append(identity.version.encoded()).append('"')
            token?.let { append(", Token=\"").append(it).append('"') }
        }

    fun url(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): HttpUrl {
        val builder = baseUrl.newBuilder()
        path.trimStart('/').split('/').forEach { builder.addPathSegment(it) }
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    /** A server-relative URL the server handed back (a transcoding or subtitle URL), made absolute. */
    fun resolve(relative: String): String = baseUrl.toString().trimEnd('/') + "/" + relative.trimStart('/')

    internal suspend fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): JsonElement = call(Request.Builder().url(url(path, query)).get(), path)

    internal suspend fun post(
        path: String,
        body: JsonObject?,
        query: Map<String, String> = emptyMap(),
    ): JsonElement = call(Request.Builder().url(url(path, query)).post((body?.toString() ?: "").toRequestBody(JSON)), path)

    internal suspend fun delete(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): JsonElement = call(Request.Builder().url(url(path, query)).delete(), path)

    private suspend fun call(
        builder: Request.Builder,
        path: String,
    ): JsonElement {
        val request = builder.header("Authorization", authorization()).header("Accept", "application/json").build()
        val text = awaitBody(http.newCall(request), path)
        return withContext(io) { if (text.isBlank()) JsonNull else PARSER.parseToJsonElement(text) }
    }

    /** Runs [call] and reads its body; cancelling the caller cancels the [Call], closing the socket. */
    private suspend fun awaitBody(
        call: Call,
        path: String,
    ): String =
        suspendCancellableCoroutine { cont ->
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
                                    if (!it.isSuccessful) throw JellyfinHttpException(it.code, path)
                                    it.body.string()
                                }
                            }
                        outcome.fold(cont::resume, cont::resumeWithException)
                    }
                },
            )
        }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val PARSER = Json { ignoreUnknownKeys = true }

        /** [raw] as a server base URL, adding http:// when no scheme was typed; null when it is not a URL. */
        fun parseBaseUrl(raw: String): HttpUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()
        }
    }
}

/** Percent-encoded as a URI component: `%20` for a space, not the form encoding's `+`. */
private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
