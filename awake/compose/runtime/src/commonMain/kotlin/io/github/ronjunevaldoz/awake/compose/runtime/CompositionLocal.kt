// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.ronjunevaldoz.awake.compose.runtime

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

/**
 * A value supplied by an ancestor and read by any descendant, without threading it through every
 * call in between -- theme, density, text style.
 *
 * Scoped to composition rather than attached to nodes: a provider is a window in the pass, and a
 * read resolves against whatever is open. `ui-core`'s equivalent is a push/pop pair on `UiContext`,
 * which leaks its value if the content between them throws; [CompositionLocalProvider] cannot.
 *
 * Note for Stage 2: once a subtree can recompose on its own, provided values have to live on the
 * node instead, because that subtree's pass will not start at the root. Nothing here assumes
 * otherwise -- reads go through the composer either way.
 */
class CompositionLocal<T> internal constructor(private val defaultFactory: () -> T) {
    private var cachedDefault: Any? = Unset

    /** Computed once. A default that allocates must not allocate again on every read. */
    internal fun defaultValue(): T {
        if (cachedDefault === Unset) cachedDefault = defaultFactory()
        @Suppress("UNCHECKED_CAST")
        return cachedDefault as T
    }

    private object Unset
}

fun <T> compositionLocalOf(default: () -> T): CompositionLocal<T> = CompositionLocal(default)

/**
 * The providers open at this point in the pass, as alternating local/value pairs.
 *
 * A flat list rather than a map of stacks: reads scan from the innermost outward, realistic nesting
 * is a handful deep, so a scan beats a map lookup and allocates nothing.
 */
internal class ProvidedLocals {
    private val entries = ArrayList<Any?>()

    fun <T> provide(local: CompositionLocal<T>, value: T, content: () -> Unit) {
        entries.add(local)
        entries.add(value)
        try {
            content()
        } finally {
            entries.removeAt(entries.lastIndex)
            entries.removeAt(entries.lastIndex)
        }
    }

    /**
     * Opens every pair at once, and closes them all together.
     *
     * One window rather than [values]`.size` nested ones, so a later entry shadowing an earlier one
     * in the same call resolves the way [read] already resolves nesting -- innermost wins, and
     * within a single call the last one listed is the innermost.
     */
    fun provideAll(values: Array<out ProvidedValue<*>>, content: () -> Unit) {
        for (i in values.indices) {
            entries.add(values[i].local)
            entries.add(values[i].value)
        }
        try {
            content()
        } finally {
            repeat(values.size) {
                entries.removeAt(entries.lastIndex)
                entries.removeAt(entries.lastIndex)
            }
        }
    }

    fun <T> read(local: CompositionLocal<T>): T {
        var i = entries.size - 2
        while (i >= 0) {
            if (entries[i] === local) {
                @Suppress("UNCHECKED_CAST")
                return entries[i + 1] as T
            }
            i -= 2
        }
        return local.defaultValue()
    }
}

/**
 * Reads the nearest provided value, or the local's default if nothing provided one.
 *
 * A property, not a function, because that is Compose's own shape -- `LocalDensity.current` is
 * probably the most-typed line in the whole API, and a pair of parens on it is exactly the
 * gratuitous difference `11-refinements.md` rule 1 exists to prevent.
 */
context(composer: Composer)
val <T> CompositionLocal<T>.current: T get() = composer.locals.read(this)

/**
 * Provides [value] for [local] to everything [content] declares.
 *
 * The window closes when [content] returns, including on a throw -- which is the failure mode a
 * hand-written push/pop pair has and this does not.
 */
context(composer: Composer)
fun <T> CompositionLocalProvider(
    local: CompositionLocal<T>,
    value: T,
    content: context(Composer) () -> Unit,
) {
    composer.locals.provide(local, value) { content(composer) }
}

/** One local bound to one value, as produced by [provides]. */
class ProvidedValue<T> internal constructor(
    internal val local: CompositionLocal<T>,
    internal val value: T,
)

/** `LocalDensity provides 2f` -- Compose's own spelling. */
infix fun <T> CompositionLocal<T>.provides(value: T): ProvidedValue<T> = ProvidedValue(this, value)

/**
 * Provides several locals to the same [content].
 *
 * Compose's shape, and it earns itself the moment a caller needs more than one: nesting a provider
 * per local puts the reader's eye three indents deep to learn that three unrelated values were set
 * together, and it grows a level every time a new local appears.
 */
context(composer: Composer)
fun CompositionLocalProvider(
    vararg values: ProvidedValue<*>,
    content: context(Composer) () -> Unit,
) {
    composer.locals.provideAll(values) { content(composer) }
}
