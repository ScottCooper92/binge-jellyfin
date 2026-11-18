package com.cooper.homelab.ephyra

/**
 * The package prefixes whose frames the app's log keeps from a logged throwable: this app's own (`com.cooper.homelab.ephyra.`) and
 * the shared library's (`com.cooper.homelab.core.`) share this one. The ring log keeps no frame outside them, so a prefix that
 * matches nothing leaves every logged error as a bare class name. A test holds that a frame of each is kept.
 */
internal val OWN_LOG_PACKAGES: List<String> = listOf("com.cooper.homelab.")
