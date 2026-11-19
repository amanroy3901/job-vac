package com.sarkarimatch.feature.jobs.ui

import com.sarkarimatch.core.data.repository.SampleMatchRepository
import com.sarkarimatch.core.model.Ist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class HomeViewModelTest {
    @Test
    fun `sample feed groups into today, three days and later`() = runTest {
        val today = LocalDate.now(Ist.ZONE)
        val state = HomeViewModel.group(SampleMatchRepository().observeFeed().first(), today)
        assertEquals(1, state.dueToday.size)
        assertEquals(1, state.withinThreeDays.size)
        assertEquals(1, state.later.size)
        assertEquals(3, state.waitingCount)
    }
}
