package com.sarkarimatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The three outcomes of on-device matching (matching-engine.md). `CheckYourself` is never shown as eligible. */
@Serializable
enum class Verdict {
    @SerialName("eligible") ELIGIBLE,
    @SerialName("check_yourself") CHECK_YOURSELF,
    @SerialName("not_eligible") NOT_ELIGIBLE,
}

/** tokens.json `semantic.deadline.*`. */
enum class DeadlineTone { Today, Urgent, Soon, Calm, Closed }

fun deadlineTone(daysLeft: Long): DeadlineTone = when {
    daysLeft < 0 -> DeadlineTone.Closed
    daysLeft == 0L -> DeadlineTone.Today
    daysLeft <= 3 -> DeadlineTone.Urgent
    daysLeft <= 7 -> DeadlineTone.Soon
    else -> DeadlineTone.Calm
}

/** All "today", countdowns and deadline tones use the IST calendar date. */
object Ist {
    val ZONE: TimeZone = TimeZone.of("Asia/Kolkata")

    /** Today's calendar date in India, whatever the device time zone. */
    fun today(clock: Clock = Clock.System): LocalDate = clock.todayIn(ZONE)

    fun daysLeft(today: LocalDate, lastDate: LocalDate): Long = today.daysUntil(lastDate).toLong()
}

/** Category tiles on cards (Direction C): recognisable without reading. */
enum class ExamCategoryTile { Police, Ssc, Railway, Bank, State }
