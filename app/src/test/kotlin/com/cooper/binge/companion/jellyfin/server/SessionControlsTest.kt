package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ME = "11111111222233334444555555555555"
private const val OTHER = "99999999222233334444555555555555"
private const val OWN_DEVICE = "own-device"

private fun policy(json: String) = JellyfinAccount("me", Json.parseToJsonElement(json).jsonObject)

private val user = policy("""{"IsAdministrator":false,"EnableRemoteControlOfOtherUsers":false,"EnableSharedDeviceControl":false}""")
private val sharer = policy("""{"IsAdministrator":false,"EnableRemoteControlOfOtherUsers":false,"EnableSharedDeviceControl":true}""")
private val remote = policy("""{"IsAdministrator":false,"EnableRemoteControlOfOtherUsers":true,"EnableSharedDeviceControl":false}""")
private val admin = policy("""{"IsAdministrator":true}""")

private fun session(
    id: String = "s1",
    userId: String? = ME,
    deviceId: String = "tv",
    remoteControl: Boolean = true,
    mediaControl: Boolean = true,
    seek: Boolean = true,
    commands: Set<String> = emptySet(),
) = NowPlaying(
    sessionId = id,
    userName = "u",
    deviceName = "d",
    client = "c",
    title = "t",
    episode = null,
    positionTicks = 0,
    runtimeTicks = 1,
    paused = false,
    method = PlayMethod.DirectPlay,
    transcodeReasons = emptyList(),
    userId = userId,
    deviceId = deviceId,
    supportsMediaControl = mediaControl,
    supportsRemoteControl = remoteControl,
    canSeek = seek,
    supportedCommands = commands,
)

private fun controls(
    session: NowPlaying,
    account: JellyfinAccount,
    controllable: Set<String> = setOf(session.sessionId),
) = SessionControls.of(session, controllable, account, ME, OWN_DEVICE)

class SessionControlsTest {
    @Test
    fun `a user's own session takes every control the session declares`() {
        val c = controls(session(), user)
        assertTrue(c.playPause && c.seek && c.skip && c.stop)
    }

    @Test
    fun `a session the server did not list as controllable takes none, whatever the account says`() {
        assertFalse(controls(session(), admin, controllable = emptySet()).any)
        assertFalse(controls(session(id = "s1"), admin, controllable = setOf("other")).any)
    }

    @Test
    fun `someone else's session needs the remote control policy`() {
        assertFalse(controls(session(userId = OTHER), user).any)
        assertFalse(controls(session(userId = OTHER), sharer).any)
        assertTrue(controls(session(userId = OTHER), remote).stop)
        assertTrue(controls(session(userId = OTHER), admin).stop)
    }

    @Test
    fun `a shared device needs the shared device policy, and an administrator has no shortcut for it`() {
        assertFalse(controls(session(userId = null), user).any)
        assertFalse(controls(session(userId = null), remote).any)
        assertFalse(controls(session(userId = null), admin).any)
        assertTrue(controls(session(userId = null), sharer).stop)
    }

    @Test
    fun `this app's own device is never controlled`() {
        assertFalse(controls(session(deviceId = OWN_DEVICE), admin).any)
        assertFalse(controls(session(deviceId = OWN_DEVICE, userId = null), sharer).any)
    }

    @Test
    fun `a session that takes no remote control takes none`() {
        assertFalse(controls(session(remoteControl = false), admin).any)
    }

    @Test
    fun `a control the session does not declare is not offered`() {
        val noMedia = controls(session(mediaControl = false), admin)
        assertFalse(noMedia.any)
        val noSeek = controls(session(seek = false), admin)
        assertTrue(noSeek.playPause && noSeek.skip && noSeek.stop)
        assertFalse(noSeek.seek)
    }

    @Test
    fun `a user id is the same user with or without dashes`() {
        val dashed = "11111111-2222-3333-4444-555555555555"
        assertTrue(controls(session(userId = dashed.uppercase()), user).stop)
    }

    @Test
    fun `volume, mute and message are offered only for the commands the session names`() {
        assertFalse(controls(session(), admin).let { it.volume || it.mute || it.message })
        val all = controls(session(commands = setOf("SetVolume", "Mute", "Unmute", "DisplayMessage")), admin)
        assertTrue(all.volume && all.mute && all.message)
        val half = controls(session(commands = setOf("Mute")), admin)
        assertFalse("Mute without Unmute is not a toggle", half.mute)
        assertTrue(controls(session(commands = setOf("DisplayMessage")), admin).message)
    }

    @Test
    fun `a message to someone else's session needs the same gates as any control`() {
        val msg = setOf("DisplayMessage")
        assertFalse(controls(session(userId = OTHER, commands = msg), user).message)
        assertTrue(controls(session(userId = OTHER, commands = msg), remote).message)
        assertFalse(controls(session(userId = null, commands = msg), remote).message)
        assertFalse(controls(session(deviceId = OWN_DEVICE, commands = msg), admin).message)
        assertFalse(controls(session(commands = msg), admin, controllable = emptySet()).message)
    }

    @Test
    fun `the shared-device flag follows the policy alone`() {
        assertEquals(false, admin.canControlSharedDevices)
        assertEquals(true, sharer.canControlSharedDevices)
    }
}
