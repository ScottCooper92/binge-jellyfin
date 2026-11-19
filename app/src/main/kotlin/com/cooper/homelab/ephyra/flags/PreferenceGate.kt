package com.cooper.homelab.ephyra.flags

import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FlagKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Whether a feature shows: the remote flag AND the user's own preference. It is the one place the two are combined, so nothing
 * else reads the flag directly. A build override forces the remote flag; the preference sits on top.
 *
 * Nothing here is specific to this app (no Ephyra type, no Android type), so it can move to the shared library unchanged.
 */
interface PreferenceGate {
    /**
     * The combined value, from one emission: null until both the flag and the preference have been read, then never null.
     * A reader that must tell a toggle from a first value keys on the null, so the two cannot disagree.
     */
    val resolved: StateFlow<Boolean?>

    /** The remote flag alone, for the moments before [resolved] has a value. Right for everyone who has not opted out. */
    val fallback: Boolean

    /** The remote flag alone: whether the setting is shown at all. */
    val offered: Flow<Boolean>

    /** The user's own choice, whatever the flag says. */
    val preference: Flow<Boolean>

    suspend fun setPreference(enabled: Boolean)

    /** What to show now: the resolved value, or the [fallback] until there is one. */
    fun current(): Boolean = resolved.value ?: fallback
}

/** A gate that is off and offers nothing, for a screen built without one. */
object NoPreferenceGate : PreferenceGate {
    override val resolved: StateFlow<Boolean?> = MutableStateFlow(false)
    override val fallback: Boolean = false
    override val offered: Flow<Boolean> = flowOf(false)
    override val preference: Flow<Boolean> = flowOf(true)

    override suspend fun setPreference(enabled: Boolean) = Unit
}

/** [flag] from [featureFlags], and the user's answer in [preference], written by [store]. */
class FlagPreferenceGate(
    private val featureFlags: FeatureFlags,
    private val flag: FlagKey,
    override val preference: Flow<Boolean>,
    private val store: suspend (Boolean) -> Unit,
    scope: CoroutineScope,
) : PreferenceGate {
    override val offered: Flow<Boolean> = featureFlags.flags.map { it[flag] }.distinctUntilChanged()
    override val fallback: Boolean get() = featureFlags.flags.value[flag]

    override val resolved: StateFlow<Boolean?> =
        combine<Boolean, Boolean, Boolean?>(offered, preference) { offered, wanted -> offered && wanted }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun setPreference(enabled: Boolean) = store(enabled)
}
