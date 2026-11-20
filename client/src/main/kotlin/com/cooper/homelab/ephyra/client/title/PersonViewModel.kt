package com.cooper.homelab.ephyra.client.title

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemsRepository
import com.cooper.homelab.ephyra.browse.PersonDetail
import com.cooper.homelab.ephyra.browse.PersonRepository
import com.cooper.homelab.ephyra.browse.UserDataRepository
import com.cooper.homelab.ephyra.browse.withChanges
import com.cooper.homelab.ephyra.server.UserDataChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private sealed interface Held {
    data object Loading : Held

    data class Failed(
        val failure: ServerFailure,
    ) : Held

    data class Person(
        val person: PersonDetail,
        val credits: List<BrowseItem>,
        val credit: CreditsState,
        val nextStart: Int?,
    ) : Held
}

/**
 * The person page's state: who they are, then the movies and series on this server they appear in, a page at a time. A page of
 * titles that fails keeps the ones shown and offers a retry. The cards follow the user's data (a title watched elsewhere shows
 * so). No Android type; [scope] and [dispatcher] are the caller's, with no default.
 */
class PersonViewModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val people: PersonRepository,
    private val userData: UserDataRepository,
    private val classify: suspend (Throwable) -> ServerFailure,
    private val pageSize: Int = ItemsRepository.DEFAULT_PAGE_SIZE,
) {
    private var personId: String? = null
    private val held = MutableStateFlow<Held>(Held.Loading)

    /** What the page shows now. */
    val state: StateFlow<PersonUiState> = held.map(::uiState).stateIn(scope, SharingStarted.Eagerly, PersonUiState.Loading)

    /** Opens the page for the person [id], by their item id, and follows the user's data from then on. Call it once. */
    fun start(id: String) {
        personId = id
        scope.launch(dispatcher) { userData.changes.collect(::apply) }
        load()
    }

    /** Reads the person again, after a failure. */
    fun retry() = load()

    /** Asks for the next page of titles, when there is one and none is being read; also the retry of a page that failed. */
    fun loadMore() {
        val id = personId ?: return
        val current = held.value as? Held.Person ?: return
        val start = current.nextStart ?: return
        if (current.credit is CreditsState.Loading) return
        held.value = current.copy(credit = CreditsState.Loading)
        scope.launch(dispatcher) {
            attempt { people.titlesWith(id, startIndex = start, limit = pageSize) }
                .onSuccess { page ->
                    held.update {
                        if (it !is Held.Person) return@update it
                        val next = page.nextStart(pageSize)
                        it.copy(credits = it.credits + page.items, credit = CreditsState.Ready(next != null), nextStart = next)
                    }
                }.onFailure { error ->
                    val failure = classify(error)
                    held.update { if (it is Held.Person) it.copy(credit = CreditsState.Failed(failure)) else it }
                }
        }
    }

    private fun load() {
        val id = personId ?: return
        held.value = Held.Loading
        scope.launch(dispatcher) {
            attempt { people.person(id) ?: error("No person $id") }
                .onSuccess { person ->
                    held.value = Held.Person(person, emptyList(), CreditsState.Ready(hasMore = true), nextStart = 0)
                    loadMore()
                }.onFailure { held.value = Held.Failed(classify(it)) }
        }
    }

    private fun apply(change: UserDataChange) {
        held.update { if (it is Held.Person) it.copy(credits = it.credits.withChanges(listOf(change))) else it }
    }
}

private fun uiState(held: Held): PersonUiState =
    when (held) {
        Held.Loading -> PersonUiState.Loading
        is Held.Failed -> PersonUiState.Failed(held.failure)
        is Held.Person -> PersonUiState.Ready(personPageUi(held.person, held.credits, held.credit))
    }
