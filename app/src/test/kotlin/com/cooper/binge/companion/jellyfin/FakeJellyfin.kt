package com.cooper.binge.companion.jellyfin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.SecretCipher
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** One title or episode on the fake server. Ticks are Jellyfin's: 10,000 to the millisecond. */
data class FakeItem(
    val id: String,
    val name: String,
    val type: String,
    val tmdbId: Int? = null,
    val runTimeTicks: Long = 60 * 60 * 10_000_000L,
    val played: Boolean = false,
    val positionTicks: Long = 0L,
    val lastPlayed: String? = null,
    val seriesId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val locationType: String = "FileSystem",
)

/**
 * A Jellyfin server in a [MockWebServer], answering the calls this app makes with the shapes the
 * server's OpenAPI document gives them. Every request is recorded; [status] overrides one path, [notJson] answers one with HTML and [slow] holds one back.
 */
class FakeJellyfin : Dispatcher() {
    val server = MockWebServer()
    val items = CopyOnWriteArrayList<FakeItem>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val status = mutableMapOf<String, Int>()
    val slow = mutableSetOf<String>()
    val notJson = mutableSetOf<String>()
    var quickConnectEnabled = true
    var quickConnectApproved = false
    var playbackInfo: String = directPlayInfo()

    init {
        server.dispatcher = this
        server.start()
    }

    val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    override fun close() = server.close()

    /** The JSON body of the last request to [path]. */
    fun lastBody(path: String): JsonObject =
        Json
            .parseToJsonElement(
                requests
                    .last { it.url.encodedPath == path }
                    .body
                    ?.utf8()
                    .orEmpty(),
            ).jsonObject

