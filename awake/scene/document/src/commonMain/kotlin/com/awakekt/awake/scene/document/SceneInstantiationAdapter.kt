/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

/**
 * Intermediate node handle produced during scene document instantiation.
 *
 * @param T Target node type.
 * @property name Node name identifier if present.
 * @property value Created node value object.
 * @property children Child node handles.
 */
data class SceneNodeHandle<T>(
    val name: String?,
    val value: T,
    val children: List<SceneNodeHandle<T>>,
)

/**
 * Adapter interface allowing custom instantiation targets for scene documents.
 *
 * @param Node Created node type.
 * @param Instance Completed scene instance type.
 */
interface SceneInstantiationAdapter<Node, Instance> {
    /** Creates a new node instance. */
    fun createNode(node: SceneNode, parent: Node?): Node

    /** Attaches a name identifier onto [node]. */
    fun attachName(node: Node, name: String)

    /** Attaches a transform descriptor onto [node]. */
    fun attachTransform(node: Node, transform: SceneTransform, parent: Node?)

    /** Attaches a component descriptor onto [node]. */
    fun attachComponent(node: Node, component: SceneComponent)

    /** Completes scene instantiation and returns the final [Instance]. */
    fun complete(roots: List<SceneNodeHandle<Node>>): Instance
}
