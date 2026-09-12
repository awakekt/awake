/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Marker interface for optional hardware capabilities exposed by a [GpuDevice].
 *
 * Backends implement capability sub-interfaces to expose advanced features (e.g. compute pipelines,
 * bindless descriptors, explicit memory barriers, timeline semaphores) without forcing downlevel
 * backends (like WebGPU or mobile drivers) to implement what they cannot support.
 *
 * Core engine code feature-detects via [GpuDevice.capability] and gracefully falls back when
 * a capability is absent, ensuring the core never requires what downlevel backends cannot do.
 */
interface GpuCapability

/**
 * Key identifying a specific [GpuCapability] type [T].
 *
 * Capabilities should declare companion or object instances implementing this interface:
 * ```kotlin
 * interface BindlessDescriptors : GpuCapability {
 *     companion object : GpuCapabilityKind<BindlessDescriptors>
 * }
 * ```
 */
interface GpuCapabilityKind<T : GpuCapability>
