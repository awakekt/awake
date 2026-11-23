/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/** The maintained [Family1] of [type] filtered by [filter]. */
fun <A : Any> World.family(type: ComponentType<A>, filter: FamilySpec): Family1<A> = when {
    filter.isEmpty -> family(type)
    else -> components.typeIdOrNull(type)?.let { cachedFamily<A>(it, filter) } ?: family(type.type, filter)
}

/** The maintained [Family2] of [typeA] and [typeB] filtered by [filter]. */
fun <A : Any, B : Any> World.family(
    typeA: ComponentType<A>,
    typeB: ComponentType<B>,
    filter: FamilySpec,
): Family2<A, B> {
    val cached = if (!filter.isEmpty) {
        val typeIdA = components.typeIdOrNull(typeA)
        val typeIdB = components.typeIdOrNull(typeB)
        if (typeIdA != null && typeIdB != null) cachedFamily<A, B>(typeIdA, typeIdB, filter) else null
    } else {
        null
    }
    return cached ?: if (filter.isEmpty) family(typeA, typeB) else family(typeA.type, typeB.type, filter)
}

/**
 * Compiled bitmask predicate evaluated incrementally by [FamilySpecCache], [Family1Cache], and
 * [Family2Cache].
 */
internal class CompiledFamilySpec(
    world: World,
    private val spec: FamilySpec,
    packedA: KClass<out Any>? = null,
    packedB: KClass<out Any>? = null,
) {
    private val allMask: LongArray
    private val oneMask: LongArray
    private val excludeMask: LongArray
    private val wide: Boolean
    private val allLow: Long
    private val oneLow: Long
    private val excludeLow: Long

    val isActive: Boolean

    init {
        val extraAll = when {
            packedA == null -> spec.all
            packedB == null -> spec.all - packedA
            else -> spec.all - packedA - packedB
        }
        allMask = buildSignatureMask(world, extraAll)
        oneMask = buildSignatureMask(world, spec.one)
        excludeMask = buildSignatureMask(world, spec.exclude)
        isActive = allMask.isNotEmpty() || oneMask.isNotEmpty() || excludeMask.isNotEmpty()
        wide = maxOf(allMask.size, oneMask.size, excludeMask.size) > 1
        allLow = allMask.firstOrNull() ?: 0L
        oneLow = oneMask.firstOrNull() ?: 0L
        excludeLow = excludeMask.firstOrNull() ?: 0L
    }

    /** Returns `true` when [entity]'s current component signature satisfies this predicate. */
    fun matches(world: World, entity: Entity): Boolean {
        if (!isActive) return true
        val signatures = world.entities
        val id = entity.id
        return if (wide) {
            (allMask.isEmpty() || signatures.containsAll(id, allMask)) &&
                (oneMask.isEmpty() || signatures.intersects(id, oneMask)) &&
                (excludeMask.isEmpty() || !signatures.intersects(id, excludeMask))
        } else {
            val signature = signatures.signatureWord(id, 0)
            (signature and allLow) == allLow &&
                (oneLow == 0L || (signature and oneLow) != 0L) &&
                (signature and excludeLow) == 0L
        }
    }

    /** Every component type whose addition or removal can change membership in this family. */
    fun allTypes(packedA: KClass<out Any>? = null, packedB: KClass<out Any>? = null): Set<KClass<out Any>> = buildSet {
        if (packedA != null) add(packedA)
        if (packedB != null) add(packedB)
        addAll(spec.all)
        addAll(spec.one)
        addAll(spec.exclude)
    }
}
