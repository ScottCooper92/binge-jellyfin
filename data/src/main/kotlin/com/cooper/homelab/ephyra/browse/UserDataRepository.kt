package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.sameItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * What the signed-in user does with a title (played, favourite), and what the server says changed. Failures are the
 * transport's, as in [LibraryRepository]; a failed write publishes nothing.
 */
interface UserDataRepository {
    /**
     * The changes to the user's data, from this app's own writes and from the server's `UserDataChanged` messages (a
     * change made in another app). A page applies them to what it holds with [withChanges]. A write's own answer and the
     * socket's message for the same change both arrive; applying a change twice leaves the same state, so the page does
     * not flicker. After two quick writes to one item a socket message for the first may land after the second's answer
     * and show the first state until the second's message follows it, which the server sends in order.
     */
    val changes: Flow<UserDataChange>

    /**
     * Marks [id] played or unplayed, and returns the user data the server answers with. On a season or series the server
     * applies it to every episode: the answer is the season's or series' own, and the episodes' changes come through [changes].
     */
    suspend fun setPlayed(
        id: String,
        played: Boolean,
    ): UserDataChange

    /** Marks [id] a favourite or not, and returns the user data the server answers with. */
    suspend fun setFavourite(
        id: String,
        favourite: Boolean,
    ): UserDataChange
}

/**
 * [UserDataRepository] over the user's own server. [socket] is the server's `UserDataChanged` messages (the lists
 * `ServerEvents.userDataChanges` gives), already for any user: those for another user are dropped here.
 */
class JellyfinUserDataRepository(
    private val client: JellyfinClient,
    private val userId: String,
    private val profile: JellyfinServerProfile,
    socket: Flow<List<UserDataChange>>,
) : UserDataRepository {
    private val written = MutableSharedFlow<UserDataChange>(extraBufferCapacity = WRITE_BUFFER)

    override val changes: Flow<UserDataChange> =
        merge(
            written,
            socket
                .flatMapConcat { it.asFlow() }
                .filter { it.userId == null || sameItem(it.userId, userId) }
                .map { it.copy(userId = userId) },
        ).distinctUntilChanged()

    override suspend fun setPlayed(
        id: String,
        played: Boolean,
    ): UserDataChange = client.browseSetPlayed(userId, profile, id, played).publish()

    override suspend fun setFavourite(
        id: String,
        favourite: Boolean,
    ): UserDataChange = client.browseSetFavourite(userId, profile, id, favourite).publish()

    private suspend fun UserDataChange.publish(): UserDataChange =
        copy(userId = this@JellyfinUserDataRepository.userId).also {
            written.emit(it)
        }

    private companion object {
        const val WRITE_BUFFER = 16
    }
}
