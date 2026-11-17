/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.node.SemanticsModifierNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsConfiguration
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ComposeHost.verifySkippedCompositions] finds a semantics link that changes its configuration in
 * place (#1512). The host reuses its semantics tree while every link returns the same configuration,
 * so such a link's change would go unreported; `SemanticsModifierNode` forbids it, and this check
 * enforces that without costing a host that does not verify.
 */
class StaleSemanticsTest {
    private val input = FrameInput(200, 100, deltaSeconds = 0f)
    private val shared = SemanticsConfiguration().apply { this[SemanticsProperties.Label] = "full" }

    /** A link that breaks the rule: it hands out one configuration and the test changes it in place. */
    private class InPlace(private val config: SemanticsConfiguration) : ModifierNodeElement<InPlaceNode>() {
        override fun create() = InPlaceNode()
        override fun update(node: InPlaceNode) {
            node.semanticsConfiguration = config
        }
    }

    private class InPlaceNode :
        Modifier.Node(),
        SemanticsModifierNode {
        override lateinit var semanticsConfiguration: SemanticsConfiguration
    }

    private fun host(verify: Boolean) = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        verifySkippedCompositions = verify
    }

    context(_: Composer)
    private fun Content() {
        Box(Modifier.size(20.dp) then InPlace(shared))
    }

    @Test
    fun aConfigurationChangedInPlaceIsFoundWhileVerifying() {
        val host = host(verify = true)
        host.frame(input) { Content() }
        host.frame(input) { Content() }
        shared[SemanticsProperties.Label] = "low"
        host.frame(input) { Content() }
        assertEquals(1, host.staleSemantics, "the reused tree still said what the link said before")
    }

    @Test
    fun withoutVerifyingNothingIsBuiltTwice() {
        val host = host(verify = false)
        host.frame(input) { Content() }
        shared[SemanticsProperties.Label] = "low"
        val label = host.frame(input) { Content() }.semantics.single().label
        assertEquals(0, host.staleSemantics)
        assertEquals("full", label, "what the check exists to catch: the change went unreported")
    }

    @Test
    fun aLinkThatReturnsANewConfigurationIsNeverStale() {
        val label = mutableStateOf("full")
        val host = host(verify = true)
        repeat(6) { frame ->
            if (frame == 3) label.value = "low"
            val reported = host.frame(input) {
                Box(Modifier.size(20.dp).semantics { this[SemanticsProperties.Label] = label.value })
            }.semantics.single().label
            assertEquals(label.value, reported)
        }
        assertEquals(0, host.staleSemantics)
    }
}
