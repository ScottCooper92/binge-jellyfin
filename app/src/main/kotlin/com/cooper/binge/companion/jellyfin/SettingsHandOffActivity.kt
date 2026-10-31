package com.cooper.binge.companion.jellyfin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HandOffPolicy
import com.cooper.binge.companion.jellyfin.service.ADMITS_UNVERIFIED_CALLERS

/** Binge's "manage" row: admits its caller and opens the settings screen. */
class SettingsHandOffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val admitted =
            if (ADMITS_UNVERIFIED_CALLERS) {
                HandOffPolicy.anyCertificateOf(tag = TAG).permits(callingPackage)
            } else {
                HandOffPolicy.pinned(this, listOf(BingeHosts.release)).permits(callingPackage)
            }
        if (admitted) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private companion object {
        const val TAG = "JellyfinHandOff"
    }
}
