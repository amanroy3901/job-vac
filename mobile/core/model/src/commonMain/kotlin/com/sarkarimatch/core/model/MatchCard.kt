package com.sarkarimatch.core.model

import kotlinx.datetime.LocalDate

/**
 * What a feed card (S20 MatchCard) needs. Built by `:core:data` from Room + a local evaluation; the UI formats
 * dates, rupees and plurals in the app language.
 */
data class MatchCardUi(
    val matchId: String,
    val title: LocalizedText,
    val organisation: LocalizedText,
    val vacancies: Int?,
    val tile: ExamCategoryTile,
    val verdict: Verdict,
    val feePaise: Long?,
    val qualification: LocalizedText,
    val lastDate: LocalDate,
    /** e.g. "23:00" for "until 11 pm" on the last day; null when the notice gives no time. */
    val lastTime: String?,
    /** First "why you match" line, already rendered for the current language. */
    val whyLine: LocalizedText?,
)

/** Deep links handled by MainActivity (navigation.md §5). Kept here so :core:notifications needs no nav graph. */
object DeepLinks {
    const val SCHEME = "sarkarimatch"
    const val WEB_HOST = "sarkarimatch.in"
    fun job(matchId: String) = "$SCHEME://job/$matchId"
    const val HOME = "$SCHEME://home"
    const val FORMS = "$SCHEME://forms"
    const val PROFILE = "$SCHEME://profile"
}
