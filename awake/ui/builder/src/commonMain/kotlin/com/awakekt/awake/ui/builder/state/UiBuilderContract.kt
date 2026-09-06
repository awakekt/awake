/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder.state

import com.awakekt.awake.ui.builder.layout.DropTarget
import com.awakekt.awake.ui.builder.model.UiLayoutDocument
import com.awakekt.awake.ui.builder.model.UiNode
import com.awakekt.awake.ui.builder.model.UiStyleSpec

object UiBuilderContract {
    data class State(
        val document: UiLayoutDocument,
        val selectedNodeId: String? = null,
        val activeDropTarget: DropTarget? = null,
        val isLivePreview: Boolean = false,
        val undoStack: List<UiLayoutDocument> = emptyList(),
        val redoStack: List<UiLayoutDocument> = emptyList(),
        val isDirty: Boolean = false,
    )

    sealed interface Intent {
        data class SelectNode(val nodeId: String?) : Intent
        data class InsertWidget(val template: UiNode, val targetParentId: String, val index: Int) : Intent
        data class DeleteNode(val nodeId: String) : Intent
        data class UpdateNodeProps(val nodeId: String, val props: Map<String, String>) : Intent
        data class UpdateNodeStyle(val nodeId: String, val style: UiStyleSpec) : Intent
        data class SetActiveDropTarget(val target: DropTarget?) : Intent
        data object ApplyMagicReflow : Intent
        data object ToggleLivePreview : Intent
        data object Undo : Intent
        data object Redo : Intent
    }

    sealed interface Effect {
        data class Saved(val documentName: String) : Effect
    }
}
