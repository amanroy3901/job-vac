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
