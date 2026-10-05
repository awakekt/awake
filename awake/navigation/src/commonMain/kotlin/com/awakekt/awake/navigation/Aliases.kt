/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.navigation.grid.bakeNavGrid as gridBakeNavGrid
import com.awakekt.awake.navigation.grid.bakeNavGridCell as gridBakeNavGridCell
import com.awakekt.awake.navigation.grid.findPath as gridFindPath
import com.awakekt.awake.navigation.grid.navGridDebugLines as gridNavGridDebugLines
import com.awakekt.awake.navigation.grid.smoothPath as gridSmoothPath

// Type aliases re-exporting grid types at root navigation package
typealias NavGridTile = com.awakekt.awake.navigation.grid.NavGridTile
typealias NavGrid = com.awakekt.awake.navigation.grid.NavGrid
typealias NavField = com.awakekt.awake.navigation.grid.NavField
typealias TileField = com.awakekt.awake.navigation.grid.TileField
typealias NavFieldSearch = com.awakekt.awake.navigation.grid.NavFieldSearch
typealias AgentRoute = com.awakekt.awake.navigation.grid.AgentRoute
typealias NavGridDebugSink = com.awakekt.awake.navigation.grid.NavGridDebugSink

/** Primary debug color for blocked/non-walkable grid samples. */
val BLOCKED_COLOR = com.awakekt.awake.navigation.grid.BLOCKED_COLOR

/** Alternate debug color for blocked/non-walkable samples on alternating checkerboard cells. */
val BLOCKED_COLOR_ALTERNATE = com.awakekt.awake.navigation.grid.BLOCKED_COLOR_ALTERNATE

/** Palette of distinct debug colors for visualizing agent routes. */
val ROUTE_COLORS = com.awakekt.awake.navigation.grid.ROUTE_COLORS

/** Default slope limit in degrees (45°) above which terrain is considered non-walkable. */
const val DEFAULT_MAX_SLOPE_DEGREES = com.awakekt.awake.navigation.grid.DEFAULT_MAX_SLOPE_DEGREES

/**
 * Bakes walkability from terrain slope across an entire [Heightmap].
 *
 * @param cellSize The distance in metres between adjacent navigation samples.
 * @param maxSlopeDegrees The maximum slope in degrees an agent can climb without being blocked.
 * @return A baked [NavGridTile] covering the heightmap.
 */
fun Heightmap.bakeNavGrid(
    cellSize: Float = 1f,
    maxSlopeDegrees: Float = DEFAULT_MAX_SLOPE_DEGREES,
): NavGridTile = this.gridBakeNavGrid(cellSize, maxSlopeDegrees)

/**
 * Bakes walkability for one streamed cell with [samples] samples spaced [sampleSize] apart.
 *
 * @param samples The number of samples along each axis of the cell.
 * @param sampleSize The distance in metres between adjacent navigation samples.
 * @param maxSlopeDegrees The maximum slope in degrees an agent can climb without being blocked.
 * @return A baked [NavGridTile] positioned at local origin.
 */
fun Heightmap.bakeNavGridCell(
    samples: Int,
    sampleSize: Float,
    maxSlopeDegrees: Float = DEFAULT_MAX_SLOPE_DEGREES,
): NavGridTile = this.gridBakeNavGridCell(samples, sampleSize, maxSlopeDegrees)

/**
 * Finds the cheapest walkable route between two grid coordinates on this [NavGridTile].
 *
 * @param startX Starting sample X coordinate on the grid.
 * @param startZ Starting sample Z coordinate on the grid.
 * @param goalX Target sample X coordinate on the grid.
 * @param goalZ Target sample Z coordinate on the grid.
 * @return An ordered list of waypoint positions, or an empty list if no path exists.
 */
fun NavGridTile.findPath(startX: Int, startZ: Int, goalX: Int, goalZ: Int): List<Vec3f> =
    this.gridFindPath(startX, startZ, goalX, goalZ)

/**
 * Finds a walkable route between world-space positions on this [NavGridTile].
 *
 * @param start Starting position in world space.
 * @param goal Target position in world space.
 * @return An ordered list of waypoint positions, or an empty list if no path exists.
 */
fun NavGridTile.findPath(start: Vec3f, goal: Vec3f): List<Vec3f> = this.gridFindPath(start, goal)

/**
 * Removes redundant waypoints from a path across this [NavGridTile] using line-of-sight checks.
 *
 * @param path The input list of waypoints to smooth.
 * @return A smoothed list of waypoints with intermediate collinear points removed.
 */
fun NavGridTile.smoothPath(path: List<Vec3f>): List<Vec3f> = this.gridSmoothPath(path)

/**
 * Finds the cheapest walkable route between two grid coordinates on this [NavField].
 *
 * @param startX Starting sample X coordinate on the grid.
 * @param startZ Starting sample Z coordinate on the grid.
 * @param goalX Target sample X coordinate on the grid.
 * @param goalZ Target sample Z coordinate on the grid.
 * @return An ordered list of waypoint positions, or an empty list if no path exists.
 */
fun NavField.findPath(startX: Int, startZ: Int, goalX: Int, goalZ: Int): List<Vec3f> =
    this.gridFindPath(startX, startZ, goalX, goalZ)

/**
 * Finds a walkable route between world-space positions on this [NavField].
 *
 * @param start Starting position in world space.
 * @param goal Target position in world space.
 * @return An ordered list of waypoint positions, or an empty list if no path exists.
 */
fun NavField.findPath(start: Vec3f, goal: Vec3f): List<Vec3f> = this.gridFindPath(start, goal)

/**
 * Removes redundant waypoints from a path across this [NavField] using line-of-sight checks.
 *
 * @param path The input list of waypoints to smooth.
 * @return A smoothed list of waypoints with intermediate collinear points removed.
 */
fun NavField.smoothPath(path: List<Vec3f>): List<Vec3f> = this.gridSmoothPath(path)

/**
 * Generates world-space wireframe debug lines visualizing blocked samples and active agent routes.
 *
 * @param tile The navigation grid tile to visualize.
 * @param surfaceAt Function resolving surface elevation at given world (x, z) coordinates.
 * @param routes Active agent routes to draw.
 * @return A list of [LineSegment] instances ready for rendering.
 */
fun navGridDebugLines(
    tile: NavGridTile,
    surfaceAt: (x: Float, z: Float) -> Float,
    routes: List<AgentRoute> = emptyList(),
): List<LineSegment> = gridNavGridDebugLines(tile, surfaceAt, routes)
