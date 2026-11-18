package com.cooper.binge.companion.jellyfin.ui.search

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.HideSource
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ENCODING_AV1
import com.cooper.binge.companion.jellyfin.server.ENCODING_HEVC
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.NETWORK_BASE_URL
import com.cooper.binge.companion.jellyfin.server.NETWORK_ENABLE_HTTPS
import com.cooper.binge.companion.jellyfin.server.NETWORK_REMOTE_ACCESS
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.homelab.core.presentation.search.SettingEntry

/**
 * An entry's stable name, to remember it by: its page and its label's resource name. A resource's number changes from build to build, its
 * name does not. These are the ids the recents have always held, so nobody loses theirs.
 */
private fun Resources.entryId(
    page: ConsolePage,
    @StringRes label: Int,
) = "${page.name}:${getResourceEntryName(label)}"

private fun Resources.page(
    page: ConsolePage,
    @StringRes label: Int,
    icon: ImageVector,
    @StringRes section: Int,
    @StringRes keywords: Int? = null,
) = SettingEntry(
    id = entryId(page, label),
    label = text(label),
    section = text(section),
    target = SearchTarget(page, icon),
    keywords = keywords?.let(::text),
)

internal fun Resources.setting(
    page: ConsolePage,
    @StringRes label: Int,
    icon: ImageVector,
    @StringRes section: Int,
    @StringRes pageLabel: Int,
    @StringRes keywords: Int? = null,
    shown: (JellyfinServerProfile, JellyfinAccount) -> Boolean = { _, _ -> true },
    row: String? = null,
) = SettingEntry(
    id = entryId(page, label),
    label = text(label),
    section = text(section),
    target = SearchTarget(page, icon, shown),
    pageLabel = text(pageLabel),
    keywords = keywords?.let(::text),
    row = row,
)

/**
 * Everything the search knows. A page of the console, and each setting worth finding by name, is listed here: a new setting adds its
 * line, and a test holds that every page the home lists has one. An entry shows only where [ConsolePage.available] says the page does.
 * The list is in order and in parts, only so that no one function is longer than detekt allows.
 */
internal fun settingsIndex(resources: Resources): List<SettingEntry<SearchTarget>> =
    with(resources) {
        indexPart1() +
            indexPart2() +
            indexPart3() +
            indexPart4() +
            indexPart5() +
            indexSwitches() +
            indexServerSwitches() +
            indexEncodingSwitches() +
            indexNetworkSwitches() +
            indexLiveTvSwitches()
    }

private fun Resources.indexPart1(): List<SettingEntry<SearchTarget>> =
    listOf(
        // The user's own.
        page(
            ConsolePage.ApproveCode,
            R.string.approve_title,
            Icons.Outlined.QrCode2,
            R.string.console_account_title,
            R.string.kw_quick_connect,
        ),
        page(
            ConsolePage.Playback,
            R.string.playback_title,
            Icons.Outlined.Translate,
            R.string.console_account_title,
            R.string.kw_playback,
        ),
        page(ConsolePage.HomeLayout, R.string.home_layout_title, Icons.Outlined.Dashboard, R.string.console_account_title),
        page(
            ConsolePage.ProfilePicture,
            R.string.profile_picture_title,
            Icons.Outlined.Image,
            R.string.console_account_title,
            R.string.kw_picture,
        ),
        page(
            ConsolePage.Password,
            R.string.password_title,
            Icons.Outlined.Password,
            R.string.console_account_title,
            R.string.kw_password,
        ),
        setting(
            ConsolePage.Playback,
            R.string.playback_subtitles,
            Icons.Outlined.Translate,
            R.string.console_account_title,
            R.string.playback_title,
        ),
        // The server's pages, in the sections the home has.
        page(
            ConsolePage.NowPlaying,
            R.string.now_playing_title,
            Icons.Outlined.PlayCircle,
            R.string.settings_server,
            R.string.kw_now_playing,
        ),
        page(ConsolePage.LiveTv, R.string.live_tv_title, Icons.Outlined.LiveTv, R.string.settings_server, R.string.kw_live_tv),
        page(ConsolePage.Users, R.string.users_title, Icons.Outlined.Group, R.string.settings_server, R.string.kw_users),
        page(ConsolePage.Libraries, R.string.libraries_title, Icons.Outlined.Folder, R.string.settings_server, R.string.kw_libraries),
        page(
            ConsolePage.Transcoding,
            R.string.transcoding_title,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.kw_transcoding,
        ),
        page(ConsolePage.ServerSettings, R.string.server_settings_title, Icons.Outlined.Tune, R.string.settings_server),
        page(
            ConsolePage.LibraryDisplay,
            R.string.display_title,
            Icons.Outlined.ViewModule,
            R.string.settings_server,
            R.string.kw_metadata,
        ),
        page(
            ConsolePage.MetadataDefaults,
            R.string.metadata_title,
            Icons.Outlined.Category,
            R.string.settings_server,
            R.string.kw_metadata,
        ),
    )

