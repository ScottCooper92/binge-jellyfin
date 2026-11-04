package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.binge.designsystem.R as DesR

private const val HUB_ROLE = "console.threePane.hub"
private const val PAGE_ROLE = "console.threePane.page"
private const val ITEM_ROLE = "console.threePane.item"

/** Marks the home's entry, so [ConsoleThreePaneStrategy] knows which entry is the first pane. */
internal val ThreePaneHub: Map<String, Any> = mapOf(HUB_ROLE to true)

/** Marks a page's entry, and which page it is, for the pane that stands in for its item while none is open. */
internal fun pageRole(page: ConsolePage): Map<String, Any> = mapOf(PAGE_ROLE to page)

/** Marks an item's entry: the third pane on a landscape tablet. */
internal val ItemRole: Map<String, Any> = mapOf(ITEM_ROLE to true)

/** The item open in the page showing, for the page's list to mark; null where none is. */
internal val LocalOpenItem = staticCompositionLocalOf<String?> { null }

/**
 * Whether the window is a landscape tablet: at least 1000 dp wide with a smallest width of at least 600 dp, the design
 * system's own line for an expanded rail, and binge-seerr's for its third pane. A resource, so a rotation answers again.
 */
@Composable
internal fun isThreePaneWindow(): Boolean = booleanResource(DesR.bool.binge_nav_rail_expanded)

/**
 * The home as the first pane, a third of the window, and the newest page, or [defaultPage] while none is open, as the
 * other two thirds. A page whose items are entries ([ConsolePage.opensItems]) is the second pane alone, with its newest
 * item, or [itemPlaceholder] while none is open, as the third: each a third of the window. Only on an [enabled] window;
 * else it is not this strategy's to lay out, and the list-detail strategy does it.
 *
 * Not Material adaptive's list-detail-extra, which gives every pane the same width and has no placeholder for the third.
 */
internal class ConsoleThreePaneStrategy(
    private val enabled: Boolean,
    private val defaultPage: @Composable () -> Unit,
    private val itemPlaceholder: @Composable (ConsolePage) -> Unit,
) : SceneStrategy<ConsoleKey> {
    override fun SceneStrategyScope<ConsoleKey>.calculateScene(entries: List<NavEntry<ConsoleKey>>): Scene<ConsoleKey>? {
        if (!enabled) return null
        val hub = entries.indexOfLast { it.metadata[HUB_ROLE] == true }
        if (hub == -1) return null
        val top = entries.lastOrNull()?.takeIf { entries.lastIndex > hub }
        val topIsItem = top?.metadata?.get(ITEM_ROLE) == true
        // The page is the last page entry, not the one under the top: an item may be open over another (a user's section).
        val page = if (topIsItem) entries.lastOrNull { it.metadata[PAGE_ROLE] != null } else top
        val page2 = page?.metadata?.get(PAGE_ROLE) as? ConsolePage
        return if (page2?.opensItems == true) {
            ConsoleThreePaneScene(entries, entries[hub], page, top.takeIf { topIsItem }, page2, defaultPage, itemPlaceholder)
        } else {
            ConsoleThreePaneScene(entries, entries[hub], top, null, null, defaultPage, itemPlaceholder)
        }
    }
}

private class ConsoleThreePaneScene(
    override val entries: List<NavEntry<ConsoleKey>>,
    private val hub: NavEntry<ConsoleKey>,
    private val page: NavEntry<ConsoleKey>?,
    private val item: NavEntry<ConsoleKey>?,
    private val itemsOf: ConsolePage?,
    private val defaultPage: @Composable () -> Unit,
    private val itemPlaceholder: @Composable (ConsolePage) -> Unit,
) : Scene<ConsoleKey> {
    /** One key for every stack, so opening or closing a page or an item swaps a pane's content rather than animating a new scene in. */
    override val key: Any = ConsoleThreePaneScene::class
    override val previousEntries: List<NavEntry<ConsoleKey>> = entries.dropLast(1)

    override val content: @Composable () -> Unit = {
        if (itemsOf == null) {
            PaneRow(1f, { hub.Content() }, 2f, { page?.Content() ?: defaultPage() })
        } else {
            ThreePaneRow({ hub.Content() }, { page?.Content() }, { item?.Content() ?: itemPlaceholder(itemsOf) })
        }
    }
}

/** The home, a page and its item, each a third of the window, with the design system's gap between them. */
@Composable
private fun ThreePaneRow(
    hub: @Composable () -> Unit,
    page: @Composable () -> Unit,
    item: @Composable () -> Unit,
) {
    val gap = dimensionResource(DesR.dimen.pane_spacer)
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { hub() }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f).fillMaxHeight()) { page() }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f).fillMaxHeight()) { item() }
    }
}

/** Two panes in a row, [first] and [second] sharing the width by their weights, with the design system's gap between them. */
@Composable
private fun PaneRow(
    firstWeight: Float,
    first: @Composable () -> Unit,
    secondWeight: Float,
    second: @Composable () -> Unit,
) {
    val gap = dimensionResource(DesR.dimen.pane_spacer)
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(firstWeight).fillMaxHeight()) { first() }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(secondWeight).fillMaxHeight()) { second() }
    }
}
