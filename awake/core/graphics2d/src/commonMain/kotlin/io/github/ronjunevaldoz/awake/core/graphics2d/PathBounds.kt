// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

import io.github.ronjunevaldoz.awake.core.math2d.Rectangle

fun DrawPath.bounds(): Rectangle {
    if (commands.isEmpty()) return Rectangle(0f, 0f, 0f, 0f)

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    fun include(x: Float, y: Float) {
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
    }

    commands.forEach { command ->
        when (command) {
            is PathCommand.MoveTo -> include(command.x, command.y)
            is PathCommand.LineTo -> include(command.x, command.y)
            is PathCommand.QuadTo -> {
                include(command.cx, command.cy)
                include(command.x, command.y)
            }
            is PathCommand.CubicTo -> {
                include(command.c1x, command.c1y)
                include(command.c2x, command.c2y)
                include(command.x, command.y)
            }
            is PathCommand.ArcTo -> {
                include(command.left, command.top)
                include(command.right, command.bottom)
            }
            PathCommand.Close -> Unit
        }
    }

    if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) {
        return Rectangle(0f, 0f, 0f, 0f)
    }
    return Rectangle(minX, minY, (maxX - minX).coerceAtLeast(0f), (maxY - minY).coerceAtLeast(0f))
}

fun DrawPath.transform(
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    translateX: Float = 0f,
    translateY: Float = 0f,
): DrawPath = copy(
    commands = commands.map { command ->
        when (command) {
            is PathCommand.MoveTo -> PathCommand.MoveTo(
                x = command.x * scaleX + translateX,
                y = command.y * scaleY + translateY,
            )
            is PathCommand.LineTo -> PathCommand.LineTo(
                x = command.x * scaleX + translateX,
                y = command.y * scaleY + translateY,
            )
            is PathCommand.QuadTo -> PathCommand.QuadTo(
                cx = command.cx * scaleX + translateX,
                cy = command.cy * scaleY + translateY,
                x = command.x * scaleX + translateX,
                y = command.y * scaleY + translateY,
            )
            is PathCommand.CubicTo -> PathCommand.CubicTo(
                c1x = command.c1x * scaleX + translateX,
                c1y = command.c1y * scaleY + translateY,
                c2x = command.c2x * scaleX + translateX,
                c2y = command.c2y * scaleY + translateY,
                x = command.x * scaleX + translateX,
                y = command.y * scaleY + translateY,
            )
            is PathCommand.ArcTo -> PathCommand.ArcTo(
                left = command.left * scaleX + translateX,
                top = command.top * scaleY + translateY,
                right = command.right * scaleX + translateX,
                bottom = command.bottom * scaleY + translateY,
                startDegrees = command.startDegrees,
                sweepDegrees = command.sweepDegrees,
            )
            PathCommand.Close -> PathCommand.Close
        }
    },
)
