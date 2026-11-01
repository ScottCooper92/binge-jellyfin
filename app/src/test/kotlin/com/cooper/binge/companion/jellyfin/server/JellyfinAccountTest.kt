package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinAccountTest {
    private fun account(policy: String) = JellyfinAccount("Claude", Json.parseToJsonElement(policy).jsonObject)

    @Test
    fun `an unread policy allows only what everyone has`() {
        val unread = JellyfinAccount("Claude")
        assertFalse(unread.isRead)
        assertFalse(unread.isAdministrator)
        assertFalse(unread.canControlOthers)
        assertTrue(unread.canEditPreferences)
        assertTrue(unread.hasLocalPassword)
    }

    @Test
    fun `an administrator may do everything`() {
        val admin = account("""{"IsAdministrator":true,"EnableUserPreferenceAccess":false}""")
        assertTrue(admin.isRead)
        assertTrue(admin.canEditPreferences)
        assertTrue(admin.canControlOthers)
    }

    @Test
    fun `a user's policy decides what they may change`() {
        val locked = account("""{"IsAdministrator":false,"EnableUserPreferenceAccess":false}""")
        assertFalse(locked.canEditPreferences)
        val remote = account("""{"IsAdministrator":false,"EnableRemoteControlOfOtherUsers":true}""")
        assertTrue(remote.canControlOthers)
    }

    @Test
    fun `only a Jellyfin password can be changed here`() {
        val local =
            account(
                """{"AuthenticationProviderId":"Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider"}""",
            )
        assertTrue(local.hasLocalPassword)
        val ldap = account("""{"AuthenticationProviderId":"Jellyfin.Plugin.LDAP_Auth.LdapAuthenticationProviderPlugin"}""")
        assertFalse(ldap.hasLocalPassword)
    }
}
