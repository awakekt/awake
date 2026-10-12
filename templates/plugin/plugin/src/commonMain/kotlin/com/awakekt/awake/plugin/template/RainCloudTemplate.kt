/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.plugin.template

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.editor.core.plugin.EntityTemplate
import com.awakekt.awake.editor.core.plugin.EntityTemplateProvider
import com.awakekt.awake.editor.core.plugin.ProviderId
import com.awakekt.awake.editor.core.plugin.ProviderMetadata
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform

/**
 * "Rain cloud" in the host's insert menu. It adds only Core scene components, so any game can load
 * the saved scene; a plugin never adds runtime the game would need Studio to run.
 */
class RainCloudTemplate : EntityTemplateProvider {
    override val metadata = ProviderMetadata(ProviderId("${WeatherPlugin.PLUGIN_ID}.template.rain-cloud"), "Rain cloud")
    override val template = EntityTemplate(
        id = "rain-cloud",
        displayName = "Rain cloud",
        category = "Weather",
    ) { world, entity ->
        world.add(entity, Name("Rain cloud"))
        world.add(entity, Transform(position = Vec3f(0f, 8f, 0f)))
    }
}
