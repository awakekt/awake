/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnCalendar
import com.awakekt.awake.ui.shadcn.components.ShadcnCalendarDate
import com.awakekt.awake.ui.shadcn.components.ShadcnDatePicker
import com.awakekt.awake.ui.shadcn.components.ShadcnText

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
