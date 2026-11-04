package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.binge.designsystem.R as DesR

/** A user's picture, with initials occupying the same fixed slot while loading or when no picture can be read. */
@Composable
internal fun UserAvatar(
    name: String,
    picture: Bitmap? = null,
    modifier: Modifier = Modifier,
) {
    val size = dimensionResource(DesR.dimen.item_group_icon_size)
    Box(modifier.size(size).clip(BingeShapes.Pill).clearAndSetSemantics {}) {
        if (picture == null) {
            BingeInitialsAvatar(name = name, size = size)
        } else {
            Image(picture.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        }
    }
}

/** Loads a server user's tagged picture without giving the rendering component a token or a network dependency. */
@Composable
internal fun ServerUserAvatar(
    user: ServerUser,
    pictures: UserPictures,
) {
    val picture by key(user.id, user.imageTag, pictures) {
        produceState<Bitmap?>(null) { value = pictures.read(user) }
    }
    UserAvatar(user.name, picture)
}
