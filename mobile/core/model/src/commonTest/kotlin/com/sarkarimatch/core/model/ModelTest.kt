package com.sarkarimatch.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {
    @Test
    fun `localized text falls back along the chain then to English`() {
        val text = LocalizedText.of("hi" to "नमस्ते", "en" to "Hello")
        assertEquals("नमस्ते", text.pick(AppLanguage.HI))
        assertEquals("नमस्ते", text.pick(AppLanguage.MR)) // mr → hi
        assertEquals("Hello", text.pick(AppLanguage.TA)) // ta → en
    }

    @Test
    fun `deadline tones`() {
        assertEquals(DeadlineTone.Closed, deadlineTone(-1))
        assertEquals(DeadlineTone.Today, deadlineTone(0))
        assertEquals(DeadlineTone.Urgent, deadlineTone(3))
        assertEquals(DeadlineTone.Soon, deadlineTone(7))
        assertEquals(DeadlineTone.Calm, deadlineTone(8))
    }

    @Test
    fun `only hindi and english launch, hindi is default`() {
        assertEquals(listOf(AppLanguage.HI, AppLanguage.EN), AppLanguage.launched)
        assertEquals(AppLanguage.HI, AppLanguage.DEFAULT)
    }
}

class SmFormatTest {
    @Test
    fun `rupees use Indian grouping and western digits`() {
        assertEquals("₹100", SmFormat.rupees(10_000))
        assertEquals("₹1,00,000", SmFormat.rupees(10_000_000))
        assertEquals("39,481", SmFormat.count(39_481))
    }

    @Test
    fun `hindi dates keep western digits`() {
        val text = SmFormat.longDate(java.time.LocalDate.of(2026, 8, 24), AppLanguage.HI)
        kotlin.test.assertTrue(text.startsWith("24 "), text)
        kotlin.test.assertTrue(text.endsWith(" 2026"), text)
    }
}
