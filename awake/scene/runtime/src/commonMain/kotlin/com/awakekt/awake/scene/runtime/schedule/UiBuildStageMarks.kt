/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.schedule

import com.awakekt.awake.compose.ui.platform.ComposeFrameStage
import com.awakekt.awake.compose.ui.platform.ComposeFrameStages

/**
 * Tells [observer] about the UI host's compose, measure and draw stages, which run inside
 * [SceneSystemObserver.UI_BUILD]. The build's mark is ended before each stage and begun again after
 * it, so no mark encloses another and a profile that adds up its marks counts each stage once:
 * `ui:build` then holds only what runs between them (input dispatch, focus, semantics, the canvas).
 */
internal class UiBuildStageMarks : ComposeFrameStages {
    var observer: SceneSystemObserver? = null

    override fun begin(stage: ComposeFrameStage) {
        val observer = observer ?: return
        observer.after(SceneSystemObserver.UI_BUILD)
        observer.before(nameOf(stage))
    }

    override fun end(stage: ComposeFrameStage) {
        val observer = observer ?: return
        observer.after(nameOf(stage))
        observer.before(SceneSystemObserver.UI_BUILD)
    }

    private fun nameOf(stage: ComposeFrameStage): String = when (stage) {
        ComposeFrameStage.Compose -> SceneSystemObserver.UI_COMPOSE
        ComposeFrameStage.Measure -> SceneSystemObserver.UI_MEASURE
        ComposeFrameStage.Draw -> SceneSystemObserver.UI_DRAW
    }
}
