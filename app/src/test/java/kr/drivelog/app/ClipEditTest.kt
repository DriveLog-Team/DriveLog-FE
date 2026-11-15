package kr.drivelog.app

import org.junit.Assert.*
import org.junit.Test

class ClipEditTest {
    private fun draft(title: String = "원래 제목", start: String = "39", end: String = "46", captions: List<String> = listOf("바다!")) =
        clipEdit("moment-001", "원래 제목", 39000, 46000, listOf("바다!"), title, start, end, captions)

    @Test fun sendsOnlyChangedFieldsAndFullOrderedCaptions() {
        val edit = draft(start = "40", end = "45", captions = listOf("바다 보인다!"))!!
        assertEquals(40000L, edit.startMs)
        assertEquals(45000L, edit.endMs)
        assertNull(edit.title)
        assertEquals(listOf("바다 보인다!"), edit.captions)
    }

    @Test fun rejectsOutOfBoundsTrimsAndReversedTimes() {
        assertNull(draft(start = "38.999"))
        assertNull(draft(end = "46.001"))
        assertNull(draft(start = "45", end = "40"))
        assertNull(draft(start = "46", end = "46"))
        assertNull(draft(start = "NaN"))
    }

    @Test fun preservesMillisecondPrecisionAndRejectsFractionalMilliseconds() {
        assertEquals(39001L, draft(start = "39.001")!!.startMs)
        assertNull(draft(start = "39.0001"))
        assertEquals("39.001", millisecondsToSeconds(39001))
    }

    @Test fun rejectsInvalidTitleCaptionCountAndLongCaptions() {
        assertNull(draft(title = " "))
        assertNull(draft(title = "a".repeat(81)))
        assertNull(draft(captions = emptyList()))
        assertNull(draft(captions = listOf("a".repeat(201))))
    }

    @Test fun doesNotCreateJobWhenNothingChanged() { assertNull(draft()) }
}
