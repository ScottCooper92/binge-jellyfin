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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
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
 * none is open so it is never empty. On a narrow window each is the whole window, as before. On a landscape tablet
 * the page is given two thirds of the window ([ConsoleThreePaneStrategy]), for a list and what it opened.
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
    item: @Composable (ConsoleEntry) -> Unit = {},
) {
    // One directive for the strategy and for the Back-arrow rule, so the two cannot disagree about whether the home
    // is on screen beside a page.
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    // An entry is built once for its key and keeps its metadata and content for as long as the key is on the stack, so
    // what changes later (a fold, a page opened) is read inside the content, through these.
    val hubBeside by rememberUpdatedState(signedIn && directive.maxHorizontalPartitions > 1)
    // A landscape tablet: the page gets two thirds of the window, for a list and what it opened.
    val threePane = hubBeside && isThreePaneWindow()
    val threePaneNow by rememberUpdatedState(threePane)
    val depth by rememberUpdatedState(stack.entries.size)
    val homeNow by rememberUpdatedState(home)
    val defaultPageNow by rememberUpdatedState(defaultPage)
    val pageNow by rememberUpdatedState(page)
    val itemNow by rememberUpdatedState(item)
    // A page and the items open in it share their view models, kept by opening.
    val stores: ConsolePageStores = viewModel()
    LaunchedEffect(stack.openings) { stores.retain(stack.openings) }
    val openItem by rememberUpdatedState(stack.topItem)
    NavDisplay(
        backStack = stack.keys(signedIn),
        onBack = onBack,
        sceneStrategies =
            listOf(
                ConsoleThreePaneStrategy(threePane, { DefaultPane(defaultPageNow) }) { ItemPlaceholderPane(it) },
                rememberConsolePaneStrategy(directive),
            ),
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { Instant },
        popTransitionSpec = { Instant },
        predictivePopTransitionSpec = { Instant },
        entryProvider = { key ->
            when (key) {
                ConsoleKey.Home -> NavEntry(key) { homeNow() }
                ConsoleKey.Hub ->
                    NavEntry(
                        key,
                        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DefaultPane(defaultPageNow) }) + ThreePaneHub,
                    ) {
                        CompositionLocalProvider(LocalIsSinglePaneNav provides !hubBeside) {
                            PaneContent(innerEdge = PaneEdge.End) { homeNow() }
                        }
                    }
                is ConsoleKey.Page ->
                    NavEntry(key, metadata = DetailPane + pageRole(key.page)) {
                        // A page that opens its items as entries is a pane of its own beside them on a tablet; one
                        // that does not is given the two panes' width and draws its list and item itself.
                        val ownPane = threePaneNow && key.page.opensItems
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides stores.owner(key.opening),
                            LocalIsSinglePaneNav provides !hubBeside,
                            LocalPaneDepth provides if (ownPane) 1 else depth,
                            LocalOpenItem provides openItem?.takeIf { it.opening == key.opening }?.item,
                        ) {
                            PaneContent(innerEdge = if (ownPane) PaneEdge.Both else PaneEdge.Start) {
                                pageNow(ConsoleEntry(key.page, key.opening))
                            }
                        }
                    }
                is ConsoleKey.Item ->
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
            }
        },
    )
}

/**
 * What fills the detail pane while no page is open. Not an entry, so it is not on the stack and has no Back of its own
 * to offer. The locals wrap PaneContent, which reads LocalIsSinglePaneNav where it is called.
 */
@Composable
private fun DefaultPane(defaultPage: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsSinglePaneNav provides false, LocalPaneDepth provides 1) {
        PaneContent(innerEdge = PaneEdge.Start) {
            OwnViewModelStore { defaultPage() }
        }
    }
}

/** What fills a page's item pane on a landscape tablet while none is open. Not an entry, so it has no Back of its own. */
@Composable
private fun ItemPlaceholderPane(page: ConsolePage) {
    CompositionLocalProvider(LocalIsSinglePaneNav provides false, LocalPaneDepth provides 1) {
        PaneContent(innerEdge = PaneEdge.Start) {
            when (page) {
                ConsolePage.Tasks -> TaskPlaceholder()
                ConsolePage.Logs -> LogPlaceholder()
                ConsolePage.Plugins -> PluginPlaceholder()
                ConsolePage.Users -> UserPlaceholder()
                ConsolePage.HomeLayout -> HomeLayoutPlaceholder()
                ConsolePage.LibraryDisplay -> LibraryDisplayPlaceholder()
                else -> Unit
            }
        }
    }
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

/**
 * A view-model store of its own for [content], cleared when it leaves the composition. The detail placeholder is not
 * a navigation entry, so no decorator scopes it and its view models would otherwise land in the Activity's store and
 * outlive it: an open event socket after a page replaces it, and the previous user's feed after another signs in.
 */
@Composable
private fun OwnViewModelStore(content: @Composable () -> Unit) {
    val owner =
        remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
}
