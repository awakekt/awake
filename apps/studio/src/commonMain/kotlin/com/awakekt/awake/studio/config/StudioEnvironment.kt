/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.config

import com.awakekt.awake.core.config.AwakeConfig
import com.awakekt.awake.core.config.defaultPlatformEnvSource

/**
 * Deployment stages supported by Awake Studio.
 */
enum class StudioStage(val rawName: String) {
    Local("local"),
    Dev("dev"),
    Staging("staging"),
    Prod("prod"),
    ;

    val isDevelopment: Boolean get() = this == Local || this == Dev

    companion object {
        fun fromString(value: String?): StudioStage {
            val normalized = value?.trim()?.lowercase() ?: return Dev
            return entries.firstOrNull { it.rawName == normalized || it.name.lowercase() == normalized } ?: Dev
        }
    }
}

/**
 * Typed runtime environment configuration for Awake Studio.
 *
 * Resolved hierarchically via :awake:core:config:
 * 1. Explicit DI overrides (for tests and mocking).
 * 2. Process environment variables (`AWAKE_STAGE`, `AWAKE_MARKETPLACE_URL`).
 * 3. System properties or local configuration files (`.env`, `studio.properties` on Desktop).
 * 4. Fallback defaults for the current deployment stage.
 */
data class StudioEnvironment(
    val stage: StudioStage = StudioStage.Dev,
    val marketplaceApiUrl: String = defaultMarketplaceUrlFor(stage),
    val allowUnsignedPlugins: Boolean = stage.isDevelopment,
    val requestTimeoutMillis: Long = 15_000L,
    val debugLogging: Boolean = stage.isDevelopment,
) {
    companion object {
        fun defaultMarketplaceUrlFor(stage: StudioStage): String = when (stage) {
            StudioStage.Local -> "http://localhost:8080/v1/marketplace"
            StudioStage.Dev -> "https://dev-marketplace.awakeengine.io/v1"
            StudioStage.Staging -> "https://staging-marketplace.awakeengine.io/v1"
            StudioStage.Prod -> "https://marketplace.awakeengine.io/v1"
        }

        val Local: StudioEnvironment = StudioEnvironment(stage = StudioStage.Local)
        val Dev: StudioEnvironment = StudioEnvironment(stage = StudioStage.Dev)
        val Staging: StudioEnvironment = StudioEnvironment(stage = StudioStage.Staging)
        val Prod: StudioEnvironment = StudioEnvironment(stage = StudioStage.Prod)
    }
}

/**
 * Loads the platform-appropriate environment configuration using [AwakeConfig].
 */
fun loadPlatformEnvironment(
    config: AwakeConfig = AwakeConfig(defaultPlatformEnvSource()),
): StudioEnvironment {
    val stage = config.getEnum("AWAKE_STAGE", StudioStage.Dev)
    val customMarketplaceUrl = config.getStringOrNull("AWAKE_MARKETPLACE_URL")
        ?: config.getStringOrNull("MARKETPLACE_URL")
    val allowUnsigned = config.getBoolean("AWAKE_ALLOW_UNSIGNED", default = stage.isDevelopment)
    val timeout = config.getLong("AWAKE_REQUEST_TIMEOUT_MS", default = 15_000L)
    val debugLogging = config.getBoolean("AWAKE_DEBUG_LOGGING", default = stage.isDevelopment)

    return StudioEnvironment(
        stage = stage,
        marketplaceApiUrl = customMarketplaceUrl?.trim()?.ifEmpty { null }
            ?: StudioEnvironment.defaultMarketplaceUrlFor(stage),
        allowUnsignedPlugins = allowUnsigned,
        requestTimeoutMillis = timeout,
        debugLogging = debugLogging,
    )
}
