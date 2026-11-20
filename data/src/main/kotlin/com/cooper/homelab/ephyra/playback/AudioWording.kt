package com.cooper.homelab.ephyra.playback

/** What a media source says about one audio stream: the server's name for its codec, its profile where it has one, and its channels. */
data class AudioFormat(
    val codec: String?,
    val profile: String?,
    val channels: Int?,
)

/**
 * Words the second line of an audio row ("AAC mono", "Dolby Digital 5.1") in the user's language, from what the server says of the stream.
 * It lives in the app, which has the strings; null where there is nothing to say.
 */
fun interface AudioWording {
    fun describe(format: AudioFormat): String?
}

/** Says nothing: the row has its language and no second line. */
val NoAudioWording = AudioWording { null }
