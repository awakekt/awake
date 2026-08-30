/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.renderer

import io.github.awakelab.awake.render.passes.debug.lineSegmentVertices
import io.github.awakelab.awake.render.renderer.LineSegment

/** Stages debug geometry for consumption by the next Vulkan frame. */
internal fun Renderer.performDrawDebugLines(lines: List<LineSegment>) {
    // The wait is what makes LineMesh's grow-in-place safe: this slot's fence is signalled, so
    // its buffer can be reallocated without a device-wide idle. No capacity guard here any more --
    // the mesh grows to fit and enforces DebugLineLayout's ceiling itself.
    waitForCurrentFrameResourceSlot()
    lineMesh.update(swapchainManager.currentFrame, lineSegmentVertices(lines))
}
