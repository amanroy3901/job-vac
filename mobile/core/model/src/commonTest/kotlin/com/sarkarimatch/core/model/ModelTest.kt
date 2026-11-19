package com.sarkarimatch.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {
    @Test
    fun localizedTextFallsBackAlongTheChainThenToEnglish() {
        val text = LocalizedText.of("hi" to "नमस्ते", "en" to "Hello")
        assertEquals("नमस्ते", text.pick(AppLanguage.HI))
        assertEquals("नमस्ते", text.pick(AppLanguage.MR)) // mr → hi
        assertEquals("Hello", text.pick(AppLanguage.TA)) // ta → en
    }

    @Test
    fun deadlineTones() {
        assertEquals(DeadlineTone.Closed, deadlineTone(-1))
        assertEquals(DeadlineTone.Today, deadlineTone(0))
        assertEquals(DeadlineTone.Urgent, deadlineTone(3))
        assertEquals(DeadlineTone.Soon, deadlineTone(7))
        assertEquals(DeadlineTone.Calm, deadlineTone(8))
    }

    @Test
    fun onlyHindiAndEnglishLaunchHindiIsDefault() {
        assertEquals(listOf(AppLanguage.HI, AppLanguage.EN), AppLanguage.launched)
        assertEquals(AppLanguage.HI, AppLanguage.DEFAULT)
    }
}

class SmFormatTest {
    @Test
    fun rupeesUseIndianGroupingAndWesternDigits() {
        assertEquals("₹100", SmFormat.rupees(10_000))
        assertEquals("₹1,00,000", SmFormat.rupees(10_000_000))
        assertEquals("39,481", SmFormat.count(39_481))
    }

    @Test
    fun hindiDatesKeepWesternDigits() {
        assertEquals("24 अगस्त 2026", SmFormat.longDate(LocalDate(2026, 8, 24), AppLanguage.HI))
        assertEquals("24 Aug 2026", SmFormat.longDate(LocalDate(2026, 8, 24), AppLanguage.EN))
        assertEquals("21 नवं", SmFormat.shortDate(LocalDate(2026, 11, 21), AppLanguage.HI))
        assertEquals("21 Nov", SmFormat.shortDate(LocalDate(2026, 11, 21), AppLanguage.EN))
    }

    @Test
    fun daysLeftUsesCalendarDays() {
        assertEquals(2L, Ist.daysLeft(LocalDate(2026, 12, 30), LocalDate(2027, 1, 1)))
        assertEquals(-1L, Ist.daysLeft(LocalDate(2026, 3, 1), LocalDate(2026, 2, 28)))
    }

    @Test
    fun hour12() {
        assertEquals(11, SmFormat.hour12(LocalTime(23, 0)))
        assertEquals(12, SmFormat.hour12(LocalTime(0, 30)))
    }
}
