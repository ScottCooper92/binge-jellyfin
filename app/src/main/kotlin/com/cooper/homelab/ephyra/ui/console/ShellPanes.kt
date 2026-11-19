package com.cooper.homelab.ephyra.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

private const val SHELL_ROLE = "console.shell"

/** Marks the shell's entry, so [ShellPanesStrategy] knows which entry holds the tab bar and the Server tab. */
internal val ShellPane: Map<String, Any> = mapOf(SHELL_ROLE to true)

/**
 * What sits above the shell on the app's stack, for the Server tab to draw beside its home: the newest [page] (or none, for the
 * default page), and the newest [item] with the [itemsOf] page it belongs to when that page opens its items as a third pane.
 */
@Immutable
internal class ShellDetail(
    val page: NavEntry<NavKey>?,
    val item: NavEntry<NavKey>?,
    val itemsOf: ConsolePage?,
    val threePane: Boolean,
)

/** The detail the scene hands the shell's entry. Null outside [ShellPanesStrategy]'s scene: the Server tab is then the home alone. */
internal val LocalShellDetail = compositionLocalOf<ShellDetail?> { null }

/**
 * A wide window's shell: one scene for the shell's entry and the pages above it, so the bar or rail stays and the Server tab draws
 * them beside its home. The pages stay on the app's stack at every width; only how they are drawn changes with the window, so a
 * fold moves no entry and its view models and scroll survive. A stack whose first entry above the shell is not a page that opens
 * beside the home (the settings search covers the window) is not this strategy's, and covers the whole window as on a phone.
 *
 * On a [threePane] window, a page that opens its items is the second pane and its item the third, as [ConsoleThreePaneStrategy]
 * lays them out; otherwise the newest page is the one other pane.
 */
internal class ShellPanesStrategy(
    private val enabled: Boolean,
    private val threePane: Boolean,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        if (!enabled) return null
        val shell = entries.firstOrNull()?.takeIf { it.metadata[SHELL_ROLE] == true } ?: return null
        val above = entries.drop(1)
        if (above.isNotEmpty() && above.first().metadata[PAGE_ROLE] == null) return null
        val top = above.lastOrNull()
        val topIsItem = top?.metadata?.get(ITEM_ROLE) == true
        // The page is the last page entry, not the one under the top: an item may be open over another (a user's section).
        val page = if (topIsItem) above.lastOrNull { it.metadata[PAGE_ROLE] != null } else top
        val pageOf = page?.metadata?.get(PAGE_ROLE) as? ConsolePage
        val detail =
            if (threePane && pageOf?.opensItems == true) {
                ShellDetail(page, top.takeIf { topIsItem }, pageOf, true)
            } else {
                ShellDetail(top, null, null, threePane)
            }
        return ShellPanesScene(entries, shell, detail)
    }
}

private class ShellPanesScene(
    override val entries: List<NavEntry<NavKey>>,
    private val shell: NavEntry<NavKey>,
    private val detail: ShellDetail,
) : Scene<NavKey> {
    /** One key for every stack, so opening or closing a page swaps a pane's content rather than animating a new scene in. */
    override val key: Any = ShellPanesScene::class
    override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)

    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalShellDetail provides detail) { shell.Content() }
    }
}

/**
 * The Server tab: the [home] alone where no scene gave it a detail (a narrow window), else the home beside the open page or
 * [defaultPage], and on a landscape tablet a page's item or [itemPlaceholder] as a third pane. The home never changes; a page and
 * what it opens replace only the panes beside it.
 */
@Composable
internal fun ServerPanes(
    home: @Composable () -> Unit,
    defaultPage: @Composable () -> Unit,
    itemPlaceholder: @Composable (ConsolePage) -> Unit,
) {
    val detail = LocalShellDetail.current
    when {
        detail == null -> home()
        detail.itemsOf != null ->
            ThreePaneRow(home, { detail.page?.Content() }, { detail.item?.Content() ?: itemPlaceholder(detail.itemsOf) })
        detail.threePane -> PaneRow(1f, home, 2f, { detail.page?.Content() ?: defaultPage() })
        else -> PaneRow(1f, home, 1f, { detail.page?.Content() ?: defaultPage() })
    }
}
