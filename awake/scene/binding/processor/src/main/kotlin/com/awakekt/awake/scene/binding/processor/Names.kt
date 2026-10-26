/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.squareup.kotlinpoet.ClassName

/** The names the processor reads and writes, in one place. */
internal object Names {
    private const val BINDING_PACKAGE = "com.awakekt.awake.scene.binding"
    private const val DOCUMENT_PACKAGE = "com.awakekt.awake.scene.document"

    val SCENE_BINDING = ClassName(BINDING_PACKAGE, "SceneBinding")
    val RUNTIME_ONLY = ClassName(BINDING_PACKAGE, "RuntimeOnly")
    val SAVED_AS = ClassName(BINDING_PACKAGE, "SavedAs")
    val SAVED_ONLY = ClassName(BINDING_PACKAGE, "SavedOnly")
    val REGISTERED_BINDING = ClassName(BINDING_PACKAGE, "RegisteredBinding")
    val UNBOUND = ClassName(BINDING_PACKAGE, "UnboundSceneComponent")
    val BINDING = ClassName(BINDING_PACKAGE, "SceneComponentBinding")
    val HOOKS = ClassName(BINDING_PACKAGE, "SceneBindingHooks")
    val CONVERTER = ClassName(BINDING_PACKAGE, "SceneValueConverter")
    val MODULE = ClassName(BINDING_PACKAGE, "SceneComponentModule")
    val CONTEXT = ClassName(BINDING_PACKAGE, "SceneResolutionContext")
    val SCENE_COMPONENT = ClassName(DOCUMENT_PACKAGE, "SceneComponent")
    val WORLD = ClassName("com.awakekt.awake.ecs", "World")
    val ENTITY = ClassName("com.awakekt.awake.ecs", "Entity")
    val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
    val SERIALIZER = ClassName("kotlinx.serialization", "KSerializer")
    val SERIALIZERS_MODULE = ClassName("kotlinx.serialization.modules", "SerializersModule")
    val K_CLASS = ClassName("kotlin.reflect", "KClass")

    /** The KSP option naming the module's generated [MODULE] object by its qualified name. */
    const val REGISTRY_OPTION = "awake.sceneComponents.registry"
}
