package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.binge.companion.sdk.attempt
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * A catalog changelog, which repositories write as Markdown, drawn as Markdown: headings, lists, tables, quotes, code and links.
 *
 * Changelogs come from third-party catalogs, so nothing in one is trusted. The renderer is given no image loader, so an image is
 * never fetched (an image is replaced by its alt text before drawing: the renderer would leave an empty block where it was), and a link does nothing until it is tapped, and then only if it is an http or https address
 * ([WebLinksOnly]). Text is drawn in [style], with headings kept close to it: a changelog is a note, not a document.
 */
@Composable
internal fun ChangelogMarkdown(
    markdown: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val system = LocalUriHandler.current
    val handler = remember(system) { WebLinksOnly(system) }
    val type = MaterialTheme.typography
    val link = MaterialTheme.colorScheme.primary
    val code = style.copy(fontFamily = FontFamily.Monospace)
    CompositionLocalProvider(LocalUriHandler provides handler) {
        Markdown(
            content = withoutImages(markdown),
            colors = markdownColor(text = MaterialTheme.colorScheme.onSurface),
            typography =
                markdownTypography(
                    h1 = type.titleMedium,
                    h2 = type.titleMedium,
                    h3 = type.titleSmall,
                    h4 = type.titleSmall,
                    h5 = type.labelLarge,
                    h6 = type.labelLarge,
                    text = style,
                    code = code,
                    inlineCode = code.copy(fontSize = TextUnit.Unspecified),
                    quote = style.copy(fontStyle = FontStyle.Italic),
                    paragraph = style,
                    ordered = style,
                    bullet = style,
                    list = style,
                    textLink = TextLinkStyles(style = style.copy(color = link, textDecoration = TextDecoration.Underline).toSpanStyle()),
                    table = style,
                    alertTitle = style.copy(fontWeight = FontWeight.Bold),
                ),
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)|!\[([^\]]*)]\[[^\]]*]""")

/**
 * [markdown] with each image (`![alt](url)` or `![alt][ref]`) replaced by its alt text, or by nothing when it has none. A changelog's
 * image is never fetched, and the renderer would leave an empty block where it was. A link, the same syntax without the bang, is
 * left alone.
 */
internal fun withoutImages(markdown: String): String =
    IMAGE.replace(markdown) { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }

/** True for an address a browser can open from a tap: http or https. A changelog's link is never any other scheme. */
internal fun isWebLink(uri: String): Boolean = uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)

/** Opens only [isWebLink] addresses, through [delegate], and does nothing, rather than crash, when no app can open one. */
internal class WebLinksOnly(
    private val delegate: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        if (isWebLink(uri)) attempt { delegate.openUri(uri) }
    }
}
