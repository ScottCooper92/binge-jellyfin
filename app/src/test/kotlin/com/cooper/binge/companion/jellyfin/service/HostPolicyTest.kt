package com.cooper.binge.companion.jellyfin.service

import io.grpc.Status
import io.grpc.binder.SecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HostPolicyTest {
    private val denyAll =
        object : SecurityPolicy() {
            override fun checkAuthorization(uid: Int): Status = Status.PERMISSION_DENIED
        }
    private val pinned =
        object : SecurityPolicy() {
            override fun checkAuthorization(uid: Int): Status = Status.OK
        }

    @Test
    fun `release pins, debug admits by package`() {
        assertSame(pinned, selectHostPolicy(admitsUnverifiedCallers = false, pinned = { pinned }, debug = { denyAll }))
        assertSame(denyAll, selectHostPolicy(admitsUnverifiedCallers = true, pinned = { pinned }, debug = { denyAll }))
    }

    @Test
    fun `the debug policy admits this app's own uid and asks the wrapped policy about anyone else`() {
        val policy = SelfOrPolicy(selfUid = 10_001, policy = denyAll)
        assertEquals(Status.OK, policy.checkAuthorization(10_001))
        assertEquals(Status.PERMISSION_DENIED, policy.checkAuthorization(10_002))
    }
}
