package com.cooper.binge.companion.jellyfin.flags

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [FeatureFlags] over a [RemoteFlagSource], with [overrides] on top: a flag a build turns on
 * (`-PbingePromotion=true`) stays on whatever the remote says, so a developer can try it on a device.
 * The flags start at the overrides alone: reading the source touches disk, so [refresh] does it off the main thread.
 */
class RemoteFeatureFlags(
    private val source: RemoteFlagSource,
    private val overrides: Map<Flag, Boolean> = emptyMap(),
) : FeatureFlags {
    private val resolved = MutableStateFlow(resolve(Flags()))
    override val flags: StateFlow<Flags> = resolved.asStateFlow()

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            resolved.value = resolve(source.current())
            source.fetch()?.let { resolved.value = resolve(it) }
        }
    }

    private fun resolve(remote: Flags) = Flags(bingePromotion = overrides[Flag.BingePromotion] ?: remote.bingePromotion)
}
