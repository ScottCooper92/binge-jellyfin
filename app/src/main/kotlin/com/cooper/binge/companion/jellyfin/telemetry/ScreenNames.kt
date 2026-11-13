package com.cooper.binge.companion.jellyfin.telemetry

import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage

/**
 * The screens this app reports, by fixed name. A name says which screen was shown and never what was on it: a console page is
 * named by its page, not by the library, user, task or key open inside it.
 */
object ScreenNames {
    const val ENTER_SERVER = "enter_server"
    const val SIGN_IN = "sign_in"
    const val SIGN_IN_QUICK_CONNECT = "sign_in_quick_connect"
    const val HOME = "home"

    /** The console page [page], as `console_` and the page's own name in snake case. */
    fun console(page: ConsolePage): String = "console_" + page.name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").lowercase()

    /** The screen [state] is showing, with [openPage] the console page open over the home; null where nothing is worth a name yet. */
    fun of(
        state: UiState,
        openPage: ConsolePage?,
    ): String? =
        when (state) {
            UiState.Loading -> null
            is UiState.EnterServer -> ENTER_SERVER
            is UiState.SignIn -> if (state.quickConnectCode != null || state.gettingCode) SIGN_IN_QUICK_CONNECT else SIGN_IN
            is UiState.SignedIn -> openPage?.let(::console) ?: HOME
        }
}
