package com.example.lolsito.feature.reminders.work

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun `an explicit request replaces and a foreground one keeps the running sync`() {
        assertEquals(ExistingWorkPolicy.REPLACE, syncPolicy(onlyIfIdle = false))
        assertEquals(ExistingWorkPolicy.KEEP, syncPolicy(onlyIfIdle = true))
    }
}
