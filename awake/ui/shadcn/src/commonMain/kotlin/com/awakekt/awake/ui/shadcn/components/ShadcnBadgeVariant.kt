/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

/**
 * shadcn's badge variants, named as `badge.tsx` names them.
 *
 * Upstream's first entry is `default`, not `primary` -- the old recipe called it `Primary`, which
 * reads as a colour rather than as the variant it is. Six here because upstream has six; the old
 * recipe and `shadcn-compose` both stop short of `Link`.
 */
enum class ShadcnBadgeVariant {
    /** Translates `bg-primary text-primary-foreground`. */
    Default,

    /** Translates `bg-secondary text-secondary-foreground`. */
    Secondary,

    /** `bg-destructive text-white` -- white literally, see [ShadcnBadge]. */
    Destructive,

    /** `border-border text-foreground`, no fill. */
    Outline,

    /** No fill and no border; colour comes from hover only. */
    Ghost,

    /** `text-primary underline-offset-4` -- reads as a link, carries no chrome. */
    Link,
}
