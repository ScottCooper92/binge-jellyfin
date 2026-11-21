package com.cooper.homelab.ephyra.openitem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cooper.homelab.ephyra.JellyfinApp
import com.cooper.homelab.ephyra.MainActivity
import com.cooper.homelab.ephyra.OpenItemActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The exported entry: that a caller can find it, and that starting it hands the request to the app and opens the app. */
@RunWith(AndroidJUnit4::class)
@Config(application = JellyfinApp::class)
class OpenItemActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val id = "0123456789abcdef0123456789abcdef"

    private fun intent() =
        Intent(OpenItem.ACTION)
            .putExtra(OpenItem.EXTRA_SERVER_URL, "http://jelly.lan:8096")
            .putExtra(OpenItem.EXTRA_ITEM_ID, id)

    @Test
    fun `the action resolves to the entry for a caller that sets this app's package`() {
        val found = context.packageManager.queryIntentActivities(intent().setPackage(context.packageName), 0)
        assertEquals(listOf(OpenItemActivity::class.java.name), found.map { it.activityInfo.name })
        assertTrue("the entry is exported", found.single().activityInfo.exported)
    }

    @Test
    fun `the action does not resolve for another package`() {
        assertTrue(context.packageManager.queryIntentActivities(intent().setPackage("com.example.other"), 0).isEmpty())
    }

    @Test
    fun `the entry is not reachable from a browser`() {
        val browsable = intent().addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(browsable, 0).isEmpty())
    }

    @Test
    fun `starting it hands the request to the app and starts the app`() {
        val activity = Robolectric.buildActivity(OpenItemActivity::class.java, intent()).create().get()
        val requests = (context as JellyfinApp).graph.openRequests
        val request = runBlocking { withTimeout(TIMEOUT_MILLIS) { requests.requests.first() } }
        assertEquals(id, request.itemId)
        assertEquals("http://jelly.lan:8096", request.serverKey)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ComponentName(context, MainActivity::class.java), started.component)
        assertTrue(activity.isFinishing)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertNull("the app's own activity is started with no extras", started.extras)
    }

    @Test
    fun `an intent with nothing to open still opens the app and hands over nothing`() {
        val activity = Robolectric.buildActivity(OpenItemActivity::class.java, Intent(OpenItem.ACTION)).create().get()
        assertNotNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
