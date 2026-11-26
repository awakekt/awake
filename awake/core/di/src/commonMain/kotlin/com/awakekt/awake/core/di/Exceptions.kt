/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

/**
 * Thrown when attempting to resolve a dependency that is not registered.
 *
 * @param message Detail message describing the missing binding.
 */
class MissingBindingException(message: String) : RuntimeException(message)

/**
 * Thrown when a cyclic dependency is detected during resolution.
 *
 * @param message Detail message describing the dependency cycle.
 */
class CyclicDependencyException(message: String) : RuntimeException(message)

/**
 * Thrown when attempting to resolve, verify, or create a child scope on a closed [Container].
 *
 * @param message Detail message describing the invalid operation on the closed container.
 */
class ContainerClosedException(message: String) : IllegalStateException(message)
