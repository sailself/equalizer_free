package com.quieteq.app.audio

import com.quieteq.app.core.AttachmentMode
import org.junit.Assert.*
import org.junit.Test

class SessionTrackerTest {
    @Test fun autoSwitchesBetweenGlobalAndPlayerSessions() {
        val tracker = SessionTracker()
        assertEquals(setOf(0), tracker.desired(AttachmentMode.AUTO))
        tracker.open(7, "one")
        assertEquals(setOf(7), tracker.desired(AttachmentMode.AUTO))
        tracker.open(9, "two")
        assertEquals(setOf(7, 9), tracker.desired(AttachmentMode.AUTO))
        assertEquals(setOf(0), tracker.desired(AttachmentMode.GLOBAL))
        tracker.close(7, "one")
        tracker.close(9, "two")
        assertEquals(setOf(0), tracker.desired(AttachmentMode.AUTO))
        assertTrue(tracker.desired(AttachmentMode.PLAYERS_ONLY).isEmpty())
    }
    @Test fun duplicateBroadcastAndMultipleOwners() {
        val tracker = SessionTracker()
        tracker.open(8, "one"); tracker.open(8, "one"); tracker.open(8, "two")
        tracker.close(8, "one")
        assertEquals(setOf(8), tracker.sessions)
        tracker.close(8, "one")
        assertEquals(setOf(8), tracker.sessions)
        tracker.close(8, "two")
        assertTrue(tracker.sessions.isEmpty())
    }
    @Test fun invalidAndExcessSessionsAreIgnored() {
        val tracker = SessionTracker()
        tracker.open(0, "invalid")
        for (id in 1..70) tracker.open(id, "owner")
        assertEquals(64, tracker.sessions.size)
        assertFalse(tracker.sessions.contains(0))
    }
}
