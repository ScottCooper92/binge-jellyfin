package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * The signed-in user's libraries. A failed read throws what the transport throws (`JellyfinHttpException`,
 * `JellyfinUnreadableAnswer`, an `IOException`); the view model decides how to show it.
 */
interface LibraryRepository {
    /** The libraries the user can see, in their home's order. */
    suspend fun views(): List<LibraryView>
}

/** [LibraryRepository] over the user's own server. */
class JellyfinLibraryRepository(
    private val client: JellyfinClient,
    private val userId: String,
    private val profile: JellyfinServerProfile,
) : LibraryRepository {
    override suspend fun views(): List<LibraryView> = client.browseViews(userId, profile)
}
