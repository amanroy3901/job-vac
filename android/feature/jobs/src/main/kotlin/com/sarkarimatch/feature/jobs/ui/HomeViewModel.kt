package com.sarkarimatch.feature.jobs.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarkarimatch.core.data.repository.MatchRepository
import com.sarkarimatch.core.model.DeadlineTone
import com.sarkarimatch.core.model.Ist
import com.sarkarimatch.core.model.MatchCardUi
import com.sarkarimatch.core.model.deadlineTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** S20 feed, grouped by urgency (Direction C): last day today · within 3 days · later. */
@Immutable
data class HomeUiState(
    val today: LocalDate,
    val dueToday: ImmutableList<MatchCardUi> = persistentListOf(),
    val withinThreeDays: ImmutableList<MatchCardUi> = persistentListOf(),
    val later: ImmutableList<MatchCardUi> = persistentListOf(),
    val loading: Boolean = true,
) {
    val waitingCount: Int get() = dueToday.size + withinThreeDays.size + later.size
}

@HiltViewModel
class HomeViewModel @Inject constructor(matchRepository: MatchRepository) : ViewModel() {
    private val today: LocalDate get() = LocalDate.now(Ist.ZONE)

    val state: StateFlow<HomeUiState> = matchRepository.observeFeed()
        .map { cards -> group(cards, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today = today))

    companion object {
        fun group(cards: List<MatchCardUi>, today: LocalDate): HomeUiState {
            val open = cards.filter { Ist.daysLeft(today, it.lastDate) >= 0 }.sortedBy { it.lastDate }
            val byTone = open.groupBy { deadlineTone(Ist.daysLeft(today, it.lastDate)) }
            return HomeUiState(
                today = today,
                dueToday = byTone[DeadlineTone.Today].orEmpty().toImmutableList(),
                withinThreeDays = byTone[DeadlineTone.Urgent].orEmpty().toImmutableList(),
                later = (byTone[DeadlineTone.Soon].orEmpty() + byTone[DeadlineTone.Calm].orEmpty()).toImmutableList(),
                loading = false,
            )
        }
    }
}
