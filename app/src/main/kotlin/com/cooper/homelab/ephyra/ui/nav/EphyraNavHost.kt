package com.cooper.homelab.ephyra.ui.nav

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.binge.designsystem.motion.rememberBingeNavMotion
import com.cooper.homelab.ephyra.ui.console.ConsoleEntry
import com.cooper.homelab.ephyra.ui.console.ConsolePageStores
import com.cooper.homelab.ephyra.ui.console.ConsoleThreePaneStrategy
import com.cooper.homelab.ephyra.ui.console.DefaultPane
import com.cooper.homelab.ephyra.ui.console.DetailPane
import com.cooper.homelab.ephyra.ui.console.ItemPlaceholderPane
import com.cooper.homelab.ephyra.ui.console.ItemRole
import com.cooper.homelab.ephyra.ui.console.LocalHubBeside
import com.cooper.homelab.ephyra.ui.console.LocalOpenItem
import com.cooper.homelab.ephyra.ui.console.ThreePaneHub
import com.cooper.homelab.ephyra.ui.console.isThreePaneWindow
import com.cooper.homelab.ephyra.ui.console.pageAlone
import com.cooper.homelab.ephyra.ui.console.pageMetadata
import com.cooper.homelab.ephyra.ui.console.predictivePopFor
import com.cooper.homelab.ephyra.ui.console.rememberConsolePaneStrategy

/**
 * The one [NavDisplay]: every screen is an entry on [backStack]. The sign-in steps are [HomeRoute] ([signedOut]); signed in,
 * [HubRoute] is the home and the console's pages and items are the keys above it. Back pops the top key through [onBack],
 * after any `BackHandler` a page has of its own (a list's open detail closes first). Each entry keeps its own saved state,
 * so the home's scroll position is still there when it comes back, and its own view models, which live as long as the entry
 * is on the stack, across a rotation, and go with it when it leaves. A page and the items open in it share theirs.
 *
 * Signed in, the home is the list pane: on a window wide enough for two panes (an unfolded Fold, a tablet) the open page,
 * and every page it opens, shows beside it, and [defaultPage] fills the detail pane while none is open so it is never
 * empty. On a narrow window each is the whole window. On a landscape tablet the page is given two thirds of the window
 * ([ConsoleThreePaneStrategy]), for a list and what it opened.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun EphyraNavHost(
    backStack: NavBackStack<NavKey>,
    signedIn: Boolean,
    onBack: () -> Unit,
    signedOut: @Composable () -> Unit,
    home: @Composable () -> Unit,
    defaultPage: @Composable () -> Unit,
    page: @Composable (ConsoleEntry) -> Unit,
    item: @Composable (ConsoleEntry) -> Unit = {},
    resolved: Boolean = true,
) {
    LaunchedEffect(signedIn, resolved) { backStack.settleHome(signedIn, resolved) }
    // One directive for the strategy and for the Back-arrow rule, so the two cannot disagree about whether the home
    // is on screen beside a page.
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    // An entry is built once for its key and keeps its metadata and content for as long as the key is on the stack, so
    // what changes later (a fold, a page opened) is read inside the content, through these.
    val hubBeside by rememberUpdatedState(signedIn && directive.maxHorizontalPartitions > 1)
    // A landscape tablet: the page gets two thirds of the window, for a list and what it opened.
    val threePane = hubBeside && isThreePaneWindow()
    val threePaneNow by rememberUpdatedState(threePane)
    val depth by rememberUpdatedState(backStack.entries().size)
    val signedOutNow by rememberUpdatedState(signedOut)
    val homeNow by rememberUpdatedState(home)
    val defaultPageNow by rememberUpdatedState(defaultPage)
    val pageNow by rememberUpdatedState(page)
    val itemNow by rememberUpdatedState(item)
    // A page and the items open in it share their view models, kept by opening.
    val stores: ConsolePageStores = viewModel()
    val openings = backStack.openings()
    LaunchedEffect(openings) { stores.retain(openings) }
    val openItem by rememberUpdatedState(backStack.topItem)
    // The setting a page was opened on: read inside its content, as the entry is built once for its key.
    val rows by rememberUpdatedState(backStack.consoleKeys.filterIsInstance<PageRoute>().associate { it.opening to it.row })
    val motion = rememberBingeNavMotion()
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        sceneStrategies =
            listOf(
                ConsoleThreePaneStrategy(threePane, { DefaultPane(defaultPageNow) }) { ItemPlaceholderPane(it) },
                rememberConsolePaneStrategy(directive),
            ),
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { motion.detailForward },
        popTransitionSpec = { motion.detailPop },
        predictivePopTransitionSpec = { motion.predictivePopFor(initialState.entries.lastOrNull()?.metadata, hubBeside) },
        entryProvider = { key ->
            when (key) {
                HomeRoute -> NavEntry(key) { signedOutNow() }
                HubRoute ->
                    NavEntry(
                        key,
                        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DefaultPane(defaultPageNow) }) + ThreePaneHub,
                    ) {
                        CompositionLocalProvider(LocalIsSinglePaneNav provides !hubBeside) {
                            PaneContent(innerEdge = PaneEdge.End) { homeNow() }
                        }
                    }
                is PageRoute ->
                    // Keyed by the opening, so a page opened on another row is the same entry and keeps its state.
                    NavEntry(key, contentKey = "page-${key.opening}", metadata = pageMetadata(key.page)) {
                        // A page that opens its items as entries is a pane of its own beside them on a tablet; one
                        // that does not is given the two panes' width and draws its list and item itself.
                        val ownPane = threePaneNow && key.page.opensItems
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides stores.owner(key.opening),
                            LocalIsSinglePaneNav provides pageAlone(key.page, hubBeside),
                            LocalHubBeside provides hubBeside,
                            LocalPaneDepth provides if (ownPane) 1 else depth,
                            LocalOpenItem provides openItem?.takeIf { it.opening == key.opening }?.item,
                        ) {
                            PaneContent(innerEdge = if (ownPane) PaneEdge.Both else PaneEdge.Start) {
                                pageNow(ConsoleEntry(key.page, key.opening, row = rows[key.opening]))
                            }
                        }
                    }
                is ItemRoute ->
                    NavEntry(key, metadata = DetailPane + ItemRole) {
                        // Beside its page on a tablet the item never leaves the screen, so it offers no Back of its own, but
                        // an item opened over it (a user's section) does; anywhere else it is stacked above its page and does.
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides stores.owner(key.opening),
                            LocalIsSinglePaneNav provides !hubBeside,
                            LocalPaneDepth provides if (threePaneNow) (depth - 1).coerceAtLeast(1) else depth,
                        ) {
                            PaneContent(innerEdge = PaneEdge.Start) {
                                itemNow(ConsoleEntry(key.page, key.opening, key.item))
                            }
                        }
                    }
                else -> error("No screen for $key")
            }
        },
    )
}