private fun Resources.indexPart2(): List<SettingEntry<SearchTarget>> =
    listOf(
        page(ConsolePage.Nfo, R.string.nfo_title, Icons.Outlined.Description, R.string.settings_server, R.string.kw_nfo),
        setting(
            ConsolePage.Nfo,
            R.string.nfo_user,
            Icons.Outlined.Person,
            R.string.settings_server,
            R.string.nfo_title,
            R.string.kw_nfo_user,
        ),
        setting(
            ConsolePage.Nfo,
            R.string.nfo_date_format,
            Icons.Outlined.CalendarMonth,
            R.string.settings_server,
            R.string.nfo_title,
            R.string.kw_nfo_date,
        ),
        setting(
            ConsolePage.Nfo,
            R.string.nfo_image_paths,
            Icons.Outlined.Image,
            R.string.settings_server,
            R.string.nfo_title,
            R.string.kw_nfo_images,
        ),
        setting(
            ConsolePage.Nfo,
            R.string.nfo_path_substitution,
            Icons.Outlined.SwapHoriz,
            R.string.settings_server,
            R.string.nfo_title,
            R.string.kw_nfo_paths,
        ),
        setting(
            ConsolePage.Nfo,
            R.string.nfo_extra_thumbs,
            Icons.Outlined.ContentCopy,
            R.string.settings_server,
            R.string.nfo_title,
            R.string.kw_nfo_thumbs,
        ),
        page(ConsolePage.Trickplay, R.string.trickplay_title, Icons.Outlined.Image, R.string.settings_server, R.string.kw_trickplay),
        page(ConsolePage.Plugins, R.string.plugins_title, Icons.Outlined.Extension, R.string.settings_server, R.string.kw_plugins),
        page(ConsolePage.Devices, R.string.devices_title, Icons.Outlined.Devices, R.string.devices_title),
        page(ConsolePage.Activity, R.string.activity_title, Icons.Outlined.History, R.string.devices_title, R.string.kw_activity),
        page(ConsolePage.Tasks, R.string.tasks_title, Icons.Outlined.Schedule, R.string.advanced_title, R.string.kw_tasks),
        page(ConsolePage.Logs, R.string.logs_title, Icons.Outlined.Description, R.string.advanced_title),
        page(ConsolePage.Network, R.string.network_title, Icons.Outlined.Router, R.string.advanced_title, R.string.kw_network),
        page(ConsolePage.ApiKeys, R.string.api_keys_title, Icons.Outlined.Key, R.string.advanced_title),
        page(ConsolePage.Backups, R.string.backups_title, Icons.Outlined.Backup, R.string.advanced_title),
        page(ConsolePage.Advanced, R.string.advanced_title, Icons.Outlined.Build, R.string.advanced_title),
        page(
            ConsolePage.Server,
            R.string.server_title,
            Icons.Outlined.SettingsApplications,
            R.string.advanced_title,
            R.string.kw_restart,
        ),
        setting(
            ConsolePage.Server,
            R.string.server_alerts,
            Icons.Outlined.WarningAmber,
            R.string.advanced_title,
            R.string.server_title,
            R.string.kw_alerts,
        ),
    )

