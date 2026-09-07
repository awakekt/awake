/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.logging.Logger
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import objcnames.protocols.MTLDeviceProtocol
import platform.CoreFoundation.CFTimeInterval
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.QuartzCore.CADisplayLink
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIEvent
import platform.UIKit.UIKeyInputProtocol
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIWindow

@OptIn(ExperimentalForeignApi::class)
class VulkanMetalView(
    frame: CValue<CGRect>,
    private val input: Input,
    private val onCreate: (metalLayer: CAMetalLayer) -> Unit,
    private val onUpdate: (deltaSeconds: Float) -> Unit,
    private val onResize: (width: Int, height: Int) -> Unit,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
) : UIView(frame),
    UIKeyInputProtocol {

    private val logger = Logger("VulkanMetalView")

    val metalLayer = CAMetalLayer().apply {
        val dev = MTLCreateSystemDefaultDevice()
        if (dev != null) {
            @Suppress("UNCHECKED_CAST")
            setDevice(dev as Any as MTLDeviceProtocol)
        } else {
            logger.error { "MTLCreateSystemDefaultDevice() returned null; Metal is unavailable on this device." }
        }
    }

    private var displayLink: CADisplayLink? = null
    private var previousTimestamp: CFTimeInterval = 0.0
    private var created = false
    private var textInputWasFocused = false

    init {
        contentScaleFactor = UIScreen.mainScreen.scale
        layer.addSublayer(metalLayer)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun layoutSubviews() {
        super.layoutSubviews()
        val scale = UIScreen.mainScreen.scale
        val width: Double
        val height: Double
        bounds.useContents {
            width = size.width * scale
            height = size.height * scale
        }
        if (width <= 0.0 || height <= 0.0) return

        metalLayer.contentsScale = scale
        metalLayer.bounds = bounds
        metalLayer.position = bounds.useContents {
            platform.CoreGraphics.CGPointMake(size.width / 2.0, size.height / 2.0)
        }
        metalLayer.transform = platform.QuartzCore.CATransform3DMakeScale(1.0, -1.0, 1.0)
        metalLayer.drawableSize = CGSizeMake(width, height)

        if (!created) {
            created = true
            try {
                onCreate(metalLayer)
            } catch (t: Throwable) {
                logger.error(t) { "Failed to execute onCreate for VulkanMetalView: ${t.message}" }
            }
        } else {
            onResize(width.toInt(), height.toInt())
        }
    }

    @ObjCAction
    @Suppress("UnusedPrivateMember", "TooGenericExceptionCaught")
    private fun tick(displayLink: CADisplayLink) {
        try {
            val currentTimestamp = displayLink.timestamp
            val deltaTime = if (previousTimestamp == 0.0) {
                0f
            } else {
                (currentTimestamp - previousTimestamp).toFloat()
            }
            previousTimestamp = currentTimestamp
            textInputWasFocused = syncAwakeTextInputFocus(textInputWasFocused, input)
            onUpdate(deltaTime)
        } catch (t: Throwable) {
            logger.error(t) { "Error in VulkanMetalView tick render step: ${t.message}" }
        }
    }

    override fun canBecomeFirstResponder(): Boolean = true

    override fun hasText(): Boolean = true

    override fun insertText(text: String) = syncAwakeTextInsert(text, input)

    override fun deleteBackward() = syncAwakeTextDeleteBackward(input)

    override fun willMoveToWindow(newWindow: UIWindow?) {
        super.willMoveToWindow(newWindow)
        if (newWindow != null) {
            startRenderLoop()
        } else {
            stopRenderLoop()
        }
    }

    private fun startRenderLoop() {
        if (displayLink != null) return
        onResume()
        previousTimestamp = 0.0
        displayLink = UIScreen.mainScreen.displayLinkWithTarget(
            this,
            NSSelectorFromString("tick:"),
        )?.apply {
            addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        }
    }

    private fun stopRenderLoop() {
        onPause()
        displayLink?.invalidate()
        displayLink = null
    }

    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesBegan(touches, withEvent)
        syncAwakePointerInput(touches, down = true, input = input)
    }

    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesMoved(touches, withEvent)
        syncAwakePointerInput(touches, down = true, input = input)
    }

    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesEnded(touches, withEvent)
        syncAwakePointerInput(touches, down = false, input = input)
    }

    override fun touchesCancelled(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesCancelled(touches, withEvent)
        syncAwakePointerInput(touches, down = false, input = input)
    }
}
