/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.theme

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocal
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.compositionLocalOf
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues

/**
 * The shadcn theme, reachable from a `:compose:foundation` recipe.
 *
 * The first piece of Stage 3's port, and a prerequisite for all of it: every recipe reads the theme,
 * so until it arrives through a `CompositionLocal` no recipe can shed its `ui-core` import and
 * `port_progress.py` cannot move off zero.
 *
 * **Deliberately alongside, not replacing.** `awake.ui.designsystem.LocalShadcnTheme` is a `UiLocal`
 * and stays until `ui-core` goes; the two coexist while recipes move one at a time, which is the
 * whole reason the engine was built beside `ui-core` rather than inside it. The values are the same
 * [ShadcnThemeValues] either way -- this is a second door to one room, not a second room.
 *
 * The different package is what lets both keep the name they should have.
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
 * exactly the call sites that use both. `ui-core`'s own `provideTheme`/`Provide` naming is the
 * precedent.
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
