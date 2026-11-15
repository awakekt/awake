/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.runtime.PhaseReads
import com.awakekt.awake.compose.ui.graphics.drawscope.PaintScope
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive

/**
 * What one node's subtree drew in the last paint, and what that output depended on (#1512 stage e).
 *
 * Not a list: [start] and [end] point into the painter's previous output, so retaining a frame costs
 * one second output list, not a copy per node. Grown once per node and reused.
 */
internal class RetainedDrawRecord {
    var paint = NEVER
    var start = 0
    var end = 0
    var absX = 0
    var absY = 0
    var relX = 0
    var relY = 0
    var width = 0
    var height = 0
    var childCount = 0
    var cacheable = false
    var translatable = false
    var reads: PhaseReads? = null
    var recordingReads = false
    val context = FloatArray(CONTEXT_SIZE)
    var linkBounds = IntArray(0)
    var checkedPaint = NEVER
    var unchanged = false
    var unretainableMark = 0
    var placementMark = 0

    /** Records where [node] sits relative to its parent, which the parent's own record compares next. */
    fun recordOffset(node: LayoutNode) {
        val parent = node.parent
        relX = node.absoluteX - (parent?.absoluteX ?: 0)
        relY = node.absoluteY - (parent?.absoluteY ?: 0)
    }

    /** Records [node]'s size and each draw link's rect, using [bounds] as scratch. */
    fun recordGeometry(node: LayoutNode, bounds: IntArray) {
        width = node.width
        height = node.height
        childCount = node.children.size
        val links = node.drawDepths.size
        if (linkBounds.size != links * 4) linkBounds = IntArray(links * 4)
        for (i in 0 until links) {
            node.drawBoundsAt(node.drawDepths[i], bounds)
            bounds.copyInto(linkBounds, i * 4)
        }
    }

    /** Whether nothing [node]'s own draw links depend on changed: their reads, its size, their rects. */
    fun ownInputsMatch(node: LayoutNode, bounds: IntArray): Boolean {
        val links = node.drawDepths.size
        var same = reads?.isInvalid != true && node.width == width && node.height == height && linkBounds.size == links * 4
        var i = 0
        while (same && i < links) {
            node.drawBoundsAt(node.drawDepths[i], bounds)
            for (k in 0 until 4) same = same && bounds[k] == linkBounds[i * 4 + k]
            i++
        }
        return same
    }
}

/**
 * Replays an unchanged subtree's last draw output instead of running its draw links (#1512 stage e).
 *
 * A subtree replays when the frame composed and measured nothing, its record is from the previous
 * paint, nothing any of its draw links read has changed, its nodes kept their sizes, draw rects and
 * offsets from one another, and what encloses it (alpha, transform, clip) is the same. Moved as a
 * whole, its primitives are copied translated, but only where the copy is bit-identical to a fresh
 * paint (see [shiftsExactly]); otherwise it paints, and its children decide for themselves.
 */
internal class RetainedDraw(private val scope: PaintScope) {
    private var paint = 0
    private var reuse = false
    private val context = FloatArray(CONTEXT_SIZE)
    private val bounds = IntArray(4)

    /** Subtrees replayed so far. */
    var replayed = 0
        private set

    /** Starts a paint; [reuse] is false on a frame that composed or measured, which only records. */
    fun beginPaint(reuse: Boolean) {
        paint++
        this.reuse = reuse
    }

    /** Starts a paint that neither records nor replays, so no record survives it. */
    fun skipPaint() {
        paint++
    }

    /** Appends [node]'s last output if nothing it depends on changed, and returns whether it did. */
    fun replay(node: LayoutNode): Boolean {
        val record = node.retainedDraw
        val replays = record != null && replayable(node, record)
        if (replays) copy(node, checkNotNull(record))
        return replays
    }

    private fun replayable(node: LayoutNode, record: RetainedDrawRecord): Boolean {
        val current = reuse && record.cacheable && record.paint == paint - 1
        if (!current || scope.insideLayer) return false
        scope.writeContext(context)
        val dx = (node.absoluteX - record.absX).toFloat()
        val dy = (node.absoluteY - record.absY).toFloat()
        val moved = dx != 0f || dy != 0f
        val movable = !moved || (record.translatable && isPlainContext(context))
        return context.contentEquals(record.context) && movable && unchanged(node) &&
            (!moved || scope.retained.shiftsExactly(record.start, record.end, dx, dy))
    }

