package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.ApiKey
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PackageVersion
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.PluginRepository
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.server.ServerSettings
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.server.TaskRun
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import com.cooper.binge.companion.jellyfin.server.TrickplaySettings
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
    val compose = createAndroidComposeRule<ComponentActivity>()

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
    fun `a grouped library keeps its switch on the main page, though it has left the list`() {
        val calls = mutableListOf<String>()
        val state =
            HomeLayoutUiState.Ready(
                listOf(UserView("a", "Movies", "movies")),
                HomeLayout(grouped = setOf("b")),
                groupable = listOf(UserView("b", "Mixed")),
            )
        compose.setContent { JellyfinTheme { HomeLayoutScreen(state, HomeLayoutActions(onGrouped = { id, on -> calls += "$id:$on" })) } }
        compose.onNodeWithText("Mixed").performScrollTo().performClick()
        assertEquals(listOf("b:false"), calls)
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
    fun `logs list the files, open one to read, search it and share it`() {
        var opened: String? = null
        var query = ""
        var shared: Pair<String, String>? = null
        var closed = 0
        val files = listOf(LogFile("log_20261031.log", 4096, 1_793_570_400_000L), LogFile("log_20261030.log", 2048, null))
        var state by mutableStateOf<LogsUiState>(LogsUiState.Ready(files))
        compose.setContent {
            JellyfinTheme {
                LogsScreen(
                    state,
                    LogsActions(
                        onOpen = { opened = it },
                        onCloseLog = { closed++ },
                        onQuery = { query = it },
                        onShare = { name, text -> shared = name to text },
                    ),
                    zone = java.time.ZoneOffset.UTC,
                )
            }
        }
        compose.onNodeWithText("log_20261030.log").assertExists()
        compose.onNodeWithText("log_20261031.log").performClick()
        assertEquals("log_20261031.log", opened)
        val lines = listOf("[10:00:00] [INF] Server started", "[10:06:00] [ERR] Boom")
        state = LogsUiState.Ready(files, OpenLog("log_20261031.log", LogContent.Ready(lines, truncated = true)))
        compose.onNodeWithText("[10:06:00] [ERR] Boom").assertExists()
        compose.onNodeWithText("Showing the last 512 KB of this log.").assertExists()
        compose.onNodeWithText("Share").performClick()
        assertEquals("log_20261031.log" to lines.joinToString("\n"), shared)
        state = LogsUiState.Ready(files, OpenLog("log_20261031.log", LogContent.Ready(lines, truncated = false), query = "zzz"))
        compose.onNodeWithText("No lines match.").assertExists()
        state = LogsUiState.Ready(emptyList())
        compose.onNodeWithText("The server has no log files.").assertExists()
        state = LogsUiState.Failed(ConsoleFailure.Unreachable)
        compose.onNodeWithText("Try again").assertExists()
    }

    @Test
    fun `system back with a log open closes the log, not the page`() {
        var closed = 0
        var left = 0
        val open = OpenLog("log_20261031.log", LogContent.Ready(listOf("[10:00:00] [INF] Server started"), truncated = false))
        compose.setContent {
            JellyfinTheme {
                LogsScreen(
                    LogsUiState.Ready(listOf(LogFile("log_20261031.log", 4096, null)), open),
                    LogsActions(onCloseLog = { closed++ }, onBack = { left++ }),
                )
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, closed)
        assertEquals(0, left)
    }

    private val aniDb =
        ServerPlugin("a1b2", "AniDB", "1.2.3.4", "Anime metadata.", PluginStatus.Active, canUninstall = true, hasSettings = true)

    private fun pluginsScreen(
        installed: PluginsUiState = PluginsUiState.Ready(listOf(aniDb)),
        catalog: CatalogUiState = CatalogUiState.Ready(emptyList()),
        repositories: RepositoriesUiState = RepositoriesUiState.Ready(emptyList()),
        actions: PluginsActions = PluginsActions(),
        catalogActions: CatalogActions = CatalogActions(),
        repositoryActions: RepositoriesActions = RepositoriesActions(),
        onBack: () -> Unit = {},
    ) = compose.setContent {
        JellyfinTheme { PluginsScreen(installed, catalog, repositories, actions, catalogActions, repositoryActions, onBack) }
    }

    @Test
    fun `installed plugins open to their page, switch, open settings, and uninstall after a confirm`() {
        val calls = mutableListOf<String>()
        val waiting = aniDb.copy(id = "c3", name = "Waiting", status = PluginStatus.Restart, hasSettings = false)
        var state by mutableStateOf(PluginsUiState.Ready(listOf(aniDb, waiting)))
        val actions =
            PluginsActions(
                onOpen = {
                    state = state.copy(open = it?.id)
                    calls += "open ${it?.name}"
                },
                onToggle = { plugin, on -> calls += "toggle ${plugin.name} $on" },
                onAskUninstall = { state = state.copy(confirming = it) },
                onUninstall = { calls += "uninstall" },
                onOpenSettings = { calls += "settings ${it.name}" },
                onRestart = { calls += "restart" },
            )
        pluginsScreen(installed = state, actions = actions)
        compose.onAllNodesWithText("Restart needed", substring = true).assertCountEquals(2)
        compose.onNodeWithText("AniDB").assertExists()
        compose.onNodeWithText("A change to a plugin takes effect when the server restarts.", substring = true).performClick()
        assertEquals(listOf("restart"), calls)
        calls.clear()
        compose.onNodeWithText("AniDB").performClick()
        assertEquals(listOf("open AniDB"), calls)
    }

    @Test
    fun `a plugin page has its switch, settings and Uninstall, last and red, behind a confirm`() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf(PluginsUiState.Ready(listOf(aniDb), open = "a1b2"))
        val actions =
            PluginsActions(
                onOpen = { calls += "open ${it?.name}" },
                onToggle = { plugin, on -> calls += "toggle ${plugin.name} $on" },
                onAskUninstall = { state = state.copy(confirming = it) },
                onUninstall = { calls += "uninstall" },
                onOpenSettings = { calls += "settings ${it.name}" },
            )
        compose.setContent {
            JellyfinTheme {
                PluginsScreen(
                    state,
                    CatalogUiState.Ready(
                        emptyList(),
                    ),
                    RepositoriesUiState.Ready(emptyList()),
                    actions,
                    CatalogActions(),
                    RepositoriesActions(),
                    {
                    },
                )
            }
        }
        compose.onNodeWithText("Anime metadata.").assertExists()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("On").performClick()
        assertEquals(listOf("settings AniDB", "toggle AniDB false"), calls)
        compose.onNodeWithText("Uninstall").performScrollTo().performClick()
        compose.onNodeWithText("Uninstall AniDB?").assertExists()
        compose.onAllNodesWithText("Uninstall")[1].performClick()
        assertTrue("uninstall" in calls)
        calls.clear()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf("open null"), calls)
    }

    @Test
    fun `a plugin that can't run has no switch, and one that can't be uninstalled has no Uninstall`() {
        val stuck = aniDb.copy(status = PluginStatus.NotSupported, canUninstall = false, hasSettings = false)
        pluginsScreen(installed = PluginsUiState.Ready(listOf(stuck), open = "a1b2"))
        compose.onNodeWithText("Not supported by this server", substring = true).assertExists()
        compose.onNodeWithText("On").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertDoesNotExist()
        compose.onNodeWithText("Uninstall").assertDoesNotExist()
    }

    @Test
    fun `the catalog lists packages by category, and a package installs the version for this server, or says there is none`() {
        val calls = mutableListOf<String>()
        val version = PackageVersion("13.0.0.0", "Fixes a thing.", "10.10.0.0", "Jellyfin Stable", "https://r.example/m.json")
        val reporting =
            CatalogPackage(
                "g1",
                "Playback Reporting",
                "Reports playback.",
                "Shows who watched what.",
                "jellyfin",
                "General",
                listOf(version),
            )
        val future =
            CatalogPackage(
                "g2",
                "Future Plugin",
                "Needs a newer server.",
                "",
                "someone",
                "Metadata",
                listOf(version.copy(targetAbi = "10.12.0.0")),
            )
        var state by mutableStateOf(CatalogUiState.Ready(listOf(reporting, future), server = JellyfinVersion.parse("10.10.7")))
        val actions =
            CatalogActions(
                onOpen = { state = state.copy(open = it) },
                onInstall = { calls += "install ${it.name}" },
                onCancel = { calls += "cancel ${it.name}" },
            )
        compose.setContent {
            JellyfinTheme {
                PluginsScreen(
                    PluginsUiState.Ready(emptyList()),
                    state,
                    RepositoriesUiState.Ready(emptyList()),
                    PluginsActions(),
                    actions,
                    RepositoriesActions(),
                    {},
                )
            }
        }
        compose.onNodeWithText("Catalog").performClick()
        compose.onNodeWithText("GENERAL").assertExists()
        compose.onNodeWithText("Reports playback.").assertExists()
        compose.onNodeWithText("Playback Reporting").performClick()
        compose.onNodeWithText("Shows who watched what.").assertExists()
        compose.onNodeWithText("13.0.0.0 · Jellyfin Stable").assertExists()
        compose.onNodeWithText("Fixes a thing.").assertExists()
        compose.onNodeWithText("Install").performClick()
        assertEquals(listOf("install Playback Reporting"), calls)
        state = state.copy(installing = mapOf("Playback Reporting" to null))
        compose.onNodeWithText("Cancel install").assertIsNotEnabled()
        state = state.copy(installing = mapOf("Playback Reporting" to "install-1"))
        compose.onNodeWithText("Cancel install").performClick()
        assertEquals("cancel Playback Reporting", calls.last())
        state =
            state.copy(
                installing = emptyMap(),
                installed = setOf("Playback Reporting"),
                outcomes =
                    mapOf(
                        "Playback Reporting" to InstallOutcome.Done,
                    ),
            )
        compose.onNodeWithText("Installed. Restart the server to start using it.").assertExists()
        compose.onNodeWithText("Install").assertDoesNotExist()
        state = state.copy(open = "Future Plugin", outcomes = emptyMap())
        compose.onNodeWithText("None built for this server's release").assertExists()
        compose.onNodeWithText("Install").assertDoesNotExist()
    }

    @Test
    fun `repositories add in two steps, with the warning, and remove after a confirm`() {
        val calls = mutableListOf<String>()
        val stable = PluginRepository("Jellyfin Stable", "https://repo.example/stable.json", true)
        var state by mutableStateOf(RepositoriesUiState.Ready(listOf(stable)))
        val actions =
            RepositoriesActions(
                onAskAdd = { state = state.copy(adding = it) },
                onAdd = { name, url -> calls += "add $name $url" },
                onAskRemove = { state = state.copy(removing = it) },
                onRemove = { calls += "remove" },
            )
        compose.setContent {
            JellyfinTheme {
                PluginsScreen(
                    PluginsUiState.Ready(emptyList()),
                    CatalogUiState.Ready(emptyList()),
                    state,
                    PluginsActions(),
                    CatalogActions(),
                    actions,
                    {},
                )
            }
        }
        compose.onNodeWithText("Repositories").performClick()
        compose.onNodeWithText("Jellyfin Stable").assertExists()
        compose.onNodeWithText("https://repo.example/stable.json").assertExists()
        compose.onNodeWithText("Add repository").performClick()
        compose.onNodeWithText("Repository name").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Mine")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Repository address").assertExists()
        compose.onNodeWithText("A repository can offer code that runs on your server. Only add one you trust.").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("not a url")
        compose.onNodeWithText("Enter a web address starting with http:// or https://.").assertExists()
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("https://mine.example/m.json")
        compose.onNodeWithText("Add").performClick()
        assertEquals(listOf("add Mine https://mine.example/m.json"), calls)
        state = state.copy(adding = false)
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove Jellyfin Stable?").assertExists()
        compose.onAllNodesWithText("Remove")[0].performClick()
        assertEquals("remove", calls.last())
        state = RepositoriesUiState.Ready(emptyList())
        compose.onNodeWithText("No repositories").assertExists()
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
    fun `api keys show only the last four, make one by name, copy it once, and revoke after a confirm`() {
        val created = mutableListOf<String>()
        val copied = mutableListOf<String>()
        val asked = mutableListOf<ApiKey?>()
        var revoked = false
        var dismissed = false
        val sonarr = ApiKey("aaaabbbbccccdddd1111", "Sonarr", null)
        var state by mutableStateOf(ApiKeysUiState.Ready(listOf(sonarr)))
        val actions =
            ApiKeysActions(
                onAskCreate = { state = state.copy(naming = it) },
                onCreate = { created += it },
                onCopy = { copied += it },
                onDismissCreated = { dismissed = true },
                onAskRemove = { asked += it },
                onRemove = { revoked = true },
            )
        compose.setContent { JellyfinTheme { ApiKeysScreen(state, actions) } }
        compose.onNodeWithText("Sonarr").assertExists()
        compose.onNodeWithText("Ends in 1111", substring = true).assertExists()
        compose.onNodeWithText("aaaabbbbccccdddd1111", substring = true).assertDoesNotExist()
        compose.onNodeWithText("New key").performClick()
        compose.onNodeWithText("App name").performTextInput("Radarr")
        compose.onNodeWithText("Create key").performClick()
        assertEquals(listOf("Radarr"), created)
        state = state.copy(naming = false, created = ApiKey("zzzzyyyyxxxxwwww9999", "Radarr", null))
        compose.onNodeWithText("zzzzyyyyxxxxwwww9999").assertExists()
        compose.onNodeWithText("Copy").performClick()
        assertEquals(listOf("zzzzyyyyxxxxwwww9999"), copied)
        compose.onNodeWithText("Done").performClick()
        assertTrue(dismissed)
        state = state.copy(created = null)
        compose.onNodeWithText("zzzzyyyyxxxxwwww9999").assertDoesNotExist()
        compose.onNodeWithContentDescription("Revoke").performClick()
        assertEquals(listOf<ApiKey?>(sonarr), asked)
        state = state.copy(removing = sonarr, actionFailed = ConsoleFailure.Refused)
        compose.onNodeWithText("Revoke Sonarr?").assertExists()
        compose.onAllNodesWithText("Revoke")[0].performClick()
        assertTrue(revoked)
        state = ApiKeysUiState.Ready(emptyList())
        compose.onNodeWithText("This server has no API keys.").assertExists()
    }

    @Test
    fun `trickplay switches, pickers and numbers reach their actions, and a number out of range is refused in place`() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf(TrickplayUiState.Ready(TrickplaySettings()))
        val actions =
            TrickplayActions(
                onEdit = { state = state.copy(editing = it) },
                onSave = { field, text -> calls += "save ${field.name} $text" },
                onHwAcceleration = { calls += "hwAcceleration $it" },
                onKeyFrameOnly = { calls += "keyFrameOnly $it" },
                onScanBehavior = { calls += "scan $it" },
                onProcessPriority = { calls += "priority $it" },
                onInterval = { calls += "interval $it" },
            )
        var keyFrames by mutableStateOf(true)
        compose.setContent { JellyfinTheme { TrickplayScreen(state, actions, keyFrameOnlyAvailable = keyFrames) } }
        compose.onNodeWithText("Hardware decoding").performClick()
        compose.onNodeWithText("Key frames only").performClick()
        keyFrames = false
        compose.onNodeWithText("Key frames only").assertDoesNotExist()
        compose.onNodeWithText("Every 10 s").performScrollTo().performClick()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Custom…"))
        compose.onNodeWithText("Custom…").assertExists()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Every 5 s"))
        compose.onNodeWithText("Every 5 s").performClick()
        compose.onNodeWithText("Don\'t wait for the images").performScrollTo().performClick()
        compose.onNodeWithText("Wait for the images").performClick()
        compose.onNodeWithText("Below normal").performClick()
        compose.onNodeWithText("Idle").performClick()
        assertEquals(
            listOf("hwAcceleration true", "keyFrameOnly true", "interval 5000", "scan Blocking", "priority Idle"),
            calls,
        )
        calls.clear()
        state = state.copy(editing = TrickplayField.JpegQuality)
        compose.onNode(hasSetTextAction()).performTextReplacement("500")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter a whole number from 1 to 100.").assertExists()
        assertEquals(emptyList<String>(), calls)
        compose.onNode(hasSetTextAction()).performTextReplacement("70")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("save JpegQuality 70"), calls)
        state = state.copy(editing = TrickplayField.Widths)
        compose.onNode(hasSetTextAction()).performTextReplacement("wide")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter widths from 1 to 7680, separated by commas.").assertExists()
        state = TrickplayUiState.Ready(TrickplaySettings(threads = 0, widths = listOf(320, 640), intervalMillis = 2500))
        compose.onNodeWithText("Automatic").assertExists()
        compose.onNodeWithText("320 px, 640 px").assertExists()
        compose.onNodeWithText("Every 2500 ms").assertExists()
        state = TrickplayUiState.Ready(TrickplaySettings(tileWidth = 1, tileHeight = 10))
        compose.onNodeWithText("1 image").assertExists()
        compose.onNodeWithText("10 images").assertExists()
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
