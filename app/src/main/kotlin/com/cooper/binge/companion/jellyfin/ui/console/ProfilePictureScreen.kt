package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** What the profile picture page can ask of its view model, and of the console. */
data class ProfilePictureActions(
    /** Opens the system photo picker. */
    val onChoose: () -> Unit = {},
    val onAskRemove: (Boolean) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private val PICTURE_SIZE = 160.dp

/**
 * The user's own picture: it, a row to choose a photo (the system picker, so no storage permission), and Remove, last, red,
 * behind a confirm. A change saves as it is made, and one the server refuses leaves the old picture and says so.
 */
@Composable
fun ProfilePictureScreen(
    state: ProfilePictureUiState,
    actions: ProfilePictureActions,
    /** The name of the user whose picture this is, for an administrator on another user's page; null is the signed-in user's own. */
    other: String? = null,
) {
    val host = remember { SnackbarHostState() }
    val ready = state as? ProfilePictureUiState.Ready
    FormScreen(
        title = stringResource(R.string.profile_picture_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        notReady =
            when (state) {
                ProfilePictureUiState.Loading -> { padding ->
                    ConsoleLoading(padding, LOADING_ROWS, intro = profilePictureBody(other), lead = { PicturePlate() })
                }
                is ProfilePictureUiState.Failed -> { padding ->
                    ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding))
                }
                is ProfilePictureUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
                PageIntro(profilePictureBody(other))
                Picture(ready, other)
                ItemGroup(
                    title = null,
                    modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
                    rows =
                        listOf(
                            ListItem(
                                icon = Icons.Outlined.AddPhotoAlternate,
                                label = stringResource(R.string.profile_picture_choose),
                                destination = ListItemDestination.Action,
                                loading = ready.busy,
                                onClick = actions.onChoose,
                            ),
                        ),
                )
                if (ready.picture != null) {
                    BingeOutlinedButton(
                        label = stringResource(R.string.profile_picture_remove),
                        onClick = { actions.onAskRemove(true) },
                        destructive = true,
                        enabled = !ready.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            RefusedMessage(ready.refused, host, R.string.profile_picture_not_changed)
            SnackbarMessage(if (ready.unreadable) stringResource(R.string.profile_picture_unreadable) else null, host)
            if (ready.confirmingRemove) RemovePictureDialog(actions, other = other)
        }
    }
}

/** The picture in a circle, or the person icon where there is none. */
@Composable
private fun profilePictureBody(other: String?) =
    if (other == null) {
        stringResource(R.string.profile_picture_body)
    } else {
        stringResource(R.string.profile_picture_body_other, other)
    }

/** The picture's place while the page loads: the empty picture's circle, without its icon. */
@Composable
private fun PicturePlate() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(PICTURE_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
internal fun Picture(
    state: ProfilePictureUiState.Ready,
    other: String? = null,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val shape = CircleShape
        if (state.picture != null) {
            Image(
                bitmap = state.picture.asImageBitmap(),
                contentDescription =
                    if (other == null) {
                        stringResource(R.string.profile_picture_description)
                    } else {
                        stringResource(R.string.profile_picture_description_other, other)
                    },
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(PICTURE_SIZE).clip(shape),
            )
        } else {
            Box(
                modifier = Modifier.size(PICTURE_SIZE).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.profile_picture_none),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(PICTURE_SIZE / 2),
                )
            }
        }
    }
}

/** Removing the picture, as a confirm that opens on Cancel. [inWindow] false draws its body for a frame. */
@Composable
internal fun RemovePictureDialog(
    actions: ProfilePictureActions,
    inWindow: Boolean = true,
    other: String? = null,
) {
    ConsoleConfirm(
        title =
            if (other == null) {
                stringResource(R.string.profile_picture_remove_title)
            } else {
                stringResource(R.string.profile_picture_remove_title_other, other)
            },
        message =
            if (other == null) {
                stringResource(R.string.profile_picture_remove_body)
            } else {
                stringResource(R.string.profile_picture_remove_body_other, other)
            },
        confirmLabel = stringResource(R.string.profile_picture_remove),
        onConfirm = actions.onRemove,
        onDismissRequest = { actions.onAskRemove(false) },
        destructive = true,
        inWindow = inWindow,
    )
}

private const val LOADING_ROWS = 3
