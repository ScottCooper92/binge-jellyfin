package com.cooper.homelab.ephyra.playback

import com.binge.companion.contracts.library.v1.CodecLimit
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile as ContractProfile

/**
 * The player's device profile as the contract's, which `DeviceProfiles.toJellyfin` already turns into a Jellyfin `DeviceProfile`. The
 * two carry the same fields, so the app's one description of what a device plays serves Binge's player and this one, and a fix to it
 * (8-bit H.264, E-AC-3 with Atmos) reaches both.
 */
internal fun PlaybackDeviceProfile.toContract(): ContractProfile =
    ContractProfile
        .newBuilder()
        .addAllVideoCodecs(videoCodecs)
        .addAllAudioCodecs(audioCodecs)
        .setMaxWidth(maxWidth)
        .setMaxHeight(maxHeight)
        .setSupportsHdr(supportsHdr)
        .setMaxBitrateBps(maxBitrateBps)
        .addAllContainerMimeTypes(containerMimeTypes)
        .addAllCodecLimits(
            codecLimits.map {
                CodecLimit
                    .newBuilder()
                    .setMimeType(it.mimeType)
                    .setProfile(it.profile)
                    .setMaxWidth(it.maxWidth)
                    .setMaxHeight(it.maxHeight)
                    .build()
            },
        ).build()
