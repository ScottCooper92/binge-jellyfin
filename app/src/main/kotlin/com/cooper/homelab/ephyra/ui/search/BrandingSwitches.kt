package com.cooper.homelab.ephyra.ui.search

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wallpaper
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.SPLASHSCREEN_ENABLED
import com.cooper.homelab.ephyra.server.branding
import com.cooper.homelab.ephyra.ui.console.ConsolePage

/**
 * The login splash screen switch, which saves as made. The page draws it from 10.9 whether or not the server sent the key, and the
 * search reads it as the page does. The search offers the switch from 10.11 only: before that the server drops the splash image's
 * location on any branding save, which the page says in a note, so a flip from here would lose an image set in another app with
 * nothing said. On 10.9 and 10.10 the entry still opens the page, where the note is.
 */
internal val BRANDING_SPECS: Map<String, SwitchSpec> =
    mapOf(
        SettingRows.SERVER_SPLASHSCREEN to
            SwitchSpec(
                ConfigKind.Branding,
                gate = { it.servesSplashscreenLocation },
                read = { it.branding().splashscreen },
                change = topLevel(SPLASHSCREEN_ENABLED),
            ),
    )

internal fun Resources.indexBrandingSwitches(): List<SettingEntry<SearchTarget>> =
    listOf(
        setting(
            ConsolePage.ServerSettings,
            R.string.server_settings_splashscreen,
            Icons.Outlined.Wallpaper,
            R.string.settings_server,
            R.string.server_settings_title,
            shown = { profile, _ -> profile.hasSplashscreen },
            row = SettingRows.SERVER_SPLASHSCREEN,
        ),
    )
