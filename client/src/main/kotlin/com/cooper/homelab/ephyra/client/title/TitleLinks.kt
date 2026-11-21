package com.cooper.homelab.ephyra.client.title

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailRoundAction
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.theme.BingeTheme
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.client.R
import com.binge.designsystem.R as DesR

private val TMDB_ID = Regex("""\d{1,12}""")
private val IMDB_ID = Regex("""tt\d{1,12}""")

/**
 * The public page to share for a title: its TMDb page when the server holds the TMDb id, else its IMDb page, else nothing. It is built
 * from the ids alone, so it is never an address on the user's server and carries no token. [series] picks TMDb's `tv` path over `movie`.
 */
internal fun TitleDetail.shareUrl(series: Boolean): String? {
    val tmdb = externalIds["Tmdb"]?.takeIf { TMDB_ID.matches(it) }
    val imdb = externalIds["Imdb"]?.takeIf { IMDB_ID.matches(it) }
    return when {
        tmdb != null -> "https://www.themoviedb.org/${if (series) "tv" else "movie"}/$tmdb"
        imdb != null -> "https://www.imdb.com/title/$imdb/"
        else -> null
    }
}

/** Share in the page's bar, beside back. [glassBackgroundAlpha] is the bar's own fade: the glass hands over to the bar as it fills. */
@Composable
internal fun ShareBarAction(
    glassBackgroundAlpha: Float,
    onShare: () -> Unit,
) {
    ExpressiveIconButton(
        onClick = onShare,
        icon = Icons.Filled.Share,
        contentDescription = stringResource(R.string.client_title_share),
        tint = lerp(BingeTheme.colors.onScrim, MaterialTheme.colorScheme.onBackground, 1f - glassBackgroundAlpha),
        tone = IconButtonTone.Glass,
        size = dimensionResource(DesR.dimen.top_bar_icon_size),
        glassBackgroundAlpha = glassBackgroundAlpha,
    )
}

/** The Trailer round action: it opens outside the app, so it carries the open-in-new mark and says so to a screen reader. */
@Composable
internal fun trailerAction(onClick: () -> Unit): DetailRoundAction {
    val label = stringResource(R.string.client_title_trailer)
    return DetailRoundAction(
        icon = Icons.Filled.PlayArrow,
        label = label,
        onClick = onClick,
        contentDescription = stringResource(DesR.string.cd_link_chip_opens_outside, label),
        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
    )
}

/** The bar's Share button for [url], or nothing when the title has no public page to share. */
internal fun shareAction(
    url: String?,
    onShare: (url: String) -> Unit,
): @Composable RowScope.(Float) -> Unit = { alpha -> if (url != null) ShareBarAction(alpha) { onShare(url) } }
