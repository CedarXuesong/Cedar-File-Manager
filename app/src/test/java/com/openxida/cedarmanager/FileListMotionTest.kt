package com.openxida.cedarmanager

import com.openxida.cedarmanager.ui.components.shouldStartFileReveal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListMotionTest {
    @Test
    fun fadeStartsOnlyForNewUnrevealedEntries() {
        assertFalse(shouldStartFileReveal(hasEntries = false, finished = false))
        assertTrue(shouldStartFileReveal(hasEntries = true, finished = false))
        assertFalse(shouldStartFileReveal(hasEntries = true, finished = true))
    }
}
