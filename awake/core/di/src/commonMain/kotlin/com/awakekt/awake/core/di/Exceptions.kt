/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

/**
 * Thrown when attempting to resolve a dependency that is not registered.
 */
class MissingBindingException(message: String) : RuntimeException(message)

/**
 * Thrown when a cyclic dependency is detected during resolution.
 */
class CyclicDependencyException(message: String) : RuntimeException(message)
