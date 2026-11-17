package com.cooper.binge.companion.jellyfin.ui.search

import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.uiandroid.search.SearchToggle

/**
 * The switch a search result for [entry] carries, or null for a result that has none. Every value and setter here is the one the
 * settings page itself is given in [Actions], so a flip in the search is the same flip as on the page, with its consent and
 * permission handling. A setting with a choice, a number, a list or a form is null, and so is one the account does not have.
 */
internal fun searchToggle(
    entry: SettingEntry<SearchTarget>,
    actions: Actions,
): SearchToggle? =
    when (entry.row) {
        SettingRows.THIS_APP_USAGE_DATA -> SearchToggle(actions.appSettings.shareUsageData, actions.onShareUsageData)
        SettingRows.THIS_APP_CRASH_REPORTS -> SearchToggle(actions.appSettings.sendCrashReports, actions.onSendCrashReports)
        SettingRows.THIS_APP_NOTIFICATIONS ->
            actions.notifications?.let { SearchToggle(it.enabled, actions.onNotificationsEnabled) }
        else -> null
    }
