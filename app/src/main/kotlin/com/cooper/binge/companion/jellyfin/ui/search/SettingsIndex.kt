package com.cooper.binge.companion.jellyfin.ui.search

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import java.text.Normalizer

/**
 * One thing the search can take the user to: a whole page, or one setting on it. [path] names where it is (the section, and for a setting
 * its page too), as Android's own Settings search shows "Device > Display and touch". [keywords] is one string of other words people
 * look for it by ("gpu" for hardware acceleration), translated with the rest. Opening an entry opens its [page]: scrolling to the setting
 * is a follow-up.
 */
internal class SettingEntry(
    val page: ConsolePage,
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val section: Int,
    /** The page a setting is on, or null for an entry that is the page itself. */
    @StringRes val pageLabel: Int? = null,
    @StringRes val keywords: Int? = null,
    /** A gate of the entry's own, for a setting its page hides on some servers or accounts; the page's own gate is applied besides. */
    val shown: (JellyfinServerProfile, JellyfinAccount) -> Boolean = { _, _ -> true },
)

/** Whether the search may offer this entry: its page is open to the user, and its own gate, if it has one, passes. */
internal fun SettingEntry.available(
    profile: JellyfinServerProfile,
    account: JellyfinAccount,
): Boolean = page.available(profile, account) && shown(profile, account)

private fun page(
    page: ConsolePage,
    @StringRes label: Int,
    icon: ImageVector,
    @StringRes section: Int,
    @StringRes keywords: Int? = null,
) = SettingEntry(page, label, icon, section, null, keywords)

private fun setting(
    page: ConsolePage,
    @StringRes label: Int,
    icon: ImageVector,
    @StringRes section: Int,
    @StringRes pageLabel: Int,
    @StringRes keywords: Int? = null,
    shown: (JellyfinServerProfile, JellyfinAccount) -> Boolean = { _, _ -> true },
) = SettingEntry(page, label, icon, section, pageLabel, keywords, shown)

/**
 * Everything the search knows. A page of the console, and each setting worth finding by name, is listed here: a new setting adds its
 * line, and a test holds that every page the home lists has one. An entry shows only where [ConsolePage.available] says the page does.
 */
