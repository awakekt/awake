/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.theme

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues

/**
 * The shadcn theme, reachable from a `:compose:foundation` recipe.
 *
 * Every recipe reads the theme through this `CompositionLocal`. It was the first piece of the port
 * off the removed immediate-mode UI layer, because no recipe could move before the theme did.
 * The values are [ShadcnThemeValues]; [LocalShadcnTheme] is only how a recipe reaches them.
 */
val LocalShadcnTheme: CompositionLocal<ShadcnThemeValues?> = compositionLocalOf { null }

/**
 * The theme in scope, or a failure naming the fix.
 *
 * A property rather than a function, for the reason `CompositionLocal.current` is one: it is among
 * the most-typed expressions in a recipe, and a pair of parens on it is the gratuitous difference
 * `11-refinements.md` rule 1 exists to prevent. Compose spells `MaterialTheme.colorScheme` the same
 * way.
 *
 * **Compared against `shadcn-compose`'s `ShadcnTheme.current`, and differing on purpose in one
 * place.** Theirs is a `staticCompositionLocalOf` with a *non-null* default -- a complete fallback
 * theme, described as protecting the canvas engine -- so reading it without a provider silently
 * succeeds.
 *
 * Awake throws instead, and that is a decision this module already made and wrote down: "There is no
 * Core-theme fallback: a missing scope is an error, so metrics and branded roles can never be
 * silently reconstructed from a generic Core theme." A fallback renders *something*, so the missing
 * provider surfaces as wrong branding somewhere else entirely rather than at the call that forgot it.
 *
 * The other difference is cosmetic and forced: theirs namespaces access as `ShadcnTheme.current`,
 * matching Compose's `MaterialTheme.colorScheme`, which is the nicer shape. That name is already
 * taken here by the default-theme object, so this is a bare property until that is untangled.
 *
 * No fallback to a neutral theme. `awake-ui-authoring` makes that a rule for a reason: a recipe that
 * silently reconstructs a generic theme renders *something*, so the missing provider is invisible
 * until someone notices the branding is wrong, which is far from where the mistake is.
 */
context(_: Composer)
val shadcnTheme: ShadcnThemeValues get() = requireNotNull(LocalShadcnTheme.current) {
    "No shadcn theme in scope. Wrap this content in provideShadcnTheme { } -- every shadcn* recipe needs it."
}

/**
 * Installs [values] for [content].
 *
 * `provideShadcnTheme`, not `ShadcnTheme`: the latter is already the default-theme object in the
 * parent package, and a function and an object sharing a name across two packages is ambiguous at
 * exactly the call sites that use both.
 */
context(_: Composer)
fun provideShadcnTheme(
    values: ShadcnThemeValues,
    content: context(Composer) () -> Unit,
) {
    CompositionLocalProvider(
        LocalShadcnTheme provides values,
        // `text-foreground` on the body, which every component inherits unless it says otherwise.
        // Without it text falls through to the engine's own default -- a light grey chosen to read
        // on a dark canvas -- so a light-themed radio label or popover body came out near-invisible.
        // A per-recipe colour would fix each place it was noticed and leave the rest.
        LocalTextStyle provides LocalTextStyle.current.copy(color = values.palette.foreground),
        content = content,
    )
}
