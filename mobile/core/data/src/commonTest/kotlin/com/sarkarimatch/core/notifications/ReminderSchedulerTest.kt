package com.sarkarimatch.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ReminderSchedulerTest {
    private val t0 = Instant.parse("2026-11-19T00:00:00Z")
    private fun reminder(i: Int) = LocalReminder("r$i", t0 + i.hours, "t", "b", SmChannel.REMINDERS, null)

    @Test
    fun iosCapKeepsTheSoonestReminders() {
        val plan = (100 downTo 1).map(::reminder)
        val kept = plan.soonest(64)
        assertEquals(64, kept.size)
        assertEquals("r1", kept.first().id)
        assertEquals("r64", kept.last().id)
    }

    @Test
    fun noCapKeepsEverything() {
        assertEquals(100, (1..100).map(::reminder).soonest(null).size)
    }

    @Test
    fun unknownChannelFallsBackToReminders() {
        assertEquals(SmChannel.REMINDERS, SmChannel.fromId("whatsapp"))
        assertEquals(SmChannel.CRITICAL, SmChannel.fromId("critical"))
    }
}
