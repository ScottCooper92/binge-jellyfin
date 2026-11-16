package com.cooper.binge.companion.jellyfin.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.R

/** The status-bar glyph as the system draws it: tinted, at its real 24 dp and enlarged, on a dark and a light bar. */
class NotificationGlyphScreenshotTest {
    @Composable
    private fun Glyph(
        size: Int,
        tint: Color,
        ground: Color,
    ) = Box(Modifier.background(ground).padding(8.dp), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ic_notification),
            contentDescription = null,
            Modifier.size(size.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }

    @PreviewTest
    @Preview(name = "glyph", widthDp = 320, heightDp = 100)
    @Composable
    fun glyph() =
        Row(
            Modifier.background(Color(0xFF000000)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(24, Color.White, Color(0xFF000000))
            Glyph(24, Color(0xFF202124), Color(0xFFF1F3F4))
            Glyph(72, Color.White, Color(0xFF000000))
            Glyph(72, Color(0xFF202124), Color(0xFFF1F3F4))
        }
}
