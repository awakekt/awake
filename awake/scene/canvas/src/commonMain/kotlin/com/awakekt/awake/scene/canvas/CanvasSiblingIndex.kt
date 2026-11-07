/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Where declarative canvas content placed this entity among its siblings: the order it declared
 * them in. Runtime only: it has no scene component binding and no serializer, so a scene or prefab
 * never saves it.
 *
 * Siblings are otherwise ordered as the world stores their `Transform`s, which a content block
 * cannot rearrange: storage swap-removes, so moving one node by removing and adding its transform
 * would move an unrelated node too.
 *
 * Among siblings:
 * - Two with an index are ordered by it, and those with the same index, as two content blocks under
 *   one root can give, by storage order.
 * - One without an index comes before one with: content declared under a node draws over what the
 *   scene already had there.
 * - Two without keep storage order, as before.
 *
 * Drawing and hit testing sort with the same comparator, so the element drawn on top takes the
 * pointer. Immutable, and replaced whole, so the store's revision tells a cached order it changed.
 *
 * @property index The place among its siblings, from 0.
 */
internal class CanvasSiblingIndex(val index: Int)

/**
 * The siblings' order by their [CanvasSiblingIndex], one without coming first, or 0 when both have
 * the same or neither has one, leaving them to the world's storage order.
 */
internal fun World.compareSiblingIndex(a: Entity, b: Entity): Int =
    (get<CanvasSiblingIndex>(a)?.index ?: UNINDEXED).compareTo(get<CanvasSiblingIndex>(b)?.index ?: UNINDEXED)

/** What a sibling with no index is ordered as: before every indexed one. */
private const val UNINDEXED = -1
