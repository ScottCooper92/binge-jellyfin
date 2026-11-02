package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.LIBRARY_DOWNLOAD_LANGUAGES
import com.cooper.binge.companion.jellyfin.server.downloadLanguages
import com.cooper.binge.companion.jellyfin.server.languagesWith
import com.binge.designsystem.R as DesR

/**
 * The languages a library downloads subtitles in: a switch for each language the server lists, in its name order, a few
 * hundred of them. A tap writes the whole list, so a code the server no longer lists stays as the library has it.
 *
 * The list is lazy, under the bar, and each alphabetical section is one keyed item (its letter): the design system's
 * group takes its rows as a list, so a section is the smallest piece that can be left uncomposed. [padding] is what the
 * screen leaves around a list that does not scroll in a form.
 */
@Composable
internal fun LanguagesPage(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    padding: PaddingValues,
) {
    val on = state.options.downloadLanguages()
    val sections = remember(state.cultures) { state.cultures.groupBy { it.displayName.sectionLetter() }.toList() }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item(key = INTRO_KEY) { PageIntro(stringResource(R.string.library_download_languages_body)) }
        if (state.cultures.isEmpty()) {
            item(
                key = EMPTY_KEY,
            ) { ConsoleEmptyScreen(stringResource(R.string.library_download_languages_unknown), Icons.Outlined.Translate) }
        }
        sections.forEach { (letter, cultures) ->
            item(key = letter) { ItemGroup(title = letter, rows = cultures.map { it.row(state, actions, on) }) }
        }
    }
}

@Composable
private fun Culture.row(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    on: List<String>,
): ListItem {
    val checked = on.any { it.equals(code, ignoreCase = true) }
    return ListItem(
        icon = Icons.Outlined.Translate,
        label = displayName,
        toggled = checked,
        onClick = { actions.onJson(LIBRARY_DOWNLOAD_LANGUAGES, state.options.languagesWith(code, !checked)) },
    )
}

/** The letter a language is listed under: the first letter or digit of its name, in upper case, or `#` when it has none. */
private fun String.sectionLetter(): String = firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"

private const val INTRO_KEY = "intro"
private const val EMPTY_KEY = "empty"
