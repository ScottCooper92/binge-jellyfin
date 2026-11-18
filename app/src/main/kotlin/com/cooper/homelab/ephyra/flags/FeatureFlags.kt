package com.cooper.homelab.ephyra.flags

import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags

/** The flags for the composition below. Off until a root provides them, so a screen shown alone names no Binge. */
val LocalFlags = staticCompositionLocalOf { Flags() }

/** Anything that asks the user to install, open or connect Binge. */
val Flags.bingePromotion: Boolean get() = this[SharedFlags.BingePromotion]
