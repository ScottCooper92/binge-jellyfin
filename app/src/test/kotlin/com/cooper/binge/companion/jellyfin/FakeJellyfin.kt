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
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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

    /** The event sockets the app opened, and every message it sent on them. */
    val sockets = CopyOnWriteArrayList<WebSocket>()
    val socketMessages = CopyOnWriteArrayList<String>()

    /** The `Data` of the `ForceKeepAlive` the socket opens with, in seconds. */
    var keepAliveSeconds = 60

    /** Sends [message] on every open event socket. */
    fun push(message: String) = sockets.forEach { it.send(message) }

    var quickConnectEnabled = true
    var quickConnectApproved = false

    /** Whether `/Users/Me` answers with an administrator's policy. */
    var administrator = false

    /** The user's configuration, as `/Users/Me` answers it and `/Users/Configuration` replaces it. */
    var userConfiguration =
        """{"AudioLanguagePreference":"eng","SubtitleLanguagePreference":"","SubtitleMode":"Default","HidePlayedInLatest":true}"""

    /** The user's password, as `/Users/Password` checks and changes it. */
    var password = "old-secret"

    /** `/Sessions`: one stream playing, a movie being transcoded, and a session playing nothing. */
    var sessions = SESSIONS

    /** `/ScheduledTasks`: the library scan, an idle task that last failed, and a hidden one. */
    var scheduledTasks = TASKS

    /** The one Quick Connect code another device is waiting to have approved. */
    var approvableCode = "654321"
    var playbackInfo: String = directPlayInfo()

    init {
        server.dispatcher = this
        server.start()
    }

    val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    override fun close() {
        // An event socket still open holds the server's shutdown, so close them first.
        sockets.forEach { it.close(GOING_AWAY, null) }
        server.close()
    }

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
        if (path == "/socket") return socket(request)
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
            path == "/Users/Me" ->
                json("""{"Name":"Claude","Policy":{"IsAdministrator":$administrator},"Configuration":$userConfiguration}""")
            path == "/Users/Configuration" -> {
                userConfiguration = request.body?.utf8().orEmpty()
                MockResponse(code = 204)
            }
            path == "/Localization/Cultures" -> json(CULTURES)
            path == "/Users/Password" -> changePassword(request)
            path == "/Sessions" -> json(sessions)
            path == "/Library/VirtualFolders" -> json(LIBRARIES)
            path == "/ScheduledTasks" -> json(scheduledTasks)
            path.startsWith("/ScheduledTasks/Running/") -> MockResponse(code = 204)
            path.startsWith("/Items/") && path.endsWith("/Refresh") -> MockResponse(code = 204)
            path.startsWith("/Sessions/") && path.endsWith("/Playing/Stop") -> MockResponse(code = 204)
            path == "/QuickConnect/Authorize" ->
                if (request.url.queryParameter("code") == approvableCode) json("true") else MockResponse(code = 404)
            path == "/Items" -> items(request)
            path.startsWith("/Shows/") && path.endsWith("/Episodes") -> episodes(path.split('/')[2])
            path == "/UserItems/Resume" -> paged(request, items.filter { it.positionTicks > 0 })
            path == "/Shows/NextUp" ->
                paged(request, items.filter { it.type == "Episode" && !it.played }.distinctBy { it.seriesId })
            path.startsWith("/UserPlayedItems/") -> setPlayed(path.removePrefix("/UserPlayedItems/"), request.method == "POST")
            path.endsWith("/PlaybackInfo") -> json(playbackInfo)
            path.startsWith("/Sessions/Playing") -> MockResponse(code = 204)
            else -> MockResponse(code = 404)
        }
    }

    private fun socket(request: RecordedRequest): MockResponse {
        if (request.headers["Authorization"]?.contains("Token=") != true) return MockResponse(code = 403)
        return MockResponse
            .Builder()
            .webSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        sockets += webSocket
                        webSocket.send("""{"MessageType":"ForceKeepAlive","Data":$keepAliveSeconds}""")
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        socketMessages += text
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        sockets -= webSocket
                    }
                },
            ).build()
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

    /** [list] paged by the request's `startIndex` and `limit`, with the whole list's count. */
    private fun paged(
        request: RecordedRequest,
        list: List<FakeItem>,
    ): MockResponse {
        val start = request.url.queryParameter("startIndex")?.toInt() ?: 0
        val limit = request.url.queryParameter("limit")?.toInt() ?: list.size
        return page(list.drop(start).take(limit), total = list.size)
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

    private fun changePassword(request: RecordedRequest): MockResponse {
        val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
        if (body["CurrentPw"]?.jsonPrimitive?.content != password) return MockResponse(code = 403)
        password = body.getValue("NewPw").jsonPrimitive.content
        return MockResponse(code = 204)
    }

    private fun json(body: String): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    companion object {
        private const val GOING_AWAY = 1001

        /** Two libraries, and an entry with no id, which isn't one. */
        const val LIBRARIES =
            """[
            {"Name":"Movies","CollectionType":"movies","ItemId":"lib-movies","Locations":["/media/movies"]},
            {"Name":"Shows","CollectionType":"tvshows","ItemId":"lib-shows","Locations":["/media/tv","/media/anime"]},
            {"Name":"Broken"}
            ]"""

        const val TASKS =
            """[
            {"Id":"t-scan","Key":"RefreshLibrary","Name":"Scan Media Library","Category":"Library","State":"Idle",
             "Description":"Scans your media library for new files.",
             "LastExecutionResult":{"Status":"Completed","EndTimeUtc":"2026-10-31T18:00:00Z"}},
            {"Id":"t-clean","Key":"CleanCache","Name":"Clean Cache Directory","Category":"Maintenance","State":"Idle",
             "LastExecutionResult":{"Status":"Failed","EndTimeUtc":"2026-10-30T03:00:00Z","ErrorMessage":"Disk full"}},
            {"Id":"t-guide","Key":"RefreshGuide","Name":"Refresh Guide","Category":"Live TV","State":"Idle","IsHidden":true}
            ]"""

        const val SESSIONS =
            """[
            {"Id":"s-tv","UserName":"Claude","DeviceName":"Living room","Client":"Jellyfin Android TV",
             "PlayState":{"PositionTicks":6000000000,"IsPaused":false,"PlayMethod":"Transcode"},
             "NowPlayingItem":{"Name":"Sintel","RunTimeTicks":8880000000},
             "TranscodingInfo":{"TranscodeReasons":["AudioCodecNotSupported","VideoCodecNotSupported"]}},
            {"Id":"s-idle","UserName":"Claude","DeviceName":"Phone","Client":"Binge Jellyfin"}
            ]"""

        /** A few of `/Localization/Cultures`, French with both of its three-letter names and two Spanishes under one code, as the server lists them. */
        private const val CULTURES =
            """[
            {"Name":"English","DisplayName":"English","TwoLetterISOLanguageName":"en","ThreeLetterISOLanguageName":"eng","ThreeLetterISOLanguageNames":["eng"]},
            {"Name":"French","DisplayName":"French","TwoLetterISOLanguageName":"fr","ThreeLetterISOLanguageName":"fre","ThreeLetterISOLanguageNames":["fre","fra"]},
            {"Name":"Spanish","DisplayName":"Spanish; Castilian","TwoLetterISOLanguageName":"es","ThreeLetterISOLanguageName":"spa","ThreeLetterISOLanguageNames":["spa"]},
            {"Name":"Spanish; Latin","DisplayName":"Spanish; Latin","TwoLetterISOLanguageName":"es-mx","ThreeLetterISOLanguageName":"spa","ThreeLetterISOLanguageNames":["spa"]},
            {"Name":"Klingon","DisplayName":"Klingon","TwoLetterISOLanguageName":"","ThreeLetterISOLanguageName":"tlh","ThreeLetterISOLanguageNames":["tlh"]},
            {"Name":"Nameless","DisplayName":"","TwoLetterISOLanguageName":"","ThreeLetterISOLanguageName":"","ThreeLetterISOLanguageNames":[]}
            ]"""

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
