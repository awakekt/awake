/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import io.ygdrasil.kffi.memoryScope
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPULowLevelApi
import io.ygdrasil.wgpu.WGPUGlobalReport
import io.ygdrasil.wgpu.WGPURegistryReport
import io.ygdrasil.wgpu.allocate
import io.ygdrasil.wgpu.wgpuGenerateReport

/**
 * How many objects of each kind [this] instance still holds, from wgpu-native's own registry report,
 * keyed by kind and leaving out kinds with none. After a full teardown every count is gone; whatever
 * remains is a handle someone never released, and it keeps its device's native memory alive.
 */
@OptIn(WGPULowLevelApi::class)
internal fun WGPU.liveObjects(): Map<String, Long> = memoryScope { scope ->
    val report = WGPUGlobalReport.allocate(scope)
    wgpuGenerateReport(handler, report)
    val hub = report.hub
    linkedMapOf(
        "surfaces" to report.surfaces,
        "adapters" to hub.adapters,
        "devices" to hub.devices,
        "queues" to hub.queues,
        "pipelineLayouts" to hub.pipelineLayouts,
        "shaderModules" to hub.shaderModules,
        "bindGroupLayouts" to hub.bindGroupLayouts,
        "bindGroups" to hub.bindGroups,
        "commandBuffers" to hub.commandBuffers,
        "renderBundles" to hub.renderBundles,
        "renderPipelines" to hub.renderPipelines,
        "computePipelines" to hub.computePipelines,
        "querySets" to hub.querySets,
        "buffers" to hub.buffers,
        "textures" to hub.textures,
        "textureViews" to hub.textureViews,
        "samplers" to hub.samplers,
    ).mapValues { (_, registry: WGPURegistryReport) -> registry.numAllocated.toLong() }
        .filterValues { it > 0 }
}
