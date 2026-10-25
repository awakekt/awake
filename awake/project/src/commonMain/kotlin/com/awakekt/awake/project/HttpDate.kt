/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlin.time.Instant

/**
 * [text] as an HTTP date (RFC 9110, section 5.6.7), in the preferred IMF-fixdate form
 * (`Sun, 06 Nov 1994 08:49:37 GMT`) or either obsolete form a recipient must still accept: RFC 850
 * (`Sunday, 06-Nov-94 08:49:37 GMT`, a two-digit year below 70 read as 20xx) and asctime
 * (`Sun Nov  6 08:49:37 1994`). Null for anything else.
 */
internal fun parseHttpDate(text: String): Instant? {
    val parts = text.split(' ').filter { it.isNotEmpty() }
    return imfFixdate(parts) ?: rfc850Date(parts) ?: asctimeDate(parts)
}

/** An IMF-fixdate, such as `Sun, 06 Nov 1994 08:49:37 GMT`. */
private fun imfFixdate(parts: List<String>): Instant? {
    val shaped = parts.size == 6 && parts[5] == "GMT" && isWeekday(parts[0], SHORT_WEEKDAYS, comma = true)
    return if (shaped) instantOf(year = parts[3], month = parts[2], day = parts[1], time = parts[4]) else null
}

/** An RFC 850 date, such as `Sunday, 06-Nov-94 08:49:37 GMT`. */
private fun rfc850Date(parts: List<String>): Instant? {
    val date = parts.takeIf { it.size == 4 && it[3] == "GMT" && isWeekday(it[0], LONG_WEEKDAYS, comma = true) }
        ?.get(1)?.split('-')?.takeIf { it.size == 3 && it[2].length == 2 }
    return date?.let { (day, month, shortYear) ->
        digits(shortYear)?.let { short ->
            val year = short + if (short < RFC_850_PIVOT) CENTURY_21 else CENTURY_20
            instantOf(year = year.toString(), month = month, day = day, time = parts[2])
        }
    }
}

/** An asctime date, such as `Sun Nov  6 08:49:37 1994`. */
private fun asctimeDate(parts: List<String>): Instant? {
    val shaped = parts.size == 5 && isWeekday(parts[0], SHORT_WEEKDAYS, comma = false)
    return if (shaped) instantOf(year = parts[4], month = parts[1], day = parts[2], time = parts[3]) else null
}

private fun isWeekday(word: String, names: Set<String>, comma: Boolean): Boolean =
    word.endsWith(',') == comma && word.removeSuffix(",") in names

/** A date with a four-digit [year], a [month] name, a [day] of that month and an `HH:MM:SS` [time], in GMT. */
private fun instantOf(year: String, month: String, day: String, time: String): Instant? {
    val y = digits(year)?.takeIf { year.length == 4 }
    val m = MONTHS.indexOf(month) + 1
    val d = digits(day)?.takeIf { y != null && m > 0 && it in 1..daysIn(y, m) }
    val second = secondOfDay(time)
    if (y == null || d == null || second == null) return null
    return Instant.fromEpochSeconds(daysFromEpoch(y, m, d) * SECONDS_PER_DAY + second)
}

/** `HH:MM:SS` as seconds since midnight, allowing a leap second; null for anything else. */
private fun secondOfDay(time: String): Long? {
    val fields = time.split(':').takeIf { it.size == 3 && it.all { field -> field.length == 2 } }?.mapNotNull(::digits)
    val (hour, minute, second) = fields?.takeIf { it.size == 3 } ?: return null
    val valid = hour < HOURS_PER_DAY && minute < MINUTES_PER_HOUR && second <= LEAP_SECOND
    return if (valid) hour * SECONDS_PER_HOUR + minute * SECONDS_PER_MINUTE + second else null
}

/** [text] as a number when it is only ASCII digits, so no sign or space slips through. */
private fun digits(text: String): Int? = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toIntOrNull()

private fun daysIn(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** Days from 1970-01-01 to the given proleptic Gregorian date (Howard Hinnant's days_from_civil). */
private fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146_097 + dayOfEra - 719_468
}

private val SHORT_WEEKDAYS = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val LONG_WEEKDAYS = setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private const val RFC_850_PIVOT = 70
private const val CENTURY_20 = 1900
private const val CENTURY_21 = 2000
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val LEAP_SECOND = 60
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86_400L
