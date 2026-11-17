package com.cooper.binge.companion.jellyfin.ui.search

import android.content.res.Resources
import androidx.annotation.StringRes
import com.cooper.homelab.core.foundation.TextKey
import com.cooper.homelab.core.foundation.UiText

/**
 * A [TextKey] for a string this app already has as a resource: [id] is the resource's own name, which is what the shared screens look
 * a key up by. The shared library asks for an enum, so every key is provable; this app's index is built from `R.string` references,
 * and a test holds that each key names a real string in both languages instead.
 */
internal class ResourceKey(
    override val id: String,
) : TextKey

/** The text of the string resource [id], as the shared screens take it. */
internal fun Resources.text(
    @StringRes id: Int,
): UiText = UiText.Keyed(ResourceKey(getResourceEntryName(id)))
