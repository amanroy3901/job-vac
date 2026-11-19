package com.sarkarimatch.core.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates, numbers and money for display. Always Western digits (`-u-nu-latn`), Indian grouping, IST dates. */
object SmFormat {
    fun locale(language: AppLanguage): Locale = Locale.forLanguageTag("${language.tag}-IN-u-nu-latn")

    /** "24 अगस्त 2026" / "24 Aug 2026". */
    fun longDate(date: LocalDate, language: AppLanguage): String {
        val pattern = if (language == AppLanguage.EN) "d MMM yyyy" else "d MMMM yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale(language)))
    }

    /** "21 नवं" / "21 Nov". */
    fun shortDate(date: LocalDate, language: AppLanguage): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", locale(language))).removeSuffix(".")

    /** "₹100", "₹1,00,000" — no decimals. */
    fun rupees(paise: Long): String = "₹" + amount(paise)

    /** Plain amount without the symbol (the card shows a ₹ icon). */
    fun amount(paise: Long): String = indianGrouping(paise / 100)

    fun count(n: Int): String = indianGrouping(n.toLong())

    /**
     * Indian digit grouping (1,00,000), done by hand: java.text on the JVM groups in threes, Android's ICU does not,
     * and the result must be identical in unit tests and on devices.
     */
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