    private fun copy(node: LayoutNode, record: RetainedDrawRecord) {
        val dx = node.absoluteX - record.absX
        val dy = node.absoluteY - record.absY
        val moved = dx != 0 || dy != 0
        val output = scope.retained
        val start = output.size
        for (i in record.start until record.end) {
            val primitive = output.previous(i)
            output.append(if (moved) primitive.shifted(dx.toFloat(), dy.toFloat()) else primitive)
        }
        rebase(node, start - record.start, dx, dy)
        record.recordOffset(node)
        replayed++
    }

    /**
     * Moves the records of [node] and every descendant painted with it into this paint's output, by
     * [shift] places and ([dx], [dy]) pixels, so a part of a replayed subtree can replay by itself next
     * frame instead of waiting a frame to be recorded again.
     */
    private fun rebase(node: LayoutNode, shift: Int, dx: Int, dy: Int) {
        val record = node.retainedDraw ?: return
        if (record.paint != paint - 1) return
        record.start += shift
        record.end += shift
        record.paint = paint
        record.absX += dx
        record.absY += dy
        // A clip inside the subtree moved with it; nothing outside it can be, or it would not have moved.
        if (record.context[CLIP_DEPTH] > 0f) {
            record.context[CLIP_DEPTH + 1] += dx
            record.context[CLIP_DEPTH + 2] += dy
        }
        val children = node.children
        for (i in 0 until children.size) rebase(children[i], shift, dx, dy)
    }

    /** Starts recording [node]'s paint; returns false inside an offscreen layer, which is never retained. */
    fun begin(node: LayoutNode): Boolean {
        if (scope.insideLayer) return false
        val record = node.retainedDraw ?: RetainedDrawRecord().also { node.retainedDraw = it }
        scope.writeContext(record.context)
        record.start = scope.retained.size
        record.unretainableMark = scope.retained.unretainable
        record.placementMark = scope.retained.placementBound
        record.paint = NEVER
        if (node.drawModifiers.isEmpty()) {
            record.reads = null
        } else {
            val reads = record.reads ?: PhaseReads().also { record.reads = it }
            reads.beginPass()
            reads.resume()
            record.recordingReads = true
        }
        return true
    }

    /** Ends [begin]; a paint that did not [complete] leaves nothing to replay. */
    fun end(node: LayoutNode, complete: Boolean) {
        val record = checkNotNull(node.retainedDraw)
        if (record.recordingReads) {
            record.recordingReads = false
            record.reads?.pause()
            if (!complete) record.reads?.invalidate()
        }
        record.checkedPaint = NEVER
        if (!complete) return
        record.end = scope.retained.size
        record.paint = paint
        record.cacheable = scope.retained.unretainable == record.unretainableMark
        record.translatable = scope.retained.placementBound == record.placementMark
        record.absX = node.absoluteX
        record.absY = node.absoluteY
        record.recordOffset(node)
        record.recordGeometry(node, bounds)
    }

    /** Whether nothing in [node]'s subtree changed since its record, checked once per paint. */
    private fun unchanged(node: LayoutNode): Boolean {
        val record = node.retainedDraw ?: return false
        if (record.checkedPaint != paint) {
            record.checkedPaint = paint
            record.unchanged = record.paint != NEVER && record.cacheable &&
                record.ownInputsMatch(node, bounds) && childrenUnchanged(node, record)
        }
        return record.unchanged
    }

    private fun childrenUnchanged(node: LayoutNode, record: RetainedDrawRecord): Boolean {
        val children = node.children
        var same = children.size == record.childCount
        var i = 0
        while (same && i < children.size) {
            val child = children[i]
            val childRecord = child.retainedDraw
            same = childRecord != null && child.absoluteX - node.absoluteX == childRecord.relX &&
                child.absoluteY - node.absoluteY == childRecord.relY && unchanged(child)
            i++
        }
        return same
    }
}

/** No transform and no clip above the node: only then does its output move with it. */
private fun isPlainContext(context: FloatArray): Boolean =
    context[1] == 1f && context[2] == 1f && context[3] == 0f && context[4] == 0f && context[CLIP_DEPTH] == 0f

internal const val CONTEXT_SIZE = 10
private const val CLIP_DEPTH = 5
private const val NEVER = Int.MIN_VALUE
