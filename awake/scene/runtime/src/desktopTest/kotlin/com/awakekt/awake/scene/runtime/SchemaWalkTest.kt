/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema
import com.awakekt.awake.scene.document.SceneComponentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The numeric guard is only as good as the walk under it. These pin the shapes it must reach: a vector's
 * and a colour's components, a scalar list element, a vector inside an object that is a list element, and
 * a map value. A new numeric property in any of them cannot slip past the guard.
 */
class SchemaWalkTest {
    private fun property(
        name: String,
        kind: PropertyKind,
        children: List<PropertySchema> = emptyList(),
        element: PropertySchema? = null,
    ) = PropertySchema(name, kind, typeName = name, nullable = false, required = false, default = null, children = children, element = element)

    private fun vector(name: String) = property(
        name,
        PropertyKind.Vector3,
        children = listOf("x", "y", "z").map { property(it, PropertyKind.Float) },
    )

    private fun numericPaths(root: PropertySchema) = descendantsOf("c", root).filter { it.isNumeric }.map { it.path }

    @Test
    fun aVectorsAndAColoursComponentsAreReached() {
        val root = property(
            "c",
            PropertyKind.Object,
            children = listOf(
                vector("eye"),
                property("tint", PropertyKind.Color, children = listOf("r", "g", "b", "a").map { property(it, PropertyKind.Float) }),
            ),
        )

        assertEquals(
            listOf("c.eye.x", "c.eye.y", "c.eye.z", "c.tint.r", "c.tint.g", "c.tint.b", "c.tint.a"),
            numericPaths(root),
        )
    }

    @Test
    fun aScalarListElementIsReached() {
        val root = property(
            "c",
            PropertyKind.Object,
            children = listOf(property("samples", PropertyKind.List, element = property("element", PropertyKind.Int))),
        )

        assertEquals(listOf("c.samples[]"), numericPaths(root))
    }

    @Test
    fun aVectorInsideAnObjectThatIsAListElementIsReached() {
        val stop = property("element", PropertyKind.Object, children = listOf(vector("min"), property("time", PropertyKind.Float)))
        val root = property("c", PropertyKind.Object, children = listOf(property("stops", PropertyKind.List, element = stop)))

        assertEquals(listOf("c.stops[].min.x", "c.stops[].min.y", "c.stops[].min.z", "c.stops[].time"), numericPaths(root))
    }

    @Test
    fun aMapValueIsReachedLikeAListElement() {
        val root = property(
            "c",
            PropertyKind.Object,
            children = listOf(property("weights", PropertyKind.Map, element = property("element", PropertyKind.Float))),
        )

        assertEquals(listOf("c.weights[]"), numericPaths(root))
    }

    @Test
    fun theCatalogsEveryNumericLeafIsSeenNotOnlyTheTopLevelOnes() {
        installEveryComponentKit()
        val seen = SceneComponentCatalog.schemas().flatMap { (id, schema) -> descendantsOf(id, schema) }.filter { it.isNumeric }

        // Counted a second way, by an explicit stack, so narrowing the walk above cannot also narrow this.
        var counted = 0
        val stack = ArrayDeque<PropertySchema>()
        SceneComponentCatalog.schemas().values.forEach { stack.add(it) }
        while (stack.isNotEmpty()) {
            val schema = stack.removeLast()
            stack += schema.children
            schema.element?.let(stack::add)
            if (schema.kind == PropertyKind.Float || schema.kind == PropertyKind.Int) counted++
        }

        assertEquals(counted, seen.size)
        assertTrue(seen.any { it.path == "camera.eye.x" } && seen.any { it.path == "light.color.r" }, "vector and colour components are numeric leaves")
        assertTrue(seen.any { it.path.endsWith("[]") }, "a scalar list element is a numeric leaf")
    }
}
