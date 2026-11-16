/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.dsl

import com.awakekt.awake.engine.platform.quality.TextureAnisotropy
import com.awakekt.awake.engine.platform.quality.TextureQuality
import com.awakekt.awake.engine.platform.quality.TextureSettings
import com.awakekt.awake.engine.platform.quality.TextureSettingsPersistence

/** Configures the app's [TextureSettings] in `app { textureSettings { ... } }`. */
@AwakeAppDsl
class TextureSettingsDsl internal constructor() {
    /** Loads the saved values and saves each change; `null` keeps them in memory only. */
    var persistence: TextureSettingsPersistence? = null

    /** The quality when nothing is saved; see [TextureSettings.defaultQuality] for a device's suggestion. */
    var initialQuality: TextureQuality = TextureQuality.High

    /** The filtering when nothing is saved; see [TextureSettings.defaultAnisotropy] for a device's suggestion. */
    var initialAnisotropy: TextureAnisotropy = TextureAnisotropy.X8
}

/**
 * Turns on texture settings for this app (#1660): registers one [TextureSettings] as an app service,
 * which `GraphicsEngine` applies before every frame, and returns it for a settings screen to drive.
 * An app that never calls this keeps the renderer's defaults: no anisotropic filtering, full-size
 * textures.
 */
fun AppSpecDsl.textureSettings(block: TextureSettingsDsl.() -> Unit = {}): TextureSettings {
    check(service(TextureSettings::class) == null) { "textureSettings { } is already registered for this app." }
    val config = TextureSettingsDsl().apply(block)
    val settings = TextureSettings(config.persistence, config.initialQuality, config.initialAnisotropy)
    service(TextureSettings::class, settings)
    return settings
}
