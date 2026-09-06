/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import com.awakekt.awake.studio.ui.dialogs.StudioMarketplaceDialog
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioMarketplaceDialogTest {

    @Test
    fun dialogRendersMarketplaceAndCanSwitchToImportTab() {
        val host = StudioTestHost()
        val bridge = StudioEditorBridge(StudioStore())

        val dialogContent: context(Composer)
        () -> Unit = {
            StudioMarketplaceDialog(
                visible = true,
                onDismissRequest = {},
                editorBridge = bridge,
            )
        }

        // 1. First frame: lay out
        val initialFrame = host.frame(content = dialogContent)
        val initialManifestNode = initialFrame.findByLabel("Manifest JSON")
        println("Initial frame Manifest JSON: $initialManifestNode")
        val importNode = initialFrame.findByLabel("Import")
        assertNotNull(importNode, "Import tab must be present in semantics")

        val importTag = importNode.testTag ?: "parity-tabs.Import"
        println("Clicking import tag: $importTag")

        // 2. Click the Import tab
        host.click(importTag, content = dialogContent)

        // 3. Render next frame and inspect
        val frameAfterClick = host.frame(content = dialogContent)
        val manifestNode = frameAfterClick.findByLabel("Manifest JSON")
        println("After click Manifest JSON: $manifestNode")

        // Also check that a node from the Import view is actually rendered (e.g. "Manifest JSON" or "Import Extension")
        val importHeaderNode = frameAfterClick.findByLabel("Import from Manifest JSON")
        println("After click Import from Manifest JSON: $importHeaderNode")
        assertNotNull(manifestNode, "Manifest JSON tab must be displayed")
        assertNotNull(importHeaderNode, "Import from Manifest JSON header must be displayed")
    }

    @Test
    fun marketplaceDialogOpensFromShellAndCanSwitchToImportTab() {
        val host = StudioTestHost()
        val store = StudioStore()
        val bridge = StudioEditorBridge(store)

        val shellContent: context(Composer)
        () -> Unit = {
            StudioShell(store, bridge, backend = "Vulkan")
        }

        // Frame 1: Lay out shell
        host.frame(content = shellContent)

        // Frame 2: Click "Marketplace" button on top bar
        val marketplaceButton = host.frame(content = shellContent).findByLabel("Marketplace")
        assertNotNull(marketplaceButton, "Marketplace button must be present in top bar")
        println("Clicking Marketplace button: label=${marketplaceButton.label} bounds=(${marketplaceButton.x}, ${marketplaceButton.y})")
        host.clickByLabel("Marketplace", content = shellContent)

        // Frame 3: Dialog should now be visible
        val frameWithDialog = host.frame(content = shellContent)
        val importTab = frameWithDialog.findByLabel("Import")
        assertNotNull(importTab, "Import tab must be visible after opening Marketplace dialog")
        println("Found Import tab: tag=${importTab.testTag} bounds=(${importTab.x}, ${importTab.y})")

        // Frame 4: Click the Import tab
        val importTag = importTab.testTag ?: "parity-tabs.Import"
        host.click(importTag, content = shellContent)

        // Frame 5: Check if Import tab view is displayed
        val frameAfterImportClick = host.frame(content = shellContent)
        val manifestHeader = frameAfterImportClick.findByLabel("Import from Manifest JSON")
        println("After clicking Import in Shell: manifestHeader=$manifestHeader")
        assertNotNull(manifestHeader, "Import from Manifest JSON must be displayed after clicking Import tab in Shell")
    }

    @Test
    fun importSubTabsSwitchWithoutCrash() {
        val host = StudioTestHost()
        val bridge = StudioEditorBridge(StudioStore())

        val dialogContent: context(Composer)
        () -> Unit = {
            StudioMarketplaceDialog(
                visible = true,
                onDismissRequest = {},
                editorBridge = bridge,
            )
        }

        // Frame 1: Lay out dialog
        val initialFrame = host.frame(content = dialogContent)
        val importNode = initialFrame.findByLabel("Import")
        assertNotNull(importNode, "Import tab must be present")

        // Frame 2: Switch to Import tab
        val importTag = importNode.testTag ?: "parity-tabs.Import"
        host.click(importTag, content = dialogContent)

        // Sub-tab 1 default: Manifest JSON
        var frame = host.frame(content = dialogContent)
        assertNotNull(frame.findByLabel("Import from Manifest JSON"))

        // Switch to Sub-tab 2: Bundle Package (.jar)
        val bundleTab = frame.findByLabel("Bundle Package (.jar)")
        assertNotNull(bundleTab, "Bundle Package (.jar) tab must be present")
        host.click(bundleTab.testTag ?: "parity-tabs.Bundle Package (.jar)", content = dialogContent)

        // Verify Bundle Package card rendered without ClassCastException
        frame = host.frame(content = dialogContent)
        assertNotNull(frame.findByLabel("Import Bundle Package (.awakeplugin / .jar)"))

        // Switch to Sub-tab 3: GitHub Repo
        val githubTab = frame.findByLabel("GitHub Repo")
        assertNotNull(githubTab, "GitHub Repo tab must be present")
        host.click(githubTab.testTag ?: "parity-tabs.GitHub Repo", content = dialogContent)

        // Verify GitHub card rendered without ClassCastException
        frame = host.frame(content = dialogContent)
        assertNotNull(frame.findByLabel("Import from GitHub Repository"))

        // Switch back to Sub-tab 1: Manifest JSON
        val manifestTab = frame.findByLabel("Manifest JSON")
        assertNotNull(manifestTab, "Manifest JSON tab must be present")
        host.click(manifestTab.testTag ?: "parity-tabs.Manifest JSON", content = dialogContent)

        // Verify Manifest JSON card rendered again without ClassCastException
        frame = host.frame(content = dialogContent)
        assertNotNull(frame.findByLabel("Import from Manifest JSON"))
    }

    @Test
    fun installedTabRendersUserExtensionsWithControlsAndSeparatesBuiltIns() {
        val host = StudioTestHost()
        val store = StudioStore()
        val bridge = StudioEditorBridge(store)

        // Install a sample dynamic extension (like one imported from GitHub)
        val manifest = com.awakekt.awake.editor.core.plugin.PluginManifest(
            id = "github.awakelab.awake-plugin-starter",
            name = "Awake Plugin Starter",
            version = "1.0.0",
            author = "awakelab",
            description = "Cloned from GitHub repository awakelab/awake-plugin-starter",
        )
        bridge.installPlugin(com.awakekt.awake.studio.plugins.DynamicStudioExtensionPlugin(manifest))

        val dialogContent: context(Composer)
        () -> Unit = {
            StudioMarketplaceDialog(
                visible = true,
                onDismissRequest = {},
                editorBridge = bridge,
            )
        }

        // Frame 1: Lay out dialog with 1 installed user extension
        var frame = host.frame(content = dialogContent)
        val installedTab = frame.findByLabel("Installed (1)")
        assertNotNull(installedTab, "Installed tab label must reflect 1 user extension: Installed (1)")

        // Switch to Installed tab
        val tabTag = installedTab.testTag ?: "parity-tabs.Installed (1)"
        host.click(tabTag, content = dialogContent)

        // Frame 2: Check installed extensions section and built-in subsystems section
        frame = host.frame(content = dialogContent)
        assertNotNull(frame.findByLabel("Installed Extensions (1)"), "Installed Extensions header must be present")
        assertNotNull(frame.findByLabel("Awake Plugin Starter"), "Extension name must be displayed")
        assertNotNull(frame.findByLabel("Disable"), "Disable button must be present for installed extension")
        assertNotNull(frame.findByLabel("Details"), "Details button must be present for installed extension")
        assertNotNull(frame.findByLabel("Uninstall"), "Uninstall button must be present for installed extension")
        assertNotNull(frame.findByLabel("Built-in Core Subsystems (7)"), "Built-in Core Subsystems section must be present")

        // Click Uninstall
        host.clickByLabel("Uninstall", content = dialogContent)

        // Frame 3: Extension is now uninstalled
        frame = host.frame(content = dialogContent)
        assertNotNull(frame.findByLabel("No custom extension plugins installed yet."), "Empty extension state must appear")
    }
}
