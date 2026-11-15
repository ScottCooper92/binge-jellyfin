package com.cooper.binge.companion.jellyfin.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.binge.designsystem.R as DesR

/** The search field at the top of the home: it looks like Android's Settings one and opens the search page when tapped. */
@Composable
internal fun SettingsSearchEntry(onClick: () -> Unit) {
    val label = stringResource(R.string.search_title)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = dimensionResource(DesR.dimen.search_bar_height))
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_l)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The search page: a field, focused as it opens, and the settings the query finds among those this user may open, each with where it is.
 * A result opens its page through [open].
 */
@Composable
internal fun SettingsSearchScreen(
    profile: JellyfinServerProfile,
    account: JellyfinAccount,
    open: (ConsolePage) -> Unit,
    onBack: () -> Unit,
    initialQuery: String = "",
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BingeScreenScaffold(title = stringResource(R.string.search_title), bar = ScreenBar.Small, onBack = onBack) { padding ->
        ScreenBody(padding) { _ ->
            val found =
                remember(
                    query,
                    profile,
                    account,
                ) { searchSettings(query, settingsIndex, { it.available(profile, account) }, context::getString) }
            Column(
                modifier = Modifier.fillMaxSize().padding(padding.screenListPadding()),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
            ) {
                BingeSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    placeholder = stringResource(R.string.search_title),
                    focusRequester = focus,
                )
                when {
                    query.isBlank() ->
                        EmptyScreen(
                            stringResource(R.string.search_prompt),
                            title = stringResource(R.string.search_title),
                            icon = Icons.Filled.Search,
                        )
                    found.isEmpty() ->
                        EmptyScreen(
                            stringResource(R.string.search_no_match),
                            title = stringResource(R.string.search_no_match_title),
                        )
                    else -> Results(found, open)
                }
            }
        }
    }
}

@Composable
private fun Results(
    found: List<SettingEntry>,
    open: (ConsolePage) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item {
            ItemGroup(
                title = null,
                rows =
                    found.map { entry ->
                        ListItem(
                            icon = entry.icon,
                            label = stringResource(entry.label),
                            detail = entry.path(),
                            onClick = { open(entry.page) },
                        )
                    },
            )
        }
    }
}

/** Where an entry is: its section, and for a setting its page too. */
@Composable
private fun SettingEntry.path(): String =
    if (pageLabel == null || pageLabel == section) {
        stringResource(section)
    } else {
        stringResource(R.string.search_path, stringResource(section), stringResource(pageLabel))
    }
