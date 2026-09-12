package com.seweryn.radiocar

import com.seweryn.radiocar.util.BluetoothDeviceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothDeviceTrackerTest {

    @Test
    fun testNullAndEmpty() {
        assertNull(BluetoothDeviceTracker.formatDeviceName(null))
        assertNull(BluetoothDeviceTracker.formatDeviceName(""))
        assertNull(BluetoothDeviceTracker.formatDeviceName("   "))
    }

    @Test
    fun testBmwFormatting() {
        // BMW with random code / ghy as user mentioned
        assertEquals("BMW", BluetoothDeviceTracker.formatDeviceName("BMW - 6784 ghy"))
        assertEquals("BMW", BluetoothDeviceTracker.formatDeviceName("BMW_6784"))
        assertEquals("BMW", BluetoothDeviceTracker.formatDeviceName("BMW 12345"))
        // BMW with specific model
        assertEquals("BMW X3", BluetoothDeviceTracker.formatDeviceName("BMW X3"))
        assertEquals("BMW M3", BluetoothDeviceTracker.formatDeviceName("BMW M3"))
        assertEquals("BMW 520d", BluetoothDeviceTracker.formatDeviceName("BMW 520d"))
    }

    @Test
    fun testJblFormatting() {
        // JBL with random numbers as user mentioned
        assertEquals("JBL Audio", BluetoothDeviceTracker.formatDeviceName("JBL 678857"))
        assertEquals("JBL Flip 5", BluetoothDeviceTracker.formatDeviceName("JBL Flip 5"))
        assertEquals("JBL Charge 4", BluetoothDeviceTracker.formatDeviceName("JBL Charge 4"))
    }

    @Test
    fun testOtherCars() {
        assertEquals("Audi MMI", BluetoothDeviceTracker.formatDeviceName("Audi MMI 3241"))
        assertEquals("Mercedes-Benz", BluetoothDeviceTracker.formatDeviceName("MB Bluetooth 9823"))
        assertEquals("Volkswagen", BluetoothDeviceTracker.formatDeviceName("VW BT 124"))
        assertEquals("Volvo", BluetoothDeviceTracker.formatDeviceName("Volvo Car 99"))
    }

    @Test
    fun testHeadphones() {
        assertEquals("AirPods Pro", BluetoothDeviceTracker.formatDeviceName("AirPods Pro"))
        assertEquals("Galaxy Buds2 Pro", BluetoothDeviceTracker.formatDeviceName("Galaxy Buds2 Pro"))
        assertEquals("Bose Audio", BluetoothDeviceTracker.formatDeviceName("Bose QC35"))
        assertEquals("Marshall Audio", BluetoothDeviceTracker.formatDeviceName("Marshall Major IV"))
    }
}
