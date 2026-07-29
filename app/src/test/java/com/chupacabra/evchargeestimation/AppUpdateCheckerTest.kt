package com.chupacabra.evchargeestimation

import com.chupacabra.evchargeestimation.update.AppUpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun newerPatchVersion() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun newerMinorAndMajor() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun stripsVPrefix() {
        assertTrue(AppUpdateChecker.isNewerVersion("v1.2.0", "1.1.0"))
    }
}
