package com.cooper.homelab.ephyra.client.home

import com.binge.designsystem.ErrorKind
import com.cooper.homelab.core.foundation.ServerFailure

/** The design system's error for [this]: what the screen says, from why the server could not be read. */
fun ServerFailure.errorKind(): ErrorKind =
    when (this) {
        ServerFailure.NotConnected, ServerFailure.SessionRejected -> ErrorKind.Auth
        ServerFailure.NotPermitted -> ErrorKind.Forbidden
        ServerFailure.NotFound -> ErrorKind.NotFound
        ServerFailure.ServerError -> ErrorKind.Server
        is ServerFailure.Unreachable -> ErrorKind.Network
        else -> ErrorKind.Generic
    }

/**
 * Whether Try again is offered: for a failure another go can fix. A rejected session is the shell's to send to sign-in, and a refusal
 * or a missing permission would only fail the same way again.
 */
fun ServerFailure.canRetry(): Boolean = isTransient