private fun Resources.indexPart3(): List<SettingEntry<SearchTarget>> =
    listOf(
        setting(
            ConsolePage.Server,
            R.string.server_storage,
            Icons.Outlined.Storage,
            R.string.advanced_title,
            R.string.server_title,
            R.string.kw_storage,
            shown = { profile, _ -> profile.hasStorageInfo },
        ),
        setting(
            ConsolePage.Server,
            R.string.server_paths,
            Icons.Outlined.Folder,
            R.string.advanced_title,
            R.string.server_title,
            R.string.kw_paths,
        ),
        // Settings on them.
        setting(
            ConsolePage.Plugins,
            R.string.catalog_versions_search,
            Icons.Outlined.History,
            R.string.settings_server,
            R.string.plugins_title,
            R.string.kw_plugin_versions,
        ),
        setting(
            ConsolePage.Libraries,
            R.string.library_image_limits,
            Icons.Outlined.Image,
            R.string.settings_server,
            R.string.libraries_title,
            R.string.kw_image_limits,
        ),
        setting(
            ConsolePage.ServerSettings,
            R.string.server_settings_name,
            Icons.Outlined.Tune,
            R.string.settings_server,
            R.string.server_settings_title,
            row = SettingRows.SERVER_NAME,
        ),
        setting(
            ConsolePage.ServerSettings,
            R.string.server_settings_quick_connect,
            Icons.Outlined.QrCode2,
            R.string.settings_server,
            R.string.server_settings_title,
            R.string.kw_quick_connect,
            row = SettingRows.SERVER_QUICK_CONNECT,
        ),
        setting(
            ConsolePage.Transcoding,
            R.string.transcoding_hardware,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.transcoding_title,
            R.string.kw_hardware,
            row = SettingRows.TRANSCODING_ACCELERATION,
        ),
        setting(
            ConsolePage.Transcoding,
            R.string.transcoding_hevc,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.transcoding_title,
            R.string.kw_codecs,
            row = SettingRows.transcoding(ENCODING_HEVC),
        ),
    )

private fun Resources.indexPart4(): List<SettingEntry<SearchTarget>> =
    listOf(
        setting(
            ConsolePage.Transcoding,
            R.string.transcoding_av1,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.transcoding_title,
            R.string.kw_codecs,
            shown = { profile, _ -> profile.hasAdvancedEncoding },
            row = SettingRows.transcoding(ENCODING_AV1),
        ),
        setting(
            ConsolePage.Network,
            R.string.network_http_port,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_port,
            row = SettingRows.NETWORK_HTTP_PORT,
        ),
        setting(
            ConsolePage.Network,
            R.string.network_https_port,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_port,
            row = SettingRows.NETWORK_HTTPS_PORT,
        ),
        setting(
            ConsolePage.Network,
            R.string.network_enable_https,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_https,
            row = SettingRows.network(NETWORK_ENABLE_HTTPS),
        ),
        setting(
            ConsolePage.Network,
            R.string.network_remote_access,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_remote,
            row = SettingRows.network(NETWORK_REMOTE_ACCESS),
        ),
        setting(
            ConsolePage.Network,
            R.string.network_base_url,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_proxy,
            row = SettingRows.network(NETWORK_BASE_URL),
        ),
        setting(
            ConsolePage.Advanced,
            R.string.advanced_cache,
            Icons.Outlined.Build,
            R.string.advanced_title,
            R.string.advanced_title,
            shown = { profile, _ -> profile.hasCacheAndLegacyAuth },
            row = SettingRows.ADVANCED_CACHE,
        ),
    )

private fun Resources.indexPart5(): List<SettingEntry<SearchTarget>> =
    listOf(
        setting(
            ConsolePage.Logs,
            R.string.logs_retention_title,
            Icons.Outlined.Description,
            R.string.advanced_title,
            R.string.logs_title,
            row = SettingRows.LOGS_RETENTION,
        ),
        setting(ConsolePage.Users, R.string.users_remote_access, Icons.Outlined.Group, R.string.settings_server, R.string.users_title),
        setting(ConsolePage.Users, R.string.users_downloading, Icons.Outlined.Group, R.string.settings_server, R.string.users_title),
        // This app.
        page(ConsolePage.ThisApp, R.string.settings_group_app, Icons.Outlined.Tune, R.string.settings_group_app, R.string.kw_this_app),
        setting(
            ConsolePage.ThisApp,
            R.string.settings_share_usage_data,
            Icons.Outlined.BarChart,
            R.string.settings_group_app,
            R.string.settings_group_app,
            R.string.kw_privacy,
            row = SettingRows.THIS_APP_USAGE_DATA,
        ),
        setting(
            ConsolePage.ThisApp,
            R.string.settings_send_crash_reports,
            Icons.Outlined.Healing,
            R.string.settings_group_app,
            R.string.settings_group_app,
            R.string.kw_privacy,
            row = SettingRows.THIS_APP_CRASH_REPORTS,
        ),
        setting(
            ConsolePage.ThisApp,
            R.string.settings_report_bug,
            Icons.Outlined.Healing,
            R.string.settings_group_app,
            R.string.settings_group_app,
            R.string.kw_feedback,
            row = SettingRows.THIS_APP_REPORT_BUG,
        ),
        setting(
            ConsolePage.ThisApp,
            R.string.notifications_switch,
            Icons.Outlined.Notifications,
            R.string.settings_group_app,
            R.string.settings_group_app,
            R.string.kw_notifications,
            shown = { _, account -> account.isAdministrator },
            row = SettingRows.THIS_APP_NOTIFICATIONS,
        ),
        page(ConsolePage.Licences, R.string.licences_title, Icons.Outlined.Description, R.string.settings_group_app),
    )

