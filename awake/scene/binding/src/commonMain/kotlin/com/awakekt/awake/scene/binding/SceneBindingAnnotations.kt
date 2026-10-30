/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import kotlin.reflect.KClass

/**
 * Generates the [SceneComponentBinding] between this scene schema and the live ECS [component] when
 * the module applies the `com.awakekt.awake.plugin.scene-components` build plugin.
 *
 * It goes on the schema, the serializable [com.awakekt.awake.scene.document.SceneComponent] the scene
 * module owns, because the live component may come from a capability module that knows nothing of
 * scenes. The generated binding copies every field both ways by name and fails the build when a field
 * has no counterpart on the other side, or the two types differ with no converter between them:
 *
 * - each schema constructor property is copied from the live property of the same name, or of the name
 *   a [SavedAs] maps it to, unless it is [SavedOnly];
 * - each live constructor parameter and each public property that holds state is copied from the schema,
 *   unless it is [RuntimeOnly] or named in [runtimeOnly];
 * - equal types are copied as they are, enums with the same constants are mapped by name, and any other
 *   pair needs one of [converters].
 *
 * With [whole], the schema wraps the live value in that constructor property instead. Loading unwraps
 * it and saving wraps it, optionally through [converters]; the live value is not constructed or copied
 * field by field. The named property must be readable and match the non-null live type, and every
 * other schema constructor parameter must have a default (used on export). Field-level [runtimeOnly]
 * and [renamed] options cannot be combined with [whole]. Hooks still run around both conversions.
 *
 * The binding is an `object` named [name], `<Component>Binding` by default, in the schema's package. It
 * exposes `S.toComponent()` and `C.toSceneComponent()`, uses the schema's `serializer()`, and is listed in
 * the module's generated [SceneComponentModule]. A binding whose conversion needs logic adds [hooks];
 * one that resolves assets, links entities or creates native objects stays hand-written and is listed
 * with [RegisteredBinding].
 *
 * @property component The live ECS component class the schema binds to.
 * @property name The generated binding's simple name; empty for `<Component>Binding`.
 * @property converters Objects converting a saved field's type to its live field's type and back.
 * @property hooks An object adjusting the generated copy; the default, [SceneBindingHooks] itself, is none.
 * @property runtimeOnly Live properties that are never saved, for a live component the module can't
 *   annotate with [RuntimeOnly].
 * @property renamed Live properties saved under another name, for a live component the module can't
 *   annotate with [SavedAs]; each gives the live [SavedAs.property].
 * @property whole The schema constructor property containing the whole live value; empty for field copying.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SceneBinding(
    val component: KClass<*>,
    val name: String = "",
    val converters: Array<KClass<out SceneValueConverter<*, *>>> = [],
    val hooks: KClass<out SceneBindingHooks<*, *>> = SceneBindingHooks::class,
    val runtimeOnly: Array<String> = [],
    val renamed: Array<SavedAs> = [],
    val whole: String = "",
)

/**
 * Marks a live component property as state that is never saved, such as a pointer press or a cached
 * handle. A [SceneBinding] neither writes nor reads it, so it keeps the value its constructor gives it.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class RuntimeOnly

/**
 * Saves a live component property under the schema property [name] instead of its own, so a field can
 * be renamed on either side without changing the other. On a live property, [property] stays empty;
 * inside [SceneBinding.renamed], it names the live property.
 *
 * A key an older document used is kept readable on the schema with kotlinx.serialization's `JsonNames`.
 *
 * @property name The schema property the live one is saved as.
 * @property property The live property, when this is listed in [SceneBinding.renamed].
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class SavedAs(val name: String, val property: String = "")

/**
 * Marks a schema constructor property with no live counterpart, such as a legacy field an older
 * document wrote. A [SceneBinding] doesn't copy it: an exported schema gets its default, and its
 * [SceneBindingHooks.afterCopyToLive] may read it to migrate the old form.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class SavedOnly

/**
 * Lists a hand-written [SceneComponentBinding] `object` in its module's generated
 * [SceneComponentModule], beside the generated ones, so a module has one registry whichever way its
 * bindings are written.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class RegisteredBinding

/**
 * Marks a scene schema the module deliberately gives no binding, so the build's check that every scene
 * component in the module has one passes, for example a schema another module binds.
 *
 * @property reason Why the schema has no binding here.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class UnboundSceneComponent(val reason: String)
