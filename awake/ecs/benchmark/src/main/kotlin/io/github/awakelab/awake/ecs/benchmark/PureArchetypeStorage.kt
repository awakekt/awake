/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer

/**
 * Benchmark-only pure-archetype storage for stable render components plus dynamic tag bits.
 *
 * Every distinct [PureArchetypeTable.signature] owns a separate dense table. Changing a tag
 * physically moves the entity's [Transform] and [MeshRenderer] references between tables,
 * including swap-removal bookkeeping for the row displaced in the source table. This models
 * the structural cost the earlier single-table prototype omitted; it is deliberately not a
 * production ECS implementation.
 */
class PureArchetypeStorage(
    entityCapacity: Int,
    private val tableCapacityHint: Int = DEFAULT_TABLE_CAPACITY,
) {
    private val tablesBySignature = HashMap<Int, PureArchetypeTable>()
    private val tables = ArrayList<PureArchetypeTable>()
    private val tableIndexByEntity = IntArray(entityCapacity) { ABSENT }
    private val rowByEntity = IntArray(entityCapacity) { ABSENT }

    /** Number of live entities across every archetype table. */
    var size: Int = 0
        private set

    /** Number of archetype tables created by the observed tag signatures. */
    val tableCount: Int
        get() = tables.size

    /** Adds one entity to the table selected by [tagSignature]. */
    fun add(
        entityId: Int,
        transform: Transform,
        meshRenderer: MeshRenderer,
        tagSignature: Int = 0,
    ) {
        require(tableIndexByEntity[entityId] == ABSENT) { "Entity $entityId is already stored" }
        val table = tableFor(tagSignature)
        val row = table.add(entityId, transform, meshRenderer)
        tableIndexByEntity[entityId] = table.index
        rowByEntity[entityId] = row
        size += 1
    }

    /** Adds [tagBit], migrating the full stable row when it changes the signature. */
    fun addTag(entityId: Int, tagBit: Int) {
        val source = tableForEntity(entityId)
        val targetSignature = source.signature or tagBit
        if (targetSignature != source.signature) migrate(entityId, source, targetSignature)
    }

    /** Removes [tagBit], migrating the full stable row when it changes the signature. */
    fun removeTag(entityId: Int, tagBit: Int) {
        val source = tableForEntity(entityId)
        val targetSignature = source.signature and tagBit.inv()
        if (targetSignature != source.signature) migrate(entityId, source, targetSignature)
    }

    /** Removes an entity and repairs the location of any row moved by swap removal. */
    fun remove(entityId: Int): Boolean {
        val tableIndex = tableIndexByEntity.getOrElse(entityId) { ABSENT }
        if (tableIndex == ABSENT) return false
        val table = tables[tableIndex]
        val removedRow = rowByEntity[entityId]
        repairMovedRow(table.removeAt(removedRow), removedRow)
        tableIndexByEntity[entityId] = ABSENT
        rowByEntity[entityId] = ABSENT
        size -= 1
        return true
    }

    /** Returns the table at [index] for allocation-free benchmark iteration. */
    fun tableAt(index: Int): PureArchetypeTable = tables[index]

    /** Counts entities in tables satisfying required and excluded tag masks. */
    fun matchingEntityCount(requiredTags: Int, excludedTags: Int): Int {
        var result = 0
        var tableIndex = 0
        while (tableIndex < tables.size) {
            val table = tables[tableIndex]
            if (table.matches(requiredTags, excludedTags)) result += table.size
            tableIndex += 1
        }
        return result
    }

    private fun migrate(entityId: Int, source: PureArchetypeTable, targetSignature: Int) {
        val sourceRow = rowByEntity[entityId]
        val transform = source.transformAt(sourceRow)
        val meshRenderer = source.meshRendererAt(sourceRow)
        repairMovedRow(source.removeAt(sourceRow), sourceRow)

        val target = tableFor(targetSignature)
        val targetRow = target.add(entityId, transform, meshRenderer)
        tableIndexByEntity[entityId] = target.index
        rowByEntity[entityId] = targetRow
    }

    private fun repairMovedRow(movedEntityId: Int, movedToRow: Int) {
        if (movedEntityId != ABSENT) rowByEntity[movedEntityId] = movedToRow
    }

    private fun tableForEntity(entityId: Int): PureArchetypeTable {
        val tableIndex = tableIndexByEntity.getOrElse(entityId) { ABSENT }
        require(tableIndex != ABSENT) { "Entity $entityId is not stored" }
        return tables[tableIndex]
    }

    private fun tableFor(signature: Int): PureArchetypeTable =
        tablesBySignature.getOrPut(signature) {
            PureArchetypeTable(tables.size, signature, tableCapacityHint).also(tables::add)
        }

    private companion object {
        const val ABSENT = -1
        const val DEFAULT_TABLE_CAPACITY = 16
    }
}

/** Dense component columns for one exact dynamic-tag signature. */
class PureArchetypeTable(
    /** Stable index used by [PureArchetypeStorage]'s entity-location map. */
    val index: Int,
    /** Dynamic tag bits shared by every entity in this table. */
    val signature: Int,
    initialCapacity: Int,
) {
    private var entityIds = IntArray(initialCapacity)
    private var transforms = arrayOfNulls<Transform>(initialCapacity)
    private var meshRenderers = arrayOfNulls<MeshRenderer>(initialCapacity)

    /** Number of occupied dense rows. */
    var size: Int = 0
        private set

    /** Appends a row and returns its dense index. */
    fun add(entityId: Int, transform: Transform, meshRenderer: MeshRenderer): Int {
        ensureCapacity(size + 1)
        val row = size
        entityIds[row] = entityId
        transforms[row] = transform
        meshRenderers[row] = meshRenderer
        size += 1
        return row
    }

    /** Returns the entity identifier at [row]. */
    fun entityAt(row: Int): Int = entityIds[row]

    /** Returns the transform reference at [row]. */
    fun transformAt(row: Int): Transform = requireNotNull(transforms[row])

    /** Returns the mesh renderer reference at [row]. */
    fun meshRendererAt(row: Int): MeshRenderer = requireNotNull(meshRenderers[row])

    /** Whether this table satisfies an archetype query's required and excluded tag masks. */
    fun matches(requiredTags: Int, excludedTags: Int): Boolean =
        (signature and requiredTags) == requiredTags && (signature and excludedTags) == 0

    /**
     * Swap-removes [row] and returns the entity moved into it, or `-1` when no row moved.
     */
    fun removeAt(row: Int): Int {
        val lastRow = size - 1
        var movedEntityId = ABSENT
        if (row != lastRow) {
            movedEntityId = entityIds[lastRow]
            entityIds[row] = movedEntityId
            transforms[row] = transforms[lastRow]
            meshRenderers[row] = meshRenderers[lastRow]
        }
        transforms[lastRow] = null
        meshRenderers[lastRow] = null
        size -= 1
        return movedEntityId
    }

    private fun ensureCapacity(required: Int) {
        if (required <= entityIds.size) return
        val newCapacity = maxOf(required, entityIds.size * CAPACITY_GROWTH_FACTOR)
        entityIds = entityIds.copyOf(newCapacity)
        transforms = transforms.copyOf(newCapacity)
        meshRenderers = meshRenderers.copyOf(newCapacity)
    }

    private companion object {
        const val ABSENT = -1
        const val CAPACITY_GROWTH_FACTOR = 2
    }
}
