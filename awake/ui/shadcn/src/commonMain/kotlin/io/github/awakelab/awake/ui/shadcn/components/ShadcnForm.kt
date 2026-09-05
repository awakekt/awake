/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * `ShadcnFormField`: A form field row combining label, input control, helper text, and validation error message.
 *
 * **Tailwind Reference**: `space-y-1.5`.
 *
 * Use cases:
 * - Form input fields with validation messages (e.g. Email, Password, Username).
 * - Helper text display for form fields.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnFormField(
 *     label = "Email Address",
 *     description = "We will never share your email.",
 *     error = emailError,
 * ) {
 *     ShadcnInput(state = emailState, placeholder = "m@example.com")
 * }
 * ```
 *
 * @param label Field title label.
 * @param modifier Custom layout modifier applied to the field row.
 * @param description Helper text displayed below the field when no error is present.
 * @param error Validation error message displayed in destructive red text when present.
 * @param content The input control slot (e.g. `ShadcnInput`, `ShadcnSelect`, `ShadcnTextarea`).
 *
 * Keywords: field, form field, label, error, validation, helper text, input wrapper.
 */
context(_: Composer)
fun ShadcnFormField(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1_5),
    ) {
        ShadcnText(label, variant = ShadcnTextVariant.Small)
        if (content != null) {
            content()
        }
        if (error != null) {
            ShadcnText(error, variant = ShadcnTextVariant.Xs, color = theme.palette.destructive)
        } else if (description != null) {
            shadcnMuted(description)
        }
    }
}

/**
 * `ShadcnForm`: A form container organizing form fields and submit actions.
 *
 * **Tailwind Reference**: `space-y-4`.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnForm(onSubmit = { handleFormSubmit() }) {
 *     ShadcnFormField(label = "Username") { ShadcnInput(state = nameState) }
 *     ShadcnButton("Submit")
 * }
 * ```
 *
 * @param modifier Custom layout modifier applied to the form container.
 * @param onSubmit Callback triggered when submitting the form.
 * @param content Slot for form fields and submit buttons.
 *
 * Keywords: form, form container, form validation, submit gating.
 */
context(_: Composer)
fun ShadcnForm(
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s4),
    ) {
        content?.let { it() }
    }
}
