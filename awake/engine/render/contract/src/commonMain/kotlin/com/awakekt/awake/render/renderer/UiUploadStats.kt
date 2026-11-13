/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * What one submitted frame wrote into the UI's GPU meshes: the cost a still UI should not pay.
 *
 * A UI whose staged draw runs did not change reuses the meshes already on the GPU, so a still
 * frame reads zero everywhere. A non-zero figure on a frame where nothing moved names the waste:
 * [runs] re-uploaded, [bytes] copied, and the [triangles] they hold, which were staged again on
 * the CPU to be uploaded.
 *
 * @property bytes Vertex and index bytes written to UI meshes, offscreen UI passes included.
 * @property runs UI draw runs whose mesh was written.
 * @property triangles Triangles in those runs.
 */
data class UiUploadStats(
    val bytes: Long,
    val runs: Int,
    val triangles: Long,
)
