package com.cooper.binge.companion.jellyfin.server

/** A Jellyfin release, `major.minor.patch`; an unstable build with no numbers has none. */
data class JellyfinVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<JellyfinVersion> {
    override fun compareTo(other: JellyfinVersion): Int =
        compareValuesBy(this, other, JellyfinVersion::major, JellyfinVersion::minor, JellyfinVersion::patch)

    companion object {
        fun parse(raw: String?): JellyfinVersion? {
            val parts = raw?.trim()?.removePrefix("v")?.split('.') ?: return null
            if (parts.size < 2) return null
            val numbers = parts.take(VERSION_PARTS).map { it.takeWhile(Char::isDigit).toIntOrNull() ?: return null }
            return JellyfinVersion(numbers[0], numbers[1], numbers.getOrElse(2) { 0 })
        }

        private const val VERSION_PARTS = 3
    }
}

/**
 * What the connected server is and can do: its release, and what its administrator turned on that
 * anyone may read. The one place a version is compared: a page asks for a named capability, never
 * for the number. `docs/server-compatibility.md` is the policy, and says which release each came
 * with (read from the server's own source at each release tag).
 *
 * A server whose version can't be read is taken to have everything, so a hiccup hides nothing; the
 * server's own 404 stays the backstop.
 */
data class JellyfinServerProfile(
    val version: JellyfinVersion?,
    /** Whether Quick Connect is on (`/QuickConnect/Enabled`, readable by anyone); unknown reads as on. */
    val quickConnect: Boolean = true,
) {
    /** Lyrics: the lyrics API, a library's lyric saving, and the user permission to manage them. 10.9. */
    val hasLyrics: Boolean get() = atLeast(TEN, NINE)

    /** Trickplay (seek thumbnails): the server's options and a library's extraction. 10.9. */
    val hasTrickplay: Boolean get() = atLeast(TEN, NINE)

    /** The login splash screen in branding. 10.9. */
    val hasSplashscreen: Boolean get() = atLeast(TEN, NINE)

    /** A library can be switched off without removing it. 10.9. */
    val canDisableLibraries: Boolean get() = atLeast(TEN, NINE)

    /** The user permissions to manage collections, lyrics and subtitles. 10.9. */
    val hasContentManagementPermissions: Boolean get() = atLeast(TEN, NINE)

    /** A user's allowed tags (the opposite of blocked ones), in their policy. 10.9. */
    val hasAllowedTags: Boolean get() = atLeast(TEN, NINE)

    /** Audio normalisation's loudness scan, per library. 10.9. */
    val hasLoudnessScan: Boolean get() = atLeast(TEN, NINE)

    /** The user's libraries are read from `/UserViews`; before 10.9, from `/Users/{id}/Views`. 10.9. */
    val hasUserViewsRoute: Boolean get() = atLeast(TEN, NINE)

    /** Trickplay can extract key frames only, from the server's options. 10.10. */
    val hasKeyFrameOnlyTrickplay: Boolean get() = atLeast(TEN, TEN)

    /** Media segments (intros, credits) and a library's segment providers. 10.10. */
    val hasMediaSegments: Boolean get() = atLeast(TEN, TEN)

    /** Server backups: list, create and restore. 10.11. */
    val hasBackups: Boolean get() = atLeast(TEN, ELEVEN)

    /** A second parental rating limit, for a rating's sub-score. 10.11. */
    val hasParentalSubRating: Boolean get() = atLeast(TEN, ELEVEN)

    /** Grouping movies and shows into collections, set separately. 10.11. */
    val hasSeparateCollectionGrouping: Boolean get() = atLeast(TEN, ELEVEN)

    private fun atLeast(
        major: Int,
        minor: Int,
    ): Boolean = version?.let { it >= JellyfinVersion(major, minor, 0) } ?: true

    companion object {
        /** The oldest release the app is tested against. An older one still connects, and offers what it has. */
        val FLOOR = JellyfinVersion(TEN, EIGHT, 0)

        fun from(
            version: String?,
            quickConnect: Boolean = true,
        ) = JellyfinServerProfile(JellyfinVersion.parse(version), quickConnect)

        private const val TEN = 10
        private const val EIGHT = 8
        private const val NINE = 9
        private const val ELEVEN = 11
    }
}
