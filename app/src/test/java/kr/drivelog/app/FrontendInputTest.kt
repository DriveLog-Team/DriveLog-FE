package kr.drivelog.app

import org.junit.Assert.*
import org.junit.Test

class FrontendInputTest {
    @Test fun validatesNamesWithoutSilentlyAcceptingControls() {
        assertEquals("은서", validDisplayName(" 은서 "))
        for (name in listOf(" ", "은\n서", "a".repeat(33))) {
            assertThrows(IllegalArgumentException::class.java) { validDisplayName(name) }
        }
    }
    @Test fun locationEventsKeepMillisecondsAndRejectInvalidTimesAndKinds() {
        assertEquals(DriveEvent(42001, "LANDMARK", "해안길"), driveEvent("42.001", "LANDMARK", " 해안길 "))
        assertNull(driveEvent("86400", "ARRIVAL", "목적지"))
        assertNull(driveEvent("-1", "LANDMARK", "해안길"))
        assertNull(driveEvent("42.0001", "LANDMARK", "해안길"))
        assertNull(driveEvent("42", "UNKNOWN", "해안길"))
        assertNull(driveEvent("42", "LANDMARK", " "))
    }
}
