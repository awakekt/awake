/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

/** Pairs every field of [request], or returns why it can't, one message per problem. */
internal fun plan(request: BindingRequest): Result<BindingPlan> = Planner(request).plan()

private class Planner(private val request: BindingRequest) {
    private val errors = mutableListOf<String>()
    private val savedName = request.saved.className.simpleName
    private val liveName = request.live.className.simpleName

    /** The saved fields some live property is saved as, whether or not their types pair. */
    private val paired = mutableSetOf<String>()

    fun plan(): Result<BindingPlan> {
        val saved = request.saved.properties
        val parameters = request.live.parameters
        val fields = if (saved == null || parameters == null) {
            if (saved != null || parameters != null) errors += "$savedName and $liveName must both be objects, or both classes"
            emptyList()
        } else {
            pairLiveProperties(saved, parameters).also {
                checkSavedProperties(saved)
                checkConstructor(parameters, it)
            }
        }
        return if (errors.isEmpty()) Result.success(BindingPlan(request, fields)) else Result.failure(BindingErrors(errors))
    }

    private fun pairLiveProperties(saved: List<SavedProperty>, parameters: List<LiveParameter>): List<FieldPlan> {
        val savedByName = saved.associateBy { it.name }
        return request.live.properties.filterNot { it.runtimeOnly }.mapNotNull { property ->
            val target = property.savedAs ?: property.name
            val savedProperty = savedByName[target]
            val viaConstructor = parameters.any { it.name == property.name }
            val duplicate = savedProperty != null && !paired.add(target)
            val problem = when {
                savedProperty == null ->
                    "$liveName.${property.name} has no saved field $savedName.$target: " +
                        "add it, map it with @SavedAs, or mark it @RuntimeOnly"
                savedProperty.savedOnly -> "$savedName.$target is @SavedOnly but $liveName.${property.name} is saved as it"
                !viaConstructor && !property.writable ->
                    "$liveName.${property.name} is read-only outside its constructor, " +
                        "so a load can't restore it: make it a var or a constructor property, or mark it @RuntimeOnly"
                duplicate -> "$savedName.$target is saved from two live properties"
                else -> null
            }
            problem?.let { errors += it }
            savedProperty?.takeIf { problem == null }?.let { pair(it, property, viaConstructor) }
        }
    }

    private fun pair(saved: SavedProperty, live: LiveProperty, viaConstructor: Boolean): FieldPlan? =
        conversion(saved.type, live.type, request.converters)
            .onFailure { errors += "$savedName.${saved.name} and $liveName.${live.name}: ${it.message}" }
            .getOrNull()
            ?.let { FieldPlan(saved.name, live.name, saved.type.name.isNullable, it, viaConstructor) }

    private fun checkSavedProperties(saved: List<SavedProperty>) {
        for (property in saved) {
            when {
                property.savedOnly && !property.hasDefault -> errors += "$savedName.${property.name} is @SavedOnly, so it needs a default"
                !property.savedOnly && property.name !in paired ->
                    errors += "$savedName.${property.name} has no live property in $liveName: " +
                        "add one, map one to it with @SavedAs, or mark it @SavedOnly"
            }
        }
    }

    private fun checkConstructor(parameters: List<LiveParameter>, fields: List<FieldPlan>) {
        for (parameter in parameters) {
            val bound = fields.any { it.live == parameter.name && it.viaConstructor }
            if (!bound && !parameter.hasDefault) {
                errors += "$liveName's constructor parameter ${parameter.name} is not saved, so it needs a default"
            }
        }
    }
}

/** How a saved field of type [saved] becomes a live field of type [live]: as it is, by enum name, or through a converter. */
private fun conversion(saved: FieldType, live: FieldType, converters: List<Converter>): Result<Conversion> {
    val savedType = saved.name.copy(nullable = false)
    val liveType = live.name.copy(nullable = false)
    val converter = converters.firstOrNull { it.saved == savedType && it.live == liveType }
    val savedConstants = saved.enumConstants
    val liveConstants = live.enumConstants
    return when {
        saved.name == live.name -> Result.success(Conversion.Direct)
        saved.name.isNullable != live.name.isNullable ->
            failure("one is nullable and the other is not (${saved.name} and ${live.name})")
        converter != null -> Result.success(Conversion.Converted(converter.className))
        savedConstants != null && liveConstants != null -> {
            val unmatched = (savedConstants - liveConstants.toSet()) + (liveConstants - savedConstants.toSet())
            if (unmatched.isEmpty()) {
                Result.success(Conversion.EnumByName(savedType, liveType, savedConstants))
            } else {
                failure("the enums $savedType and $liveType differ in ${unmatched.joinToString()}")
            }
        }
        else -> failure("$savedType is not $liveType: list a SceneValueConverter<$savedType, $liveType> in converters")
    }
}

private fun failure(message: String): Result<Conversion> = Result.failure(IllegalArgumentException(message))
