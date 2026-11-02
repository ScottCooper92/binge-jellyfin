package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LIBRARY_DOWNLOAD_LANGUAGES
import com.cooper.binge.companion.jellyfin.server.downloadLanguages
import com.cooper.binge.companion.jellyfin.server.languagesWith
import com.binge.designsystem.R as DesR

/**
 * The languages a library downloads subtitles in: a switch for each language the server lists, in its name order. A tap
 * writes the whole list, so a code the server no longer lists stays as the library has it.
 */
@Composable
internal fun LanguagesPage(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
) {
    val on = state.options.downloadLanguages()
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.library_download_languages_body))
        if (state.cultures.isEmpty()) {
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.Translate,
                            label = stringResource(R.string.library_download_languages_unknown),
                            clickable = false,
                        ),
                    ),
            )
        } else {
            ItemGroup(
                title = null,
                rows =
                    state.cultures.map { culture ->
                        val checked = on.any { it.equals(culture.code, ignoreCase = true) }
                        ListItem(
                            icon = Icons.Outlined.Translate,
                            label = culture.displayName,
                            toggled = checked,
                            onClick = { actions.onJson(LIBRARY_DOWNLOAD_LANGUAGES, state.options.languagesWith(culture.code, !checked)) },
                        )
                    },
            )
        }
    }
}
