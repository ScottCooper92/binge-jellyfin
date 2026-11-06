package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount

/** The Live TV page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun LiveTvRoute(
    graph: AppGraph,
    account: JellyfinAccount,
    onOpenTasks: (() -> Unit)?,
    onOpenRecording: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val liveTv: LiveTvViewModel =
        viewModel(factory = viewModelFactory { initializer { LiveTvViewModel(graph.connection, withGuide = account.canAccessLiveTv) } })
    val guide: LiveTvGuideViewModel =
        viewModel(factory = viewModelFactory { initializer { LiveTvGuideViewModel(graph.connection, liveTv::refresh) } })
    val sd: LiveTvSchedulesDirectViewModel =
        viewModel(factory = viewModelFactory { initializer { LiveTvSchedulesDirectViewModel(graph.connection, liveTv::refresh) } })
    val state by liveTv.state.collectAsStateWithLifecycle()
    val guideState by guide.state.collectAsStateWithLifecycle()
    val sdState by sd.state.collectAsStateWithLifecycle()
    LiveTvScreen(
        state,
        LiveTvActions(
            onOpenTasks = onOpenTasks,
            onOpenRecording = onOpenRecording,
            onRetry = liveTv::load,
            onBack = onBack,
            onSheet = liveTv::sheet,
            onDiscover = liveTv::discover,
            onForm = liveTv::form,
            onEdit = liveTv::edit,
            onSave = liveTv::save,
            onAskRemove = liveTv::askRemove,
            onCancelRemove = liveTv::cancelRemove,
            onRemove = liveTv::remove,
            onDismissProblem = liveTv::dismissProblem,
        ),
        guide = guideState,
        sd = sdState,
        canFinishSetup = account.canAccessLiveTv,
        sdActions =
            SdActions(
                onSignIn = sd::signIn,
                onChooseCountry = sd::chooseCountry,
                onPickCountry = sd::pickCountry,
                onPostalCode = sd::typePostalCode,
                onFind = sd::findLineups,
                onBackToLocation = sd::backToLocation,
                onBackToChoice = {
                    sd.close()
                    guide.choose()
                },
                onAddLineup = sd::addLineup,
                onClose = sd::close,
                onDismissProblem = sd::dismissProblem,
            ),
        guideActions =
            LiveTvGuideActions(
                // Schedules Direct is offered only where the user may read countries and lineups, which the server needs for its setup.
                onAdd = { if (account.canAccessLiveTv) guide.choose() else guide.add() },
                onAddXmlTv = guide::add,
                onAddSchedulesDirect = {
                    guide.choose(false)
                    sd.start()
                },
                onDismissChoice = { guide.choose(false) },
                onBackToChoice = guide::backToChoice,
                onFinishSetup = {
                    guide.dismissForm()
                    sd.locate(it)
                },
                onEdit = guide::edit,
                onSave = guide::save,
                onDismissForm = guide::dismissForm,
                onAskRemove = guide::askRemove,
                onCancelRemove = guide::cancelRemove,
                onRemove = guide::remove,
                onDismissProblem = guide::dismissProblem,
            ),
    )
}
