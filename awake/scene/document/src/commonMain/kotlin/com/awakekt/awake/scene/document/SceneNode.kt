/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Serializable node in a scene hierarchy.
 *
 * @property name Optional node identifier name.
 * @property transform Local 3D transform spatial orientation.
 * @property components Attached serializable components.
 * @property children Nested child nodes in the hierarchy.
 */
@Serializable
data class SceneNode(
    val name: String? = null,
    val transform: SceneTransform = SceneTransform(),
    val components: List<SceneComponent> = emptyList(),
    val children: List<SceneNode> = emptyList(),
) {
    /** Values derived from this node, newest first; not part of the node's value, its saved form or its copies. */
    @Transient
    private var derivedValues: Derived? = null

    /**
     * The value [key] derives from this node, built by [build] the first time it is asked for and kept
     * with the node after: a node is immutable, and a change makes a new node, so it never goes stale.
     *
     * For a module that reads a node the same way often, such as a canvas reading the element its
     * components make on every frame (#1621). It lives and goes with the node, and each node holds
     * its own, so there is nothing to evict. Each entry is immutable: a reader on another thread
     * sees a whole entry or none, and at worst two threads build the same value once each.
     */
    fun <T : Any> derived(key: SceneNodeView<T>, build: (SceneNode) -> T): T {
        var entry = derivedValues
        while (entry != null) {
            @Suppress("UNCHECKED_CAST") // Only [key] stores under [key], and only a T.
            if (entry.key === key) return entry.value as T
            entry = entry.next
        }
        val value = build(this)
        derivedValues = Derived(key, value, derivedValues)
        return value
    }

    private class Derived(val key: SceneNodeView<*>, val value: Any, val next: Derived?)
}

/**
 * A kind of value derived from scene nodes with [SceneNode.derived], told apart by identity: a module
 * makes one, once, for each kind it derives.
 */
class SceneNodeView<T : Any>
