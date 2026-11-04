package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.template.EmptyScreen
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
 * Whether the page showing is given two panes' width: a page that lists and opens things (users, logs, tasks) then
 * draws the list and what it opened side by side, the home still a pane of its own beside them. False everywhere else,
 * where the page is one pane and opens an item in place of its list.
 */
internal val LocalPageSpansTwoPanes = staticCompositionLocalOf { false }

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
        val page = if (topIsItem) entries.getOrNull(entries.lastIndex - 1) else top
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

/**
 * A list-detail page's body: on a page given two panes' width ([LocalPageSpansTwoPanes]), the [list] and the [detail]
 * side by side, each a third of the window; anywhere else [detail] while [detailOpen] and [list] otherwise, as the page
 * has always done, with its own Back to close the detail.
 */
@Composable
internal fun ListDetailBody(
    detailOpen: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    if (LocalPageSpansTwoPanes.current) {
        PaneRow(1f, list, 1f, detail)
    } else if (detailOpen) {
        detail()
    } else {
        list()
    }
}

/** The padding under a detail pane's [DetailHeading]: [this] without its top, which the heading took. */
@Composable
internal fun PaddingValues.belowHeading(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )
}

/** The title of the item open in the detail pane beside its list, where alone it would be the page's own title. */
@Composable
internal fun DetailHeading(
    text: String,
    inner: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier.padding(
                top = inner.calculateTopPadding(),
                start = inner.calculateStartPadding(LocalLayoutDirection.current),
            ),
    )
}

/** The detail pane while the list beside it has nothing open. */
@Composable
internal fun NothingSelected(
    title: String,
    message: String,
    icon: ImageVector,
    inner: PaddingValues,
) {
    Box(Modifier.fillMaxSize().padding(inner)) { EmptyScreen(message = message, title = title, icon = icon) }
}

/**
 * Marks [content] as one level deeper than its page while an item is open inside it ([open]): a task, a log, a user, a
 * section. Beside the home a page has no Back of its own, since the home never leaves the screen. But an open item is
 * not that: Back from it returns to the page's list, which is the only way there on screen. A level deeper is what the
 * design system's rule keeps the Back arrow for. Where the page spans two panes ([LocalPageSpansTwoPanes]) the item is
 * beside its list, not over it, so nothing changes.
 */
@Composable
internal fun PageItemDepth(
    open: Boolean,
    content: @Composable () -> Unit,
) {
    val deeper = open && !LocalPageSpansTwoPanes.current
    CompositionLocalProvider(LocalPaneDepth provides LocalPaneDepth.current + if (deeper) 1 else 0, content = content)
}
