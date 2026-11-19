package com.cooper.homelab.ephyra.ui.console

import androidx.compose.animation.ContentTransform
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.PaneBackNavigationBehavior
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.binge.designsystem.motion.BingeNavMotion

/** The metadata that puts a page in the detail pane beside the hub; on a narrow window it takes the whole window. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val DetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * A page's pane role: the detail pane beside the home, with its page for the three-pane layout. The search is not given one: it
 * covers the whole window, as Android's own Settings search does, because without a pane role the strategy leaves the entry to
 * the default, a single pane.
 */
internal fun pageMetadata(page: ConsolePage): Map<String, Any> =
    if (page.coversWindow) mapOf(COVERS_WINDOW to true) else DetailPane + pageRole(page)

/** The metadata key of an entry that covers the whole window. A predictive back reads it off the popped entry, whose key is not public. */
internal const val COVERS_WINDOW = "ephyra.console.coversWindow"

/**
 * Whether popping the entry with [metadata] is a drill-down inside the window the home shares: the home is beside it ([hubBeside]) and the
 * page does not cover the window.
 */
internal fun isDrillDownPop(
    metadata: Map<String, Any>?,
    hubBeside: Boolean,
): Boolean = hubBeside && metadata?.get(COVERS_WINDOW) != true

/** Whether the home is on screen beside the pages: a window wide enough for two panes. The search reads it to open a result beside the home. */
internal val LocalHubBeside = compositionLocalOf { false }

/** Whether the page is the whole window, so it offers its own Back: a page that covers the window, or any page where the home is not beside. */
internal fun pageAlone(
    page: ConsolePage,
    hubBeside: Boolean,
): Boolean = page.coversWindow || !hubBeside

/**
 * The drag of a predictive back for the screen being popped ([isDrillDownPop]): a drill-down inside the window the home shares is the
 * lighter drag, as the screen under is the same one; a page that covers the window (the search) or fills it alone is a whole screen.
 */
internal fun BingeNavMotion.predictivePopFor(
    popped: Map<String, Any>?,
    hubBeside: Boolean,
): ContentTransform = if (isDrillDownPop(popped, hubBeside)) tabPredictivePop else detailPredictivePop

/**
 * What fills the detail pane while no page is open. Not an entry, so it is not on the stack and has no Back of its own
 * to offer. The locals wrap PaneContent, which reads LocalIsSinglePaneNav where it is called.
 */
@Composable
internal fun DefaultPane(defaultPage: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsSinglePaneNav provides false, LocalPaneDepth provides 1) {
        PaneContent(innerEdge = PaneEdge.Start) {
            OwnViewModelStore { defaultPage() }
        }
    }
}

/** What fills a page's item pane on a landscape tablet while none is open. Not an entry, so it has no Back of its own. */
@Composable
internal fun ItemPlaceholderPane(page: ConsolePage) {
    CompositionLocalProvider(LocalIsSinglePaneNav provides false, LocalPaneDepth provides 1) {
        PaneContent(innerEdge = PaneEdge.Start) {
            when (page) {
                ConsolePage.Tasks -> TaskPlaceholder()
                ConsolePage.Logs -> LogPlaceholder()
                ConsolePage.Plugins -> PluginPlaceholder()
                ConsolePage.Users -> UserPlaceholder()
                ConsolePage.HomeLayout -> HomeLayoutPlaceholder()
                ConsolePage.LibraryDisplay -> LibraryDisplayPlaceholder()
                ConsolePage.MetadataDefaults -> MetadataDefaultsPlaceholder()
                ConsolePage.Libraries -> LibrariesPlaceholder()
                ConsolePage.NowPlaying -> NowPlayingPlaceholder()
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
internal fun rememberConsolePaneStrategy(directive: PaneScaffoldDirective): ListDetailSceneStrategy<NavKey> =
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
