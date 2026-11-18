package com.cooper.binge.companion.jellyfin.ui

/**
 * Whether a server is signed in, as the review prompt reads it: null while the saved session is still being read, so loading
 * is never mistaken for being signed out and back in.
 */
internal fun UiState.connected(): Boolean? = if (this is UiState.Loading) null else this is UiState.SignedIn
