/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs

import kotlin.reflect.KClass

/**
 * Public facade for the ECS world. Coordinates internal managers for entity lifecycle,
 * component storage, and query caching.
 *
 * Not thread-safe; this ECS is single-threaded by design (see the `game-framework-dev`/
 * `ecs-dev` agent docs).
 */
class World {
    @PublishedApi internal val entities = EntityArena()

    @PublishedApi internal val components = ComponentRegistry()
    private val collector = QueryCollector(entities, components)
    private val familyRegistry = FamilyRegistry(this, collector)
    private val queryCache = QueryCache { types -> collector.collect(types) }

    /**
     * Creates a new entity in this world.
     *
     * @return a stable [Entity] handle.
     */
    fun create(): Entity {
        val entity = entities.create()
        queryCache.markEmptyQueriesDirty()
        return entity
    }

    /**
     * Destroys an entity and recycles its ID.
     *
     * Recycling the id is safe, and the reason is worth stating here rather than only on [Entity]:
     * the generation beside the id is bumped, so the recycled id can never produce a handle equal
     * to the destroyed one and [isAlive] rejects the old handle. A caller holding an [Entity] is
     * therefore safe across a destroy; a caller holding a bare `Int` id is **not**, and must come
     * back through [entityById] before trusting it.
     *
     * @param entity the entity to destroy.
     * @return true if the entity was alive and successfully destroyed.
     */
    fun destroy(entity: Entity): Boolean {
        if (!entities.isAlive(entity)) {
            return false
        }

        familyRegistry.removeEntity(entity)
        components.removeEntity(entity, entities.signature(entity.id))

        val destroyed = entities.destroy(entity)
        if (destroyed) {
            queryCache.markAllQueriesDirty()
        }
        return destroyed
    }

    /**
     * Returns true if the entity handle is still valid and has not been destroyed.
     */
    fun isAlive(entity: Entity): Boolean = entities.isAlive(entity)

    /**
     * The live handle for [id], or `null` when nothing with that id is alive.
     *
     * A raw id is not an entity: [Entity] packs a generation beside it so a recycled id cannot
     * alias an older handle, and only the arena knows the current generation. Anything holding an
     * id rather than a handle -- a picker returning a hit, a serialized reference -- has to come
     * back through here before it can be trusted.
     */
    fun entityById(id: Int): Entity? {
        if (!entities.isAlive(id)) return null
        return entities.entity(id)
    }

    /**
     * Adds a new component of type [T] to the entity.
     *
     * Falls back to reflective construction if no pool is registered; use a factory
     * overload or [registerPool] for iOS/wasmJs compatibility.
     *
     * @return the new component instance.
     */
    inline fun <reified T : Any> add(entity: Entity): T = add(entity, T::class)

    /**
     * Adds a new component of the specified [type] to the entity.
     *
     * @return the new component instance.
     */
    fun <T : Any> add(entity: Entity, type: KClass<T>): T {
        val instance = components.pool(type).obtain() as T
        add(entity, type, instance)
        return instance
    }

    /**
     * Adds the provided [component] instance to the entity.
     *
     * @return the previous component of this type, or null if none was present.
     */
    inline fun <reified T : Any> add(entity: Entity, component: T): T? {
        val typeId = components.typeIdForKey(componentTypeKey<T>()) { T::class }
        val store = components.storeForKey(typeId) { T::class }
        return addInternal(entity, typeId, store, component)
    }

    /**
     * Adds the provided [component] instance of the specified [type] to the entity.
     *
     * @return the previous component of this type, or null if none was present.
     */
    fun <T : Any> add(entity: Entity, type: KClass<T>, component: T): T? {
        val typeId = components.typeId(type)
        return addInternal(entity, typeId, components.store(typeId, type), component)
    }

    /**
     * Internal implementation for adding a component.
     */
    @PublishedApi
    internal fun <T : Any> addInternal(
        entity: Entity,
        typeId: ComponentTypeId,
        store: ComponentStore<T>,
        component: T,
    ): T? {
        requireAlive(entity)
        val previous = store.add(entity, component)
        if (previous == null) {
            entities.markComponentAdded(entity.id, typeId)
            queryCache.markAllQueriesDirty()
            familyRegistry.addComponent(entity, typeId, component)
        } else {
            components.recycle(typeId, previous)
            familyRegistry.replaceComponent(entity, typeId, component)
        }
        return previous
    }

    /**
     * Returns the component of type [T] attached to the entity, or null if absent.
     */
    inline fun <reified T : Any> get(entity: Entity): T? {
        val typeId = components.typeIdForKeyOrNull(componentTypeKey<T>()) ?: return null
        return getInternal<T>(entity, typeId)
    }

    /**
     * Returns the component of the specified [type] attached to the entity, or null if absent.
     */
    fun <T : Any> get(entity: Entity, type: KClass<T>): T? {
        val typeId = components.typeIdOrNull(type) ?: return null
        return getInternal<T>(entity, typeId)
    }

    /**
     * Internal implementation for retrieving a component.
     */
    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> getInternal(entity: Entity, typeId: ComponentTypeId): T? {
        if (!entities.isAlive(entity)) {
            return null
        }
        return components.storeOrNull<T>(typeId)?.get(entity)
    }

