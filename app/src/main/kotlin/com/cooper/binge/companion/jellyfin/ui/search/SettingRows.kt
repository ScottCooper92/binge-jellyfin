package com.cooper.binge.companion.jellyfin.ui.search

/**
 * The keys a console page gives its rows (`ListItem.key`) so the settings search can open the page on one of them. A page and
 * the search take the key from here, so the two cannot drift: a row is found by the same name it is given.
 */
internal object SettingRows {
    /** A Networking row, by the name of its field in the server's network configuration. */
    fun network(field: String) = "network.$field"

    /** A Live TV recording row, by the name of its field in the server's Live TV configuration. */
    fun liveTv(field: String) = "livetv.$field"

    /** A Playback and transcoding row, by the name of its field in the server's encoding configuration. */
    fun transcoding(field: String) = "transcoding.$field"

    /** Networking's two local ports, which the page names by their [com.cooper.binge.companion.jellyfin.ui.console.NetworkNumber]. */
    val NETWORK_HTTP_PORT = network("HttpPort")
    val NETWORK_HTTPS_PORT = network("HttpsPort")

    const val TRANSCODING_ACCELERATION = "transcoding.acceleration"
    const val ADVANCED_CACHE = "advanced.cache"
    const val LOGS_RETENTION = "logs.retention"
    const val THIS_APP_USAGE_DATA = "this-app.usage-data"
    const val THIS_APP_CRASH_REPORTS = "this-app.crash-reports"
    const val THIS_APP_REPORT_BUG = "this-app.report-bug"
    const val THIS_APP_NOTIFICATIONS = "this-app.notifications"
    const val PLAYBACK_DEFAULT_AUDIO = "playback.default-audio"
    const val PLAYBACK_REMEMBER_AUDIO = "playback.remember-audio"
    const val PLAYBACK_REMEMBER_SUBTITLES = "playback.remember-subtitles"
    const val PLAYBACK_AUTOPLAY = "playback.autoplay"
    const val HOME_HIDE_PLAYED = "home.hide-played"
    const val HOME_MISSING_EPISODES = "home.missing-episodes"
    const val HOME_COLLECTIONS = "home.collections"
    const val TRICKPLAY_HW_ACCELERATION = "trickplay.hw-acceleration"
    const val TRICKPLAY_HW_ENCODING = "trickplay.hw-encoding"
    const val TRICKPLAY_KEY_FRAMES = "trickplay.key-frames"
    const val ADVANCED_SLOW_WARNING = "advanced.slow-warning"
    const val ADVANCED_CLIENT_LOGS = "advanced.client-logs"
    const val ADVANCED_METRICS = "advanced.metrics"
    const val ADVANCED_LEGACY_AUTH = "advanced.legacy-auth"
    const val SERVER_NAME = "server.name"
    const val SERVER_QUICK_CONNECT = "server.quick-connect"
}
