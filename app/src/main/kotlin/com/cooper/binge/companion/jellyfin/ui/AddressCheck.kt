package com.cooper.binge.companion.jellyfin.ui

import android.util.Log
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.JellyfinUnreadableAnswer
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.server.publicInfo
import com.cooper.homelab.core.server.network.CleartextRefusedException
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import java.io.IOException

/**
 * Asks each address the user's typing could mean who it is, in order, and takes the first that answers as Jellyfin. When none does,
 * the error is the most telling one: an answer that was not Jellyfin beats a refusal to send, and a refusal beats silence.
 */
internal class AddressCheck(
    private val connection: Connection,
    private val localNetwork: LocalNetworkPermission,
) {
    sealed interface Result {
        class Found(
            val url: HttpUrl,
            val info: PublicServerInfo,
        ) : Result

        class Failed(
            val error: SetupError,
        ) : Result
    }

    private enum class Answer { NotJellyfin, Refused, Silent }

    suspend fun first(candidates: List<HttpUrl>): Result {
        val failures = mutableListOf<Answer>()
        for (url in candidates) {
            when (val probe = probe(url)) {
                is Probe.Found -> return Result.Found(url, probe.info)
                is Probe.Failed -> failures += probe.answer
            }
        }
        val error =
            when {
                Answer.NotJellyfin in failures -> SetupError.NotJellyfin
                Answer.Refused in failures -> SetupError.Generic
                else -> unreachableSetup(candidates.first().toString(), localNetwork)
            }
        return Result.Failed(error)
    }

    private sealed interface Probe {
        class Found(
            val info: PublicServerInfo,
        ) : Probe

        class Failed(
            val answer: Answer,
        ) : Probe
    }

    private suspend fun probe(url: HttpUrl): Probe =
        try {
            connection
                .anonymous(url)
                .publicInfo()
                .takeIf { it.serverId.isNotBlank() }
                ?.let { Probe.Found(it) }
                ?: Probe.Failed(Answer.NotJellyfin)
        } catch (e: CancellationException) {
            throw e
        } catch (e: JellyfinHttpException) {
            Log.i(TAG, "The address answered, but not as Jellyfin", e)
            Probe.Failed(Answer.NotJellyfin)
        } catch (e: JellyfinUnreadableAnswer) {
            Log.i(TAG, "The address answered something that is not Jellyfin", e)
            Probe.Failed(Answer.NotJellyfin)
        } catch (e: CleartextRefusedException) {
            Log.i(TAG, "Plain HTTP to a public host was refused", e)
            Probe.Failed(Answer.Refused)
        } catch (e: IOException) {
            Log.i(TAG, "Server check failed", e)
            Probe.Failed(Answer.Silent)
        }

    private companion object {
        const val TAG = "AddressCheck"
    }
}
