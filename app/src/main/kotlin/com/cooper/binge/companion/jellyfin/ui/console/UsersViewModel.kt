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
import com.cooper.binge.companion.jellyfin.server.user
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
     * The users, and the libraries a user may be given; [me], the signed-in user's id; [open], the user the screens
     * are showing (which user, and which section of their settings, is an entry on the stack; the screen of one
     * says so here with [UsersViewModel.select], so the changes made on it act on them); [dialog] over it; [busy] while a create, password or delete is on its way;
     * [done] what the last one did; [actionFailed] the last change that didn't go through.
     */
    data class Ready(
        val users: List<ServerUser>,
        val libraries: List<ServerLibrary>,
        val me: String,
        val open: String? = null,
        val dialog: UserDialog? = null,
        val busy: Boolean = false,
        val done: UserDone? = null,
        /** The user just created, until the page has opened their screen; opening it once keeps Back from opening it again. */
        val created: String? = null,
        val actionFailed: ConsoleFailure? = null,
        /** Why the open form (a new user, a password) was refused, shown in the form, which stays open with what was typed. */
        val formFailure: ConsoleFailure? = null,
        /** True while a pull reads the users again; the list stays as it is. [refreshFailed] says why a pull didn't. */
        val refreshing: Boolean = false,
        val refreshFailed: ConsoleFailure? = null,
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

    /** Counts every start and finish of a policy change, create, password or delete, so a read can tell one happened while it was in flight. */
    private var changes = 0

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
                            val libraries = async { client.libraries().filter { it.id.isNotEmpty() } }
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

    /**
     * A pull: reads the users and libraries again in place, keeping the page, and says in a message when it couldn't.
     * A change that is waiting, or that started or finished while the read was in flight, could be newer than the
     * read, so that read is dropped, and the next pull gets it.
     */
    fun refresh() {
        val ready = _state.value as? UsersUiState.Ready ?: return
        if (ready.refreshing) return
        update { copy(refreshing = true, refreshFailed = null) }
        val changesAtStart = changes
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    coroutineScope {
                        val libraries = async { client.libraries().filter { it.id.isNotEmpty() } }
                        client.users() to libraries.await()
                    }
                }.onSuccess { (users, libraries) ->
                    val settled =
                        changes == changesAtStart &&
                            pending.values.all { it.isEmpty() } &&
                            !saving.isLocked &&
                            (_state.value as? UsersUiState.Ready)?.busy == false
                    if (settled) users.forEach { saved[it.id] = it.policy }
                    update {
                        if (settled) {
                            copy(
                                users = users,
                                libraries = libraries,
                                open =
                                    open?.takeIf { id ->
                                        users.any { it.id == id }
                                    },
                                refreshing = false,
                            )
                        } else {
                            copy(refreshing = false)
                        }
                    }
                }.onFailure { error -> update { copy(refreshing = false, refreshFailed = error.consoleFailure()) } }
        }
    }

    /** The screen of user [id], or none (null), is showing: the changes the policy screens make are to them. */
    fun select(id: String?) = update { copy(open = id) }

    /**
     * A user is being opened: what the last screen reported, and a dialog it left open, are not this one's. They live
     * here, shared by the list and the user, so a screen composed afresh would say them again.
     */
    fun itemOpened() = update { copy(dialog = null, done = null, actionFailed = null, formFailure = null) }

    /** The page has opened the screen of the user just created; the list they return to is as it was. */
    fun createdOpened() = update { copy(created = null, done = null) }

    fun show(dialog: UserDialog?) = update { copy(dialog = dialog, done = null, formFailure = null, actionFailed = null) }

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

    fun create(
        name: String,
        password: String,
    ) {
        if (name.isBlank()) return
        act(UserDone.Created, form = { dialog == UserDialog.Create }) { client ->
            val user = client.createUser(name.trim(), password)
            saved[user.id] = user.policy
            update { copy(users = (users + user).sortedBy { it.name.lowercase() }, open = user.id, created = user.id) }
        }
    }

    fun setPassword(password: String) {
        val ready = _state.value as? UsersUiState.Ready ?: return
        // The server asks the signed-in user for their current password, which the Password page does.
        val user = ready.openUser?.takeUnless { it.id == ready.me } ?: return
        act(UserDone.PasswordSet, form = { dialog == UserDialog.Password && open == user.id }) { client ->
            client.setPassword(user.id, password)
            update { copy(users = users.map { if (it.id == user.id) it.copy(hasPassword = password.isNotEmpty()) else it }) }
        }
    }

    fun delete() {
        val ready = _state.value as? UsersUiState.Ready ?: return
        val user = ready.openUser?.takeUnless { it.id == ready.me } ?: return
        act(UserDone.Deleted, form = null) { client ->
            client.deleteUser(user.id)
            update { copy(users = users - user, open = null) }
        }
    }

    /**
     * A create, password or delete, and the page says what happened. A delete's confirm closes as it is answered; a form
     * ([form]) stays open while the call runs, closes when it works, and keeps what was typed, with the reason in the
     * form, when it doesn't. Only that same form counts (the same dialog, for the same user): if it was left and another opened, the result goes to the page.
     * One call at a time: a second while one is running is ignored.
     */
    private fun act(
        done: UserDone,
        form: (UsersUiState.Ready.() -> Boolean)?,
        call: suspend (JellyfinClient) -> Unit,
    ) {
        if ((_state.value as? UsersUiState.Ready)?.busy != false) return
        update { copy(dialog = if (form != null) dialog else null, busy = true, done = null, formFailure = null, actionFailed = null) }
        changes++
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> call(client) }
                .also { changes++ }
                .onSuccess { update { copy(busy = false, done = done, dialog = if (form != null && form()) null else dialog) } }
                .onFailure { error ->
                    // A form left meanwhile has nowhere to say it, so the page does.
                    update {
                        if (form != null && form()) {
                            copy(busy = false, formFailure = error.consoleFailure())
                        } else {
                            copy(busy = false, actionFailed = error.consoleFailure())
                        }
                    }
                }
        }
    }

    /**
     * Saves one change to the open user's policy as it's made. It shows at once; if the server refuses
     * it, the user's policy goes back to what the server last accepted, and the page says so.
     */
    internal fun changePolicy(
        mineAllowed: Boolean = true,
        change: (JsonObject) -> JsonObject,
    ) {
        val ready = _state.value as? UsersUiState.Ready ?: return
        val user = ready.openUser ?: return
        if (!mineAllowed && user.id == ready.me) return
        update { copy(users = users.withPolicy(user.id, change(user.policy)), done = null, actionFailed = null) }
        pending.getOrPut(user.id) { mutableListOf() }.add(change)
        changes++
        viewModelScope.launch {
            saving.withLock {
                // The change goes on what the server holds now, not on a copy read earlier: Jellyfin replaces the whole policy, so a
                // field changed elsewhere since would otherwise be saved back as it was. A read that fails saves nothing.
                val result =
                    connection.consoleCall(TAG) { _, client ->
                        change(client.user(user.id).policy).also { client.savePolicy(user.id, it) }
                    }
                result.onSuccess { saved[user.id] = it }
                val queued = pending.getValue(user.id).also { it.remove(change) }.toList()
                changes++
                // What the screen shows is what the server holds, plus the changes still waiting their turn.
                val shown = queued.fold(saved[user.id] ?: user.policy) { policy, queuedChange -> queuedChange(policy) }
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

/** Makes the open user an administrator, or not. Not the signed-in user's own. */
fun UsersViewModel.setAdministrator(on: Boolean) = changePolicy(mineAllowed = false) { it + (IS_ADMINISTRATOR to JsonPrimitive(on)) }

/** Lets the open user sign in, or not. Not the signed-in user's own. */
fun UsersViewModel.setCanSignIn(on: Boolean) = changePolicy(mineAllowed = false) { it + (IS_DISABLED to JsonPrimitive(!on)) }

private fun List<ServerUser>.withPolicy(
    id: String,
    policy: JsonObject,
): List<ServerUser> = map { if (it.id == id) it.copy(policy = policy) else it }

private operator fun JsonObject.plus(entry: Pair<String, JsonElement>): JsonObject = JsonObject(toMap() + entry)
