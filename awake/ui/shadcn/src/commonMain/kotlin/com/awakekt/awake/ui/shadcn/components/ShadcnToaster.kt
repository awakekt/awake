/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.widthIn
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.layout.LayerPosition
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw

/** One queued toast. [remainingSeconds] counts down while it is on screen. */
class ShadcnToastEntry internal constructor(
    val key: Long,
    val message: String,
    val title: String?,
    internal var remainingSeconds: Float,
)

/**
 * The queue behind [ShadcnToaster], owned by the app rather than by a call site.
 *
 * A toast outlives whatever raised it -- the button that fired one is usually gone by the time it
 * fades -- so the queue cannot live in the raising composable's `remember`. Hold one of these
 * wherever the app's state lives and pass it to a single toaster near the root.
 *
 * Not thread-safe. It is read and written during composition, on the frame loop's own thread, which
 * is where every other piece of UI state here lives too.
 */
class ShadcnToastState {
    private val entries = mutableListOf<ShadcnToastEntry>()
    private var nextKey = 0L

    /** Queues a toast. [durationSeconds] is how long it stays up once shown. */
    fun show(
        message: String,
        title: String? = null,
        durationSeconds: Float = DEFAULT_TOAST_SECONDS,
    ): Long {
        require(durationSeconds > 0f) { "durationSeconds must be positive, was $durationSeconds" }
        val key = nextKey++
        entries += ShadcnToastEntry(key, message, title, durationSeconds)
        // Oldest first: a burst of toasts must not push the newest off the screen it just arrived on.
        while (entries.size > MAX_VISIBLE_TOASTS) entries.removeAt(0)
        return key
    }

    fun dismiss(key: Long) {
        entries.removeAll { it.key == key }
    }

    fun clear() = entries.clear()

    /** What is on screen right now. */
    val visible: List<ShadcnToastEntry> get() = entries

    /**
     * Ages every toast by one frame and drops the expired.
     *
     * Called by the toaster during its own build, the way a slider consumes its pending drag: the
     * frame's delta is already known by then, and waiting a frame to apply it would show every toast
     * one frame longer than it asked for.
     */
    internal fun advance(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        for (entry in entries) entry.remainingSeconds -= deltaSeconds
        entries.removeAll { it.remainingSeconds <= 0f }
    }
}

/**
 * shadcn's `Toaster`: the viewport toasts appear in. Place one near the app root.
 *
 * Bottom-trailing and non-modal, which is the whole difference from the other overlays -- a toast
 * reports something without taking the frame, so it neither scrims nor traps focus. Nothing is
 * emitted at all while the queue is empty, so an idle toaster costs no layer.
 */
context(_: Composer)
fun ShadcnToaster(
    state: ShadcnToastState,
    modifier: Modifier = Modifier,
    id: String? = null,
) {
    state.advance(LocalFrameClock.current.deltaSeconds)
    val toasts = state.visible
    if (toasts.isEmpty()) return

    Layer(
        kind = LayerKind.Toast,
        // A layer sizes to its content and, without a provider, is placed at the origin -- the other
        // overlays only appear centred because their scrim makes the layer viewport-sized and the
        // alignment then acts inside it. A toaster has no scrim, so it has to say where it goes.
        positionProvider = { _, _, layerWidth, layerHeight, viewportWidth, viewportHeight ->
            LayerPosition(viewportWidth - layerWidth, viewportHeight - layerHeight)
        },
        measurePolicy = BoxMeasurePolicy(),
    ) {
        Box(
            modifier.padding(ToasterInset)
                .semantics { if (id != null) this[SemanticsProperties.TestTag] = id },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ToastGap)) {
                for (toast in toasts) {
                    shadcnToast(
                        toast.message,
                        modifier = Modifier
                            .widthIn(max = ToastMaxWidth)
                            .semantics {
                                if (id != null) {
                                    this[SemanticsProperties.TestTag] =
                                        "$id.${toast.key}"
                                }
                            },
                        title = toast.title,
                    )
                }
            }
        }
    }
}

/** Sonner's default dwell. */
const val DEFAULT_TOAST_SECONDS: Float = 4f

/**
 * How many toasts can be on screen before the oldest is dropped.
 *
 * Sonner's own visibleToasts default. Without a cap a loop that toasts per frame would grow the
 * queue without bound and paint a column taller than the window.
 */
const val MAX_VISIBLE_TOASTS: Int = 3

/** `p-4`. */
private val ToasterInset: Dp = Tw.Spacing.s4

/** `md:max-w-[420px]`. */
private val ToastMaxWidth: Dp = 420.dp
private val ToastGap: Dp = Tw.Spacing.s2