/** The user's own on/off settings: a page's switch is found, and shown in the results, by its own name. */
private fun Resources.indexSwitches(): List<SettingEntry<SearchTarget>> =
    listOf(
        setting(
            ConsolePage.Playback,
            R.string.playback_default_audio,
            Icons.Outlined.GraphicEq,
            R.string.console_account_title,
            R.string.playback_title,
            row = SettingRows.PLAYBACK_DEFAULT_AUDIO,
        ),
        setting(
            ConsolePage.Playback,
            R.string.playback_remember_audio,
            Icons.Outlined.History,
            R.string.console_account_title,
            R.string.playback_title,
            row = SettingRows.PLAYBACK_REMEMBER_AUDIO,
        ),
        setting(
            ConsolePage.Playback,
            R.string.playback_remember_subtitles,
            Icons.Outlined.Restore,
            R.string.console_account_title,
            R.string.playback_title,
            row = SettingRows.PLAYBACK_REMEMBER_SUBTITLES,
        ),
        setting(
            ConsolePage.Playback,
            R.string.playback_autoplay,
            Icons.Outlined.SkipNext,
            R.string.console_account_title,
            R.string.playback_title,
            row = SettingRows.PLAYBACK_AUTOPLAY,
        ),
        setting(
            ConsolePage.HomeLayout,
            R.string.home_layout_hide_played,
            Icons.Outlined.NewReleases,
            R.string.console_account_title,
            R.string.home_layout_title,
            row = SettingRows.HOME_HIDE_PLAYED,
        ),
        setting(
            ConsolePage.HomeLayout,
            R.string.home_layout_missing,
            Icons.Outlined.HideSource,
            R.string.console_account_title,
            R.string.home_layout_title,
            row = SettingRows.HOME_MISSING_EPISODES,
        ),
        setting(
            ConsolePage.HomeLayout,
            R.string.home_layout_collections,
            Icons.Outlined.CollectionsBookmark,
            R.string.console_account_title,
            R.string.home_layout_title,
            row = SettingRows.HOME_COLLECTIONS,
        ),
    )

/** The server's own on/off settings, for an administrator: Trickplay and Advanced (Quick Connect has its entry above). */
private fun Resources.indexServerSwitches(): List<SettingEntry<SearchTarget>> =
    listOf(
        setting(
            ConsolePage.Trickplay,
            R.string.trickplay_hw_acceleration,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.trickplay_title,
            R.string.kw_trickplay,
            row = SettingRows.TRICKPLAY_HW_ACCELERATION,
        ),
        setting(
            ConsolePage.Trickplay,
            R.string.trickplay_hw_encoding,
            Icons.Outlined.Videocam,
            R.string.settings_server,
            R.string.trickplay_title,
            R.string.kw_trickplay,
            row = SettingRows.TRICKPLAY_HW_ENCODING,
        ),
        setting(
            ConsolePage.Trickplay,
            R.string.trickplay_key_frames,
            Icons.Outlined.Bolt,
            R.string.settings_server,
            R.string.trickplay_title,
            R.string.kw_trickplay,
            shown = { profile, _ -> profile.hasKeyFrameOnlyTrickplay },
            row = SettingRows.TRICKPLAY_KEY_FRAMES,
        ),
        setting(
            ConsolePage.Advanced,
            R.string.advanced_slow_warning,
            Icons.Outlined.Warning,
            R.string.advanced_title,
            R.string.advanced_title,
            row = SettingRows.ADVANCED_SLOW_WARNING,
        ),
        setting(
            ConsolePage.Advanced,
            R.string.advanced_client_logs,
            Icons.Outlined.BugReport,
            R.string.advanced_title,
            R.string.advanced_title,
            row = SettingRows.ADVANCED_CLIENT_LOGS,
        ),
        setting(
            ConsolePage.Advanced,
            R.string.advanced_metrics,
            Icons.Outlined.Analytics,
            R.string.advanced_title,
            R.string.advanced_title,
            row = SettingRows.ADVANCED_METRICS,
        ),
        setting(
            ConsolePage.Advanced,
            R.string.advanced_legacy_auth,
            Icons.Outlined.Lock,
            R.string.advanced_title,
            R.string.advanced_title,
            shown = { profile, _ -> profile.hasCacheAndLegacyAuth },
            row = SettingRows.ADVANCED_LEGACY_AUTH,
        ),
    )
