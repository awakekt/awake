/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw

/**
 * Type-safe date representation for [ShadcnCalendar] and [ShadcnDatePicker].
 *
 * Stored as pure civil integers (`year`, `month`, `day`), making it **100% immune**
 * to timezone shifts, Daylight Saving Time (DST) transitions, and wall-clock drift.
 *
 * @param year Year number (e.g. 2026).
 * @param month Month number (1..12, 1 = January).
 * @param day Day of month number (1..31).
 */
data class ShadcnCalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    /** Formatted short string representation (e.g., "Oct 15, 2026"). */
    fun toShortString(): String {
        val monthName = MonthNames.getOrElse((month - 1).coerceIn(0, 11)) { "Oct" }.take(3)
        return "$monthName $day, $year"
    }

    /** Converts this date to UTC epoch milliseconds (at 00:00:00 UTC). */
    fun toEpochMillis(): Long {
        var days = 0L
        for (y in 1970 until year) {
            days += if (isLeapYear(y)) 366 else 365
        }
        for (m in 1 until month) {
            days += daysInMonth(m, year)
        }
        days += (day - 1)
        return days * 86_400_000L
    }

    companion object {
        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        fun daysInMonth(month: Int, year: Int): Int = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        /** Returns weekday index for 1st day of month (0 = Sunday, 1 = Monday, ..., 6 = Saturday). */
        fun firstDayWeekday(year: Int, month: Int): Int {
            val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
            var y = year
            if (month < 3) y -= 1
            return (y + y / 4 - y / 100 + y / 400 + t[(month - 1).coerceIn(0, 11)] + 1) % 7
        }

        /** Creates a [ShadcnCalendarDate] from UTC epoch milliseconds. */
        fun fromEpochMillis(epochMillis: Long): ShadcnCalendarDate {
            var days = (epochMillis / 86_400_000L).coerceAtLeast(0L)
            var y = 1970
            while (true) {
                val yearDays = if (isLeapYear(y)) 366 else 365
                if (days < yearDays) break
                days -= yearDays
                y++
            }
            var m = 1
            while (m <= 12) {
                val dim = daysInMonth(m, y)
                if (days < dim) break
                days -= dim
                m++
            }
            val d = (days + 1).toInt()
            return ShadcnCalendarDate(y, m, d)
        }
    }
}

/**
 * Type-safe date range selection representation for [ShadcnCalendar].
 *
 * @param start Start date of the range.
 * @param end End date of the range.
 */
data class ShadcnDateRange(
    val start: ShadcnCalendarDate? = null,
    val end: ShadcnCalendarDate? = null,
)

private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val WeekdayHeaders = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/**
 * `ShadcnCalendar`: A month grid view calendar component for single date and date range selection.
 *
 * **Tailwind Reference**: `p-3 w-fit rounded-md border bg-popover text-popover-foreground shadow-md`.
 *
 * Uses pure civil date math, making it 100% immune to DST shifts and timezone bugs.
 *
 * @param selectedDate Currently selected [ShadcnCalendarDate].
 * @param onDateSelected Callback triggered when a date is selected.
 * @param initialMonth Month index (1..12, default 10 = October).
 * @param initialYear Initial display year (default 2026).
 * @param modifier Custom layout modifier applied to the calendar container.
 *
 * Keywords: calendar, date picker, month grid, date selector, scheduling.
 */
