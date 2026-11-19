package com.sarkarimatch.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number

/**
 * Dates, numbers and money for display. Always Western digits, Indian grouping, IST dates. Pure Kotlin (no
 * java.text / ICU / NSDateFormatter) so the output is identical on Android, iOS and in unit tests. Month names exist
 * for the launch languages (hi, en); other languages fall back to English until their strings are reviewed.
 */
object SmFormat {
    private val monthsEn = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val monthsHi = listOf(
        "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
        "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
    )
    private val monthsHiShort = listOf(
        "जन", "फ़र", "मार्च", "अप्रैल", "मई", "जून",
        "जुल", "अग", "सित", "अक्टू", "नवं", "दिस",
    )

    private fun month(date: LocalDate, language: AppLanguage, short: Boolean): String {
        val i = date.month.number - 1
        return when (language) {
            AppLanguage.HI -> if (short) monthsHiShort[i] else monthsHi[i]
            else -> if (short) monthsEn[i].take(3) else monthsEn[i]
        }
    }

    /** "24 अगस्त 2026" / "24 Aug 2026". */
    fun longDate(date: LocalDate, language: AppLanguage): String =
        "${date.day} ${month(date, language, short = language == AppLanguage.EN)} ${date.year}"

    /** "21 नवं" / "21 Nov". */
    fun shortDate(date: LocalDate, language: AppLanguage): String = "${date.day} ${month(date, language, short = true)}"

    /** "₹100", "₹1,00,000" — no decimals. */
    fun rupees(paise: Long): String = "₹" + amount(paise)

    /** Plain amount without the symbol (the card shows a ₹ icon). */
    fun amount(paise: Long): String = indianGrouping(paise / 100)

    fun count(n: Int): String = indianGrouping(n.toLong())

    /** Indian digit grouping (1,00,000), done by hand so every platform prints the same. */
    fun indianGrouping(value: Long): String {
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()
        if (digits.length <= 3) return if (negative) "-$digits" else digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return (if (negative) "-" else "") + rest + "," + last3
    }

    /** 12-hour clock hour for "रात 11 बजे" / "11 pm" templates. */
    fun hour12(time: LocalTime): Int = (time.hour % 12).let { if (it == 0) 12 else it }
}
