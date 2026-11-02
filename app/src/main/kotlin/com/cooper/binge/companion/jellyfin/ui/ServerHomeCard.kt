package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ServerCard
import com.binge.designsystem.component.ServerCardChip
import com.binge.designsystem.component.ServerCardStat
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.jellyfin.R

/**
 * The console home's server card: which server, whether it answered, and what is worth acting on (not encrypted,
 * an update, a restart). Its totals are the ones the signed-in user may see: an administrator also gets the user
 * count. A total that hasn't arrived is a placeholder, and one whose read failed stays one.
 */
@Composable
internal fun ServerHomeCard(state: UiState.SignedIn) {
    val session = state.session
    val card = state.card
    val chips =
        listOfNotNull(
            ServerCardChip(stringResource(R.string.server_card_not_encrypted), BingeSentiment.Caution).takeIf { state.cleartext },
            ServerCardChip(stringResource(R.string.server_card_update), BingeSentiment.Info).takeIf { card.updateAvailable },
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
