package com.awakekt.awake.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.awakekt.awake.engine.window.AwakeSurfaceView
import com.awakekt.awake.vulkan.application.VulkanEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AwakeSurfaceView(this, VulkanEngine(createGame(), AwakeRenderPlan)))
    }
}
