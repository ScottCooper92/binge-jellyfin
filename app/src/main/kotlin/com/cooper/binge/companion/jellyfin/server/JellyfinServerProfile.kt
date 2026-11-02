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

    /** The resolution of chapter images and the dummy chapter duration, in the server's configuration. 10.9. */
    val hasChapterImageSettings: Boolean get() = atLeast(TEN, NINE)

    /** Segment deletion, the stereo downmix algorithm, audio VBR and VideoToolbox tone mapping, in the encoding options. 10.9. */
    val hasAdvancedEncoding: Boolean get() = atLeast(TEN, NINE)

    /** `none` and `auto` as the encoding options' stored values for no acceleration and the default preset; before 10.10 both are `""`. */
    val hasNamedEncodingDefaults: Boolean get() = atLeast(TEN, TEN)

    /** The Quick Sync device and two more stereo downmix algorithms (RFC 7845, AC-4), in the encoding options. 10.10. */
    val hasQsvDeviceAndDownmix: Boolean get() = atLeast(TEN, TEN)

    /** Preferring an extra's embedded title over its file name, in a library's options. 10.9. */
    val hasEmbeddedExtrasTitles: Boolean get() = atLeast(TEN, NINE)

    /** Hardware decoding of HEVC range extensions (8/10-bit and 12-bit), in the encoding options. 10.10. */
    val hasHevcRext: Boolean get() = atLeast(TEN, TEN)

    /** A library's lyric providers: which, and in what order, listed by the server. 10.10. */
    val hasLibraryLyricFetchers: Boolean get() = atLeast(TEN, TEN)

    /** A library can save its trickplay images beside the media. 10.10. */
    val hasLibraryTrickplayWithMedia: Boolean get() = atLeast(TEN, TEN)

    /** A library's media segment providers: which, and in what order, listed by the server. 10.11. */
    val hasLibrarySegmentProviders: Boolean get() = atLeast(TEN, ELEVEN)

    /** Server backups: list, create and restore. 10.11. */
    val hasBackups: Boolean get() = atLeast(TEN, ELEVEN)

    /** A second parental rating limit, for a rating's sub-score. 10.11. */
    val hasParentalSubRating: Boolean get() = atLeast(TEN, ELEVEN)

    /** Grouping movies and shows into collections, set separately. 10.11. */
    val hasSeparateCollectionGrouping: Boolean get() = atLeast(TEN, ELEVEN)

    /** The network configuration's ports and IPv4 and IPv6 switches have their later names (`InternalHttpPort`). 10.9. */
    val hasInternalPortNames: Boolean get() = atLeast(TEN, NINE)

    /** UPnP port mapping: in the network configuration until 10.11 marks it no longer supported. */
    val hasUpnpOption: Boolean get() = version?.let { it < JellyfinVersion(TEN, ELEVEN, 0) } ?: true

    /** Library update duration, parallel image encoding, the inactive session threshold and cast receivers, in the server's configuration. 10.9. */
    val hasAdvancedPerformance: Boolean get() = atLeast(TEN, NINE)

    /** The user-data cache size and the legacy authorization switch, in the server's configuration. 10.11. */
    val hasCacheAndLegacyAuth: Boolean get() = atLeast(TEN, ELEVEN)

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
