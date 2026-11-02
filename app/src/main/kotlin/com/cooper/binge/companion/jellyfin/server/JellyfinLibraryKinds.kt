package com.cooper.binge.companion.jellyfin.server

/** Whether a library of [kind] chooses which embedded subtitles it offers: the kinds jellyfin-web shows the choice for. */
fun libraryHasEmbeddedSubtitleChoice(kind: String): Boolean = kind in EMBEDDED_SUBTITLE_KINDS

private val EMBEDDED_SUBTITLE_KINDS = setOf("movies", "tvshows", "musicvideos", MIXED_LIBRARY)

/** Whether the library's kind has video: its chapter images, subtitles and trickplay mean something. */
fun libraryHasVideo(kind: String): Boolean = kind in VIDEO_KINDS

/** Whether the library is of shows, or mixed content that can hold them. */
fun libraryHasShows(kind: String): Boolean = kind == "tvshows" || kind == MIXED_LIBRARY

fun libraryHasMovies(kind: String): Boolean = kind == "movies" || kind == MIXED_LIBRARY

private val VIDEO_KINDS = setOf("movies", "tvshows", "musicvideos", "homevideos", MIXED_LIBRARY)
