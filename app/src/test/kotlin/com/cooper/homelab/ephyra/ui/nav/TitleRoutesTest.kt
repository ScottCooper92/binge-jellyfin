package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.browse.ItemKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The title routes: which key an item of each kind opens, that the keys carry the id alone, and that they are saved like the rest. */
class TitleRoutesTest {
    private fun stack() = NavBackStack<NavKey>(ShellRoute)

    @Test
    fun `each kind of item opens its own page`() {
        assertEquals(MovieRoute("i"), titleRoute(ItemKind.Movie, "i"))
        assertEquals(SeriesRoute("i"), titleRoute(ItemKind.Series, "i"))
        assertEquals(SeasonRoute("i"), titleRoute(ItemKind.Season, "i"))
        assertEquals(EpisodeRoute("i"), titleRoute(ItemKind.Episode, "i"))
        assertEquals(CollectionRoute("i"), titleRoute(ItemKind.BoxSet, "i"))
    }

    @Test
    fun `a kind with no page opens nothing`() {
        assertNull(titleRoute(ItemKind.Other, "i"))
        val back = stack()
        back.openTitle(ItemKind.Other, "i")
        assertEquals(listOf<NavKey>(ShellRoute), back.toList())
    }

    @Test
    fun `a title goes on top of the stack, and the same title again adds nothing`() {
        val back = stack()
        back.openTitle(ItemKind.Movie, "m1")
        back.openTitle(ItemKind.Movie, "m1")
        assertEquals(listOf(ShellRoute, MovieRoute("m1")), back.toList())
        back.openTitle(ItemKind.Series, "s1")
        assertEquals(listOf(ShellRoute, MovieRoute("m1"), SeriesRoute("s1")), back.toList())
    }

    @Test
    fun `a film's collection opens on top of the film, and Back returns to the film`() {
        val back = stack()
        back.openTitle(ItemKind.Movie, "m1")
        back.openTitle(ItemKind.BoxSet, "box")
        assertEquals(listOf(ShellRoute, MovieRoute("m1"), CollectionRoute("box")), back.toList())
        back.closeTop()
        assertEquals(listOf(ShellRoute, MovieRoute("m1")), back.toList())
    }

    @Test
    fun `a person opens its page, and the same person again adds nothing`() {
        val back = stack()
        back.openPerson("p1")
        back.openPerson("p1")
        assertEquals(listOf(ShellRoute, PersonRoute("p1")), back.toList())
    }

    /** Saves [key] under its own serializer, as the back stack does, and restores it. Only the id is in what is saved. */
    private inline fun <reified T : TitleRoute> savedAsItself(key: T) {
        val saved = Json.encodeToString(key)
        assertEquals(key, Json.decodeFromString<T>(saved))
        // The id and nothing else: no screen state to restore, so another app can build one of these from an id.
        assertEquals(setOf("itemId"), Json.parseToJsonElement(saved).jsonObject.keys)
    }

    @Test
    fun `every title key is saved and restored as itself, carrying the id alone`() {
        savedAsItself(MovieRoute("a"))
        savedAsItself(SeriesRoute("b"))
        savedAsItself(SeasonRoute("c"))
        savedAsItself(EpisodeRoute("d"))
        savedAsItself(CollectionRoute("e"))
        savedAsItself(PersonRoute("f"))
    }
}
