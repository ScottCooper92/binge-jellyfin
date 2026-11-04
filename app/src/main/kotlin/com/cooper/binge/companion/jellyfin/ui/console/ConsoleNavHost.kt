package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.PaneBackNavigationBehavior
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge

/** A page changes with no animation, as it did before Navigation 3: the new page is simply there. */
private val Instant = ContentTransform(EnterTransition.None, ExitTransition.None)

/** The metadata that puts a page in the detail pane beside the hub; on a narrow window it takes the whole window. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val DetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * The console's pages as Navigation 3 entries over [stack]: the home at the bottom, then each open page. Back
 * pops the top page through [onBack], after any `BackHandler` a page has of its own (a list's open detail closes
 * first). Each entry keeps its own saved state, so the home's scroll position is still there when it comes back,
 * and its own view models, which live as long as the entry is on the stack, across a rotation, and go with it
 * when it leaves.
 *
 * Signed in ([signedIn]), the home is the list pane: on a window wide enough for two panes (an unfolded Fold, a
 * tablet) the open page, and every page it opens, shows beside it, and [defaultPage] fills the detail pane while
 * none is open so it is never empty. On a narrow window each is the whole window, as before.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ConsoleNavHost(
    stack: ConsoleBackStack,
    signedIn: Boolean,
    onBack: () -> Unit,
    home: @Composable () -> Unit,
    defaultPage: @Composable () -> Unit,
    page: @Composable (ConsoleEntry) -> Unit,
) {
    // One directive for the strategy and for the Back-arrow rule, so the two cannot disagree about whether the home
    // is on screen beside a page.
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    // An entry is built once for its key and keeps its metadata and content for as long as the key is on the stack, so
    // what changes later (a fold, a page opened) is read inside the content, through these.
    val hubBeside by rememberUpdatedState(signedIn && directive.maxHorizontalPartitions > 1)
    val depth by rememberUpdatedState(stack.entries.size)
    NavDisplay(
        backStack = stack.keys(signedIn),
        onBack = onBack,
        sceneStrategies = listOf(rememberConsolePaneStrategy(directive)),
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { Instant },
        popTransitionSpec = { Instant },
        predictivePopTransitionSpec = { Instant },
        entryProvider = { key ->
            when (key) {
                ConsoleKey.Home -> NavEntry(key) { home() }
                ConsoleKey.Hub ->
                    NavEntry(
                        key,
                        metadata =
                            ListDetailSceneStrategy.listPane(
                                detailPlaceholder = {
                                    PaneContent(innerEdge = PaneEdge.Start) {
                                        // Not an entry, so it is not on the stack and has no Back of its own to offer.
                                        CompositionLocalProvider(LocalIsSinglePaneNav provides false, LocalPaneDepth provides 1) {
                                            defaultPage()
                                        }
                                    }
                                },
                            ),
                    ) {
                        PaneContent(innerEdge = PaneEdge.End) { home() }
                    }
                is ConsoleKey.Page ->
                    NavEntry(key, metadata = DetailPane) {
                        PaneContent(innerEdge = PaneEdge.Start) {
                            CompositionLocalProvider(LocalIsSinglePaneNav provides !hubBeside, LocalPaneDepth provides depth) {
                                page(ConsoleEntry(key.page, key.opening))
                            }
                        }
                    }
            }
        },
    )
}

/**
 * The list-detail strategy for the home and the page beside it. [PaneBackNavigationBehavior] (design system, shared
 * with the other apps): the library's own default pops until the layout changes, and with the home at the root of
 * the stack there is no earlier layout to change to, so Back beside it would pass every stacked page.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberConsolePaneStrategy(directive: PaneScaffoldDirective): ListDetailSceneStrategy<ConsoleKey> =
    rememberListDetailSceneStrategy(
        backNavigationBehavior = PaneBackNavigationBehavior,
        directive = directive.copy(defaultPanePreferredWidth = equalPaneWidth(directive)),
    )

/** Half the window less half the gap between the panes, so the gap sits at the window's centre. */
@Composable
private fun equalPaneWidth(directive: PaneScaffoldDirective): Dp {
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val windowWidth = with(LocalDensity.current) { windowWidthPx.toDp() }
    return (windowWidth - directive.horizontalPartitionSpacerSize) / 2
}
