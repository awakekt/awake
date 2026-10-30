/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

/**
 * Primary simulation plane for planar operations such as radial outward push and cone/fan velocity
 * spread.
 */
enum class ParticlePlane {
    /**
     * 3D world-space horizontal plane (`X`, `Z`) with `Y` up: cone spread samples a 3D spherical
     * cone around the base velocity and radial push acts horizontally in `(X, Z)`.
     */
    XZ,

    /**
     * 2D scene and UI canvas plane (`X`, `Y`) with `Z = 0`: cone spread fans within the `(X, Y)`
     * plane so speed stays on-plane, and radial push acts outward in `(X, Y)`.
     */
    XY,
}

/**
 * Spatial distribution around a [ParticleSimulation]'s origin where newly spawned particles appear.
 */
sealed interface ParticleSpawnShape {
    /** Spawns every particle directly at the emitter's origin with zero RNG calls. */
    data object Point : ParticleSpawnShape

    /**
     * Spawns on a horizontal circle of [radius] in the local `XZ` plane (`y = 0`).
     *
     * @property radius Circle radius in simulation units; must be finite and non-negative.
     */
    data class RingXZ(val radius: Float) : ParticleSpawnShape {
        init {
            require(isValidExtent(radius)) {
                "radius must be finite and not negative, got $radius"
            }
        }
    }

    /**
     * Spawns uniformly by area inside an annulus (or filled disc when `innerRadius == 0f`, or
     * circle perimeter when `innerRadius == outerRadius`) in the local `XY` plane (`z = 0`).
     *
     * @property innerRadius Inner hole radius; must be finite, non-negative, and `<= outerRadius`.
     * @property outerRadius Outer boundary radius; must be finite and `>= innerRadius`.
     */
    data class RingXY(
        val innerRadius: Float = 0f,
        val outerRadius: Float,
    ) : ParticleSpawnShape {
        init {
            require(isValidExtent(innerRadius) && isValidExtent(outerRadius)) {
                "ring radii must be finite and not negative, got inner=$innerRadius outer=$outerRadius"
            }
            require(innerRadius <= outerRadius) {
                "innerRadius ($innerRadius) must not exceed outerRadius ($outerRadius)"
            }
        }
    }

    /**
     * Spawns uniformly inside an axis-aligned box `[-halfExtentX..halfExtentX, -halfExtentY..halfExtentY, -halfExtentZ..halfExtentZ]`.
     *
     * @property halfExtentX Half-width along local X; must be finite and non-negative.
     * @property halfExtentY Half-height along local Y; must be finite and non-negative.
     * @property halfExtentZ Half-depth along local Z (`0f` for 2D/UI rectangles); must be finite and non-negative.
     */
    data class Box(
        val halfExtentX: Float,
        val halfExtentY: Float,
        val halfExtentZ: Float = 0f,
    ) : ParticleSpawnShape {
        init {
            require(isValidExtent(halfExtentX) && isValidExtent(halfExtentY) && isValidExtent(halfExtentZ)) {
                "box half-extents must be finite and not negative, got ($halfExtentX, $halfExtentY, $halfExtentZ)"
            }
        }
    }

    /**
     * Spawns uniformly by arc length along the perimeter of a 2D rectangle `[-halfWidth..halfWidth, -halfHeight..halfHeight]`
     * in the local `XY` plane (`z = 0`), suitable for bar, slot, and button border effects.
     *
     * @property halfWidth Half-width along local X; must be finite and non-negative.
     * @property halfHeight Half-height along local Y; must be finite and non-negative.
     */
    data class RectEdge(
        val halfWidth: Float,
        val halfHeight: Float,
    ) : ParticleSpawnShape {
        init {
            require(isValidExtent(halfWidth) && isValidExtent(halfHeight)) {
                "rect half-dimensions must be finite and not negative, got ($halfWidth, $halfHeight)"
            }
        }
    }
}

private fun isValidExtent(value: Float): Boolean = value >= 0f && value.isFinite()
