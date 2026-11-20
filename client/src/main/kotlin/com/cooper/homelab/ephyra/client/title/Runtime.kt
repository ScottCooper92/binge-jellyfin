package com.cooper.homelab.ephyra.client.title

internal const val TICKS_PER_MINUTE = 600_000_000L

/** A length in whole minutes, or null when it is under one: a stat that says "0 min" is wrong, and one that says nothing is true. */
internal fun runtimeMinutes(ticks: Long): Int? = (ticks / TICKS_PER_MINUTE).toInt().takeIf { it > 0 }
