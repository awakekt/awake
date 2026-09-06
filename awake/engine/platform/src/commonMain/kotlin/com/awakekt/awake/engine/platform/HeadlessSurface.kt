/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform

/**
 * A surface for an engine to render into when there is no window.
 *
 * `GraphicsEngine.create` takes `Any` because a surface is whatever the host hands over -- a GLFW
 * window handle, an Android `Surface`, a canvas. This is the fourth case: nothing at all, at a
 * fixed size. A backend recognising it creates its device and swapchain headlessly and renders
 * exactly the plan it was given.
 *
 * The point is to be able to render an APP's own `RenderPlan` -- its content features, its depth
 * passes, its pipelines -- without a display. A test that hand-builds a renderer instead tests the
 * fixture it wrote, which is how a scene can look wrong on screen while every probe passes.
 */
data class HeadlessSurface(val width: Int, val height: Int)
