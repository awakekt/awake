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
    var layerMark = 0
    var unretainableMark = 0
    var placementMark = 0
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
        val record = node.retainedDraw ?: return false
        if (!reuse || !record.cacheable || record.paint != paint - 1 || scope.insideLayer) return false
        scope.writeContext(context)
        if (!context.contentEquals(record.context)) return false
        val dx = node.absoluteX - record.absX
        val dy = node.absoluteY - record.absY
        val moved = dx != 0 || dy != 0
        if (moved && !(record.translatable && isPlainContext(context))) return false
        if (!unchanged(node)) return false
        if (moved && !shiftsExactly(record, dx.toFloat(), dy.toFloat())) return false
        val start = scope.size
        for (i in record.start until record.end) {
            val primitive = scope.previous(i)
            scope.append(if (moved) primitive.shifted(dx.toFloat(), dy.toFloat(), scope) else primitive)
        }
        rebase(node, start - record.start, dx, dy)
        // Where the output now sits relative to the parent, which a parent's own record compares next.
        recordOffsetFromParent(node, record)
        replayed++
        return true
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
        record.start = scope.size
        record.layerMark = scope.layerCount
        record.unretainableMark = scope.unretainable
        record.placementMark = scope.placementBound
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
        record.end = scope.size
        record.paint = paint
        record.cacheable = scope.layerCount == record.layerMark && scope.unretainable == record.unretainableMark
        record.translatable = scope.placementBound == record.placementMark
        record.absX = node.absoluteX
        record.absY = node.absoluteY
        recordOffsetFromParent(node, record)
        record.width = node.width
        record.height = node.height
        record.childCount = node.children.size
        val links = node.drawDepths.size
        if (record.linkBounds.size != links * 4) record.linkBounds = IntArray(links * 4)
        for (i in 0 until links) {
            node.drawBoundsAt(node.drawDepths[i], bounds)
            bounds.copyInto(record.linkBounds, i * 4)
        }
    }

    private fun recordOffsetFromParent(node: LayoutNode, record: RetainedDrawRecord) {
        val parent = node.parent
        record.relX = node.absoluteX - (parent?.absoluteX ?: 0)
        record.relY = node.absoluteY - (parent?.absoluteY ?: 0)
    }

    /** Whether nothing in [node]'s subtree changed since its record, checked once per paint. */
    private fun unchanged(node: LayoutNode): Boolean {
        val record = node.retainedDraw ?: return false
        if (record.checkedPaint == paint) return record.unchanged
        record.checkedPaint = paint
        record.unchanged = record.paint != NEVER && record.cacheable && ownInputsUnchanged(node, record) && childrenUnchanged(node, record)
        return record.unchanged
    }

    private fun ownInputsUnchanged(node: LayoutNode, record: RetainedDrawRecord): Boolean {
        if (record.reads?.isInvalid == true) return false
        if (node.width != record.width || node.height != record.height) return false
        val links = node.drawDepths.size
        if (record.linkBounds.size != links * 4) return false
        for (i in 0 until links) {
            node.drawBoundsAt(node.drawDepths[i], bounds)
            for (k in 0 until 4) if (bounds[k] != record.linkBounds[i * 4 + k]) return false
        }
        return true
    }

    private fun childrenUnchanged(node: LayoutNode, record: RetainedDrawRecord): Boolean {
        val children = node.children
        if (children.size != record.childCount) return false
        for (i in 0 until children.size) {
            val child = children[i]
            val childRecord = child.retainedDraw ?: return false
            if (child.absoluteX - node.absoluteX != childRecord.relX) return false
            if (child.absoluteY - node.absoluteY != childRecord.relY) return false
            if (!unchanged(child)) return false
        }
        return true
    }

    /** Whether every primitive of [record]'s range moves by ([dx], [dy]) exactly as a fresh paint would place it. */
    private fun shiftsExactly(record: RetainedDrawRecord, dx: Float, dy: Float): Boolean {
        for (i in record.start until record.end) {
            if (!scope.previous(i).shiftsExactly(dx, dy, scope)) return false
        }
        return true
    }
}

/** No transform and no clip above the node: only then does its output move with it. */
private fun isPlainContext(context: FloatArray): Boolean =
    context[1] == 1f && context[2] == 1f && context[3] == 0f && context[4] == 0f && context[CLIP_DEPTH] == 0f

internal const val CONTEXT_SIZE = 10
private const val CLIP_DEPTH = 5
private const val NEVER = Int.MIN_VALUE