    /**
     * Removes the component of type [T] from the entity and recycles it.
     *
     * @return the removed component instance, or null if none was present.
     */
    inline fun <reified T : Any> remove(entity: Entity): T? {
        val typeId = components.typeIdForKeyOrNull(componentTypeKey<T>()) ?: return null
        return removeInternal(entity, typeId)
    }

    /**
     * Removes the component of the specified [type] from the entity and recycles it.
     *
     * @return the removed component instance, or null if none was present.
     */
    fun <T : Any> remove(entity: Entity, type: KClass<T>): T? {
        val typeId = components.typeIdOrNull(type) ?: return null
        return removeInternal(entity, typeId)
    }

    /**
     * Internal implementation for removing a component.
     */
    @PublishedApi
    internal fun <T : Any> removeInternal(entity: Entity, typeId: ComponentTypeId): T? {
        if (!entities.isAlive(entity)) {
            return null
        }
        val removed = components.storeOrNull<T>(typeId)?.remove(entity)
        if (removed != null) {
            entities.markComponentRemoved(entity.id, typeId)
            queryCache.markAllQueriesDirty()
            familyRegistry.removeComponent(entity, typeId)
            components.recycle(typeId, removed)
        }
        return removed
    }

    /**
     * Returns true if the entity has a component of type [T].
     */
    inline fun <reified T : Any> has(entity: Entity): Boolean {
        val typeId = components.typeIdForKeyOrNull(componentTypeKey<T>()) ?: return false
        return hasInternal(entity, typeId)
    }

    /**
     * Returns true if the entity has a component of the specified [type].
     */
    fun has(entity: Entity, type: KClass<out Any>): Boolean {
        val typeId = components.typeIdOrNull(type) ?: return false
        return hasInternal(entity, typeId)
    }

    /**
     * Internal implementation for checking component presence.
     */
    @PublishedApi
    internal fun hasInternal(entity: Entity, typeId: ComponentTypeId): Boolean = entities.has(entity, typeId)

    /**
     * Returns a list of entities that have all of the specified component [types].
     *
     * Cost: invalidates query cache and rescans if structural changes occurred since last query.
     */
    fun query(vararg types: KClass<out Any>): List<Entity> = queryCache.query(types.toSet())

    /**
     * Iterates every entity carrying the specified [type].
     *
     * Uses a maintained family cache for zero-allocation iteration.
     */
    fun <A : Any> queryEach(type: KClass<A>, block: (Entity, A) -> Unit) {
        familyRegistry.familyCache(type).forEach(block)
    }

    /**
     * Iterates every entity carrying the specified type [A].
     *
     * Uses a maintained family cache for zero-allocation iteration.
     */
    inline fun <reified A : Any> queryEach(noinline block: (Entity, A) -> Unit) {
        queryEach(A::class, block)
    }

    /**
     * Returns a maintained [Family1] for the specified [type].
     */
    fun <A : Any> family(type: KClass<A>): Family1<A> = Family1(familyRegistry.familyCache(type))

    /**
     * Returns a maintained [Family1] for the specified type [A].
     */
    inline fun <reified A : Any> family(): Family1<A> = family(A::class)

    /**
     * Iterates every entity carrying both [typeA] and [typeB].
     *
     * Uses a maintained family cache for zero-allocation iteration.
     */
    fun <A : Any, B : Any> queryEach(
        typeA: KClass<A>,
        typeB: KClass<B>,
        block: (Entity, A, B) -> Unit,
    ) {
        familyRegistry.familyCache(typeA, typeB).forEach(block)
    }

    /**
     * Iterates every entity carrying both type [A] and [B].
     *
     * Uses a maintained family cache for zero-allocation iteration.
     */
    inline fun <reified A : Any, reified B : Any> queryEach(noinline block: (Entity, A, B) -> Unit) {
        queryEach(A::class, B::class, block)
    }

    /**
     * Returns a maintained [Family2] for the specified [typeA] and [typeB].
     */
    fun <A : Any, B : Any> family(typeA: KClass<A>, typeB: KClass<B>): Family2<A, B> = Family2(familyRegistry.familyCache(typeA, typeB))

    /**
     * Returns a maintained [Family2] for the specified types [A] and [B].
     */
    inline fun <reified A : Any, reified B : Any> family(): Family2<A, B> = family(A::class, B::class)

    /**
     * Returns a maintained [Family] matching the specified configuration.
     */
    fun family(configure: FamilySpecBuilder.() -> Unit): Family {
        val spec = FamilySpecBuilder().apply(configure).build()
        return Family(familyRegistry.familySpecCache(spec))
    }

    /**
     * Returns a list of entities matching the specified type [T].
     */
    inline fun <reified T : Any> query(): List<Entity> = query(T::class)

    /**
     * Registers a factory function for obtaining pooled instances of the specified [type].
     */
    fun registerPool(type: KClass<out Any>, factory: () -> Any) {
        // registerPool only ever stores/reads this pool keyed by `type` itself, and `factory`
        // already produces values of that same runtime type, so widening KClass<out Any> to
        // KClass<Any> here changes no actual instance, only the static type-parameter bound.
        @Suppress("UNCHECKED_CAST")
        components.registerPool(type as KClass<Any>, factory)
    }

