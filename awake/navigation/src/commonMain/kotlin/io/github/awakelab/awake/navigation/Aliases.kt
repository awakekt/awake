/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.navigation

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.renderer.LineSegment
import io.github.awakelab.awake.navigation.grid.bakeNavGrid as gridBakeNavGrid
import io.github.awakelab.awake.navigation.grid.bakeNavGridCell as gridBakeNavGridCell
import io.github.awakelab.awake.navigation.grid.findPath as gridFindPath
import io.github.awakelab.awake.navigation.grid.navGridDebugLines as gridNavGridDebugLines
import io.github.awakelab.awake.navigation.grid.smoothPath as gridSmoothPath

// Type aliases re-exporting grid types at root navigation package
typealias NavGridTile = io.github.awakelab.awake.navigation.grid.NavGridTile
typealias NavGrid = io.github.awakelab.awake.navigation.grid.NavGrid
typealias NavField = io.github.awakelab.awake.navigation.grid.NavField
typealias TileField = io.github.awakelab.awake.navigation.grid.TileField
typealias NavFieldSearch = io.github.awakelab.awake.navigation.grid.NavFieldSearch
typealias AgentRoute = io.github.awakelab.awake.navigation.grid.AgentRoute
typealias NavGridDebugSink = io.github.awakelab.awake.navigation.grid.NavGridDebugSink

val BLOCKED_COLOR = io.github.awakelab.awake.navigation.grid.BLOCKED_COLOR
val BLOCKED_COLOR_ALTERNATE = io.github.awakelab.awake.navigation.grid.BLOCKED_COLOR_ALTERNATE
val ROUTE_COLORS = io.github.awakelab.awake.navigation.grid.ROUTE_COLORS
const val DEFAULT_MAX_SLOPE_DEGREES = io.github.awakelab.awake.navigation.grid.DEFAULT_MAX_SLOPE_DEGREES

fun Heightmap.bakeNavGrid(
    cellSize: Float = 1f,
    maxSlopeDegrees: Float = DEFAULT_MAX_SLOPE_DEGREES,
): NavGridTile = this.gridBakeNavGrid(cellSize, maxSlopeDegrees)

fun Heightmap.bakeNavGridCell(
    samples: Int,
    sampleSize: Float,
    maxSlopeDegrees: Float = DEFAULT_MAX_SLOPE_DEGREES,
): NavGridTile = this.gridBakeNavGridCell(samples, sampleSize, maxSlopeDegrees)

fun NavGridTile.findPath(startX: Int, startZ: Int, goalX: Int, goalZ: Int): List<Vec3f> =
    this.gridFindPath(startX, startZ, goalX, goalZ)

fun NavGridTile.findPath(start: Vec3f, goal: Vec3f): List<Vec3f> = this.gridFindPath(start, goal)

fun NavGridTile.smoothPath(path: List<Vec3f>): List<Vec3f> = this.gridSmoothPath(path)

fun NavField.findPath(startX: Int, startZ: Int, goalX: Int, goalZ: Int): List<Vec3f> =
    this.gridFindPath(startX, startZ, goalX, goalZ)

fun NavField.findPath(start: Vec3f, goal: Vec3f): List<Vec3f> = this.gridFindPath(start, goal)

fun NavField.smoothPath(path: List<Vec3f>): List<Vec3f> = this.gridSmoothPath(path)

fun navGridDebugLines(
    tile: NavGridTile,
    surfaceAt: (x: Float, z: Float) -> Float,
    routes: List<AgentRoute> = emptyList(),
): List<LineSegment> = gridNavGridDebugLines(tile, surfaceAt, routes)
