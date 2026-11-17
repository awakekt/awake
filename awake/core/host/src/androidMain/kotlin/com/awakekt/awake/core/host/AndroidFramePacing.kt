/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * How an Android app holds a frame to its [FrameRateMode] (#1699): set with
 * [AndroidFrameLoop.framePacing], at start or while the app runs.
 */
enum class AndroidFramePacing {
    /** The frame loop sleeps out what is left of a capped frame's budget. The default. */
    Sleep,

    /**
     * Swappy, Android's frame-pacing library, holds each frame at present for the swap interval the
     * mode asks for, so the loop does not also sleep. A backend that cannot run it keeps sleeping:
     * whether it runs is [AndroidFrameLoop.presentPacing].
     */
    Swappy,
}
