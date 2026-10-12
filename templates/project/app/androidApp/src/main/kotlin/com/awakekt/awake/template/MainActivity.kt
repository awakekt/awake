package com.awakekt.awake.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.awakekt.awake.engine.window.AwakeSurfaceView
import com.awakekt.awake.vulkan.application.VulkanEngine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reading the project happens off the main thread; the view appears once it's loaded.
        lifecycleScope.launch {
            val project = loadGame()
            val engine = VulkanEngine(createGame(project, touchControls = true), AwakeRenderPlan)
            setContentView(AwakeSurfaceView(this@MainActivity, engine))
        }
    }
}
