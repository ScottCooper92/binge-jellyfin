package com.cooper.binge.companion.jellyfin.ui.console

import com.binge.designsystem.SaveStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CombinedSaveTest {
    private val idle = SaveProgress()
    private val saving = SaveProgress(SaveStatus.Saving)
    private val saved = SaveProgress(SaveStatus.Saved)
    private val failed = SaveProgress(failed = true)

    @Test
    fun `two idle writers are idle`() = assertEquals(idle, combined(idle, idle))

    @Test
    fun `busy if either is, before saved`() {
        assertEquals(SaveStatus.Saving, combined(saved, saving).status)
        assertEquals(SaveStatus.Saving, combined(saving, saved).status)
    }

    @Test
    fun `saved if either is and neither is busy`() = assertEquals(SaveStatus.Saved, combined(idle, saved).status)

    @Test
    fun `failed if either is, whatever the other says`() {
        assertEquals(true, combined(failed, saved).failed)
        assertEquals(true, combined(saved, failed).failed)
        assertEquals(false, combined(saved, idle).failed)
    }
}
