package com.sarkarimatch.core.data.repository

import com.sarkarimatch.core.model.ExamCategoryTile
import com.sarkarimatch.core.model.Ist
import com.sarkarimatch.core.model.LocalizedText
import com.sarkarimatch.core.model.MatchCardUi
import com.sarkarimatch.core.model.Verdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

/** Open matches for the S20 feed. Real implementation (Room + MatchEngine) arrives with FE-13/FE-16. */
interface MatchRepository {
    fun observeFeed(): Flow<List<MatchCardUi>>
}

/**
 * Sample feed so the skeleton renders like Direction C (the same three jobs as docs/design/directions). Dates are
 * relative to today (IST) so the ribbons always show "today", "2 days left" and a calm card.
 */
class SampleMatchRepository @Inject constructor() : MatchRepository {
    override fun observeFeed(): Flow<List<MatchCardUi>> {
        val today = LocalDate.now(Ist.ZONE)
        return flowOf(
            listOf(
                MatchCardUi(
                    matchId = "m_sample_up_police",
                    title = LocalizedText.of("hi" to "UP Police कांस्टेबल भर्ती 2026", "en" to "UP Police Constable Recruitment 2026"),
                    organisation = LocalizedText.of("hi" to "UPPRPB", "en" to "UPPRPB"),
                    vacancies = 19_220,
                    tile = ExamCategoryTile.Police,
                    verdict = Verdict.ELIGIBLE,
                    feePaise = 40_000,
                    qualification = LocalizedText.of("hi" to "12वीं पास", "en" to "12th pass"),
                    lastDate = today,
                    lastTime = "23:00",
                    whyLine = LocalizedText.of("hi" to "उम्र: 23 (सीमा 22, OBC +5 = 27)", "en" to "Age: 23 (limit 22, OBC +5 = 27)"),
                ),
                MatchCardUi(
                    matchId = "m_sample_ssc_gd",
                    title = LocalizedText.of("hi" to "SSC GD कांस्टेबल भर्ती 2027", "en" to "SSC GD Constable Recruitment 2027"),
                    organisation = LocalizedText.of("hi" to "कर्मचारी चयन आयोग", "en" to "Staff Selection Commission"),
                    vacancies = 39_481,
                    tile = ExamCategoryTile.Ssc,
                    verdict = Verdict.ELIGIBLE,
                    feePaise = 10_000,
                    qualification = LocalizedText.of("hi" to "10वीं पास", "en" to "10th pass"),
                    lastDate = today.plusDays(2),
                    lastTime = null,
                    whyLine = LocalizedText.of("hi" to "उम्र: 23 (सीमा 23, OBC +3 = 26)", "en" to "Age: 23 (limit 23, OBC +3 = 26)"),
                ),
                MatchCardUi(
                    matchId = "m_sample_upsssc_ja",
                    title = LocalizedText.of("hi" to "UPSSSC जूनियर असिस्टेंट", "en" to "UPSSSC Junior Assistant"),
                    organisation = LocalizedText.of(
                        "hi" to "उ.प्र. अधीनस्थ सेवा चयन आयोग",
                        "en" to "UP Subordinate Services Selection Commission",
                    ),
                    vacancies = null,
                    tile = ExamCategoryTile.State,
                    verdict = Verdict.CHECK_YOURSELF,
                    feePaise = 2_500,
                    qualification = LocalizedText.of("hi" to "12वीं + CCC", "en" to "12th + CCC"),
                    lastDate = today.plusDays(21),
                    lastTime = null,
                    whyLine = LocalizedText.of(
                        "hi" to "CCC प्रमाणपत्र: आपकी प्रोफ़ाइल में नहीं है, ख़ुद जाँचें",
                        "en" to "CCC certificate: not in your profile, check yourself",
                    ),
                ),
            ),
        )
    }
}