    /**
     * Registers a factory function for obtaining pooled instances of type [T].
     */
    inline fun <reified T : Any> registerPool(noinline factory: () -> T) {
        registerPool(T::class, factory)
    }

    /**
     * Destroys all entities and clears all component storage.
     */
    fun clear() {
        entities.clear()
        components.clear()
        familyRegistry.clear()
        queryCache.clear()
    }

    /**
     * Returns the number of entities carrying a component of the specified [type].
     */
    fun componentCount(type: KClass<out Any>): Int = components.componentCount(type)

    /**
     * Returns the [ComponentStorageKind] currently selected for type [T].
     */
    inline fun <reified T : Any> storageKind(): ComponentStorageKind = storageKind(T::class)

    /**
     * Returns the [ComponentStorageKind] currently selected for the specified [type].
     */
    fun storageKind(type: KClass<out Any>): ComponentStorageKind = components.storageKind(type)

    /**
     * Returns diagnostic storage information for all registered component types.
     */
    fun describeStorage(): List<ComponentStorageInfo> = components.storageInfo()

    /**
     * Returns the component types attached to the [entity], in registration order, or an empty
     * list if the entity is not alive.
     *
     * This is the answer to "what is on this entity" -- an editor inspector, a debug overlay, a
     * serializer deciding what to write. [inspectStorage] returns the same set with storage kinds
     * and per-type counts attached, which is a question about performance rather than about the
     * entity.
     *
     * Types, not values: there is no type-safe way to hand back a heterogeneous set of components.
     * Read each one back with [get].
     */
    fun componentTypes(entity: Entity): List<KClass<out Any>> {
        if (!entities.isAlive(entity)) return emptyList()
        return components.componentTypes(entities.signature(entity.id))
    }

    /**
     * Returns diagnostic storage information for the components attached to the [entity].
     *
     * For the component types alone, prefer [components]: it answers what the entity is without
     * allocating the storage kind and count of each type.
     */
    fun inspectStorage(entity: Entity): EntityStorageInfo? {
        if (!entities.isAlive(entity)) return null
        return EntityStorageInfo(entity, components.storageInfo(entities.signature(entity.id)))
    }

    /**
     * Returns the raw [ComponentStore] for the specified [type].
     */
    // No cast actually happens on this line -- components.store(typeId, type) already
    // returns ComponentStore<T> via its own generic signature. Kept here only because
    // this delegates into ComponentRegistry.store, whose real UNCHECKED_CAST this mirrors.
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> store(type: KClass<T>): ComponentStore<T> = components.store(typeId(type), type)

    /**
     * Returns the raw [ComponentStore] for the specified [typeId].
     */
    @PublishedApi
    internal fun <T : Any> store(typeId: ComponentTypeId, type: KClass<T>): ComponentStore<T> = components.store(typeId, type)

    /**
     * Returns the raw [ComponentStore] for the specified [typeId], or null if uninitialized.
     */
    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> storeOrNull(typeId: ComponentTypeId): ComponentStore<T>? = components.storeOrNull(typeId)

    /**
     * Returns the internal bitmask signature for the entity at [id].
     */
    internal fun getSignature(id: Int): Long = entities.signature(id)

    private fun requireAlive(entity: Entity) {
        require(entities.isAlive(entity)) { "Entity is not alive: $entity" }
    }

    /**
     * Returns the unique [ComponentTypeId] for the specified [type].
     */
    fun typeId(type: KClass<out Any>): ComponentTypeId = components.typeId(type)

    // --- Helpers for EcsOptimizationTest.kt that expects overloads taking ComponentTypeId

    /**
     * Optimized [add] using a pre-resolved [typeId].
     */
    inline fun <reified T : Any> add(entity: Entity, typeId: ComponentTypeId): T {
        val instance = components.pool(typeId)?.obtain() ?: components.pool(T::class).obtain()

        @Suppress("UNCHECKED_CAST")
        val typedInstance = instance as T
        add(entity, typeId, typedInstance)
        return typedInstance
    }

    /**
     * Optimized [add] using a pre-resolved [typeId] and provided [component].
     */
    inline fun <reified T : Any> add(entity: Entity, typeId: ComponentTypeId, component: T): T? {
        val store = components.storeOrNull<T>(typeId) ?: components.store(typeId, T::class)
        return addInternal(entity, typeId, store, component)
    }

    /**
     * Optimized [get] using a pre-resolved [typeId].
     */
    inline fun <reified T : Any> get(entity: Entity, typeId: ComponentTypeId): T? = getInternal<T>(entity, typeId)

    /**
     * Optimized [remove] using a pre-resolved [typeId].
     */
    inline fun <reified T : Any> remove(entity: Entity, typeId: ComponentTypeId): T? = removeInternal(entity, typeId)

    /**
     * Optimized [has] using a pre-resolved [typeId].
     */
    fun has(entity: Entity, typeId: ComponentTypeId): Boolean = hasInternal(entity, typeId)
}
