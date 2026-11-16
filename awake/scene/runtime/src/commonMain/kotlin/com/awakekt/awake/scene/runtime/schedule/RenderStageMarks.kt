/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.schedule

import com.awakekt.awake.render.renderer.RenderDrawStage
import com.awakekt.awake.render.renderer.RenderDrawStages

/**
 * Tells [observer] about the renderer's draw stages, which run inside the scheduled system that
 * draws (`infrastructure:RenderSystem3D` in a 3D scene). That system's mark, [SceneSchedule.openMark],
 * is ended before each stage and begun again after it, the way [UiBuildStageMarks] splits
 * `ui:build`, so no mark encloses another and the system's own mark keeps only its planning.
 */
internal class RenderStageMarks(private val schedule: SceneSchedule) : RenderDrawStages {
    var observer: SceneSystemObserver? = null

    override fun begin(stage: RenderDrawStage) {
        val observer = observer ?: return
        val open = schedule.openMark
        if (open != null) observer.after(open)
        observer.before(nameOf(stage))
    }

    override fun end(stage: RenderDrawStage) {
        val observer = observer ?: return
        observer.after(nameOf(stage))
        val open = schedule.openMark
        if (open != null) observer.before(open)
    }

    private fun nameOf(stage: RenderDrawStage): String = when (stage) {
        RenderDrawStage.Collect -> SceneSystemObserver.RENDER_COLLECT
        RenderDrawStage.Compile -> SceneSystemObserver.RENDER_COMPILE
        RenderDrawStage.Prepare -> SceneSystemObserver.RENDER_PREPARE
        RenderDrawStage.Upload -> SceneSystemObserver.RENDER_UPLOAD
        RenderDrawStage.Record -> SceneSystemObserver.RENDER_RECORD
        RenderDrawStage.Submit -> SceneSystemObserver.RENDER_SUBMIT
    }
}
