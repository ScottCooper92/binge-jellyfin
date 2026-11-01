package com.cooper.binge.companion.jellyfin.ui.console

/**
 * The languages most libraries are in, or dubbed and subtitled into, as two-letter codes. A language
 * picker suggests these after the device's own, so the usual choices sit at the top of a list of
 * hundreds.
 */
internal val POPULAR_LANGUAGES = listOf("en", "es", "fr", "de", "it", "pt", "ja", "zh", "ko", "hi", "ar", "ru", "nl", "sv", "pl")

/** The regions most metadata is read for, as two-letter codes, suggested the same way. */
internal val POPULAR_REGIONS = listOf("US", "GB", "CA", "AU", "IE", "NZ", "DE", "FR", "ES", "IT", "NL", "SE", "JP", "BR", "MX", "IN")

/** [device] first, then [popular], each once, keeping only what [offered] has. */
internal fun <T> suggestions(
    device: List<T>,
    popular: List<T>,
    offered: Set<T>,
): List<T> = (device + popular).filter { it in offered }.distinct()