internal val settingsIndex: List<SettingEntry> =
    listOf(
        // The user's own.
        page(
            ConsolePage.ApproveCode,
            R.string.approve_title,
            Icons.Outlined.QrCode2,
            R.string.console_account_title,
            R.string.kw_quick_connect,
        ),
        page(ConsolePage.Playback, R.string.playback_title, Icons.Outlined.Translate, R.string.console_account_title, R.string.kw_playback),
        page(ConsolePage.HomeLayout, R.string.home_layout_title, Icons.Outlined.Dashboard, R.string.console_account_title),
        page(
            ConsolePage.ProfilePicture,
            R.string.profile_picture_title,
            Icons.Outlined.Image,
            R.string.console_account_title,
            R.string.kw_picture,
        ),
        page(ConsolePage.Password, R.string.password_title, Icons.Outlined.Password, R.string.console_account_title, R.string.kw_password),
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
        page(ConsolePage.Transcoding, R.string.transcoding_title, Icons.Outlined.Memory, R.string.settings_server, R.string.kw_transcoding),
        page(ConsolePage.ServerSettings, R.string.server_settings_title, Icons.Outlined.Tune, R.string.settings_server),
        page(ConsolePage.LibraryDisplay, R.string.display_title, Icons.Outlined.ViewModule, R.string.settings_server, R.string.kw_metadata),
        page(
            ConsolePage.MetadataDefaults,
            R.string.metadata_title,
            Icons.Outlined.Category,
            R.string.settings_server,
            R.string.kw_metadata,
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
        page(ConsolePage.Server, R.string.server_title, Icons.Outlined.SettingsApplications, R.string.advanced_title, R.string.kw_restart),
        // Settings on them.
        setting(
            ConsolePage.ServerSettings,
            R.string.server_settings_name,
            Icons.Outlined.Tune,
            R.string.settings_server,
            R.string.server_settings_title,
        ),
        setting(
            ConsolePage.ServerSettings,
            R.string.server_settings_quick_connect,
            Icons.Outlined.QrCode2,
            R.string.settings_server,
            R.string.server_settings_title,
            R.string.kw_quick_connect,
        ),
        setting(
            ConsolePage.Transcoding,
            R.string.transcoding_hardware,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.transcoding_title,
            R.string.kw_hardware,
        ),
        setting(
            ConsolePage.Transcoding,
            R.string.transcoding_tonemapping_enable,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.transcoding_title,
            R.string.kw_tonemapping,
        ),
        setting(
            ConsolePage.Transcoding,
            R.string.transcoding_hevc,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.transcoding_title,
            R.string.kw_codecs,
        ),
        setting(
            ConsolePage.Transcoding,
            R.string.transcoding_av1,
            Icons.Outlined.Memory,
            R.string.settings_server,
            R.string.transcoding_title,
            R.string.kw_codecs,
            shown = { profile, _ -> profile.hasAdvancedEncoding },
        ),
        setting(
            ConsolePage.Network,
            R.string.network_http_port,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_port,
        ),
        setting(
            ConsolePage.Network,
            R.string.network_https_port,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_port,
        ),
        setting(
            ConsolePage.Network,
            R.string.network_enable_https,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_https,
        ),
        setting(
            ConsolePage.Network,
            R.string.network_remote_access,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_remote,
        ),
        setting(
            ConsolePage.Network,
            R.string.network_base_url,
            Icons.Outlined.Router,
            R.string.advanced_title,
            R.string.network_title,
            R.string.kw_proxy,
        ),
        setting(ConsolePage.Advanced, R.string.advanced_cache, Icons.Outlined.Build, R.string.advanced_title, R.string.advanced_title),
        setting(ConsolePage.Logs, R.string.logs_retention_title, Icons.Outlined.Description, R.string.advanced_title, R.string.logs_title),
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
        ),
        setting(
            ConsolePage.ThisApp,
            R.string.settings_send_crash_reports,
            Icons.Outlined.Healing,
            R.string.settings_group_app,
            R.string.settings_group_app,
            R.string.kw_privacy,
        ),
        setting(
            ConsolePage.ThisApp,
            R.string.settings_report_bug,
            Icons.Outlined.Healing,
            R.string.settings_group_app,
            R.string.settings_group_app,
            R.string.kw_feedback,
        ),
        page(ConsolePage.Licences, R.string.licences_title, Icons.Outlined.Description, R.string.settings_group_app),
    )

/** Lower case, accents dropped: "Configuración" and "configuracion" are the same word to someone typing on a phone. */
internal fun String.searchForm(): String =
    Normalizer
        .normalize(lowercase(), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

/** What the user may be offered most at once: past this a longer query is the better help. */
private const val MAX_RESULTS = 30

private const val SCORE_LABEL_STARTS = 4
private const val SCORE_WORD_STARTS = 3
private const val SCORE_OTHER = 1

/**
 * The entries [query] finds among those the user may open ([available]), best first. Every word of the query must match:
 * the label counts most (a word of it must start with the query's word, so "port" does not find "reports"), then the path and the keywords. [text] resolves a string resource in the current language, so the search
 * follows the app's locale with nothing of its own to translate.
 */
internal fun searchSettings(
    query: String,
    entries: List<SettingEntry>,
    available: (SettingEntry) -> Boolean,
    text: (Int) -> String,
): List<SettingEntry> {
    val words = query.searchForm().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return entries
        .filter(available)
        .mapIndexedNotNull { index, entry ->
            val label = text(entry.label).searchForm()
            val other = listOfNotNull(entry.pageLabel, entry.section, entry.keywords).joinToString(" ") { text(it).searchForm() }
            var total = 0
            for (word in words) {
                val score = wordScore(word, label, other)
                if (score == 0) return@mapIndexedNotNull null
                total += score
            }
            Triple(entry, total, index)
        }.sortedWith(
            compareByDescending<Triple<SettingEntry, Int, Int>> { it.second }.thenBy { it.first.pageLabel != null }.thenBy { it.third },
        ).take(MAX_RESULTS)
        .map { it.first }
}

private fun wordScore(
    word: String,
    label: String,
    other: String,
): Int =
    when {
        label.startsWith(word) -> SCORE_LABEL_STARTS
        label.split(' ').any { it.startsWith(word) } -> SCORE_WORD_STARTS
        other.contains(word) -> SCORE_OTHER
        else -> 0
    }
