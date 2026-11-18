package com.cooper.homelab.ephyra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ServerCard
import com.binge.designsystem.component.ServerCardChip
import com.binge.designsystem.component.ServerCardStat
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.flags.LocalFlags
import com.cooper.homelab.ephyra.flags.bingePromotion
import com.cooper.homelab.ephyra.server.TmdbCoverage
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import com.binge.designsystem.R as DesR

/**
 * The console home's server card: which server, whether it answered, and what is worth acting on (not encrypted,
 * an update, a restart). Its totals are the ones the signed-in user may see: an administrator also gets the user
 * count. A total that hasn't arrived is a placeholder, and one whose read failed stays one. Where the server is reached over plain HTTP
 * by the user's agreement, its "Not encrypted" mark opens the way to take that back ([onTakeBack]).
 */
@Composable
internal fun ServerHomeCard(
    state: UiState.SignedIn,
    canOpen: (String) -> Boolean = LocalContext.current::canOpen,
    open: (String) -> Unit = LocalContext.current::openInBrowser,
    onTakeBack: () -> Unit = {},
) {
    val session = state.session
    val card = state.card
    // The Update available pill opens the list of releases, where something can: the API gives a flag, not the newer version's number.
    val updateOpens = remember { canOpen(JELLYFIN_RELEASES_URL) }
    val releaseNotes = stringResource(R.string.release_notes)
    val chips =
        listOfNotNull(
            ServerCardChip(
                label = stringResource(R.string.server_card_not_encrypted),
                sentiment = BingeSentiment.Caution,
                // Where the user agreed to it on the server step, the mark is also the way to take that back.
                onClick = onTakeBack.takeIf { state.consentHost != null },
                contentDescription = stringResource(R.string.take_back_description).takeIf { state.consentHost != null },
            ).takeIf { state.cleartext },
            ServerCardChip(
                label = stringResource(R.string.server_card_update),
                sentiment = BingeSentiment.Info,
                onClick = { open(JELLYFIN_RELEASES_URL) }.takeIf { updateOpens },
                contentDescription = releaseNotes.takeIf { updateOpens },
            ).takeIf { card.updateAvailable },
            ServerCardChip(stringResource(R.string.server_card_restart), BingeSentiment.Caution).takeIf { card.restartPending },
            ServerCardChip(stringResource(R.string.server_card_shutting_down), BingeSentiment.Negative).takeIf { card.shuttingDown },
        )
    val stats =
        listOfNotNull(
            ServerCardStat(Icons.Outlined.PlayCircle, card.playing?.toString(), stringResource(R.string.server_card_playing)),
            ServerCardStat(Icons.Outlined.People, card.users?.toString(), stringResource(R.string.server_card_users))
                .takeIf { state.isAdministrator },
            ServerCardStat(Icons.Outlined.Movie, card.movies?.toString(), stringResource(R.string.server_card_movies)),
            ServerCardStat(Icons.Outlined.Tv, card.series?.toString(), stringResource(R.string.server_card_series)),
        )
    ServerCard(
        title = session.serverName.ifBlank { session.baseUrl },
        logo = painterResource(R.drawable.ic_companion),
        subtitle = session.serverVersion.takeIf { it.isNotBlank() }?.let { stringResource(R.string.server_card_version, it) },
        address = session.baseUrl,
        chips = chips,
        statusChip = ServerCardChip(stringResource(R.string.server_card_connected), BingeSentiment.Positive).takeIf { card.connected },
        stats = stats,
        // The list around it already insets its rows.
        sides = PaddingValues(),
    )
}

/**
 * Says, once, that some of the library cannot be matched by TMDB id, with the cause and the fix: those titles carry no id, so
 * nothing that looks one up can find them. It is read-only, and shows only when there are some. What it names is plain unless
 * the Binge flag is on, as the rest of the app does.
 */
@Composable
internal fun TmdbCoverageNote(coverage: TmdbCoverage?) {
    if (coverage == null || coverage.withoutTmdbId <= 0) return
    val detail = if (LocalFlags.current.bingePromotion) R.string.tmdb_missing_detail_promoted else R.string.tmdb_missing_detail_plain
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.Info,
                    label =
                        pluralStringResource(
                            R.plurals.tmdb_missing_title,
                            coverage.withoutTmdbId,
                            coverage.withoutTmdbId,
                            coverage.titles,
                        ),
                    detail = stringResource(detail),
                    supportingMaxLines = NOTE_LINES,
                    clickable = false,
                ),
            ),
    )
}

private const val NOTE_LINES = 10

/**
 * Whether the home hints at adding a first library: an administrator whose server has answered that it has none. A count that
 * hasn't arrived or whose read failed is null and shows nothing, and the page it points at must be open to them.
 */
internal fun UiState.SignedIn.needsFirstLibrary(): Boolean =
    isAdministrator && card.libraries == 0 && ConsolePage.Libraries.available(profile, account)

/** Says a server with no libraries has nothing to answer from, and takes the administrator to the page that adds one. */
@Composable
internal fun FirstLibraryCard(onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.VideoLibrary,
                        label = stringResource(R.string.first_library_title),
                        detail = stringResource(R.string.first_library_detail),
                        supportingMaxLines = NOTE_LINES,
                        clickable = false,
                    ),
                ),
        )
        BingeFilledButton(
            label = stringResource(R.string.libraries_add),
            onClick = onAdd,
            leadingIcon = Icons.Outlined.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
