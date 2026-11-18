package com.cooper.binge.companion.jellyfin.ui.tv

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.jellyfin.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import com.cooper.homelab.core.uiandroid.R as CoreR

/**
 * The television keeps its own consent screen, but the words it shares with the phone's question are the shared library's, so the
 * two cannot say different things. Only what is worded for a remote (the last point's title and the reassurance) is the TV's own.
 */
@RunWith(RobolectricTestRunner::class)
class TvConsentCopyTest {
    private fun context(language: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return base.createConfigurationContext(configuration)
    }

    private val shared =
        listOf(
            R.string.tv_consent_kicker to CoreR.string.consent_kicker,
            R.string.tv_consent_title to CoreR.string.consent_title,
            R.string.tv_consent_subtitle to CoreR.string.consent_subtitle,
            R.string.tv_consent_point_shared_title to CoreR.string.consent_point_shared_title,
            R.string.tv_consent_point_shared_detail to CoreR.string.consent_point_shared_detail,
            R.string.tv_consent_point_never_title to CoreR.string.consent_point_never_title,
            R.string.tv_consent_point_never_detail to CoreR.string.consent_point_never_detail,
            R.string.tv_consent_point_change_detail to CoreR.string.consent_point_change_detail,
            R.string.tv_consent_accept to CoreR.string.consent_accept,
            R.string.tv_consent_decline to CoreR.string.consent_decline,
        )

    private fun assertSame(language: String) {
        val context = context(language)
        shared.forEach { (tv, core) ->
            assertEquals(context.resources.getResourceEntryName(tv), context.getString(core), context.getString(tv))
        }
    }

    @Test
    fun `the words shared with the phone are the same in English`() = assertSame("en")

    @Test
    fun `the words shared with the phone are the same in Spanish`() = assertSame("es")
}
