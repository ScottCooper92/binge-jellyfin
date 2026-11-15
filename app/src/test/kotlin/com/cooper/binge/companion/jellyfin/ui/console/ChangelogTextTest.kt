package com.cooper.binge.companion.jellyfin.ui.console

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogTextTest {
    @Test
    fun `markdown reads as plain text`() {
        val markdown = "## What's new\n\n* **Faster** scans\n- Fixes [the report](https://x.example/a) on a `slow` disk\n\n\n\n_Thanks_"
        assertEquals("What's new\n\n• Faster scans\n• Fixes the report on a slow disk\n\nThanks", plainChangelog(markdown))
    }

    @Test
    fun `plain text is left as written`() {
        assertEquals("Fixes a thing.", plainChangelog("Fixes a thing."))
        assertEquals("snake_case_name stays", plainChangelog("snake_case_name stays"))
    }
}
