package com.sarkarimatch.core.testing

import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.model.MatchCardUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMatchRepository(initial: List<MatchCardUi> = emptyList()) : MatchRepository {
    val feed = MutableStateFlow(initial)
    override fun observeFeed(): Flow<List<MatchCardUi>> = feed
}
