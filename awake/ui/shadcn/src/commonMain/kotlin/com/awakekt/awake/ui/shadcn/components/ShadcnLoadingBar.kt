/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * A loading screen's bar: a label, [ShadcnProgress] for how far the load has come, and when it
 * failed, the error and a retry button. It is the default view of a content load: an app maps its
 * loading state, such as `awake:project`'s `LoadingView`, onto these values, and supplies every text
 * itself, in its own language.
 *
 * - [fraction] from 0 to 1 fills the track. Null means the progress can't be measured, as while the
 *   load waits for sign-in or its source counts no bytes: the track stays empty and a
 *   [ShadcnSpinner] beside the label shows that something is still happening.
 * - [error], when not null, replaces the track with the message in the theme's destructive colour.
 *   With [onRetry] and [retryLabel] both given, an outline button below it retries; leave them out
 *   when a retry would fail the same way, as for content newer than the app.
 *
 * Laid out as `flex flex-col gap-2 w-full`, with the label row `flex items-center gap-2`.
 *
 * ```kotlin
 * val view = stages.state.value.loadingView()
 * ShadcnLoadingBar(
 *     label = strings[phaseKey(view.phase)],
 *     fraction = view.fraction,
 *     error = view.error?.let { strings["loading.failed"] },
 *     retryLabel = strings["loading.retry"],
 *     onRetry = if (view.canRetry) ::retry else null,
 * )
 * ```
 */
context(_: Composer)
fun ShadcnLoadingBar(
    label: String,
    fraction: Float?,
    modifier: Modifier = Modifier,
    error: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            if (error == null && fraction == null) ShadcnSpinner()
            ShadcnText(label, variant = ShadcnTextVariant.Small)
        }
        if (error == null) {
            ShadcnProgress(fraction ?: 0f)
        } else {
            ShadcnText(error, variant = ShadcnTextVariant.Small, color = shadcnTheme.palette.destructive)
            val retry = onRetry
            if (retry != null && retryLabel != null) {
                ShadcnButton(retryLabel, variant = ShadcnButtonVariant.Outline, onClick = retry)
            }
        }
    }
}
