/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Tags
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * The roles a scene node plays to gameplay, such as `enemy` or `pickup`, saved with the scene. It
 * becomes the entity's [Tags], which gameplay finds with `World.withTag` and `World.hasTag`.
 *
 * A prefab's tags ride on its root entity, and tags on the node that links the prefab tag that node.
 *
 * @property tags The node's tags, each one [Tags.isValid] accepts.
 */
@Serializable
@SerialName("tag")
data class SceneTag(val tags: Set<String> = emptySet()) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = tags.filterNot(Tags::isValid).map { tag ->
        SceneValidationIssue(path, "tag.tags: \"$tag\" is not a tag: use letters, digits, '_', '.' and '-', starting with a letter, digit or '_'")
    }
}

/** Binds a scene node's [SceneTag] to its entity's [Tags], both ways. */
object TagBinding : SceneComponentBinding<Tags, SceneTag> {
    override val componentClass: KClass<Tags> = Tags::class
    override val schemaClass: KClass<SceneTag> = SceneTag::class
    override val serializer = SceneTag.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneTag, context: SceneResolutionContext) {
        world.add(entity, Tags(component.tags))
    }

    override fun export(world: World, entity: Entity, component: Tags): SceneTag = SceneTag(component.names)
}
