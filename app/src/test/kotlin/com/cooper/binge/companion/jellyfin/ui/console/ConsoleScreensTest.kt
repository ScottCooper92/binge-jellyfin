package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerSettings
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.server.TaskRun
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import com.cooper.binge.companion.jellyfin.server.UserView
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The console's pages, driven as a user would: each tap reaches the action it should. */
@RunWith(RobolectricTestRunner::class)
class ConsoleScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val stream =
        NowPlaying("tv", "Ana", "Living room", "Web", "Sintel", null, 1, 4, paused = true, PlayMethod.Transcode, listOf("AudioIsExternal"))

    private val cultures = listOf(Culture("English", "en", listOf("eng")), Culture("French", "fr", listOf("fre", "fra")))

    @Test
    fun `now playing shows each stream, and Stop asks before it stops`() {
        val asked = mutableListOf<NowPlaying>()
        var confirmed = false
        val actions = NowPlayingActions(onStop = { asked += it }, onConfirmStop = { confirmed = true })
        var state by mutableStateOf<NowPlayingUiState>(NowPlayingUiState.Ready(listOf(stream)))
        compose.setContent { JellyfinTheme { NowPlayingScreen(state, administrator = false, actions) } }
        compose.onNodeWithText("Sintel").assertExists()
        compose.onNodeWithText("Transcoding: the device can't play the audio").assertExists()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf(stream), asked)
        state = NowPlayingUiState.Ready(listOf(stream), confirming = stream)
        compose.onNodeWithText("Stop Sintel?").assertExists()
        compose.onAllNodesWithText("Stop")[1].performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun `now playing with nothing playing says so, and a failed read offers Try again`() {
        var retried = false
        var state by mutableStateOf<NowPlayingUiState>(NowPlayingUiState.Ready(emptyList()))
        compose.setContent {
            JellyfinTheme {
                NowPlayingScreen(
                    state,
                    administrator = true,
                    NowPlayingActions(onRetry = { retried = true }),
                )
            }
        }
        compose.onNodeWithText("Nothing's playing right now.").assertExists()
        compose.onNodeWithText("Everyone streaming from this server, as it happens.").assertExists()
        state = NowPlayingUiState.Failed(ConsoleFailure.Unreachable)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `each direct method reads as itself`() {
        val streams =
            listOf(
                stream.copy(sessionId = "a", method = PlayMethod.DirectPlay),
                stream.copy(sessionId = "b", method = PlayMethod.DirectStream),
            )
        compose.setContent {
            JellyfinTheme {
                NowPlayingScreen(
                    NowPlayingUiState.Ready(streams),
                    administrator = true,
                    NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Direct play: the file as it is").assertExists()
        compose.onNodeWithText("Direct stream: repackaged, not re-encoded").assertExists()
    }

    @Test
    fun `a language row opens its sheet, and a pick reaches the action`() {
        val audio = mutableListOf<String>()
        val modes = mutableListOf<SubtitleMode>()
        val state = PlaybackUiState.Ready(PlaybackPreferences("eng", "", SubtitleMode.Default), cultures)
        compose.setContent {
            JellyfinTheme { PlaybackScreen(state, PlaybackActions(onAudio = { audio += it }, onSubtitleMode = { modes += it })) }
        }
        compose.onNodeWithText("Any language").assertExists()
        compose.onNodeWithText("Audio language").performClick()
        compose.onNodeWithText("French").performClick()
        assertEquals(listOf("fre"), audio)
        compose.onNodeWithText("Subtitle mode").performScrollTo().performClick()
        compose.onNodeWithText("Always").performClick()
        assertEquals(listOf(SubtitleMode.Always), modes)
    }

    @Test
    fun `a playback switch hands its new value to the action`() {
        val switched = mutableListOf<String>()
        val state = PlaybackUiState.Ready(PlaybackPreferences("eng", "", SubtitleMode.Default, autoplayNextEpisode = false), cultures)
        val actions =
            PlaybackActions(
                onPlayDefaultAudioTrack = { switched += "default:$it" },
                onRememberAudio = { switched += "audio:$it" },
                onRememberSubtitles = { switched += "subtitles:$it" },
                onAutoplayNextEpisode = { switched += "next:$it" },
            )
        compose.setContent { JellyfinTheme { PlaybackScreen(state, actions) } }
        compose.onNodeWithText("Play the default track").performClick()
        compose.onNodeWithText("Remember my audio choice").performClick()
        compose.onNodeWithText("Remember my subtitle choice").performScrollTo().performClick()
        compose.onNodeWithText("Play the next episode automatically").performScrollTo().performClick()
        assertEquals(listOf("default:false", "audio:false", "subtitles:false", "next:true"), switched)
    }

    @Test
    fun `the home screen page lists the libraries, and a library's page hands its changes on`() {
        val calls = mutableListOf<String>()
        val views = listOf(UserView("a", "Movies", "movies"), UserView("b", "Shows", "tvshows"))
        val list = HomeLayoutUiState.Ready(views, HomeLayout(latestExcludes = setOf("b")))
        val actions =
            HomeLayoutActions(
                onOpen = { calls += "open:$it" },
                onInMyMedia = { id, on -> calls += "media:$id:$on" },
                onInLatest = { id, on -> calls += "latest:$id:$on" },
                onMove = { id, by -> calls += "move:$id:$by" },
            )
        var state by mutableStateOf<HomeLayoutUiState>(list)
        compose.setContent { JellyfinTheme { HomeLayoutScreen(state, actions) } }
        compose.onNodeWithText("In My Media and Latest").assertExists()
        compose.onNodeWithText("In My Media").assertExists()
        compose.onNodeWithText("Shows").performClick()
        state = list.copy(openId = "b")
        compose.onNodeWithText("Show in Latest").performClick()
        compose.onNodeWithText("Show in My Media").performClick()
        compose.onNodeWithText("Group with other libraries").assertDoesNotExist()
        compose.onNodeWithText("Move down").assertIsNotEnabled()
        compose.onNodeWithText("Move up").performClick()
        assertEquals(listOf("open:b", "latest:b:true", "media:b:false", "move:b:-1"), calls)
    }

    @Test
    fun `the server settings page hands its switches and dialogs on, and hides the splash screen before 10_9`() {
        val calls = mutableListOf<String>()
        val state = ServerSettingsUiState.Ready(ServerSettings("truenas", "en", "GB"), Branding())
        val actions =
            ServerSettingsActions(
                onEdit = { calls += "edit:$it" },
                onQuickConnect = { calls += "qc:$it" },
                onSplashscreen = { calls += "splash:$it" },
            )
        var splash by mutableStateOf(true)
        compose.setContent { JellyfinTheme { ServerSettingsScreen(state, actions, splashscreenAvailable = splash) } }
        compose.onNodeWithText("Server name").performClick()
        compose.onNodeWithText("Quick Connect").performScrollTo().performClick()
        compose.onNodeWithText("Login disclaimer").performScrollTo().performClick()
        compose.onNodeWithText("Splash screen").performScrollTo().performClick()
        assertEquals(listOf("edit:Name", "qc:false", "edit:LoginDisclaimer", "splash:true"), calls)
        splash = false
        compose.onNodeWithText("Splash screen").assertDoesNotExist()
    }

    @Test
    fun `every subtitle mode is offered, with what it does`() {
        val state = PlaybackUiState.Ready(PlaybackPreferences("", "", SubtitleMode.None), cultures, saveFailed = ConsoleFailure.Refused)
        compose.setContent { JellyfinTheme { PlaybackScreen(state, PlaybackActions()) } }
        compose.onNodeWithText("Not saved. Something went wrong. Try again.").assertExists()
        compose.onNodeWithText("Subtitle mode").performScrollTo().performClick()
        listOf("Follow the file", "Smart", "Only forced", "Always").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("You can still turn them on while watching").assertExists()
    }

    @Test
    fun `the password form hands each field and the submit to its view model`() {
        val typed = mutableListOf<String>()
        var changed = false
        val actions =
            PasswordActions(onCurrent = { typed += "c:$it" }, onNew = { typed += "n:$it" }, onConfirm = { typed += "r:$it" }, onChange = {
                changed =
                    true
            })
        compose.setContent { JellyfinTheme { PasswordScreen(PasswordUiState(new = "a", confirm = "a"), actions) } }
        compose.onNodeWithText("Current password").performTextInput("x")
        compose.onNodeWithText("New password again").performTextInput("y")
        compose.onNode(hasText("Change password") and hasClickAction()).performScrollTo().performClick()
        // The fields are the view model's: each keystroke goes to it, against the value it holds.
        assertTrue("c:x" in typed)
        assertTrue(typed.any { it.startsWith("r:") && 'y' in it })
        assertEquals(true, changed)
    }

    @Test
    fun `each password result reads as it should`() {
        var state by mutableStateOf(PasswordUiState(result = PasswordResult.Changed))
        compose.setContent { JellyfinTheme { PasswordScreen(state, PasswordActions()) } }
        mapOf(
            PasswordResult.Changed to "Changed. Your other devices will ask for the new password.",
            PasswordResult.WrongCurrent to "That isn't your current password.",
            PasswordResult.Mismatch to "The new passwords don't match.",
            PasswordResult.Refused to "Something went wrong. Try again.",
            PasswordResult.Unreachable to "Couldn't reach your Jellyfin server",
        ).forEach { (result, text) ->
            state = PasswordUiState(result = result)
            compose.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun `libraries scan one or all as job rows, stop the scan of everything, and show how a run went`() {
        val scanned = mutableListOf<String>()
        var everything = 0
        var stopped = false
        val libraries =
            listOf(
                ServerLibrary("m", "Movies", "movies", listOf("/media/movies")),
                ServerLibrary("s", "Shows", "tvshows", listOf("/media/tv")),
            )
        val scanTask = ServerTask("t", LIBRARY_SCAN_TASK, "Scan Media Library", "", "Library", false, false, null, null)
        var state by mutableStateOf(LibrariesUiState.Ready(libraries, TaskBoard(listOf(scanTask))))
        val actions =
            LibrariesActions(onScan = { scanned += it.id }, onScanEverything = { everything++ }, onStopEverything = { stopped = true })
        compose.setContent { JellyfinTheme { LibrariesScreen(state, actions) } }
        compose.onNodeWithText("Hasn't run yet").assertExists()
        compose.onAllNodesWithContentDescription("Scan")[0].performClick()
        assertEquals(1, everything)
        compose.onAllNodesWithContentDescription("Scan")[1].performClick()
        assertEquals(listOf("m"), scanned)
        state =
            state.copy(
                board = TaskBoard(listOf(scanTask.copy(running = true, percent = 12))),
                scanning = mapOf("m" to LibraryScan(40, started = true)),
                outcomes = mapOf("s" to JobOutcome.Failed),
                actionFailed = ConsoleFailure.Unreachable,
            )
        compose.onNodeWithText("Running… 12%").assertExists()
        compose.onNodeWithText("Running… 40%").assertExists()
        compose.onNodeWithText("Failed").assertExists()
        compose.onNodeWithContentDescription("Stop").performClick()
        assertTrue(stopped)
        val notStarted = "The scan didn't start. Couldn't reach your Jellyfin server"
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(notStarted))
        compose.onNodeWithText(notStarted).assertExists()
        state = state.copy(board = TaskBoard(listOf(scanTask)), scanning = emptyMap(), outcomes = mapOf("m" to JobOutcome.Succeeded))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Done"))
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun `each kind of library has its icon, and a mixed one a folder`() {
        val icons = listOf("movies", "tvshows", "music", "books", "photos", "homevideos", "", "boxsets").map(::typeIcon)
        assertEquals(6, icons.distinct().size)
        assertEquals(typeIcon(""), typeIcon("boxsets"))
    }

    @Test
    fun `tasks group by category, run and stop in place, and say how each last ran`() {
        val ran = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val scan = ServerTask("a", LIBRARY_SCAN_TASK, "Scan Media Library", "", "Library", false, false, null, null)
        val clean =
            ServerTask("b", "CleanCache", "Clean Cache Directory", "", "Maintenance", true, false, 30, TaskRun(TaskStatus.Failed, 0, null))
        val failed = ServerTask("c", "OptimizeDatabaseTask", "Optimise database", "", "Maintenance", false, false, null, clean.lastRun)
        var state by mutableStateOf(TasksUiState.Ready(TaskBoard(listOf(scan, clean, failed))))
        compose.setContent {
            JellyfinTheme { TasksScreen(state, TasksActions(onRun = { ran += it.id }, onStop = { stopped += it.id }), now = 0) }
        }
        compose.onNodeWithText("LIBRARY").assertExists()
        compose.onNodeWithText("MAINTENANCE").assertExists()
        compose.onNodeWithText("Running… 30%").assertExists()
        compose.onNodeWithText("Hasn't run yet").assertExists()
        compose.onAllNodesWithContentDescription("Run")[0].performClick()
        compose.onNodeWithContentDescription("Stop").performClick()
        assertEquals(listOf("a"), ran)
        assertEquals(listOf("b"), stopped)
        state = state.copy(actionFailed = ConsoleFailure.Refused)
        val notSent = "That didn't reach the server. Something went wrong. Try again."
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(notSent))
        compose.onNodeWithText(notSent).assertExists()
    }

    @Test
    fun `each kind of task has its icon, and one this app doesn't know a clock`() {
        val keys =
            listOf(
                LIBRARY_SCAN_TASK,
                "AudioNormalization",
                "DownloadSubtitles",
                "DownloadLyrics",
                "RefreshPeople",
                "RefreshChapterImages",
                "DeleteCacheFiles",
                "PluginUpdates",
                "RefreshGuide",
                "OptimizeDatabaseTask",
                "Something",
            )
        assertEquals(keys.size, keys.map(::taskIcon).distinct().size)
    }

    @Test
    fun `activity groups by day, marks errors, and asks for more at the end`() {
        var more = 0
        val now = 1_793_570_400_000L
        val entries =
            listOf(
                ActivityEntry(
                    2,
                    "Failed login attempt from sam",
                    "IP address: 10.0.0.9",
                    "AuthenticationFailed",
                    now - 60_000,
                    Severity.Error,
                ),
                ActivityEntry(1, "sam is online from TV", null, "SessionStarted", now - 2 * 86_400_000L, Severity.Information),
            )
        var state by mutableStateOf<ActivityUiState>(ActivityUiState.Ready(entries, total = 3))
        compose.setContent {
            JellyfinTheme { ActivityScreen(state, ActivityActions(onLoadMore = { more++ }), now = now, zone = java.time.ZoneOffset.UTC) }
        }
        compose.onNodeWithText("Failed login attempt from sam").assertExists()
        compose.onNodeWithText("sam is online from TV").assertExists()
        compose.waitUntil { more == 1 }
        state = ActivityUiState.Ready(entries, total = 3, moreFailed = ConsoleFailure.Unreachable)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Try again"))
        compose.onNodeWithText("Try again").performClick()
        assertEquals(2, more)
        state = ActivityUiState.Ready(emptyList(), total = 0)
        compose.onNodeWithText("Nothing has happened on this server yet.").assertExists()
    }

    @Test
    fun `each kind of activity has its icon, and one this app doesn't know an information mark`() {
        val types =
            listOf(
                "SessionStarted",
                "SessionEnded",
                "AuthenticationFailed",
                "AuthenticationSucceeded",
                "VideoPlaybackStopped",
                "VideoPlayback",
                "UserCreated",
                "PluginInstalled",
                "ScheduledTaskFailed",
                "SubtitleDownloadFailure",
                "Something",
            )
        assertEquals(types.size, types.map(::activityIcon).distinct().size)
    }

    @Test
    fun `devices rename on a tap, remove after a confirm, and this one has no Remove`() {
        val renamed = mutableListOf<String>()
        val asked = mutableListOf<ServerDevice?>()
        var removed = false
        val tv = ServerDevice("tv", "SHIELD", null, "Jellyfin Android TV", "sam", null)
        val mine = ServerDevice("me", "Pixel", null, "Jellyfin Companion", "Claude", null)
        var state by mutableStateOf(DevicesUiState.Ready(listOf(tv, mine), thisDevice = "me"))
        val actions =
            DevicesActions(
                onAskRename = { state = state.copy(renaming = it) },
                onRename = { renamed += it },
                onAskRemove = { asked += it },
                onRemove = { removed = true },
            )
        compose.setContent { JellyfinTheme { DevicesScreen(state, actions) } }
        compose.onNodeWithText("This device. Sign out here instead.").assertExists()
        compose.onAllNodesWithContentDescription("Remove").assertCountEquals(1)
        compose.onNodeWithContentDescription("Remove").performClick()
        assertEquals(listOf<ServerDevice?>(tv), asked)
        compose.onNodeWithText("SHIELD").performClick()
        compose.onNodeWithText("Name").performTextInput("Den")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("Den"), renamed)
        state = state.copy(renaming = null, removing = tv, actionFailed = ConsoleFailure.Refused)
        compose.onNodeWithText("Remove SHIELD?").assertExists()
        compose.onAllNodesWithText("Remove")[0].performClick()
        assertTrue(removed)
    }

    @Test
    fun `each kind of device has its icon`() {
        val icons =
            listOf(
                deviceIcon("Jellyfin Android TV", "SHIELD"),
                deviceIcon("Jellyfin Web", "Firefox"),
                deviceIcon("Jellyfin Companion", "Pixel 10"),
                deviceIcon("Kodi", "Den box"),
            )
        assertEquals(4, icons.distinct().size)
    }

    @Test
    fun `users open a page whose switches, password and delete reach their actions, and mine has none of the risky ones`() {
        val calls = mutableListOf<String>()
        val policy = { admin: Boolean, all: Boolean ->
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"IsAdministrator":$admin,"IsDisabled":false,"EnableAllFolders":$all,"EnabledFolders":["m"]}""",
            ) as kotlinx.serialization.json.JsonObject
        }
        val me = ServerUser("me", "Claude", true, null, policy(true, true))
        val sam = ServerUser("sam", "Sam", false, null, policy(false, false))
        val libraries = listOf(ServerLibrary("m", "Movies", "movies", emptyList()), ServerLibrary("s", "Shows", "tvshows", emptyList()))
        var state by mutableStateOf(UsersUiState.Ready(listOf(me, sam), libraries, me = "me"))
        val actions =
            UsersActions(
                onOpen = { state = state.copy(open = it?.id) },
                onShow = { state = state.copy(dialog = it) },
                onAdministrator = { calls += "admin $it" },
                onCanSignIn = { calls += "signin $it" },
                onAllLibraries = { calls += "all $it" },
                onLibrary = { id, on -> calls += "lib $id $on" },
                onCreate = { name, _ -> calls += "create $name" },
                onSetPassword = { calls += "password $it" },
                onDelete = { calls += "delete" },
            )
        compose.setContent { JellyfinTheme { UsersScreen(state, actions) } }
        compose.onNodeWithText("Claude (you)").assertExists()
        compose.onNodeWithText("Add a user").performClick()
        compose.onNodeWithText("Username").performTextInput("Ana")
        compose.onNodeWithText("Add").performClick()
        state = state.copy(dialog = null)
        compose.onNodeWithText("Sam").performClick()
        compose.onNodeWithText("Can sign in").performClick()
        compose.onNodeWithText("Administrator").performClick()
        compose.onNodeWithText("All libraries").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Shows"))
        compose.onNodeWithText("Shows").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Delete user"))
        compose.onNodeWithText("Delete user").performClick()
        compose.onNodeWithText("Delete Sam?").assertExists()
        compose.onAllNodesWithText("Delete user")[1].performClick()
        state = state.copy(dialog = null, done = UserDone.Deleted)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Set a new password"))
        compose.onNodeWithText("Set a new password").performClick()
        compose.onNodeWithText("New password").performTextInput("pw")
        compose.onNodeWithText("Set").performClick()
        assertEquals(
            listOf("create Ana", "signin false", "admin true", "all true", "lib s true", "delete", "password pw"),
            calls,
        )
        state = state.copy(open = "me", dialog = null, actionFailed = ConsoleFailure.Refused)
        compose.onNodeWithText("This is you. Another admin can change these.").assertExists()
        compose.onNodeWithText("Can sign in").assertDoesNotExist()
        compose.onNodeWithText("Delete user").assertDoesNotExist()
        state = state.copy(open = null)
        compose.onNodeWithText("Users").assertExists()
    }

    @Test
    fun `the server page restarts after a confirm, reports how it went, and says when it can't`() {
        val asked = mutableListOf<Boolean>()
        var restarted = false
        val info =
            ServerInfo("truenas", "10.11.6", "http://nas:8096", "X64", "", updateAvailable = true, restartPending = true, canRestart = true)
        var state by mutableStateOf(ServerUiState.Ready(info))
        compose.setContent {
            JellyfinTheme { ServerScreen(state, ServerActions(onConfirm = { asked += it }, onRestart = { restarted = true })) }
        }
        compose.onNodeWithText("Jellyfin 10.11.6").assertExists()
        compose.onNodeWithText("An update is available. Install it where the server runs.").assertExists()
        compose.onNodeWithText("A restart is waiting").assertExists()
        compose.onNodeWithText("Restart server").performScrollTo().performClick()
        assertEquals(listOf(true), asked)
        state = state.copy(confirming = true)
        compose.onNodeWithText("Restart truenas?").assertExists()
        compose.onAllNodesWithText("Restart server")[1].performClick()
        assertTrue(restarted)
        Restart.entries.forEach { restart -> state = state.copy(confirming = false, restart = restart) }
        compose.onNodeWithText("The server didn't take the restart. Try again.").assertExists()
        state = state.copy(info = info.copy(canRestart = false), restart = null)
        compose.onNodeWithText("This server can't restart itself. Restart it where it runs.").assertExists()
    }
}
