package com.cooper.binge.companion.jellyfin.server

import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinChannelMappingsApiTest {
    private val options =
        ChannelMappingOptions(
            providerName = "XMLTV",
            providerChannels = listOf(ProviderChannel("p1", "BBC One"), ProviderChannel("p2", "BBC Two")),
            tunerChannels = listOf(TunerChannel("c1", "101", "p1"), TunerChannel("c2", "102", "p1"), TunerChannel("c3", "103", null)),
        )

    @Test
    fun `a provider channel is paired with every tuner channel that points at it`() {
        assertEquals(listOf("c1", "c2"), options.pairedWith("p1").map { it.id })
        assertEquals(emptyList<TunerChannel>(), options.pairedWith("p2"))
    }

    @Test
    fun `pairing a tuner channel changes that channel alone`() {
        val paired = options.paired("c3", "p2")
        assertEquals(listOf("p1", "p1", "p2"), paired.tunerChannels.map { it.providerChannelId })
        assertEquals(options.tunerChannels.take(2), paired.tunerChannels.take(2))
    }
}
