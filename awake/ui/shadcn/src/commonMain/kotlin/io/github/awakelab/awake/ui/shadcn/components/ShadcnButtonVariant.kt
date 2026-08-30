/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

/**
 * shadcn's button variants, named as `button.tsx` names them.
 *
 * The hover alphas are not uniform and that is upstream, not a rounding: `default`, `destructive`
 * and `link` hover at `/90`, **`secondary` at `/80`**. Writing one constant for all of them is the
 * kind of tidying that silently changes a design.
 */
enum class ShadcnButtonVariant {
    /** Translates `bg-primary text-primary-foreground hover:bg-primary/90`. */
    Default,

    /** Translates `bg-destructive text-white hover:bg-destructive/90`. */
    Destructive,

    /** Translates `border bg-background hover:bg-accent hover:text-accent-foreground`. */
    Outline,

    /** Translates `bg-secondary text-secondary-foreground hover:bg-secondary/80`. */
    Secondary,

    /** Translates `hover:bg-accent hover:text-accent-foreground`, no fill at rest. */
    Ghost,

    /** Translates `text-primary underline-offset-4 hover:underline`, no chrome. */
    Link,
    ;

    /**
     * Whether the variant draws a border, and therefore reserves layout space for one.
     *
     * shadcn is `border-box`: the border sits inside the declared width, so a bordered button is
     * wider than the same label unbordered rather than the content being squeezed. Awake's border
     * paints without reserving, so the recipe folds it into the inset -- see `ShadcnButton`.
     */
    internal val bordered: Boolean get() = this == Outline
}
