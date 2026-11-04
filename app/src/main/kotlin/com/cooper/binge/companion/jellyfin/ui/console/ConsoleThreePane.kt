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
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.R as DesR

private const val HUB_ROLE = "console.threePane.hub"

/** Marks the home's entry, so [ConsoleThreePaneStrategy] knows which entry is the first pane. */
internal val ThreePaneHub: Map<String, Any> = mapOf(HUB_ROLE to true)

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
 * other two thirds. Only on a [enabled] window; else it is not this strategy's to lay out, and the list-detail
 * strategy does it.
 *
 * Not Material adaptive's list-detail-extra, which gives every pane the same width: a page that splits itself in two
 * needs a pane twice the home's, so that each of the three is a third of the window.
 */
internal class ConsoleThreePaneStrategy(
    private val enabled: Boolean,
    private val defaultPage: @Composable () -> Unit,
) : SceneStrategy<ConsoleKey> {
    override fun SceneStrategyScope<ConsoleKey>.calculateScene(entries: List<NavEntry<ConsoleKey>>): Scene<ConsoleKey>? {
        if (!enabled) return null
        val hub = entries.indexOfLast { it.metadata[HUB_ROLE] == true }
        if (hub == -1) return null
        return ConsoleThreePaneScene(entries, entries[hub], entries.lastOrNull()?.takeIf { entries.lastIndex > hub }, defaultPage)
    }
}

private class ConsoleThreePaneScene(
    override val entries: List<NavEntry<ConsoleKey>>,
    private val hub: NavEntry<ConsoleKey>,
    private val page: NavEntry<ConsoleKey>?,
    private val defaultPage: @Composable () -> Unit,
) : Scene<ConsoleKey> {
    /** One key for every stack, so opening or closing a page swaps the pane's content rather than animating a new scene in. */
    override val key: Any = ConsoleThreePaneScene::class
    override val previousEntries: List<NavEntry<ConsoleKey>> = entries.dropLast(1)

    override val content: @Composable () -> Unit = {
        ThreePaneRow(hub = { hub.Content() }, page = { page?.Content() ?: defaultPage() })
    }
}

/** The home a third wide and the page two thirds, with the design system's gap between them. */
@Composable
private fun ThreePaneRow(
    hub: @Composable () -> Unit,
    page: @Composable () -> Unit,
) {
    val gap = dimensionResource(DesR.dimen.pane_spacer)
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { hub() }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(2f).fillMaxHeight()) { page() }
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
        val gap = dimensionResource(DesR.dimen.pane_spacer)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { list() }
            Spacer(Modifier.width(gap))
            Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
        }
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
