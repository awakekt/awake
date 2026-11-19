/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/**
 * The per-entity component calls a system makes every frame, each run once for every one of
 * [ENTITIES] entities, for the platform allocation gates to measure. None should allocate once warm.
 */
internal class LookupCalls {
    class Position(var x: Float = 0f)
    class Velocity(var x: Float = 1f)
    class Health(var value: Int = 0)

    private val world = World()
    private val entities = Array(ENTITIES) { world.create().also { world.add(it, Position()); world.add(it, Velocity()) } }
    private val health = Health()
    private val positions = arrayOf(Position(), Position())
    private val positionType: KClass<Position> = Position::class
    private val velocityType: KClass<Velocity> = Velocity::class
    private val positionHandle: ComponentType<Position> = componentType()
    var sink = 0f
        private set

    val calls: List<Pair<String, () -> Unit>> = listOf(
        "get<T>" to { for (e in entities) sink += world.get<Position>(e)!!.x },
        "get(KClass)" to { for (e in entities) sink += world.get(e, positionType)!!.x },
        "get(ComponentType)" to { for (e in entities) sink += world.get(e, positionHandle)!!.x },
        "componentStore<T>()" to { for (e in entities) sink += world.componentStore<Position>()!!.get(e)!!.x },
        "componentStore(ComponentType)" to { for (e in entities) sink += world.componentStore(positionHandle)!!.get(e)!!.x },
        "has<T>" to { for (e in entities) if (world.has<Velocity>(e)) sink += 1f },
        "add<T> then remove<T>" to { for (e in entities) { world.add(e, health); world.remove<Health>(e) } },
        "add<T> replacing" to { for (i in entities.indices) world.add(entities[i], positions[i and 1]) },
        "typeId(KClass)" to { for (e in entities) sink += world.typeId(positionType).value },
        "queryEach<A, B>" to { world.queryEach<Position, Velocity> { _, p, v -> sink += p.x + v.x } },
        "queryEach(KClass, KClass)" to { world.queryEach(positionType, velocityType) { _, p, v -> sink += p.x + v.x } },
        "family<A, B>().forEach" to { world.family<Position, Velocity>().forEach { _, p, v -> sink += p.x + v.x } },
        "queryEach<A>" to { world.queryEach<Position> { _, p -> sink += p.x } },
    )

    companion object {
        const val ENTITIES = 1_000
    }
}
