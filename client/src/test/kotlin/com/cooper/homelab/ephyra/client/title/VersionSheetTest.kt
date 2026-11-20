package com.cooper.homelab.ephyra.client.title

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.homelab.ephyra.browse.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VersionSheetTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun track(
        index: Int,
        kind: TrackKind,
        label: String? = null,
    ) = TrackUi(index, kind, label, isDefault = false, isForced = false, isExternal = false, isHearingImpaired = false)

    private fun version(
        name: String? = null,
        resolution: Resolution? = null,
        range: String? = null,
        container: String? = "mkv",
        size: Long? = 4_000L,
        audio: List<TrackUi> = emptyList(),
        subtitles: List<TrackUi> = emptyList(),
        defaultAudio: Int? = null,
    ) = VersionUi("v", name, container, resolution, range, size, audio, subtitles, defaultAudio, null)

    @Test
    fun `a version is named by its file name, else its resolution and range, else its place`() {
        assertEquals("Alien 4K", versionTitle(version(name = "Alien 4K", resolution = Resolution.Uhd), 0, resources))
        assertEquals("4K · HDR10", versionTitle(version(resolution = Resolution.Uhd, range = "HDR10"), 0, resources))
        assertEquals("1080p", versionTitle(version(resolution = Resolution.FullHd), 1, resources))
        assertEquals("Version 3", versionTitle(version(), 2, resources))
    }

    @Test
    fun `the summary tells files apart by container, size, the audio it opens with and its subtitles`() {
        val audio = listOf(track(1, TrackKind.Audio, "ENG · EAC3"), track(2, TrackKind.Audio, "SPA · AC3"))
        val subtitles = listOf(track(3, TrackKind.Subtitle), track(4, TrackKind.Subtitle))
        val summary = versionSummary(version(audio = audio, subtitles = subtitles, defaultAudio = 2), resources) { "$it B" }
        assertEquals("MKV · 4000 B · SPA · AC3 · 2 subtitles", summary)
        assertEquals(
            "MKV · 4000 B · ENG · EAC3 · 1 subtitle",
            versionSummary(
                version(audio = audio, subtitles = subtitles.take(1)),
                resources,
            ) {
                "$it B"
            },
        )
    }

    @Test
    fun `a file with nothing to tell has no summary`() {
        assertNull(versionSummary(version(container = null, size = null), resources) { "$it B" })
    }
}
