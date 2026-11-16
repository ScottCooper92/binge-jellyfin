package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BingePullToRefresh
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.binge.designsystem.R as DesR

/**
 * The home of a server that has answered: the server card, then a group for each thing the user may manage. A pull reads the server and
 * the library again in place.
 */
@Composable
internal fun SignedInHome(
    state: UiState.SignedIn,
    actions: Actions,
    inner: PaddingValues,
    topInset: Dp,
) {
    val promoted = LocalFlags.current.bingePromotion
    // A pull reads the server and the library again in place. Only a pull shows the spinner: the read every start makes would
    // otherwise draw it.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.refreshing) { if (!state.refreshing) pulled = false }
    BingePullToRefresh(
        isRefreshing = pulled && state.refreshing,
        onRefresh = {
            pulled = true
            actions.onRefresh()
        },
        indicatorTopInset = topInset,
    ) {
        LazyColumn(
            contentPadding = inner,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
        ) {
            item { ServerHomeCard(state) }
            if ((state.card.tmdb?.withoutTmdbId ?: 0) > 0) item { TmdbCoverageNote(state.card.tmdb) }
            if (state.needsFirstLibrary()) item { FirstLibraryCard { actions.onOpenPage(ConsolePage.Libraries) } }
            item { ServerGroup(state, actions) }
            // An administrator's own settings are in the account, behind the avatar. Everyone else has nothing else to
            // show, so their home is their account: the same rows, inline, and Sign out last.
            if (!state.isAdministrator) item { AccountGroup(state, actions) }
            adminSections(state, actions)
            // What Binge can find in the library is Binge's to show; without the flag the app names no Binge.
            if (promoted) item { LibraryGroup(state, actions) }
            if (state.isAdministrator) thisAppRow(actions) else homeFoot(actions)
        }
    }
}
