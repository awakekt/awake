/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.runtime.PhaseReads
import com.awakekt.awake.compose.ui.graphics.drawscope.PaintScope
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.core.graphics2d.TranslateOffsets
import com.awakekt.awake.core.graphics2d.TranslatePop
import com.awakekt.awake.core.graphics2d.TranslatePush
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

    /** Where the node sat at its last paint or replay; its clip context is in that place. */
    var atX = 0
    var atY = 0
    var relX = 0
    var relY = 0
    var width = 0
    var height = 0
    var childCount = 0
    var drawVersion = 0
    var zIndex = 0f
    var cacheable = false
    var translatable = false

    /** The range marker this record replayed in last, kept so a range that stays moved allocates nothing. */
    var push: TranslatePush? = null
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

    /** Records [node]'s size, draw version, z-index, and each draw link's rect, using [bounds] as scratch. */
    fun recordGeometry(node: LayoutNode, bounds: IntArray) {
        width = node.width
        height = node.height
        childCount = placedChildren(node)
        drawVersion = node.drawVersion
        zIndex = node.zIndex
        val links = node.drawDepths.size
        if (linkBounds.size != links * 4) linkBounds = IntArray(links * 4)
        for (i in 0 until links) {
            node.drawBoundsAt(node.drawDepths[i], bounds)
            bounds.copyInto(linkBounds, i * 4)
        }
    }

    /** Whether nothing [node]'s own draw links depend on changed: their reads, draw version, z-order, size, their rects. */
    fun ownInputsMatch(node: LayoutNode, bounds: IntArray): Boolean {
        val links = node.drawDepths.size
        var same = reads?.isInvalid != true && node.drawVersion == drawVersion && node.zIndex == zIndex &&
            node.width == width && node.height == height && linkBounds.size == links * 4
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
 * offsets from one another, and what encloses it (alpha, transform, clip) is the same.
 *
 * Moved as a whole, its primitives are replayed as they were inside a [TranslatePush] and
 * [TranslatePop], which the UI staging applies as it builds the vertices, so nothing is copied. A
 * record's `absX` and `absY` are where its primitives were painted, so a subtree that stays moved
 * replays the same primitives every frame. Where a range cannot hold them (a texture), they are
 * copied translated instead. Either is done only where the result stages bit-identically to a fresh
 * paint (see [translatesExactly] and [shiftsExactly]); otherwise it paints, and its children decide
 * for themselves.
 */
internal class RetainedDraw(private val scope: PaintScope) {
    private var paint = 0
    private var reuse = false
    private val context = FloatArray(CONTEXT_SIZE)
    private val bounds = IntArray(4)
    private val offsets = TranslateOffsets()

    // Whether the subtree [replayable] just accepted replays inside a translate range.
    private var ranged = false

    // Translate ranges opened and not yet closed this paint; zero at its end (see [endPaint]).
    private var openRanges = 0

    /** Subtrees replayed so far. */
    var replayed = 0
        private set

    /** Starts a paint; [reuse] is false on a frame that composed or measured, which only records. */
    fun beginPaint(reuse: Boolean) {
        paint++
        this.reuse = reuse
        openRanges = 0
    }

    /** Ends a paint: every translate range it opened is closed, as each replay closes its own. */
    fun endPaint() {
        check(openRanges == 0) { "$openRanges translate range(s) left open by a retained replay" }
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
        val same = context.contentEquals(record.context) && movable && unchanged(node)
        val move = if (same && moved) scope.retained.moveOf(record.start, record.end, dx, dy, offsets) else MOVE_NONE
        ranged = move == MOVE_RANGED
        return same && (!moved || move != MOVE_NONE)
    }

    private fun copy(node: LayoutNode, record: RetainedDrawRecord) {
        val dx = node.absoluteX - record.absX
        val dy = node.absoluteY - record.absY
        val moved = dx != 0 || dy != 0
        val output = scope.retained
        if (moved && ranged) {
            val push = translatePush(record.push, dx.toFloat(), dy.toFloat())
            record.push = push
            output.append(push)
            openRanges++
        }
        val shifts = moved && !ranged
        val start = output.size
        for (i in record.start until record.end) {
            val primitive = output.previous(i)
            output.append(if (shifts) primitive.shifted(dx.toFloat(), dy.toFloat()) else primitive)
        }
        if (moved && ranged) {
            output.append(TranslatePop)
            openRanges--
        }
        // A range keeps the primitives where they were painted, so the records keep that place too.
        val stepX = node.absoluteX - record.atX
        val stepY = node.absoluteY - record.atY
        if (shifts) rebase(node, start - record.start, dx, dy, stepX, stepY) else rebase(node, start - record.start, 0, 0, stepX, stepY)
        record.recordOffset(node)
        replayed++
    }

    /**
     * Moves the records of [node] and every descendant painted with it into this paint's output, by
     * [shift] places and ([dx], [dy]) pixels, so a part of a replayed subtree can replay by itself next
     * frame instead of waiting a frame to be recorded again.
     */
    private fun rebase(node: LayoutNode, shift: Int, dx: Int, dy: Int, clipDx: Int, clipDy: Int) {
        val record = node.retainedDraw ?: return
        if (record.paint != paint - 1) return
        record.start += shift
        record.end += shift
        record.paint = paint
        record.absX += dx
        record.absY += dy
        record.atX += clipDx
        record.atY += clipDy
        // A clip inside the subtree moved with it; nothing outside it can be, or it would not have moved.
        if (record.context[CLIP_DEPTH] > 0f) {
            record.context[CLIP_DEPTH + 1] += clipDx
            record.context[CLIP_DEPTH + 2] += clipDy
        }
        val children = node.children
        for (i in 0 until children.size) {
            if (node.placesChild(children[i])) rebase(children[i], shift, dx, dy, clipDx, clipDy)
        }
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
        record.atX = node.absoluteX
        record.atY = node.absoluteY
        record.recordOffset(node)
        record.recordGeometry(node, bounds)
    }

    /** Whether nothing in [node]'s subtree changed since its record, checked once per paint. */
    private fun unchanged(node: LayoutNode): Boolean {
        val record = node.retainedDraw ?: return false
        if (record.checkedPaint != paint) {
            record.checkedPaint = paint
            record.unchanged = record.paint == paint - 1 && record.cacheable &&
                record.ownInputsMatch(node, bounds) && childrenUnchanged(node, record)
        }
        return record.unchanged
    }

    private fun childrenUnchanged(node: LayoutNode, record: RetainedDrawRecord): Boolean {
        val children = node.children
        var same = placedChildren(node) == record.childCount
        var i = 0
        while (same && i < children.size) {
            val child = children[i]
            i++
            // Not drawn, so not in the range: only the drawn children have to match.
            if (!node.placesChild(child)) continue
            val childRecord = child.retainedDraw
            same = childRecord != null && child.absoluteX - node.absoluteX == childRecord.relX &&
                child.absoluteY - node.absoluteY == childRecord.relY && unchanged(child)
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

/** How many of [node]'s children its placement placed this pass: the ones painted, and so recorded. */
private fun placedChildren(node: LayoutNode): Int {
    var count = 0
    for (i in 0 until node.children.size) if (node.placesChild(node.children[i])) count++
    return count
}
