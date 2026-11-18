package com.cooper.homelab.ephyra.ui.search

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.LIVE_TV_ORIGINAL_AUDIO
import com.cooper.homelab.ephyra.server.LIVE_TV_SAVE_IMAGES
import com.cooper.homelab.ephyra.server.LIVE_TV_SAVE_NFO
import com.cooper.homelab.ephyra.server.LIVE_TV_SUBFOLDERS
import com.cooper.homelab.ephyra.server.bool
import com.cooper.homelab.ephyra.ui.console.ConsolePage

/**
 * A switch of the Live TV recording page, which saves as made. It reads null where the server did not send its key, as the page hides a
 * row it was not sent. The page itself is for an administrator who manages Live TV, which an entry's page gate already says.
 */
private fun liveTvSpec(key: String): Pair<String, SwitchSpec> =
    SettingRows.liveTv(key) to
        SwitchSpec(ConfigKind.LiveTv, read = { if (key in it) it.bool(key) ?: false else null }, change = topLevel(key))

internal val LIVE_TV_SPECS: Map<String, SwitchSpec> =
    mapOf(
        liveTvSpec(LIVE_TV_SUBFOLDERS),
        liveTvSpec(LIVE_TV_ORIGINAL_AUDIO),
        liveTvSpec(LIVE_TV_SAVE_NFO),
        liveTvSpec(LIVE_TV_SAVE_IMAGES),
    )

internal fun Resources.indexLiveTvSwitches(): List<SettingEntry<SearchTarget>> =
    listOf(
        liveTvEntry(R.string.live_tv_recording_subfolders, Icons.Outlined.VideoLibrary, LIVE_TV_SUBFOLDERS),
        liveTvEntry(R.string.live_tv_recording_original_audio, Icons.Outlined.Audiotrack, LIVE_TV_ORIGINAL_AUDIO),
        liveTvEntry(R.string.live_tv_recording_nfo, Icons.Outlined.Description, LIVE_TV_SAVE_NFO),
        liveTvEntry(R.string.live_tv_recording_images, Icons.Outlined.Image, LIVE_TV_SAVE_IMAGES),
    )

private fun Resources.liveTvEntry(
    @StringRes label: Int,
    icon: ImageVector,
    key: String,
) = setting(
    ConsolePage.LiveTvRecording,
    label,
    icon,
    R.string.settings_server,
    R.string.live_tv_recording_title,
    row = SettingRows.liveTv(key),
)
