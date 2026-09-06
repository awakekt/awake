/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder

import com.awakekt.awake.ui.builder.model.UiNode
import com.awakekt.awake.ui.builder.state.UiBuilderContract
import com.awakekt.awake.ui.builder.state.UiBuilderStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UiBuilderStoreTest {
    @Test
    fun insertWidgetPushesUndoStackAndUpdatesState() {
        val store = UiBuilderStore()
        val buttonNode = UiNode(id = "btn_test", type = "Widget.ShadcnButton")

        store.dispatch(UiBuilderContract.Intent.InsertWidget(buttonNode, targetParentId = "root", index = 0))

        val state = store.state.value
        assertNotNull(state.document.rootNode.findNode("btn_test"))
        assertEquals("btn_test", state.selectedNodeId)
        assertEquals(1, state.undoStack.size)

        store.dispatch(UiBuilderContract.Intent.Undo)
        val undoneState = store.state.value
        assertNull(undoneState.document.rootNode.findNode("btn_test"))
        assertEquals(1, undoneState.redoStack.size)
    }
}
