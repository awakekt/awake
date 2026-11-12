/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientNetFeatureHooksTest {
    private class Ctx {
        val events = mutableListOf<String>()
    }

    private class Custom(override val id: String) : ClientNetFeature<Ctx, String> {
        override fun onEntityDisappear(context: Ctx, entity: NetId) {
            context.events += "$id.gone:${entity.value}"
        }

        override fun onReset(context: Ctx) {
            context.events += "$id.reset"
        }

        override fun router(): MessageRouter<Ctx, String> = MessageRouter.empty()
    }

    private class Bare : ClientNetFeature<Ctx, String> {
        override val id: String = "bare"
        override fun router(): MessageRouter<Ctx, String> = MessageRouter.empty()
    }

    @Test
    fun entityDisappearAndResetDispatchInRegistrationOrder() {
        val features = clientNetFeatures<Ctx, String> {
            install("a") {
                onEntityDisappear { ctx, entity -> ctx.events += "a.gone:${entity.value}" }
                onReset { ctx -> ctx.events += "a.reset" }
            }
            install("silent") { }
            install(Custom("b"))
            install(Bare())
        }
        val ctx = Ctx()

        features.onEntityDisappear(ctx, NetId(7L))
        features.onReset(ctx)

        assertEquals(listOf("a.gone:7", "b.gone:7", "a.reset", "b.reset"), ctx.events)
    }

    @Test
    fun hooksDefaultToNoOp() {
        val ctx = Ctx()
        val feature = clientNetFeature<Ctx, String>("plain") { }

        feature.onEntityDisappear(ctx, NetId(1L))
        feature.onReset(ctx)
        Bare().onEntityDisappear(ctx, NetId(1L))
        Bare().onReset(ctx)

        assertEquals(emptyList(), ctx.events)
    }
}
