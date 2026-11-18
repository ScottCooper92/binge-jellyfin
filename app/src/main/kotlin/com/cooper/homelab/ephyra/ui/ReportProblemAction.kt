package com.cooper.homelab.ephyra.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.feedback.installBugReportUrl

/**
 * The overflow menu on the screens a user sees before a server is connected, where Settings is out of reach. One item for
 * now: **Report a problem**, which opens the same prefilled bug form as Settings. No server is connected, so the link names
 * none: only the app version and the device.
 */
@Composable
internal fun ReportProblemAction(open: (String) -> Unit = LocalContext.current::openInBrowser) {
    var expanded by remember { mutableStateOf(false) }
    val more = stringResource(R.string.overflow_menu)
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = more }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.report_problem)) },
                leadingIcon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                onClick = {
                    expanded = false
                    open(installBugReportUrl())
                },
            )
        }
    }
}
