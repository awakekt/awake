/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.TransportServer
import com.awakekt.awake.net.server.host.HostPlayer
import com.awakekt.awake.net.server.host.ServerHost
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Configuration for [GameServerHostPlugin].
 */
class GameServerHostConfig {
    /** The authoritative server host to manage. */
    var host: ServerHost<*, *>? = null

    /** Transport server supplying incoming sessions. If `null`, uses [application.gameTransport]. */
    var transport: TransportServer? = null
}

/** Attribute key for retrieving [ServerHost] from [Application.attributes]. */
val GameServerHostKey: AttributeKey<ServerHost<*, *>> = AttributeKey("GameServerHost")

/**
 * Ktor plugin that binds a [ServerHost] to the Ktor application lifecycle.
 */
val GameServerHost: ApplicationPlugin<GameServerHostConfig> =
    createApplicationPlugin("GameServerHost", ::GameServerHostConfig) {
        val host = requireNotNull(pluginConfig.host) {
            "GameServerHost requires a configured ServerHost instance."
        }
        application.attributes.put(GameServerHostKey, host)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        on(MonitoringEvent(ApplicationStarted)) {
            host.start()
            val transport = pluginConfig.transport ?: application.gameTransport
            scope.launch {
                for (session in transport.sessions()) {
                    @Suppress("UNCHECKED_CAST")
                    (host as ServerHost<HostPlayer, Any>).serve(session)
                }
            }
        }

        on(MonitoringEvent(ApplicationStopping)) {
            scope.cancel()
            host.close()
        }
    }

/**
 * Accessor for the installed [ServerHost] on [Application].
 */
@Suppress("UNCHECKED_CAST")
fun <P : HostPlayer, M : Any> Application.gameServerHost(): ServerHost<P, M> =
    (attributes.getOrNull(GameServerHostKey) as? ServerHost<P, M>)
        ?: error("GameServerHost plugin is not installed on this Application.")
