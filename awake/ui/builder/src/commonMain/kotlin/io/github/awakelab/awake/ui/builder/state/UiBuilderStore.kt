/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.awakelab.awake.ui.builder.state

import io.github.awakelab.awake.ui.builder.layout.MagicReflowEngine
import io.github.awakelab.awake.ui.builder.model.UiLayoutDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State store for the UI Builder with MVI reducer and Undo/Redo stack management.
 */
class UiBuilderStore(initialDocument: UiLayoutDocument = UiLayoutDocument.createEmpty()) {
    private val _state = MutableStateFlow(UiBuilderContract.State(document = initialDocument))
    val state: StateFlow<UiBuilderContract.State> = _state.asStateFlow()

    fun dispatch(intent: UiBuilderContract.Intent) {
        val current = _state.value
        when (intent) {
            is UiBuilderContract.Intent.SelectNode -> {
                _state.value = current.copy(selectedNodeId = intent.nodeId)
            }
            is UiBuilderContract.Intent.SetActiveDropTarget -> {
                _state.value = current.copy(activeDropTarget = intent.target)
            }
            is UiBuilderContract.Intent.ToggleLivePreview -> {
                _state.value = current.copy(isLivePreview = !current.isLivePreview)
            }
            is UiBuilderContract.Intent.InsertWidget -> {
                val updatedRoot = current.document.rootNode.insertNode(
                    targetParentId = intent.targetParentId,
                    index = intent.index,
                    newNode = intent.template,
                )
                val newDoc = current.document.copy(rootNode = updatedRoot)
                pushDocument(newDoc, selectedNodeId = intent.template.id)
            }
            is UiBuilderContract.Intent.DeleteNode -> {
                val updatedRoot = current.document.rootNode.removeNode(intent.nodeId)
                val newDoc = current.document.copy(rootNode = updatedRoot)
                pushDocument(newDoc, selectedNodeId = null)
            }
            is UiBuilderContract.Intent.UpdateNodeProps -> {
                val updatedRoot = current.document.rootNode.updateNode(intent.nodeId) {
                    it.copy(props = intent.props)
                }
                val newDoc = current.document.copy(rootNode = updatedRoot)
                pushDocument(newDoc, selectedNodeId = intent.nodeId)
            }
            is UiBuilderContract.Intent.UpdateNodeStyle -> {
                val updatedRoot = current.document.rootNode.updateNode(intent.nodeId) {
                    it.copy(style = intent.style)
                }
                val newDoc = current.document.copy(rootNode = updatedRoot)
                pushDocument(newDoc, selectedNodeId = intent.nodeId)
            }
            is UiBuilderContract.Intent.ApplyMagicReflow -> {
                val updatedRoot = MagicReflowEngine.reflowContainer(current.document.rootNode)
                val newDoc = current.document.copy(rootNode = updatedRoot)
                pushDocument(newDoc, selectedNodeId = current.selectedNodeId)
            }
            is UiBuilderContract.Intent.Undo -> performUndo()
            is UiBuilderContract.Intent.Redo -> performRedo()
        }
    }

    private fun pushDocument(newDoc: UiLayoutDocument, selectedNodeId: String?) {
        val current = _state.value
        _state.value = current.copy(
            document = newDoc,
            selectedNodeId = selectedNodeId,
            undoStack = current.undoStack + current.document,
            redoStack = emptyList(),
            isDirty = true,
            activeDropTarget = null,
        )
    }

    private fun performUndo() {
        val current = _state.value
        if (current.undoStack.isEmpty()) return
        val previousDoc = current.undoStack.last()
        val newUndo = current.undoStack.dropLast(1)
        _state.value = current.copy(
            document = previousDoc,
            undoStack = newUndo,
            redoStack = current.redoStack + current.document,
            isDirty = newUndo.isNotEmpty(),
        )
    }

    private fun performRedo() {
        val current = _state.value
        if (current.redoStack.isEmpty()) return
        val nextDoc = current.redoStack.last()
        val newRedo = current.redoStack.dropLast(1)
        _state.value = current.copy(
            document = nextDoc,
            undoStack = current.undoStack + current.document,
            redoStack = newRedo,
            isDirty = true,
        )
    }
}
