/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClientNetFeatureTest {

    private class TestClientContext(val sessionTag: String) {
        val events = mutableListOf<String>()
        val outboundSent = mutableListOf<String>()
    }

    private sealed interface TestServerMessage {
        data class Pong(val seq: Int) : TestServerMessage
        data class ChatBroadcast(val sender: String, val text: String) : TestServerMessage
        data class PartyInvite(val leader: String) : TestServerMessage
        data class GuildNotice(val headline: String) : TestServerMessage
        data class TradeOffer(val copper: Int) : TestServerMessage
        data class CombatHit(val damage: Int) : TestServerMessage
        data class Unhandled(val code: Int) : TestServerMessage
    }

    @Test
    fun messageRouterDispatchesRegisteredTypesAndIgnoresUnhandled() {
        val router = messageRouter<TestClientContext, TestServerMessage> {
            on<TestServerMessage.Pong> { ctx, msg ->
                ctx.events += "${ctx.sessionTag}:pong:${msg.seq}"
            }
            on<TestServerMessage.ChatBroadcast> { ctx, msg ->
                ctx.events += "${ctx.sessionTag}:chat:${msg.sender}:${msg.text}"
            }
        }

        val ctx = TestClientContext("s1")
        assertEquals(2, router.size)
        assertEquals(
            setOf<KClass<out TestServerMessage>>(
                TestServerMessage.Pong::class,
                TestServerMessage.ChatBroadcast::class,
            ),
            router.handledTypes,
        )

        assertTrue(router.handles(TestServerMessage.Pong(7)))
        assertTrue(router.handles(TestServerMessage.Pong::class))
        assertTrue(router.handles(TestServerMessage.ChatBroadcast::class))
        assertFalse(router.handles(TestServerMessage.Unhandled(99)))
        assertFalse(router.handles(TestServerMessage.Unhandled::class))

        assertTrue(router.route(ctx, TestServerMessage.Pong(7)))
        assertTrue(router.route(ctx, TestServerMessage.ChatBroadcast("npc", "hello")))
        assertFalse(router.route(ctx, TestServerMessage.Unhandled(99)))

        assertEquals(
            listOf("s1:pong:7", "s1:chat:npc:hello"),
            ctx.events,
        )
    }

    @Test
    fun messageRouterRejectsDuplicateHandlerRegistration() {
        val error = assertFailsWith<IllegalArgumentException> {
            messageRouter<TestClientContext, TestServerMessage> {
                on<TestServerMessage.Pong> { _, _ -> }
                on<TestServerMessage.Pong> { _, _ -> }
            }
        }
        assertTrue(error.message.orEmpty().contains("Pong"))
    }

    @Test
    fun messageRouterEmptyAndDefensiveCopyFromMap() {
        val empty = MessageRouter.empty<TestClientContext, TestServerMessage>()
        val ctx = TestClientContext("s0")
        assertEquals(0, empty.size)
        assertTrue(empty.handledTypes.isEmpty())
        assertFalse(empty.handles(TestServerMessage.Pong(1)))
        assertFalse(empty.handles(TestServerMessage.Pong::class))
        assertFalse(empty.route(ctx, TestServerMessage.Pong(1)))
        assertSame(empty, MessageRouter.from(emptyMap()))

        val source = HashMap<KClass<out TestServerMessage>, (TestClientContext, TestServerMessage) -> Unit>()
        source[TestServerMessage.Pong::class] = { c, m ->
            c.events += "pong:${(m as TestServerMessage.Pong).seq}"
        }
        val copied = MessageRouter.from(source)
        source.clear()

        assertEquals(1, copied.size)
        assertTrue(copied.route(ctx, TestServerMessage.Pong(12)))
        assertEquals(listOf("pong:12"), ctx.events)
    }

    @Test
    fun clientNetFeaturesDispatchesAcrossMultipleInstalledFeatures() {
        val pingFeature = clientNetFeature<TestClientContext, TestServerMessage>("ping") {
            on<TestServerMessage.Pong> { ctx, msg -> ctx.events += "ping:${ctx.sessionTag}:${msg.seq}" }
        }
        val chatFeature = clientNetFeature<TestClientContext, TestServerMessage>("chat") {
            on<TestServerMessage.ChatBroadcast> { ctx, msg -> ctx.events += "chat:${msg.sender}:${msg.text}" }
        }
        val partyFeature = clientNetFeature<TestClientContext, TestServerMessage>("party") {
            on<TestServerMessage.PartyInvite> { ctx, msg -> ctx.events += "party:${msg.leader}" }
        }
        val guildFeature = clientNetFeature<TestClientContext, TestServerMessage>("guild") {
            on<TestServerMessage.GuildNotice> { ctx, msg -> ctx.events += "guild:${msg.headline}" }
        }
        val tradeFeature = clientNetFeature<TestClientContext, TestServerMessage>("trade") {
            on<TestServerMessage.TradeOffer> { ctx, msg -> ctx.events += "trade:${msg.copper}" }
        }

        val features = clientNetFeatures<TestClientContext, TestServerMessage> {
            install(pingFeature)
            install(chatFeature, partyFeature)
            install(listOf(guildFeature, tradeFeature))
            install("combat") {
                on<TestServerMessage.CombatHit> { ctx, msg -> ctx.events += "combat:${msg.damage}" }
            }
        }

        assertEquals(6, features.size)
        assertEquals(listOf("ping", "chat", "party", "guild", "trade", "combat"), features.featureIds.toList())
        assertEquals(6, features.handledTypes.size)
        assertTrue(features.handles(TestServerMessage.CombatHit(10)))
        assertTrue(features.handles(TestServerMessage.TradeOffer::class))
        assertFalse(features.handles(TestServerMessage.Unhandled(1)))
        assertFalse(features.handles(TestServerMessage.Unhandled::class))

        val ctx = TestClientContext("c1")
        assertTrue(features.route(ctx, TestServerMessage.Pong(3)))
        assertTrue(features.route(ctx, TestServerMessage.ChatBroadcast("hero", "ready")))
        assertTrue(features.route(ctx, TestServerMessage.PartyInvite("leaderA")))
        assertTrue(features.route(ctx, TestServerMessage.GuildNotice("welcome")))
        assertTrue(features.route(ctx, TestServerMessage.TradeOffer(500)))
        assertTrue(features.route(ctx, TestServerMessage.CombatHit(25)))
        assertFalse(features.route(ctx, TestServerMessage.Unhandled(404)))

        assertEquals(
            listOf("ping:c1:3", "chat:hero:ready", "party:leaderA", "guild:welcome", "trade:500", "combat:25"),
            ctx.events,
        )
    }

    @Test
    fun blankOrDuplicateFeatureIdThrowsFailFast() {
        assertFailsWith<IllegalArgumentException> {
            clientNetFeature<TestClientContext, TestServerMessage>("   ") {
                on<TestServerMessage.Pong> { _, _ -> }
            }
        }

        val first = clientNetFeature<TestClientContext, TestServerMessage>("social") {
            on<TestServerMessage.ChatBroadcast> { _, _ -> }
        }
        val duplicateId = clientNetFeature<TestClientContext, TestServerMessage>("social") {
            on<TestServerMessage.PartyInvite> { _, _ -> }
        }

        val dslError = assertFailsWith<IllegalArgumentException> {
            clientNetFeatures<TestClientContext, TestServerMessage> {
                install(first)
                install(duplicateId)
            }
        }
        assertTrue(dslError.message.orEmpty().contains("social"))

        val installError = assertFailsWith<IllegalArgumentException> {
            ClientNetFeatures<TestClientContext, TestServerMessage>().install(first, duplicateId)
        }
        assertTrue(installError.message.orEmpty().contains("social"))
    }

    @Test
    fun duplicateMessageRouteWithinSameFeatureThrows() {
        val error = assertFailsWith<IllegalArgumentException> {
            clientNetFeature<TestClientContext, TestServerMessage>("chat") {
                on<TestServerMessage.ChatBroadcast> { _, _ -> }
                on<TestServerMessage.ChatBroadcast> { _, _ -> }
            }
        }
        assertTrue(error.message.orEmpty().contains("chat"))
        assertTrue(error.message.orEmpty().contains("ChatBroadcast"))
    }

    @Test
    fun duplicateMessageRouteAcrossDifferentFeaturesThrows() {
        val chatFeature = clientNetFeature<TestClientContext, TestServerMessage>("chat") {
            on<TestServerMessage.ChatBroadcast> { _, _ -> }
        }
        val collidingFeature = clientNetFeature<TestClientContext, TestServerMessage>("moderation") {
            on<TestServerMessage.PartyInvite> { _, _ -> }
            on<TestServerMessage.ChatBroadcast> { _, _ -> }
        }

        val dslError = assertFailsWith<IllegalArgumentException> {
            clientNetFeatures<TestClientContext, TestServerMessage> {
                install(chatFeature)
                install(collidingFeature)
            }
        }
        assertTrue(dslError.message.orEmpty().contains("chat"))
        assertTrue(dslError.message.orEmpty().contains("moderation"))
        assertTrue(dslError.message.orEmpty().contains("ChatBroadcast"))

        val installError = assertFailsWith<IllegalArgumentException> {
            ClientNetFeatures<TestClientContext, TestServerMessage>().install(chatFeature, collidingFeature)
        }
        assertTrue(installError.message.orEmpty().contains("chat"))
        assertTrue(installError.message.orEmpty().contains("moderation"))
    }

    private fun customPartyFeature(pendingParty: ArrayDeque<String>) =
        object : ClientNetFeature<TestClientContext, TestServerMessage> {
            private val builtRouter = messageRouter<TestClientContext, TestServerMessage> {
                on<TestServerMessage.PartyInvite> { ctx, msg -> ctx.events += "party.in:${msg.leader}" }
            }
            override val id: String = "party"
            override fun onConnected(context: TestClientContext) {
                context.events += "party.connected:${context.sessionTag}"
                pendingParty.addLast("invite:ally")
            }
            override fun flushOutbound(context: TestClientContext) {
                while (pendingParty.isNotEmpty()) {
                    val req = pendingParty.removeFirst()
                    context.outboundSent += "party:$req"
                    context.events += "party.flush:$req"
                }
            }
            override fun onDisconnected(context: TestClientContext) {
                pendingParty.clear()
                context.events += "party.disconnected:${context.sessionTag}"
            }
            override fun router(): MessageRouter<TestClientContext, TestServerMessage> = builtRouter
        }

    @Test
    fun flushOutboundOnConnectedAndOnDisconnectedRunInRegistrationOrder() {
        val pendingChat = ArrayDeque<String>()
        val pendingParty = ArrayDeque<String>()

        val chatFeature = clientNetFeature<TestClientContext, TestServerMessage>("chat") {
            onConnected { ctx ->
                ctx.events += "chat.connected:${ctx.sessionTag}"
                pendingChat.addLast("hello-world")
            }
            flushOutbound { ctx ->
                while (pendingChat.isNotEmpty()) {
                    val text = pendingChat.removeFirst()
                    ctx.outboundSent += "chat:$text"
                    ctx.events += "chat.flush:$text"
                }
            }
            onDisconnected { ctx ->
                pendingChat.clear()
                ctx.events += "chat.disconnected:${ctx.sessionTag}"
            }
        }
        val passiveFeature = clientNetFeature<TestClientContext, TestServerMessage>("passive") {
            on<TestServerMessage.GuildNotice> { ctx, msg -> ctx.events += "passive:${msg.headline}" }
        }
        val features = clientNetFeatures<TestClientContext, TestServerMessage> {
            install(chatFeature, passiveFeature, customPartyFeature(pendingParty))
        }

        val ctx = TestClientContext("live")
        features.onConnected(ctx)
        features.flushOutbound(ctx)
        features.flushOutbound(ctx)
        assertTrue(features.route(ctx, TestServerMessage.GuildNotice("motd")))
        assertTrue(features.route(ctx, TestServerMessage.PartyInvite("captain")))
        pendingChat.addLast("unsent-before-drop")
        features.onDisconnected(ctx)
        assertTrue(pendingChat.isEmpty())

        assertEquals(listOf("chat:hello-world", "party:invite:ally"), ctx.outboundSent)
        assertEquals(
            listOf(
                "chat.connected:live",
                "party.connected:live",
                "chat.flush:hello-world",
                "party.flush:invite:ally",
                "passive:motd",
                "party.in:captain",
                "chat.disconnected:live",
                "party.disconnected:live",
            ),
            ctx.events,
        )
    }

    @Test
    fun immutableInstallReturnsNewContainerWithoutMutatingOriginal() {
        val f1 = clientNetFeature<TestClientContext, TestServerMessage>("ping") {
            on<TestServerMessage.Pong> { _, _ -> }
        }
        val f2 = clientNetFeature<TestClientContext, TestServerMessage>("chat") {
            on<TestServerMessage.ChatBroadcast> { _, _ -> }
        }

        val base = ClientNetFeatures<TestClientContext, TestServerMessage>().install(f1)
        assertSame(base, base.install())
        val extended = base.install(f2)

        assertEquals(1, base.size)
        assertFalse(base.handles(TestServerMessage.ChatBroadcast::class))
        assertEquals(2, extended.size)
        assertTrue(extended.handles(TestServerMessage.Pong::class))
        assertTrue(extended.handles(TestServerMessage.ChatBroadcast::class))
    }
}
