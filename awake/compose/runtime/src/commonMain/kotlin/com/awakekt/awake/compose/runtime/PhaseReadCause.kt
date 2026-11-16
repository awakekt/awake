/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

/**
 * What made a [PhaseReads] pass out of date, as recorded while [PhaseReads.explains] is on (#1512):
 * the first change found since the pass, so a host can say why a phase it hoped to skip ran.
 */
enum class PhaseReadCause {
    /** Nothing: the pass is still current, or nothing was being explained. */
    None,

    /** There is no pass to compare with: none has run yet, or the reads were forgotten. */
    NoPass,

    /** A [SnapshotMutableState] the pass read was written with a different value. */
    StateWritten,

    /** A [PolledState] the pass read now returns something else. */
    PolledChanged,

    /** A composition that ran inside the pass, a subcomposition, is out of date. */
    CompositionInvalid,

    /** [PhaseReads.invalidate] was called, or the pass threw before it finished. */
    Invalidated,
}

/**
 * Who is reading while a [PhaseReads] records, for [PhaseReads.causeOwner]: a host that explains its
 * passes sets it around each unit of work, such as a layout node being measured, and puts back the
 * one before. Only read while some pass explains, so a host that explains nothing can leave it alone.
 */
object ReadOwner {
    /** The unit of work reading now, or null. */
    var current: Any? = null

    /** Whether any pass is explaining now, so a host knows whether setting [current] is worth it. */
    val wanted: Boolean get() = ReadTracking.current?.explains == true
}
