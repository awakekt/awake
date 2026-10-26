/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import kotlinx.serialization.modules.SerializersModule

/**
 * The scene components one module provides: its generated [SceneBinding]s and its
 * [RegisteredBinding]s. The `com.awakekt.awake.plugin.scene-components` build plugin generates one
 * `object` per module, such as `SceneCoreSceneComponents`.
 *
 * Nothing installs a module by itself: an app, a scene or a capability chooses the modules it uses
 * with [SceneComponentRegistry.register], so a scene decodes only the components its code provides.
 */
interface SceneComponentModule {
    /** The module's bindings, generated and registered, sorted by their qualified names. */
    val bindings: List<SceneComponentBinding<*, *>>

    /**
     * The polymorphic serializers of the module's `@Serializable` schemas, for a `Json` built without a
     * [SceneComponentRegistry]. Registering the module registers them too, through each binding's
     * [SceneComponentBinding.serializer].
     */
    val serializersModule: SerializersModule
}
