package com.cooper.homelab.ephyra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.openitem.OpenItem
import com.cooper.homelab.ephyra.openitem.OpenItemRequest

/**
 * The entry other apps use to open one title here. **The action and the extras below are a public contract that other apps build
 * against, so renaming or retyping any of them, or changing what they mean, is a breaking change.** Add an extra rather than change one.
 *
 * The action is [OpenItem.ACTION], with the category `DEFAULT` only, so a web page cannot reach it. The extras are all optional:
 * - [OpenItem.EXTRA_SERVER_URL] (String): the Jellyfin server's base URL, with `http` or `https`;
 * - [OpenItem.EXTRA_ITEM_ID] (String): the item's Jellyfin id, with or without dashes;
 * - [OpenItem.EXTRA_TMDB_TYPE] (String): `movie` or `tv`;
 * - [OpenItem.EXTRA_TMDB_ID] (Int): the TMDB id.
 * A caller sets this app's package on the intent, checks `resolveActivity` (which needs a `<queries>` entry for the action on Android
 * 11 and later) and falls back to the web when it does not resolve; that check is the whole of feature detection.
 *
 * The app opens the item by its id when the server address is the signed-in server's, then by TMDB id, and otherwise as a normal
 * launch. It is exported, so every extra is untrusted: each is checked, a malformed one is ignored, and the server address is only
 * compared with the saved one and never requested. This activity reads the intent and shows nothing; [MainActivity] reads no extras.
 */
class OpenItemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as JellyfinApp).graph
        intent.toOpenItemRequest()?.let(graph.openRequests::offer)
        // A running app takes the request in onNewIntent with its stack intact; a cold start builds the app as usual.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}

/** The request this intent carries, or null when it names no title. A bundle that cannot be read is no request, and never a crash. */
internal fun Intent.toOpenItemRequest(): OpenItemRequest? =
    attempt {
        OpenItemRequest.parse(
            serverUrl = getStringExtra(OpenItem.EXTRA_SERVER_URL),
            itemId = getStringExtra(OpenItem.EXTRA_ITEM_ID),
            tmdbType = getStringExtra(OpenItem.EXTRA_TMDB_TYPE),
            tmdbId = getIntExtra(OpenItem.EXTRA_TMDB_ID, 0).takeIf { it > 0 },
        )
    }.getOrNull()
