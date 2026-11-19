package com.sarkarimatch.feature.jobs.ui

import com.sarkarimatch.core.data.repository.SampleMatchRepository
import com.sarkarimatch.core.model.Ist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeViewModelTest {
    @Test
    fun sampleFeedGroupsIntoTodayThreeDaysAndLater() = runTest {
        val today = Ist.today()
        val state = HomeViewModel.group(SampleMatchRepository().observeFeed().first(), today)
        assertEquals(1, state.dueToday.size)
        assertEquals(1, state.withinThreeDays.size)
        assertEquals(1, state.later.size)
        assertEquals(3, state.waitingCount)
    }
}
