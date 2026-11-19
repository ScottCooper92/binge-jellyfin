package com.cooper.homelab.ephyra.client.home

import com.binge.designsystem.ErrorKind
import com.cooper.homelab.core.foundation.ServerFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFailureTest {
    @Test
    fun `a failure says what it was, in the design system's terms`() {
        assertEquals(ErrorKind.Network, ServerFailure.Unreachable().errorKind())
        assertEquals(ErrorKind.Server, ServerFailure.ServerError.errorKind())
        assertEquals(ErrorKind.Forbidden, ServerFailure.NotPermitted.errorKind())
        assertEquals(ErrorKind.NotFound, ServerFailure.NotFound.errorKind())
        assertEquals(ErrorKind.Auth, ServerFailure.SessionRejected.errorKind())
        assertEquals(ErrorKind.Auth, ServerFailure.NotConnected.errorKind())
        assertEquals(ErrorKind.Generic, ServerFailure.Refused("no").errorKind())
    }

    @Test
    fun `Try again is offered only where another go can fix it`() {
        assertTrue(ServerFailure.Unreachable().canRetry())
        assertTrue(ServerFailure.ServerError.canRetry())
        assertFalse(ServerFailure.NotPermitted.canRetry())
        assertFalse(ServerFailure.SessionRejected.canRetry())
    }
}
