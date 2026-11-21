package com.cooper.homelab.ephyra.openitem

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.nav.HomeRoute
import com.cooper.homelab.ephyra.ui.nav.HubRoute
import com.cooper.homelab.ephyra.ui.nav.LibraryRoute
import com.cooper.homelab.ephyra.ui.nav.MovieRoute
import com.cooper.homelab.ephyra.ui.nav.PlayerRoute
import com.cooper.homelab.ephyra.ui.nav.SeriesRoute
import com.cooper.homelab.ephyra.ui.nav.ShellRoute
import com.cooper.homelab.ephyra.ui.nav.closeTop
import com.cooper.homelab.ephyra.ui.nav.openTitle
import org.junit.Assert.assertEquals
import org.junit.Test

/** When a pending request is acted on, waited for or dropped, and what it does to the back stack. */
class OpenReadinessTest {
    @Test
    fun `it waits while the session is being read`() {
        assertEquals(OpenReadiness.Wait, openReadiness(connected = null, clientResolved = true, root = ShellRoute))
        assertEquals(OpenReadiness.Wait, openReadiness(connected = null, clientResolved = null, root = HomeRoute))
    }

    @Test
    fun `it is dropped when nobody is signed in`() {
        assertEquals(OpenReadiness.Drop, openReadiness(connected = false, clientResolved = true, root = HomeRoute))
        assertEquals(OpenReadiness.Drop, openReadiness(connected = false, clientResolved = null, root = HomeRoute))
    }

    @Test
    fun `it waits for the client gate to be read and is not fooled by a gate still unread`() {
        assertEquals(OpenReadiness.Wait, openReadiness(connected = true, clientResolved = null, root = HubRoute))
        assertEquals(OpenReadiness.Wait, openReadiness(connected = true, clientResolved = null, root = ShellRoute))
    }

    @Test
    fun `it is dropped when the client is off`() {
        assertEquals(OpenReadiness.Drop, openReadiness(connected = true, clientResolved = false, root = HubRoute))
    }

    @Test
    fun `it waits for the root to settle to the shell and then goes`() {
        assertEquals(OpenReadiness.Wait, openReadiness(connected = true, clientResolved = true, root = HomeRoute))
        assertEquals(OpenReadiness.Wait, openReadiness(connected = true, clientResolved = true, root = HubRoute))
        assertEquals(OpenReadiness.Go, openReadiness(connected = true, clientResolved = true, root = ShellRoute))
    }

    @Test
    fun `it waits on an empty stack`() {
        assertEquals(OpenReadiness.Wait, openReadiness(connected = true, clientResolved = true, root = null))
    }

    private fun stack(vararg keys: NavKey) = NavBackStack<NavKey>(*keys)

    @Test
    fun `a cold start has the shell and the title, and Back returns to the shell`() {
        val back = stack(ShellRoute)
        back.openTitle(com.cooper.homelab.ephyra.browse.ItemKind.Movie, "m1")
        assertEquals(listOf(ShellRoute, MovieRoute("m1")), back.toList())
        back.closeTop()
        assertEquals(listOf<NavKey>(ShellRoute), back.toList())
    }

    @Test
    fun `a running app keeps its stack and Back returns to where the user was`() {
        val back = stack(ShellRoute, LibraryRoute("lib"), MovieRoute("other"))
        back.openTitle(com.cooper.homelab.ephyra.browse.ItemKind.Series, "s1")
        assertEquals(listOf(ShellRoute, LibraryRoute("lib"), MovieRoute("other"), SeriesRoute("s1")), back.toList())
        back.closeTop()
        assertEquals(listOf(ShellRoute, LibraryRoute("lib"), MovieRoute("other")), back.toList())
    }

    @Test
    fun `the title already on top adds nothing`() {
        val back = stack(ShellRoute, MovieRoute("m1"))
        back.openTitle(com.cooper.homelab.ephyra.browse.ItemKind.Movie, "m1")
        assertEquals(listOf(ShellRoute, MovieRoute("m1")), back.toList())
    }

    @Test
    fun `a title goes over a player and Back returns to it`() {
        val back = stack(ShellRoute, MovieRoute("m1"), PlayerRoute("sitting", "A film"))
        back.openTitle(com.cooper.homelab.ephyra.browse.ItemKind.Movie, "m2")
        assertEquals(listOf(ShellRoute, MovieRoute("m1"), PlayerRoute("sitting", "A film"), MovieRoute("m2")), back.toList())
        back.closeTop()
        assertEquals(PlayerRoute("sitting", "A film"), back.last())
    }
}