    fun hits(path: String): Int = requests.count { it.url.encodedPath == path }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val path = request.url.encodedPath
        if (path in slow) {
            return MockResponse
                .Builder()
                .code(200)
                .headersDelay(30, TimeUnit.SECONDS)
                .build()
        }
        status[path]?.let { return MockResponse(code = it) }
        if (path in notJson) {
            return MockResponse
                .Builder()
                .code(200)
                .body("<html>Sign in to the Wi-Fi</html>")
                .build()
        }
        return when {
            path == "/System/Info/Public" ->
                json("""{"ServerName":"truenas","Version":"10.11.6","Id":"server-1","StartupWizardCompleted":true}""")
            path == "/Users/AuthenticateByName" -> {
                val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
                if (body["Pw"].toString() == "\"right\"") signedIn() else MockResponse(code = 401)
            }
            path == "/QuickConnect/Enabled" -> json(quickConnectEnabled.toString())
            path == "/QuickConnect/Initiate" -> json("""{"Secret":"qc-secret","Code":"123456"}""")
            path == "/QuickConnect/Connect" -> json("""{"Authenticated":$quickConnectApproved}""")
            path == "/Users/AuthenticateWithQuickConnect" -> signedIn()
            path == "/Sessions/Logout" -> MockResponse(code = 204)
            path == "/Users/Me" -> json("""{"Name":"Claude"}""")
            path == "/Items" -> items(request)
            path.startsWith("/Shows/") && path.endsWith("/Episodes") -> episodes(path.split('/')[2])
            path == "/UserItems/Resume" -> page(items.filter { it.positionTicks > 0 })
            path == "/Shows/NextUp" -> page(items.filter { it.type == "Episode" && !it.played }.take(1))
            path.startsWith("/UserPlayedItems/") -> setPlayed(path.removePrefix("/UserPlayedItems/"), request.method == "POST")
            path.endsWith("/PlaybackInfo") -> json(playbackInfo)
            path.startsWith("/Sessions/Playing") -> MockResponse(code = 204)
            else -> MockResponse(code = 404)
        }
    }

    private fun signedIn(): MockResponse =
        json("""{"AccessToken":"token-1","ServerId":"server-1","User":{"Id":"user-1","Name":"Claude"}}""")

    private fun items(request: RecordedRequest): MockResponse {
        val ids = request.url.queryParameter("ids")
        val selected =
            if (ids != null) {
                items.filter { it.id == ids }
            } else {
                val types =
                    request.url
                        .queryParameter("includeItemTypes")
                        .orEmpty()
                        .split(',')
                items.filter { it.type in types && it.tmdbId != null }
            }
        val start = request.url.queryParameter("startIndex")?.toInt() ?: 0
        val limit = request.url.queryParameter("limit")?.toInt() ?: selected.size
        return page(selected.drop(start).take(limit), total = selected.size)
    }

    private fun episodes(seriesId: String): MockResponse = page(items.filter { it.seriesId == seriesId })

    private fun setPlayed(
        id: String,
        played: Boolean,
    ): MockResponse {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return MockResponse(code = 404)
        items[index] = items[index].copy(played = played)
        return json("{}")
    }

    private fun page(
        list: List<FakeItem>,
        total: Int = list.size,
    ): MockResponse = json("""{"Items":[${list.joinToString(",") { it.toJson() }}],"TotalRecordCount":$total}""")

    private fun FakeItem.toJson(): String =
        buildString {
            append("""{"Id":"$id","Name":"$name","Type":"$type","RunTimeTicks":$runTimeTicks,"LocationType":"$locationType",""")
            append(""""ProviderIds":{${tmdbId?.let { "\"Tmdb\":\"$it\"" }.orEmpty()}},""")
            append(""""UserData":{"Played":$played,"PlaybackPositionTicks":$positionTicks""")
            lastPlayed?.let { append(""","LastPlayedDate":"$it"""") }
            append("}")
            seriesId?.let { append(""","SeriesId":"$it"""") }
            season?.let { append(""","ParentIndexNumber":$it""") }
            episode?.let { append(""","IndexNumber":$it""") }
            append(""","ImageTags":{"Primary":"tag"}}""")
        }

    private fun json(body: String): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    companion object {
        fun directPlayInfo(): String =
            """{"PlaySessionId":"play-1","MediaSources":[{"Id":"source-1","Container":"mp4","SupportsDirectPlay":true,""" +
                """"SupportsDirectStream":true,"RunTimeTicks":36000000000,"MediaStreams":[""" +
                """{"Type":"Subtitle","DeliveryMethod":"External","DeliveryUrl":"/Videos/x/Subtitles/2/0/Stream.vtt","Codec":"webvtt",""" +
                """"Language":"eng","DisplayTitle":"English","IsForced":false,"IsHearingImpaired":true},""" +
                """{"Type":"Subtitle","DeliveryMethod":"Embed","Codec":"pgssub"}]}]}"""

        fun transcodeInfo(): String =
            """{"PlaySessionId":"play-2","MediaSources":[{"Id":"source-2","Container":"mkv","SupportsDirectPlay":false,""" +
                """"SupportsDirectStream":false,"TranscodingUrl":"/videos/x/master.m3u8?MediaSourceId=source-2&ApiKey=token-1","MediaStreams":[]}]}"""
    }
}

/** Stores the plaintext as the "ciphertext": the Keystore is not there on a JVM. */
object PlainCipher : SecretCipher {
    override fun encrypt(plaintext: String): String = "enc:$plaintext"

    override fun decrypt(ciphertext: String): String? = ciphertext.removePrefix("enc:").takeIf { ciphertext.startsWith("enc:") }
}

/** A [SessionStore] on a file of its own, in a directory that is deleted with the JVM. */
fun testSessionStore(
    cipher: SecretCipher = PlainCipher,
    io: CoroutineDispatcher = Dispatchers.IO,
): SessionStore {
    val dir: File = Files.createTempDirectory("session").toFile().apply { deleteOnExit() }
    return SessionStore(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(dir, "session.preferences_pb") },
        ),
        cipher,
        io,
    )
}

fun FakeJellyfin.session(): JellyfinSession =
    JellyfinSession(
        baseUrl = baseUrl,
        serverName = "truenas",
        serverId = "server-1",
        serverVersion = "10.11.6",
        userId = "user-1",
        userName = "Claude",
        token = "token-1",
    )
