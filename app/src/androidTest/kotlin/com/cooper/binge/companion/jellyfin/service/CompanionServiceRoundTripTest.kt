package com.cooper.binge.companion.jellyfin.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.binge.companion.contracts.library.v1.GetAvailabilityRequest
import com.binge.companion.contracts.library.v1.HandshakeRequest
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.v1.HostInfo
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.userMessageOf
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderChannelBuilder
import io.grpc.binder.SecurityPolicies
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole path a host takes to this companion, across a real Binder on the minified build: the
 * exported Service under its caller policy, the generated stubs, the protobuf runtime and the
 * user-message trailer under R8, then a handshake and an availability lookup.
 *
 * The lane's emulator has no Jellyfin server and no session, so both calls answer UNAUTHENTICATED with
 * the sentence that sends the user here to sign in, which is the contract's answer with nothing
 * connected. On a device that is signed in, the same calls succeed; either answer proves the
 * crossing. The unit tests cover what the answers say.
 *
 * The debug policy admits Binge's package names and the app's own uid, which is what lets the test
 * bind from inside the app; a release build's pinned policy would refuse it, so the lane runs the
 * minified debug build.
 */
@RunWith(AndroidJUnit4::class)
class CompanionServiceRoundTripTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun handshakeAndAvailabilityCrossTheBinderOnTheMinifiedBuild() =
        runBlocking<Unit> {
            val channel =
                BinderChannelBuilder
                    .forAddress(AndroidComponentAddress.forRemoteComponent(app.packageName, SERVICE_CLASS), app)
                    .securityPolicy(SecurityPolicies.internalOnly())
                    .build()
            try {
                val stub = LibraryServiceGrpcKt.LibraryServiceCoroutineStub(channel)
                answerOrSignedOut { stub.handshake(handshakeRequest()) }?.let { handshake ->
                    assertTrue("no capabilities declared: $handshake", handshake.capabilitiesCount > 0)
                    assertEquals("Jellyfin", handshake.providerName)
                }
                answerOrSignedOut { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(FILM).build()) }
            } finally {
                channel.shutdownNow()
            }
        }

    /** [call]'s answer, or null when it is UNAUTHENTICATED with a sentence for the user; any other failure fails the test. */
    private suspend fun <T> answerOrSignedOut(call: suspend () -> T): T? =
        try {
            withTimeout(CALL_TIMEOUT_MS) { call() }
        } catch (e: StatusException) {
            assertEquals("not UNAUTHENTICATED: ${e.status}", Status.Code.UNAUTHENTICATED, e.status.code)
            val message = userMessageOf(e.trailers)?.message.orEmpty()
            assertTrue("UNAUTHENTICATED without a sentence for the user", message.isNotBlank())
            null
        }

    private fun handshakeRequest(): HandshakeRequest =
        HandshakeRequest
            .newBuilder()
            .setHost(
                HostInfo
                    .newBuilder()
                    .setPackageName(app.packageName)
                    .setVersionName("androidTest")
                    .setVersionCode(1),
            ).build()

    private companion object {
        const val SERVICE_CLASS = "com.cooper.binge.companion.jellyfin.service.JellyfinCompanionService"
        const val CALL_TIMEOUT_MS = 10_000L
        val FILM: MediaId =
            MediaId
                .newBuilder()
                .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
                .setTmdbId(1084199)
                .build()
    }
}