context(_: Composer)
fun ShadcnCalendar(
    selectedDate: ShadcnCalendarDate?,
    onDateSelected: (ShadcnCalendarDate) -> Unit,
    modifier: Modifier = Modifier,
    initialMonth: Int = 10,
    initialYear: Int = 2026,
) {
    val currentMonthState = remember { CalendarIntState(initialMonth.coerceIn(1, 12)) }
    val currentYearState = remember { CalendarIntState(initialYear) }

    val month = currentMonthState.value
    val year = currentYearState.value
    val monthTitle = "${MonthNames.getOrElse(month - 1) { "October" }} $year"
    val totalDays = ShadcnCalendarDate.daysInMonth(month, year)
    val startWeekdayOffset = ShadcnCalendarDate.firstDayWeekday(year, month)

    ShadcnCard(modifier = modifier.width(280.dp), contentPadding = Tw.Spacing.s3) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            // Header: Month title and month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnButton(
                    label = "<",
                    variant = ShadcnButtonVariant.Outline,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = {
                        if (currentMonthState.value == 1) {
                            currentMonthState.value = 12
                            currentYearState.value--
                        } else {
                            currentMonthState.value--
                        }
                    },
                )
                ShadcnText(monthTitle, variant = ShadcnTextVariant.Small)
                ShadcnButton(
                    label = ">",
                    variant = ShadcnButtonVariant.Outline,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = {
                        if (currentMonthState.value == 12) {
                            currentMonthState.value = 1
                            currentYearState.value++
                        } else {
                            currentMonthState.value++
                        }
                    },
                )
            }

            ShadcnSeparator()

            // Days of Week Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            ) {
                WeekdayHeaders.forEach { day ->
                    Box(
                        modifier = Modifier.size(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        shadcnMuted(day)
                    }
                }
            }

            // Month Grid
            Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                var currentDay = 1
                for (week in 0..5) {
                    if (currentDay > totalDays) break
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                    ) {
                        for (dayOfWeek in 0..6) {
                            val isOffsetCell = week == 0 && dayOfWeek < startWeekdayOffset
                            if (isOffsetCell || currentDay > totalDays) {
                                Box(modifier = Modifier.size(32.dp))
                            } else {
                                val dayNumber = currentDay
                                val cellDate = ShadcnCalendarDate(year, month, dayNumber)
                                val isSelected = cellDate == selectedDate
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { onDateSelected(cellDate) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isSelected) {
                                        ShadcnBadge(label = dayNumber.toString())
                                    } else {
                                        ShadcnText(dayNumber.toString(), variant = ShadcnTextVariant.Small)
                                    }
                                }
                                currentDay++
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convenience overload accepting raw UTC epoch milliseconds for [ShadcnCalendar].
 */
context(_: Composer)
fun ShadcnCalendar(
    selectedEpochMillis: Long?,
    onEpochMillisSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialMonth: Int = 10,
    initialYear: Int = 2026,
) {
    val selectedDate = selectedEpochMillis?.let { ShadcnCalendarDate.fromEpochMillis(it) }
    ShadcnCalendar(
        selectedDate = selectedDate,
        onDateSelected = { date -> onEpochMillisSelected(date.toEpochMillis()) },
        modifier = modifier,
        initialMonth = initialMonth,
        initialYear = initialYear,
    )
}

/**
 * `ShadcnDatePicker`: Controlled date picker popover input button with popover calendar view.
 *
 * @param selectedDate Currently selected [ShadcnCalendarDate].
 * @param onDateSelected Callback triggered when selecting a date.
 * @param placeholder Input trigger text when no date is selected.
 * @param modifier Custom layout modifier.
 *
 * Keywords: date picker, date input, calendar popover.
 */
context(_: Composer)
fun ShadcnDatePicker(
    selectedDate: ShadcnCalendarDate?,
    onDateSelected: (ShadcnCalendarDate) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pick a date",
) {
    val popoverState = remember { BooleanState(false) }
    val labelText = selectedDate?.toShortString() ?: placeholder

    Box(modifier = modifier) {
        ShadcnButton(
            label = labelText,
            variant = ShadcnButtonVariant.Outline,
            onClick = { popoverState.value = !popoverState.value },
        )

        if (popoverState.value) {
            ShadcnCalendar(
                selectedDate = selectedDate,
                onDateSelected = { date ->
                    onDateSelected(date)
                    popoverState.value = false
                },
                modifier = Modifier.padding(top = 40.dp),
            )
        }
    }
}

/**
 * Convenience overload accepting raw UTC epoch milliseconds for [ShadcnDatePicker].
 */
context(_: Composer)
fun ShadcnDatePicker(
    selectedEpochMillis: Long?,
    onEpochMillisSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pick a date",
) {
    val selectedDate = selectedEpochMillis?.let { ShadcnCalendarDate.fromEpochMillis(it) }
    ShadcnDatePicker(
        selectedDate = selectedDate,
        onDateSelected = { date -> onEpochMillisSelected(date.toEpochMillis()) },
        modifier = modifier,
        placeholder = placeholder,
    )
}

private class CalendarIntState(var value: Int)
private class BooleanState(var value: Boolean)
