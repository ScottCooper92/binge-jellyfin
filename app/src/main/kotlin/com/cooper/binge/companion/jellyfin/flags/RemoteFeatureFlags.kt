package com.cooper.binge.companion.jellyfin.flags

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [FeatureFlags] over a [RemoteFlagSource], with [overrides] on top: a flag a build turns on
 * (`-PbingePromotion=true`) stays on whatever the remote says, so a developer can try it on a device.
 */
class RemoteFeatureFlags(
    private val source: RemoteFlagSource,
    private val overrides: Map<Flag, Boolean> = emptyMap(),
) : FeatureFlags {
    private val resolved = MutableStateFlow(resolve(source.current()))
    override val flags: StateFlow<Flags> = resolved.asStateFlow()

    override suspend fun refresh() {
        source.fetch()?.let { resolved.value = resolve(it) }
    }

    private fun resolve(remote: Flags) = Flags(bingePromotion = overrides[Flag.BingePromotion] ?: remote.bingePromotion)
}
