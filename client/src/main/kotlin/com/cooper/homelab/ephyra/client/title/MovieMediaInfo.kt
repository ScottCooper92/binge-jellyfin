package com.cooper.homelab.ephyra.client.title

import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailInfoSection
import com.cooper.homelab.ephyra.client.R

/**
 * What the file the user will play is, under a heading at the end of the page: its container, size, picture (resolution and range), and
 * its audio and subtitle streams. It is the design system's facts section, so it reads like the page's own details. The versions sheet
 * keeps only the choice between files. A line the file has nothing for is left out, and with nothing at all the section is not drawn.
 */
@Composable
fun MovieMediaInfo(
    version: VersionUi,
    modifier: Modifier = Modifier,
) {
    // Read first, so a change of configuration recomposes the section and its sizes follow the new locale.
    LocalConfiguration.current
    val context = LocalContext.current
    val picture = listOfNotNull(version.resolution?.label, version.range).joinToString(SEPARATOR).ifBlank { null }
    DetailInfoSection(
        heading = stringResource(R.string.client_movie_media_info),
        entries =
            listOf(
                DetailInfoEntry(Icons.Filled.Movie, stringResource(R.string.client_movie_media_container), version.container?.uppercase()),
                DetailInfoEntry(
                    Icons.Filled.Storage,
                    stringResource(R.string.client_movie_media_size),
                    version.sizeBytes?.let {
                        Formatter.formatFileSize(context, it)
                    },
                ),
                DetailInfoEntry(Icons.Filled.HighQuality, stringResource(R.string.client_movie_media_picture), picture),
                DetailInfoEntry(Icons.Filled.Audiotrack, stringResource(R.string.client_movie_media_audio), version.audio.streams()),
                DetailInfoEntry(Icons.Filled.Subtitles, stringResource(R.string.client_movie_media_subtitles), version.subtitles.streams()),
            ),
        modifier = modifier,
    )
}

/** The streams, one to a line: the server's own description of each, or what its language and codec say. */
private fun List<TrackUi>.streams(): String? = mapNotNull { it.label }.joinToString("\n").ifBlank { null }

private const val SEPARATOR = " · "
