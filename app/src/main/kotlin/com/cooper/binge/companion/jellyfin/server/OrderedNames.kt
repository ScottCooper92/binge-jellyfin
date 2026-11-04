package com.cooper.binge.companion.jellyfin.server

/** Moves the entry named [name] [by] places, leaving an unknown name or an out-of-bounds move unchanged. */
internal fun <T> List<T>.moved(
    name: String,
    by: Int,
    nameOf: (T) -> String,
): List<T> {
    val from = indexOfFirst { nameOf(it).equals(name, ignoreCase = true) }
    val to = from + by
    return if (from >= 0 && to in indices) toMutableList().also { it.add(to, it.removeAt(from)) } else this
}
