package com.openxida.cedarmanager

import com.openxida.cedarmanager.domain.RootState
import com.openxida.cedarmanager.ui.components.StorageSnapshot
import com.openxida.cedarmanager.ui.components.deviceModelLabel
import com.openxida.cedarmanager.ui.components.homeFormatBytes
import com.openxida.cedarmanager.ui.components.rootAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStatusTest {
    @Test
    fun deviceModelAvoidsDuplicateManufacturer() {
        assertEquals("Xiaomi 14", deviceModelLabel("xiaomi", "Xiaomi 14"))
        assertEquals("Xiaomi M2101K9C", deviceModelLabel("Xiaomi", "M2101K9C"))
        assertEquals("Android 设备", deviceModelLabel("", ""))
    }

    @Test
    fun rootAvailabilityIsShortAndClear() {
        assertEquals("可用", rootAvailability(RootState.GRANTED))
        assertEquals("不可用", rootAvailability(RootState.DENIED))
        assertEquals("不可用", rootAvailability(RootState.UNAVAILABLE))
        assertEquals("检测中", rootAvailability(RootState.CHECKING))
    }

    @Test
    fun storageUsageHandlesEmptyAndPartialVolumes() {
        assertEquals(0f, StorageSnapshot(0L, 0L).usage, 0.001f)
        assertEquals(0, StorageSnapshot(0L, 0L).percent)

        val half = StorageSnapshot(availableBytes = 500L, totalBytes = 1_000L)
        assertEquals(0.5f, half.usage, 0.001f)
        assertEquals(500L, half.usedBytes)
        assertEquals(50, half.percent)
    }

    @Test
    fun byteFormattingUsesBinaryUnits() {
        assertEquals("512 B", homeFormatBytes(512L))
        assertEquals("1.0 KiB", homeFormatBytes(1_024L))
        assertEquals("1.0 MiB", homeFormatBytes(1_048_576L))
    }
}
