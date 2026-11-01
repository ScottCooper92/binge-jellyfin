package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.IS_ADMINISTRATOR
import com.cooper.binge.companion.jellyfin.server.IS_DISABLED
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.createUser
import com.cooper.binge.companion.jellyfin.server.deleteUser
import com.cooper.binge.companion.jellyfin.server.libraries
import com.cooper.binge.companion.jellyfin.server.savePolicy
import com.cooper.binge.companion.jellyfin.server.setPassword
import com.cooper.binge.companion.jellyfin.server.users
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The dialog open over the users page, if any. */
enum class UserDialog { Create, Password, Delete }

/** A page of settings under one user's page. */
enum class UserSection { Access, Playback, Content, Control, LiveTv, Devices, Parental }

/** What the last create, password or delete did, said under its page. */
enum class UserDone { Created, PasswordSet, Deleted }

sealed interface UsersUiState {
    data object Loading : UsersUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : UsersUiState

    /**
     * The users, and the libraries a user may be given; [me], the signed-in user's id; [open], the user
     * whose page is showing, and [section], the page of their settings that is; [dialog] over it; [busy] while a create, password or delete is on its way;
     * [done] what the last one did; [actionFailed] the last change that didn't go through.
     */
    data class Ready(
        val users: List<ServerUser>,
        val libraries: List<ServerLibrary>,
        val me: String,
        val open: String? = null,
        val section: UserSection? = null,
        val dialog: UserDialog? = null,
        val busy: Boolean = false,
        val done: UserDone? = null,
        val actionFailed: ConsoleFailure? = null,
    ) : UsersUiState {
        val openUser: ServerUser? get() = users.firstOrNull { it.id == open }
    }
}

/**
 * The server's users. Admin only. Each switch on a user's page saves as it's made: the policy the
 * server last accepted, with that one change. Creating a user, setting a password and deleting one are
 * explicit, the last behind a confirm. The signed-in user can't be disabled, demoted or deleted from
 * here: the server would refuse, and deleting signs that user out everywhere before refusing.
 */
class UsersViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<UsersUiState>(UsersUiState.Loading)
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    /** Each user's policy as the server last accepted it. Changes take turns, each building on the last. */
    private val saved = mutableMapOf<String, JsonObject>()
    private val saving = Mutex()

    /** Each user's changes that are made but not yet answered, oldest first. */
    private val pending = mutableMapOf<String, MutableList<(JsonObject) -> JsonObject>>()

    init {
        load()
    }

    fun load() {
        _state.value = UsersUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { session, client ->
                        coroutineScope {
                            val libraries = async { client.libraries() }
                            UsersUiState.Ready(client.users(), libraries.await(), me = session.userId)
                        }
                    }.fold(
                        onSuccess = { ready ->
                            ready.users.forEach { saved[it.id] = it.policy }
                            ready
                        },
                        onFailure = { UsersUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun open(user: ServerUser?) = update { copy(open = user?.id, section = null, dialog = null, done = null, actionFailed = null) }

    fun show(dialog: UserDialog?) = update { copy(dialog = dialog, done = null, actionFailed = null) }

    fun setAdministrator(on: Boolean) = changePolicy(mineAllowed = false) { it + (IS_ADMINISTRATOR to JsonPrimitive(on)) }

    fun setCanSignIn(on: Boolean) = changePolicy(mineAllowed = false) { it + (IS_DISABLED to JsonPrimitive(!on)) }

    /** Changes one setting of the open user's policy, and saves it. */
    fun setPolicy(
        key: String,
        value: JsonElement,
    ) = changePolicy { it + (key to value) }

    /** Changes one list in the open user's policy (their libraries, devices, tags, schedules), and saves it. */
    fun editList(
        key: String,
        change: (List<JsonElement>) -> List<JsonElement>,
    ) = changePolicy { policy -> policy + (key to JsonArray(change((policy[key] as? JsonArray).orEmpty()))) }

    /** Opens [section] of the open user's settings, or their page for null. */
    fun section(section: UserSection?) = update { copy(section = section, done = null, actionFailed = null) }

    fun create(
        name: String,
        password: String,
    ) {
        if (name.isBlank()) return
        act(UserDone.Created) { client ->
            val user = client.createUser(name.trim(), password)
            saved[user.id] = user.policy
            update { copy(users = (users + user).sortedBy { it.name.lowercase() }, open = user.id) }
        }
    }

    fun setPassword(password: String) {
        val ready = _state.value as? UsersUiState.Ready ?: return
        // The server asks the signed-in user for their current password, which the Password page does.
        val user = ready.openUser?.takeUnless { it.id == ready.me } ?: return
        act(UserDone.PasswordSet) { client ->
            client.setPassword(user.id, password)
            update { copy(users = users.map { if (it.id == user.id) it.copy(hasPassword = password.isNotEmpty()) else it }) }
        }
    }

    fun delete() {
        val ready = _state.value as? UsersUiState.Ready ?: return
        val user = ready.openUser?.takeUnless { it.id == ready.me } ?: return
        act(UserDone.Deleted) { client ->
            client.deleteUser(user.id)
            update { copy(users = users - user, open = null) }
        }
    }

    /** A create, password or delete: the dialog closes, and the page says what happened or why it didn't. */
    private fun act(
        done: UserDone,
        call: suspend (JellyfinClient) -> Unit,
    ) {
        update { copy(dialog = null, busy = true, done = null, actionFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> call(client) }
                .onSuccess { update { copy(busy = false, done = done) } }
                .onFailure { error -> update { copy(busy = false, actionFailed = error.consoleFailure()) } }
        }
    }

    /**
     * Saves one change to the open user's policy as it's made. It shows at once; if the server refuses
     * it, the user's policy goes back to what the server last accepted, and the page says so.
     */
    private fun changePolicy(
        mineAllowed: Boolean = true,
        change: (JsonObject) -> JsonObject,
    ) {
        val ready = _state.value as? UsersUiState.Ready ?: return
        val user = ready.openUser ?: return
        if (!mineAllowed && user.id == ready.me) return
        update { copy(users = users.withPolicy(user.id, change(user.policy)), done = null, actionFailed = null) }
        pending.getOrPut(user.id) { mutableListOf() }.add(change)
        viewModelScope.launch {
            saving.withLock {
                val next = change(saved[user.id] ?: user.policy)
                val result = connection.consoleCall(TAG) { _, client -> client.savePolicy(user.id, next) }
                result.onSuccess { saved[user.id] = next }
                val queued = pending.getValue(user.id).also { it.remove(change) }
                // What the screen shows is what the server holds, plus the changes still waiting their turn.
                val shown = queued.fold(saved[user.id] ?: next) { policy, queuedChange -> queuedChange(policy) }
                update {
                    copy(
                        users = users.withPolicy(user.id, shown),
                        actionFailed = result.exceptionOrNull()?.consoleFailure() ?: actionFailed,
                    )
                }
            }
        }
    }

    private fun update(change: UsersUiState.Ready.() -> UsersUiState.Ready) = _state.update { (it as? UsersUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinUsers"
    }
}

private fun List<ServerUser>.withPolicy(
    id: String,
    policy: JsonObject,
): List<ServerUser> = map { if (it.id == id) it.copy(policy = policy) else it }

private operator fun JsonObject.plus(entry: Pair<String, JsonElement>): JsonObject = JsonObject(toMap() + entry)
