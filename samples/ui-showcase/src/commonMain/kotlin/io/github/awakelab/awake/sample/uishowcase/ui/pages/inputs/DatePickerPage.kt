/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCalendar
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCalendarDate
import io.github.awakelab.awake.ui.shadcn.components.ShadcnDatePicker
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText

internal val DatePickerPage = ShowcasePage(
    id = "date-picker",
    title = "Date Picker",
    category = ShowcaseCategory.Inputs,
    description = "A calendar popover component for selecting a single date or date range.",
    usageCode = """
ShadcnDatePicker(
    selectedDate = date,
    onDateSelected = { date = it }
)
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/date-picker-demo.tsx",
    previewHeight = 450,
    hero = {
        val selectedDateState = remember { NullableDateState(ShadcnCalendarDate(2026, 10, 15)) }

        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s4)) {
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s4)) {
                ShadcnDatePicker(
                    selectedDate = selectedDateState.value,
                    onDateSelected = { selectedDateState.value = it },
                )
            }

            ShadcnText("Calendar Grid View:")

            ShadcnCalendar(
                selectedDate = selectedDateState.value,
                onDateSelected = { selectedDateState.value = it },
            )
        }
    },
)

private class NullableDateState(var value: ShadcnCalendarDate?)
