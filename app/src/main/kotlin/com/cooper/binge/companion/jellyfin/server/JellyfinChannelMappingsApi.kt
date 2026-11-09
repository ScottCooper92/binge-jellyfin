package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A channel the guide data provider lists. */
data class ProviderChannel(
    val id: String,
    val name: String,
)

/** A channel a tuner offers, with the provider channel the server pairs it with ([providerChannelId]), or null while it pairs it with none. */
data class TunerChannel(
    val id: String,
    val name: String,
    val providerChannelId: String?,
)

/**
 * What `ChannelMappingOptions` holds for one guide data provider: its [providerChannels] and the [tunerChannels] to pair them with. The
 * server keeps the pairing on the tuner channel; a provider channel is paired with every tuner channel that points at it.
 */
data class ChannelMappingOptions(
    val providerName: String,
    val providerChannels: List<ProviderChannel>,
    val tunerChannels: List<TunerChannel>,
) {
    /** The tuner channels paired with the provider channel [providerChannelId]. */
    fun pairedWith(providerChannelId: String): List<TunerChannel> = tunerChannels.filter { it.providerChannelId == providerChannelId }

    /** [this] with the tuner channel [tunerChannelId] paired with [providerChannelId]. */
    fun paired(
        tunerChannelId: String,
        providerChannelId: String,
    ): ChannelMappingOptions =
        copy(tunerChannels = tunerChannels.map { if (it.id == tunerChannelId) it.copy(providerChannelId = providerChannelId) else it })
}

/** The channels of the guide data provider [providerId] and the tuner channels, with the pairing the server holds. Administrators only. */
suspend fun JellyfinClient.channelMappingOptions(providerId: String): ChannelMappingOptions {
    val json = getObject(CHANNEL_MAPPING_OPTIONS, mapOf("providerId" to providerId))
    return ChannelMappingOptions(
        providerName = json.string("ProviderName").orEmpty(),
        providerChannels =
            json.array("ProviderChannels").mapNotNull { element ->
                val channel = element as? JsonObject
                val id = channel?.string("Id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ProviderChannel(id, channel.string("Name")?.takeIf { it.isNotBlank() } ?: id)
            },
        tunerChannels = json.array("TunerChannels").mapNotNull { (it as? JsonObject)?.tunerChannel() },
    )
}

/**
 * Pairs the tuner channel [tunerChannelId] with the provider channel [providerChannelId] of the guide data provider [providerId], and
 * answers with the tuner channel as the server now holds it. The server takes one provider channel for a tuner channel.
 */
suspend fun JellyfinClient.setChannelMapping(
    providerId: String,
    tunerChannelId: String,
    providerChannelId: String,
): TunerChannel? =
    postObject(
        CHANNEL_MAPPINGS,
        JsonObject(
            mapOf(
                "ProviderId" to JsonPrimitive(providerId),
                "TunerChannelId" to JsonPrimitive(tunerChannelId),
                "ProviderChannelId" to JsonPrimitive(providerChannelId),
            ),
        ),
    ).tunerChannel()

private fun JsonObject.tunerChannel(): TunerChannel? {
    val id = string("Id")?.takeIf { it.isNotBlank() } ?: return null
    return TunerChannel(
        id = id,
        name = string("Name")?.takeIf { it.isNotBlank() } ?: id,
        providerChannelId = string("ProviderChannelId")?.takeIf { it.isNotBlank() },
    )
}

private const val CHANNEL_MAPPING_OPTIONS = "LiveTv/ChannelMappingOptions"
private const val CHANNEL_MAPPINGS = "LiveTv/ChannelMappings"
