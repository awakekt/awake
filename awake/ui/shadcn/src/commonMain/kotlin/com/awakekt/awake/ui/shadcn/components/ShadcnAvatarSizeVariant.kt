/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp

/**
 * shadcn's avatar sizes.
 *
 * `Default` is **`size-8` -- 32dp**. `awake-ui-authoring` cites this component as evidence that a
 * `ui-headless` 40dp default propagated upward, quoting a comment about matching "this module's
 * pre-existing 40dp default" rather than shadcn. That was fixed at some point and the skill was not
 * updated: the existing recipe already reads 24/32/40 for sm/default/lg, which is exactly
 * `size-6`/`size-8`/`size-10`. The lesson stands; the example is stale.
 *
 * The fallback's type shrinks with the avatar -- `text-sm`, and `text-xs` at `sm`.
 */
enum class ShadcnAvatarSizeVariant(internal val size: Dp, internal val text: ShadcnTextVariant) {
    /** Translates `size-6` with `text-xs`. */
    Sm(24.dp, ShadcnTextVariant.Muted),

    /** Translates `size-8` with `text-sm`. */
    Default(32.dp, ShadcnTextVariant.Small),

    /** Translates `size-10` with `text-sm`. */
    Lg(40.dp, ShadcnTextVariant.Small),
}
