/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.runtime

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

/**
 * A value supplied by an ancestor and read by any descendant, without threading it through every
 * call in between -- theme, density, text style.
 *
 * Scoped to composition rather than attached to nodes: a provider is a window in the pass, and a
 * read resolves against whatever is open. The removed `ui-core`'s equivalent was a push/pop pair on
 * `UiContext`, which leaked its value if the content between them threw; [CompositionLocalProvider] cannot.
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

/**
 * Creates a [CompositionLocal] whose value, where no ancestor provides one, is [default]: computed
 * on the first such read and reused after.
 */
fun <T> compositionLocalOf(default: () -> T): CompositionLocal<T> = CompositionLocal(default)

/**
 * The providers open at this point in the pass, as alternating local/value pairs.
 *
 * A flat list rather than a map of stacks: reads scan from the innermost outward, realistic nesting
 * is a handful deep, so a scan beats a map lookup and allocates nothing.
 */
class ProvidedLocals internal constructor(initialEntries: List<Any?> = emptyList()) {
    /** Creates an empty local table for a host to populate and update in place across passes. */
    constructor() : this(emptyList())

    private val entries = ArrayList<Any?>(initialEntries)

    /** A copy of the providers open now, for a composition that starts here, such as a subcomposition. */
    fun snapshot(): ProvidedLocals = ProvidedLocals(entries)

    /**
     * Makes these the providers [from] holds, or none for null, when they differ (#1774): a
     * composition started from its parent's locals takes the parent's current ones before each pass.
     * Equal providers are left as they are, so an unchanged pass copies and allocates nothing. Call it
     * only between passes, while nothing is open here.
     */
    internal fun resetTo(from: ProvidedLocals?) {
        val source = from?.entries
        if (source == null) {
            entries.clear()
        } else if (!entries.sameEntriesAs(source)) {
            entries.clear()
            for (i in source.indices) entries.add(source[i])
        }
    }

    @PublishedApi
    internal fun push(local: CompositionLocal<*>, value: Any?) {
        entries.add(local)
        entries.add(value)
    }

    @PublishedApi
    internal fun pushAll(values: Array<out ProvidedValue<*>>) {
        for (i in values.indices) {
            entries.add(values[i].local)
            entries.add(values[i].value)
        }
    }

    @PublishedApi
    internal fun pushAll(from: ProvidedLocals): Int {
        val source = from.entries
        val count = source.size
        for (i in 0 until count) entries.add(source[i])
        return count
    }

    @PublishedApi
    internal fun popEntries(count: Int) {
        repeat(count) { entries.removeAt(entries.lastIndex) }
    }

    /** Replaces the stored value for [local] in place if present, or appends a new binding. */
    fun <T> set(local: CompositionLocal<T>, value: T) {
        var i = entries.size - 2
        while (i >= 0) {
            if (entries[i] === local) {
                entries[i + 1] = value
                return
            }
            i -= 2
        }
        entries.add(local)
        entries.add(value)
    }

    /** Opens [local] = [value] for [content], and closes it when [content] returns or throws. */
    fun <T> provide(local: CompositionLocal<T>, value: T, content: () -> Unit) {
        push(local, value)
        try {
            content()
        } finally {
            popEntries(2)
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
        pushAll(values)
        try {
            content()
        } finally {
            popEntries(values.size * 2)
        }
    }

    /**
     * Opens every value [from] holds open, over the ones open here, and closes them all together.
     *
     * It exists to re-provide a [snapshot] across compositions: content declared in one composition
     * and composed in another, such as Compose UI declared in canvas content and composed where the
     * canvas draws, still reads the locals that were open where it was declared. Values here that
     * [from] does not provide stay readable; those it does provide are shadowed while [content] runs.
     */
    fun provideAll(from: ProvidedLocals, content: () -> Unit) {
        val count = pushAll(from)
        try {
            content()
        } finally {
            popEntries(count)
        }
    }

    /** The innermost value provided for [local], or its default when nothing provides one. */
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

private fun List<Any?>.sameEntriesAs(other: List<Any?>): Boolean {
    var same = other.size == size
    var i = 0
    while (same && i < size) {
        same = this[i] == other[i]
        i++
    }
    return same
}

context(composer: Composer)
/**
 * Reads the nearest provided value, or the local's default if nothing provided one.
 *
 * A property, not a function, because that is Compose's own shape -- `LocalDensity.current` is
 * probably the most-typed line in the whole API, and a pair of parens on it is exactly the
 * gratuitous difference `11-refinements.md` rule 1 exists to prevent.
 */
val <T> CompositionLocal<T>.current: T get() = composer.locals.read(this)

context(composer: Composer)
/**
 * Provides [value] for [local] to everything [content] declares.
 *
 * The window closes when [content] returns, including on a throw -- which is the failure mode a
 * hand-written push/pop pair has and this does not.
 */
inline fun <T> CompositionLocalProvider(
    local: CompositionLocal<T>,
    value: T,
    content: context(Composer) () -> Unit,
) {
    composer.locals.push(local, value)
    try {
        content(composer)
    } finally {
        composer.locals.popEntries(2)
    }
}

/** One local bound to one value, as produced by [provides]. */
class ProvidedValue<T> internal constructor(
    @PublishedApi
    internal val local: CompositionLocal<T>,
    @PublishedApi
    internal val value: T,
)

/** `LocalDensity provides 2f` -- Compose's own spelling. */
infix fun <T> CompositionLocal<T>.provides(value: T): ProvidedValue<T> = ProvidedValue(this, value)

context(composer: Composer)
/**
 * Provides a single [value] bound with [provides] to [content] without allocating a vararg array.
 *
 * Mirrors `androidx.compose.runtime.CompositionLocalProvider(value: ProvidedValue<*>, content)`.
 */
inline fun CompositionLocalProvider(
    value: ProvidedValue<*>,
    content: context(Composer) () -> Unit,
) {
    composer.locals.push(value.local, value.value)
    try {
        content(composer)
    } finally {
        composer.locals.popEntries(2)
    }
}

context(composer: Composer)
/**
 * Provides several locals to the same [content].
 *
 * Compose's shape, and it earns itself the moment a caller needs more than one: nesting a provider
 * per local puts the reader's eye three indents deep to learn that three unrelated values were set
 * together, and it grows a level every time a new local appears.
 */
inline fun CompositionLocalProvider(
    vararg values: ProvidedValue<*>,
    content: context(Composer) () -> Unit,
) {
    composer.locals.pushAll(values)
    try {
        content(composer)
    } finally {
        composer.locals.popEntries(values.size * 2)
    }
}

context(composer: Composer)
/**
 * Runs [content] with every value [locals] holds open provided over the ones open here.
 *
 * For content that one composition declares and another composes: take
 * [Composer.localsSnapshot] where the content is declared, and provide it here where it is
 * composed, so the content reads the locals of the place it was written, over this composition's
 * own. Within one composition, the [ProvidedValue] overloads are the ones to use.
 */
inline fun CompositionLocalProvider(
    locals: ProvidedLocals,
    content: context(Composer) () -> Unit,
) {
    val count = composer.locals.pushAll(locals)
    try {
        content(composer)
    } finally {
        composer.locals.popEntries(count)
    }
}
