package com.cooper.binge.companion.jellyfin.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.encodingConfiguration
import com.cooper.binge.companion.jellyfin.server.liveTvConfiguration
import com.cooper.binge.companion.jellyfin.server.networkConfiguration
import com.cooper.binge.companion.jellyfin.server.nfoConfiguration
import com.cooper.binge.companion.jellyfin.server.saveEncodingConfiguration
import com.cooper.binge.companion.jellyfin.server.saveLiveTvConfiguration
import com.cooper.binge.companion.jellyfin.server.saveNetworkConfiguration
import com.cooper.binge.companion.jellyfin.server.saveNfoConfiguration
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import com.cooper.binge.companion.jellyfin.server.userConfiguration

/** The configurations the search's switches live in. Each is a whole object the server keeps, and the search holds one copy of each. */
enum class ConfigKind {
    /** The signed-in user's own configuration: Playback and the Home screen. */
    User,

    /** The server's configuration: Quick Connect, Trickplay, Advanced. For an administrator. */
    Server,

    /** The server's encoding configuration: the Transcoding switches. For an administrator. */
    Encoding,

    /** The server's network configuration: the switches that save as made. For an administrator. */
    Network,

    /** The server's Live TV configuration: the recording switches. For an administrator who manages Live TV. */
    LiveTv,

    /** The server's NFO configuration: the switches that save as made. For an administrator. */
    Nfo,
}

/**
 * The search's switches: one [SearchConfigCopy] for each configuration a switch lives in (and why one: see the class). The
 * copies are made with the view model and read nothing until a switch of theirs is shown, and they live as long as the search
 * does, past a page opened over it, so a change in flight is not cut off with the search's composition.
 */
class SearchSwitchesViewModel(
    connection: Connection,
) : ViewModel() {
    val user = SearchConfigCopy(connection, viewModelScope, "JellyfinSearchSwitches", { it.userConfiguration() })

    val server =
        SearchConfigCopy(
            connection,
            viewModelScope,
            "JellyfinSearchServerConfig",
            { it.serverConfiguration() },
            { _, client, json -> client.saveServerConfiguration(json) },
        )

    val encoding =
        SearchConfigCopy(
            connection,
            viewModelScope,
            "JellyfinSearchEncodingConfig",
            { it.encodingConfiguration() },
            { _, client, json -> client.saveEncodingConfiguration(json) },
        )

    val network =
        SearchConfigCopy(
            connection,
            viewModelScope,
            "JellyfinSearchNetworkConfig",
            { it.networkConfiguration() },
            { _, client, json -> client.saveNetworkConfiguration(json) },
        )

    val liveTv =
        SearchConfigCopy(
            connection,
            viewModelScope,
            "JellyfinSearchLiveTvConfig",
            { it.liveTvConfiguration() },
            { _, client, json -> client.saveLiveTvConfiguration(json) },
        )

    val nfo =
        SearchConfigCopy(
            connection,
            viewModelScope,
            "JellyfinSearchNfoConfig",
            { it.nfoConfiguration() },
            { _, client, json -> client.saveNfoConfiguration(json) },
        )

    private val copies = listOf(user, server, encoding, network, liveTv, nfo)

    fun copy(kind: ConfigKind): SearchConfigCopy =
        when (kind) {
            ConfigKind.User -> user
            ConfigKind.Server -> server
            ConfigKind.Encoding -> encoding
            ConfigKind.Network -> network
            ConfigKind.LiveTv -> liveTv
            ConfigKind.Nfo -> nfo
        }

    /** Reads again each copy that has been read before, once, as the search comes to the front. A copy nothing has asked for stays unread. */
    fun refresh() = copies.forEach { if (it.state.value !is SearchConfigState.Idle) it.refresh() }

    /** A page is about to open over the search: what any copy still owes the server goes first. */
    fun leaving() = copies.forEach { it.leaving() }

    override fun onCleared() = copies.forEach { it.cleared() }
}
