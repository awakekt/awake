/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core

import com.awakekt.awake.ecs.Tags
import com.awakekt.awake.scene.binding.SavedAs
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --8<-- [start:tag]
/**
 * The roles a scene node plays to gameplay, such as `enemy` or `pickup`, saved with the scene. It
 * becomes the entity's [Tags], which gameplay finds with `World.withTag` and `World.hasTag`.
 *
 * A prefab's tags ride on its root entity, and tags on the node that links the prefab tag that node.
 *
 * The generated `TagBinding` copies it to and from the entity's [Tags], whose `names` it saves as [tags].
 *
 * @property tags The node's tags, each one [Tags.isValid] accepts.
 */
@Serializable
@SerialName("tag")
@SceneBinding(Tags::class, name = "TagBinding", renamed = [SavedAs("tags", property = "names")])
data class SceneTag(val tags: Set<String> = emptySet()) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = tags.filterNot(Tags::isValid).map { tag ->
        SceneValidationIssue(path, "tag.tags: \"$tag\" is not a tag: use letters, digits, '_', '.' and '-', starting with a letter, digit or '_'")
    }
}
// --8<-- [end:tag]
