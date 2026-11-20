package com.cooper.homelab.ephyra.client.title

import android.content.res.Resources
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.cooper.homelab.ephyra.client.R

private const val SEPARATOR = " · "

/**
 * The sheet that lists a film's files and lets the user pick the one to play: one radio row per version, with what tells two files
 * apart under its name. It is the design system's choice sheet, because choosing a file is a single choice from a short list;
 * the streams of the chosen file are shown by [versionSummary] on the row, not in a second list. [onSelect] gets the version's id.
 */
@Composable
fun VersionSheet(
    versions: List<VersionUi>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Read first, so a change of configuration recomposes the sheet and its strings follow the new locale.
    LocalConfiguration.current
    val context = LocalContext.current
    val resources = context.resources
    val choices =
        versions.mapIndexed { index, version ->
            BingeChoice(
                value = version.id,
                label = versionTitle(version, index, resources),
                supportingText = versionSummary(version, resources) { Formatter.formatFileSize(context, it) },
                supportingMaxLines = SUMMARY_LINES,
            )
        }
    BingeChoiceSheet(
        title = stringResource(R.string.client_movie_versions_title),
        choices = BingeChoiceList.Ready(choices),
        selected = selected,
        onSelect = onSelect,
        onDismissRequest = onDismiss,
    )
}

private const val SUMMARY_LINES = 3

/** The row's name: the file's own name when it has one, else its resolution and range, else "Version 2". */
fun versionTitle(
    version: VersionUi,
    index: Int,
    resources: Resources,
): String =
    version.name
        ?: listOfNotNull(version.resolution?.label, version.range).joinToString(SEPARATOR).ifBlank { null }
        ?: resources.getString(R.string.client_movie_version_number, index + 1)

/**
 * What tells this file from another: its container, size, the audio it opens with and how many subtitles it carries. [formatSize]
 * words a byte count in the user's language, so this holds no Android type but [Resources].
 */
fun versionSummary(
    version: VersionUi,
    resources: Resources,
    formatSize: (Long) -> String,
): String? {
    val audio = version.audio.firstOrNull { it.index == version.defaultAudioIndex } ?: version.audio.firstOrNull()
    val subtitles = version.subtitles.size
    return listOfNotNull(
        version.container?.uppercase(),
        version.sizeBytes?.let(formatSize),
        audio?.label,
        subtitles.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.client_movie_version_subtitles, it, it) },
    ).joinToString(SEPARATOR).ifBlank { null }
}
