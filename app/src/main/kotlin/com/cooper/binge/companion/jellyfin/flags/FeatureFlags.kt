package com.cooper.binge.companion.jellyfin.flags

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A switch for the Binge-facing surfaces, with the Remote Config key that carries it, as binge-seerr has. */
enum class Flag(
    val key: String,
) {
    /** Anything that names Binge to the user: what it can see, what it stops doing, where it plays. */
    BingePromotion("binge_promotion_enabled"),
}

/**
 * Every flag at once. Off by default, so the app stands on its own as Jellyfin Companion and says
 * nothing about Binge. The LIBRARY Service isn't flagged: a host that binds it can always use it.
 */
data class Flags(
    val bingePromotion: Boolean = false,
) {
    operator fun get(flag: Flag): Boolean =
        when (flag) {
            Flag.BingePromotion -> bingePromotion
        }

    companion object {
        val AllOn = Flags(bingePromotion = true)
    }
}

/** The current [Flags]: Remote Config's values, with a build's local override on top ([RemoteFeatureFlags]). */
interface FeatureFlags {
    val flags: StateFlow<Flags>

    /** Fetches fresh remote values; the next value of [flags] carries them. A failed fetch keeps the last ones. */
    suspend fun refresh() = Unit
}

/** Fixed flags, for a build, a test or a preview. It has no default of its own: the caller says which. */
class FixedFeatureFlags(
    value: Flags,
) : FeatureFlags {
    override val flags: StateFlow<Flags> = MutableStateFlow(value)
}

/** The flags for the composition below. Off until a root provides them, so a screen shown alone names no Binge. */
val LocalFlags = staticCompositionLocalOf { Flags() }
