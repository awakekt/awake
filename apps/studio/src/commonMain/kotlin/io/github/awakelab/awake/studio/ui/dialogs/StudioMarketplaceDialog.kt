/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "MatchingDeclarationName", "TooManyFunctions", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui.dialogs

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.heightIn
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.rememberScrollState
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.foundation.verticalScroll
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.editor.core.files.EditorFileChooser
import io.github.awakelab.awake.editor.core.files.createPlatformFileChooser
import io.github.awakelab.awake.editor.core.license.AwakeLicenseRegistry
import io.github.awakelab.awake.editor.core.plugin.EditorPlugin
import io.github.awakelab.awake.editor.core.plugin.EditorPluginApiVersion
import io.github.awakelab.awake.editor.core.plugin.EditorPluginId
import io.github.awakelab.awake.editor.core.plugin.EditorPluginMetadata
import io.github.awakelab.awake.editor.core.plugin.EditorProvider
import io.github.awakelab.awake.editor.core.plugin.PluginInstallResult
import io.github.awakelab.awake.editor.core.plugin.PluginManifest
import io.github.awakelab.awake.editor.core.plugin.StudioPluginArchive
import io.github.awakelab.awake.editor.core.plugin.StudioPluginInstaller
import io.github.awakelab.awake.studio.plugins.discovery.MarketplacePluginEntry
import io.github.awakelab.awake.studio.plugins.discovery.PluginCategory
import io.github.awakelab.awake.studio.plugins.discovery.PluginDiscovery
import io.github.awakelab.awake.studio.plugins.discovery.PluginDiscoveryOfficial
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAvatar
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnDialog
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.ShadcnItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTabs
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme
import kotlinx.serialization.SerializationException

/**
 * Retained mutable UI state for StudioMarketplaceDialog.
 */
private class MarketplaceState(
    var currentTab: String = "marketplace",
    var selectedCategory: PluginCategory = PluginCategory.All,
    var activeDetailPlugin: MarketplacePluginEntry? = null,
    var notificationMessage: String? = null,
)

/**
 * Studio Marketplace Dialog UI for browsing, searching, and managing Studio extensions.
 */
context(_: Composer)
internal fun StudioMarketplaceDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 680.dp,
    editorBridge: StudioEditorBridge? = null,
    discovery: PluginDiscovery = remember { PluginDiscoveryOfficial() },
    onRequireLicense: () -> Unit = {},
) {
    val state = remember { MarketplaceState() }
    val searchState = remember { TextFieldState("") }
    val plugins = remember { discovery.discover().toMutableList() }
    val scrollState = rememberScrollState()
    val realInstalled = editorBridge?.plugins?.installed ?: emptyList()

    ShadcnDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
        id = "studio-marketplace-dialog",
    ) {
        header { MarketplaceHeader(onDismiss = onDismissRequest) }
        ShadcnSeparator()
        MarketplaceTabBar(state, plugins, realInstalled)
        if (state.activeDetailPlugin == null && state.currentTab != "import") {
            MarketplaceSearchAndFilters(searchState, state)
        }
        state.notificationMessage?.let { msg ->
            MarketplaceNotificationBanner(msg, onDismiss = { state.notificationMessage = null })
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 460.dp)
                .verticalScroll(scrollState),
        ) {
            MarketplaceDialogBody(
                MarketplaceBodyContext(
                    state = state,
                    plugins = plugins,
                    realInstalled = realInstalled,
                    searchQuery = searchState.text,
                    editorBridge = editorBridge,
                    onRequireLicense = onRequireLicense,
                ),
            )
        }
    }
}

context(_: Composer)
private fun MarketplaceHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText("Studio Marketplace", variant = ShadcnTextVariant.H3)
                ShadcnBadge("EXTENSIONS", variant = ShadcnBadgeVariant.Secondary)
            }
            shadcnMuted("Discover, install, and extend Awake Studio with community and Pro plugins.")
        }
        ShadcnButton("X", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onDismiss)
    }
}

context(_: Composer)
private fun MarketplaceTabBar(
    state: MarketplaceState,
    plugins: List<MarketplacePluginEntry>,
    realInstalled: List<EditorPluginMetadata>,
) {
    val totalInstalled = realInstalled.size + plugins.count { entry ->
        entry.isInstalled && realInstalled.none { it.id.value == entry.manifest.id }
    }
    ShadcnTabs(
        selectedValue = state.currentTab,
        onSelectedChange = {
            state.currentTab = it
            state.activeDetailPlugin = null
        },
    ) {
        tab("marketplace", "All Extensions")
        tab("installed", "Installed ($totalInstalled)")
        tab("import", "Import")
    }
}

context(_: Composer)
private fun MarketplaceSearchAndFilters(
    searchState: TextFieldState,
    state: MarketplaceState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ShadcnInput(state = searchState, placeholder = "Search extensions by name, tag, or author...")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginCategory.entries.forEach { cat ->
                val isSelected = state.selectedCategory == cat
                ShadcnBadge(
                    label = cat.label,
                    variant = if (isSelected) ShadcnBadgeVariant.Default else ShadcnBadgeVariant.Outline,
                    modifier = Modifier.clickable { state.selectedCategory = cat },
                )
            }
        }
    }
}

context(_: Composer)
private fun MarketplaceNotificationBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tw.Spacing.s1),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnBadge(message, variant = ShadcnBadgeVariant.Secondary)
        ShadcnButton("Dismiss", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onDismiss)
    }
}

private data class MarketplaceBodyContext(
    val state: MarketplaceState,
    val plugins: MutableList<MarketplacePluginEntry>,
    val realInstalled: List<EditorPluginMetadata>,
    val searchQuery: String,
    val editorBridge: StudioEditorBridge?,
    val onRequireLicense: () -> Unit,
)

context(_: Composer)
private fun MarketplaceDialogBody(context: MarketplaceBodyContext) {
    val state = context.state
    val activeDetail = state.activeDetailPlugin
    if (activeDetail != null) {
        MarketplacePluginDetailsView(
            entry = activeDetail,
            onBack = { state.activeDetailPlugin = null },
            onInstallToggle = {
                handleInstallToggle(activeDetail, context.editorBridge, state)
            },
            onRequireLicense = context.onRequireLicense,
        )
        return
    }

    when (state.currentTab) {
        "marketplace" -> MarketplaceCatalogList(
            MarketplaceCatalogContext(
                plugins = context.plugins,
                realInstalled = context.realInstalled,
                searchQuery = context.searchQuery,
                category = state.selectedCategory,
                onOpenDetails = { state.activeDetailPlugin = it },
                onInstallToggle = { entry -> handleInstallToggle(entry, context.editorBridge, state) },
                onRequireLicense = context.onRequireLicense,
            ),
        )
        "installed" -> MarketplaceInstalledList(
            plugins = context.plugins,
            realInstalled = context.realInstalled,
            searchQuery = context.searchQuery,
            onUninstall = { entry -> handleUninstall(entry, state) },
        )
        "import" -> MarketplaceImportSection(
            plugins = context.plugins,
            editorBridge = context.editorBridge,
            onSuccess = { msg ->
                state.notificationMessage = msg
                state.currentTab = "installed"
            },
        )
    }
}

private fun handleInstallToggle(
    entry: MarketplacePluginEntry,
    editorBridge: StudioEditorBridge?,
    state: MarketplaceState,
) {
    if (entry.isInstalled) {
        entry.isInstalled = false
        state.notificationMessage = "Uninstalled extension '${entry.manifest.name}'."
    } else {
        entry.isInstalled = true
        installDynamicPlugin(entry.manifest, editorBridge)
        state.notificationMessage = "Successfully installed '${entry.manifest.name}'."
    }
}

private fun handleUninstall(
    entry: MarketplacePluginEntry,
    state: MarketplaceState,
) {
    entry.isInstalled = false
    state.notificationMessage = "Uninstalled extension '${entry.manifest.name}'."
}

private class DynamicStudioExtensionPlugin(
    override val metadata: EditorPluginMetadata,
) : EditorPlugin {
    override fun createProviders(): List<EditorProvider> = emptyList()
}

private fun installDynamicPlugin(manifest: PluginManifest, editorBridge: StudioEditorBridge?) {
    editorBridge?.let { bridge ->
        val pluginId = EditorPluginId(manifest.id)
        if (bridge.plugins.installed.none { it.id == pluginId }) {
            try {
                bridge.installPlugin(
                    DynamicStudioExtensionPlugin(
                        EditorPluginMetadata(
                            id = pluginId,
                            displayName = manifest.name,
                            version = manifest.version,
                            requiredApiVersion = EditorPluginApiVersion(manifest.requiredApiVersion),
                        ),
                    ),
                )
            } catch (_: IllegalArgumentException) {
                // Silently handle if already registered in host
            }
        }
    }
}

private data class MarketplaceCatalogContext(
    val plugins: List<MarketplacePluginEntry>,
    val realInstalled: List<EditorPluginMetadata>,
    val searchQuery: String,
    val category: PluginCategory,
    val onOpenDetails: (MarketplacePluginEntry) -> Unit,
    val onInstallToggle: (MarketplacePluginEntry) -> Unit,
    val onRequireLicense: () -> Unit,
)

context(_: Composer)
private fun MarketplaceCatalogList(ctx: MarketplaceCatalogContext) {
    val query = ctx.searchQuery.trim()
    val filtered = ctx.plugins.filter { entry ->
        val matchesCategory = ctx.category == PluginCategory.All || entry.category == ctx.category ||
            (ctx.category == PluginCategory.Pro && entry.isPro) ||
            (ctx.category == PluginCategory.Community && !entry.isPro)
        val matchesQuery = query.isEmpty() ||
            entry.manifest.name.contains(query, ignoreCase = true) ||
            entry.manifest.description.contains(query, ignoreCase = true) ||
            entry.tags.any { it.contains(query, ignoreCase = true) } ||
            entry.manifest.id.contains(query, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s6), contentAlignment = Alignment.Center) {
            shadcnMuted("No extensions matching filters.")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            filtered.forEach { entry ->
                val isActuallyInstalled = entry.isInstalled || ctx.realInstalled.any { it.id.value == entry.manifest.id }
                MarketplaceCatalogCard(entry, isActuallyInstalled, ctx.onOpenDetails, ctx.onInstallToggle, ctx.onRequireLicense)
            }
        }
    }
}

context(_: Composer)
private fun MarketplaceCatalogCard(
    entry: MarketplacePluginEntry,
    isActuallyInstalled: Boolean,
    onOpenDetails: (MarketplacePluginEntry) -> Unit,
    onInstallToggle: (MarketplacePluginEntry) -> Unit,
    onRequireLicense: () -> Unit,
) {
    ShadcnItem(
        modifier = Modifier.clickable { onOpenDetails(entry) },
        leading = { ShadcnAvatar(initials = entry.manifest.name.take(2).uppercase()) },
        trailing = {
            MarketplacePluginAction(entry, isActuallyInstalled, onInstallToggle, onRequireLicense)
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(entry.manifest.name, variant = ShadcnTextVariant.Small)
            if (entry.isPro) {
                ShadcnBadge("PRO", variant = ShadcnBadgeVariant.Default)
            } else {
                ShadcnBadge("FREE", variant = ShadcnBadgeVariant.Secondary)
            }
            ShadcnBadge(entry.category.label.uppercase(), variant = ShadcnBadgeVariant.Outline)
            ShadcnBadge("SAMPLE", variant = ShadcnBadgeVariant.Outline)
        }
        ShadcnText(entry.manifest.description, variant = ShadcnTextVariant.Muted)
        ShadcnText("v${entry.manifest.version} - by ${entry.manifest.author}", variant = ShadcnTextVariant.Muted)
    }
}

context(_: Composer)
private fun MarketplacePluginAction(
    entry: MarketplacePluginEntry,
    isInstalled: Boolean,
    onInstallToggle: (MarketplacePluginEntry) -> Unit,
    onRequireLicense: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isInstalled) {
            ShadcnButton(
                label = "Installed",
                variant = ShadcnButtonVariant.Outline,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = { onInstallToggle(entry) },
            )
        } else if (entry.isPro && !AwakeLicenseRegistry.isProActive) {
            ShadcnButton(
                label = "Get Pro",
                variant = ShadcnButtonVariant.Outline,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = onRequireLicense,
            )
        } else {
            ShadcnButton(
                label = "Install",
                size = ShadcnButtonSizeVariant.Sm,
                onClick = { onInstallToggle(entry) },
            )
        }
    }
}

context(_: Composer)
private fun MarketplacePluginDetailsView(
    entry: MarketplacePluginEntry,
    onBack: () -> Unit,
    onInstallToggle: () -> Unit,
    onRequireLicense: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnButton("Back to Catalog", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onBack)
            if (entry.isInstalled) {
                ShadcnButton("Uninstall", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm, onClick = onInstallToggle)
            } else if (entry.isPro && !AwakeLicenseRegistry.isProActive) {
                ShadcnButton("Get Pro License", variant = ShadcnButtonVariant.Default, size = ShadcnButtonSizeVariant.Sm, onClick = onRequireLicense)
            } else {
                ShadcnButton("Install Extension", size = ShadcnButtonSizeVariant.Sm, onClick = onInstallToggle)
            }
        }
        ShadcnCard(modifier = Modifier.fillMaxWidth()) {
            header {
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                    ShadcnText(entry.manifest.name, variant = ShadcnTextVariant.H3)
                    if (entry.isPro) ShadcnBadge("PRO", variant = ShadcnBadgeVariant.Default) else ShadcnBadge("FREE", variant = ShadcnBadgeVariant.Secondary)
                    ShadcnBadge(entry.category.label.uppercase(), variant = ShadcnBadgeVariant.Outline)
                }
                shadcnMuted("id: ${entry.manifest.id} - v${entry.manifest.version} - by ${entry.manifest.author}")
            }
            content {
                Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
                    ShadcnText("Overview", variant = ShadcnTextVariant.H4)
                    ShadcnText(entry.detailedDescription.ifEmpty { entry.manifest.description })
                    if (entry.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
                            entry.tags.forEach { tag ->
                                ShadcnBadge("#$tag", variant = ShadcnBadgeVariant.Outline)
                            }
                        }
                    }
                    if (entry.documentationUrl.isNotEmpty()) {
                        ShadcnText("Docs: ${entry.documentationUrl}", variant = ShadcnTextVariant.Muted)
                    }
                    JsonManifestViewer(formatPrettyJson(entry.manifest), title = "Manifest JSON (plugin.json)")
                }
            }
        }
    }
}

context(_: Composer)
private fun MarketplaceInstalledList(
    plugins: List<MarketplacePluginEntry>,
    realInstalled: List<EditorPluginMetadata>,
    searchQuery: String,
    onUninstall: (MarketplacePluginEntry) -> Unit,
) {
    val query = searchQuery.trim()
    val filteredReal = if (query.isEmpty()) {
        realInstalled
    } else {
        realInstalled.filter {
            it.displayName.contains(query, ignoreCase = true) || it.id.value.contains(query, ignoreCase = true)
        }
    }
    val additionalInstalled = plugins.filter { entry ->
        entry.isInstalled && realInstalled.none { it.id.value == entry.manifest.id } &&
            (query.isEmpty() || entry.manifest.name.contains(query, ignoreCase = true))
    }

    if (filteredReal.isEmpty() && additionalInstalled.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(Tw.Spacing.s6), contentAlignment = Alignment.Center) {
            shadcnMuted(if (query.isEmpty()) "No extension plugins installed yet." else "No installed extensions matching '$query'.")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
            filteredReal.forEach { plugin -> RealInstalledPluginCard(plugin) }
            additionalInstalled.forEach { entry ->
                AdditionalInstalledPluginCard(entry, onUninstall = { onUninstall(entry) })
            }
        }
    }
}

context(_: Composer)
private fun RealInstalledPluginCard(plugin: EditorPluginMetadata) {
    ShadcnItem(
        leading = { ShadcnAvatar(initials = plugin.displayName.take(2).uppercase()) },
        trailing = { ShadcnBadge("Active", variant = ShadcnBadgeVariant.Outline) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(plugin.displayName, variant = ShadcnTextVariant.Small)
            ShadcnBadge("BUILT-IN", variant = ShadcnBadgeVariant.Secondary)
        }
        ShadcnText("id: ${plugin.id.value} - v${plugin.version}", variant = ShadcnTextVariant.Muted)
    }
}

context(_: Composer)
private fun AdditionalInstalledPluginCard(
    entry: MarketplacePluginEntry,
    onUninstall: () -> Unit,
) {
    ShadcnItem(
        leading = { ShadcnAvatar(initials = entry.manifest.name.take(2).uppercase()) },
        trailing = {
            ShadcnButton(
                label = "Uninstall",
                variant = ShadcnButtonVariant.Outline,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = onUninstall,
            )
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(entry.manifest.name, variant = ShadcnTextVariant.Small)
            ShadcnBadge(entry.category.label.uppercase(), variant = ShadcnBadgeVariant.Outline)
            ShadcnBadge("SAMPLE", variant = ShadcnBadgeVariant.Outline)
        }
        ShadcnText("v${entry.manifest.version} - by ${entry.manifest.author}", variant = ShadcnTextVariant.Muted)
    }
}

context(_: Composer)
private fun MarketplaceImportSection(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    onSuccess: (String) -> Unit,
) {
    val importState = remember { MarketplaceImportState() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tw.Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
    ) {
        ShadcnTabs(
            selectedValue = importState.sourceType.name,
            onSelectedChange = { selected ->
                importState.sourceType = ImportSourceType.valueOf(selected)
                importState.error = null
            },
        ) {
            tab("MetaJson", "Manifest JSON")
            tab("BundleArchive", "Bundle Package (.jar)")
            tab("GitHub", "GitHub Repo")
        }

        importState.error?.let { err ->
            ShadcnBadge(err, variant = ShadcnBadgeVariant.Secondary)
        }

        when (importState.sourceType) {
            ImportSourceType.MetaJson -> ImportMetaJsonCard(plugins, editorBridge, importState, onSuccess)
            ImportSourceType.BundleArchive -> ImportBundleArchiveCard(plugins, editorBridge, importState, onSuccess)
            ImportSourceType.GitHub -> ImportGitHubCard(plugins, editorBridge, importState, onSuccess)
        }
    }
}

private enum class ImportSourceType(val label: String, val badge: String) {
    MetaJson("Manifest JSON", "plugin.json"),
    BundleArchive("Bundle Package", ".awakeplugin / .jar"),
    GitHub("GitHub Link", "git"),
}

private class MarketplaceImportState(
    var sourceType: ImportSourceType = ImportSourceType.MetaJson,
    var isRawMode: Boolean = false,
    var error: String? = null,
)

private class ManifestFormState(
    val id: TextFieldState = TextFieldState("community.custom-tool"),
    val name: TextFieldState = TextFieldState("Custom Editor Tool"),
    val version: TextFieldState = TextFieldState("1.0.0"),
    val author: TextFieldState = TextFieldState("Local Developer"),
    val description: TextFieldState = TextFieldState("Local community extension package."),
    val rawJson: TextFieldState = TextFieldState(
        """{"id":"community.custom-tool","name":"Custom Editor Tool","version":"1.0.0","author":"Local Developer","description":"Local community extension package."}""",
    ),
) {
    fun toManifest(): PluginManifest = PluginManifest(
        id = id.text.trim().ifEmpty { "community.custom-tool" },
        name = name.text.trim().ifEmpty { "Custom Editor Tool" },
        version = version.text.trim().ifEmpty { "1.0.0" },
        author = author.text.trim().ifEmpty { "Community" },
        description = description.text.trim(),
    )

    fun syncFrom(manifest: PluginManifest) {
        id.setText(manifest.id)
        name.setText(manifest.name)
        version.setText(manifest.version)
        author.setText(manifest.author)
        description.setText(manifest.description)
        rawJson.setText(formatPrettyJson(manifest))
    }
}

context(_: Composer)
private fun ImportMetaJsonCard(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    state: MarketplaceImportState,
    onSuccess: (String) -> Unit,
) {
    val form = remember { ManifestFormState() }

    ShadcnCard(modifier = Modifier.fillMaxWidth(), contentPadding = Tw.Spacing.s4) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                    ShadcnText("Import from Manifest JSON", variant = ShadcnTextVariant.H4)
                    shadcnMuted(if (state.isRawMode) "Direct JSON editor mode." else "Edit fields to configure extension manifest.")
                }
                ShadcnButton(
                    label = if (state.isRawMode) "Form Fields" else "Raw JSON",
                    variant = ShadcnButtonVariant.Outline,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { toggleFormMode(form, state) },
                )
            }

            if (state.isRawMode) {
                ShadcnInput(state = form.rawJson, placeholder = "Paste plugin.json content...")
            } else {
                MetaJsonFormFields(form)
            }

            val currentManifest = resolveCurrentManifest(form, state)
            currentManifest?.let { JsonManifestViewer(formatPrettyJson(it)) }

            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "Validate & Install",
                    variant = ShadcnButtonVariant.Default,
                    onClick = {
                        installManifestFromForm(form, state) { manifest ->
                            registerImportedManifest(manifest, plugins, editorBridge, onSuccess)
                        }
                    },
                )
            }
        }
    }
}

context(_: Composer)
private fun MetaJsonFormFields(form: ManifestFormState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Extension Name", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.name, placeholder = "Tool Name")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Unique ID", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.id, placeholder = "com.example.tool")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Version", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.version, placeholder = "1.0.0")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnText("Author", variant = ShadcnTextVariant.Xs)
                ShadcnInput(state = form.author, placeholder = "Author Name")
            }
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
            ShadcnText("Description", variant = ShadcnTextVariant.Xs)
            ShadcnInput(state = form.description, placeholder = "Extension summary...")
        }
    }
}

private fun toggleFormMode(form: ManifestFormState, state: MarketplaceImportState) {
    if (state.isRawMode) {
        try {
            val parsed = PluginManifest.fromJson(form.rawJson.text)
            form.syncFrom(parsed)
            state.error = null
            state.isRawMode = false
        } catch (e: SerializationException) {
            state.error = "Cannot parse JSON: ${e.message ?: "Invalid syntax"}"
        } catch (e: IllegalArgumentException) {
            state.error = "Invalid manifest: ${e.message ?: "Validation failed"}"
        }
    } else {
        form.rawJson.setText(formatPrettyJson(form.toManifest()))
        state.isRawMode = true
        state.error = null
    }
}

private fun resolveCurrentManifest(form: ManifestFormState, state: MarketplaceImportState): PluginManifest? =
    if (state.isRawMode) {
        try {
            PluginManifest.fromJson(form.rawJson.text)
        } catch (_: Exception) {
            null
        }
    } else {
        form.toManifest()
    }

private fun installManifestFromForm(
    form: ManifestFormState,
    state: MarketplaceImportState,
    onInstalled: (PluginManifest) -> Unit,
) {
    try {
        val manifest = if (state.isRawMode) {
            PluginManifest.fromJson(form.rawJson.text)
        } else {
            form.toManifest()
        }
        onInstalled(manifest)
        state.error = null
    } catch (e: SerializationException) {
        state.error = "Malformed JSON: ${e.message ?: "Failed to parse manifest"}"
    } catch (e: IllegalArgumentException) {
        state.error = "Invalid manifest: ${e.message ?: "Validation failed"}"
    }
}

context(_: Composer)
private fun ImportBundleArchiveCard(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    state: MarketplaceImportState,
    onSuccess: (String) -> Unit,
) {
    val pathInput = remember { TextFieldState("plugins/sample-tool.awakeplugin") }
    val sigInput = remember { TextFieldState("") }
    val fileChooser = editorBridge?.fileChooser ?: createPlatformFileChooser()

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Tw.Spacing.s4,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
        ) {
            ShadcnText("Import Bundle Package (.awakeplugin / .jar)", variant = ShadcnTextVariant.H4)
            shadcnMuted("Provide the path to a compiled plugin archive or jar package.")
            BundlePathInputRow(pathInput, fileChooser)
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "plugins/ folder",
                    variant = ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { pathInput.setText("plugins/") },
                )
                ShadcnButton(
                    label = "sample-tool.jar",
                    variant = ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { pathInput.setText("plugins/sample-tool.jar") },
                )
            }
            ShadcnInput(state = sigInput, placeholder = "Hex signature (optional)...")
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "Install Bundle",
                    variant = ShadcnButtonVariant.Default,
                    onClick = {
                        installBundleArchive(pathInput.text, sigInput.text, state) { manifest ->
                            registerImportedManifest(manifest, plugins, editorBridge, onSuccess)
                        }
                    },
                )
            }
        }
    }
}

context(_: Composer)
private fun BundlePathInputRow(pathInput: TextFieldState, fileChooser: EditorFileChooser) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ShadcnInput(state = pathInput, placeholder = "plugins/my-plugin.awakeplugin or path/to/archive.jar...")
        }
        if (fileChooser.isAvailable) {
            ShadcnButton(
                label = "Browse...",
                variant = ShadcnButtonVariant.Outline,
                onClick = {
                    fileChooser.openFile(
                        title = "Select Plugin Package",
                        extensions = listOf("awakeplugin", "jar"),
                    ) { selectedPath ->
                        if (selectedPath != null) {
                            pathInput.setText(selectedPath)
                        }
                    }
                },
            )
        }
    }
}

private fun installBundleArchive(
    pathRaw: String,
    sigRaw: String,
    state: MarketplaceImportState,
    onInstalled: (PluginManifest) -> Unit,
) {
    val path = pathRaw.trim()
    if (path.isEmpty()) {
        state.error = "Bundle path must not be blank."
        return
    }
    val rawName = path.substringAfterLast('/').substringAfterLast('\\')
    val baseId = rawName.removeSuffix(".awakeplugin").removeSuffix(".jar").ifEmpty { "custom-bundle" }
    val manifest = PluginManifest(
        id = "local.$baseId",
        name = baseId.replace('-', ' ').replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        version = "1.0.0",
        author = "Local Package",
        description = "Imported from package archive: $rawName",
    )
    val archive = StudioPluginArchive(
        manifest = manifest,
        signatureHex = sigRaw.trim().ifEmpty { null },
    )
    when (val res = StudioPluginInstaller().install(archive)) {
        is PluginInstallResult.Success -> {
            onInstalled(manifest)
            state.error = null
        }
        is PluginInstallResult.Rejected -> {
            state.error = "Package rejected: ${res.reason}"
        }
    }
}

context(_: Composer)
private fun ImportGitHubCard(
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    state: MarketplaceImportState,
    onSuccess: (String) -> Unit,
) {
    val repoInput = remember { TextFieldState("https://github.com/awakelab/awake-plugin-starter") }
    val branchInput = remember { TextFieldState("main") }

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Tw.Spacing.s4,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
        ) {
            ShadcnText("Import from GitHub Repository", variant = ShadcnTextVariant.H4)
            shadcnMuted("Import an open-source extension directly from a public Git repository or release tag.")
            ShadcnInput(state = repoInput, placeholder = "https://github.com/owner/repository...")
            ShadcnInput(state = branchInput, placeholder = "Branch or tag (default: main)...")
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
                ShadcnButton(
                    label = "Clone & Install",
                    variant = ShadcnButtonVariant.Default,
                    onClick = {
                        installFromGitHub(repoInput.text, branchInput.text, state) { manifest ->
                            registerImportedManifest(manifest, plugins, editorBridge, onSuccess)
                        }
                    },
                )
            }
        }
    }
}

private fun installFromGitHub(
    repoRaw: String,
    branchRaw: String,
    state: MarketplaceImportState,
    onInstalled: (PluginManifest) -> Unit,
) {
    val url = repoRaw.trim()
    val repoParts = parseGitHubUrl(url)
    if (repoParts == null) {
        state.error = "Invalid GitHub URL format. Use: https://github.com/owner/repository"
        return
    }
    val (owner, repo) = repoParts
    val branch = branchRaw.trim().ifEmpty { "main" }
    val manifest = PluginManifest(
        id = "github.$owner.$repo",
        name = repo.replace('-', ' ').replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        version = "1.0.0",
        author = owner,
        description = "Cloned from GitHub repository $owner/$repo ($branch branch).",
    )
    onInstalled(manifest)
    state.error = null
}

context(_: Composer)
private fun JsonManifestViewer(
    manifestText: String,
    title: String = "Manifest JSON (plugin.json)",
) {
    val theme = shadcnTheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1),
    ) {
        ShadcnText(title, variant = ShadcnTextVariant.Small)
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(theme.palette.muted, cornerRadius = 6.dp)
                .border(1.dp, theme.palette.border, cornerRadius = 6.dp)
                .padding(Tw.Spacing.s3),
        ) {
            ShadcnText(
                manifestText,
                variant = ShadcnTextVariant.Xs,
                color = theme.palette.foreground,
            )
        }
    }
}

private fun formatPrettyJson(manifest: PluginManifest): String {
    val ep = if (manifest.entrypointClass.isNotBlank()) ",\n  \"entrypointClass\": \"${manifest.entrypointClass}\"" else ""
    val pro = if (manifest.isPro) ",\n  \"isPro\": true" else ""
    val req = manifest.requiredLicense?.let { ",\n  \"requiredLicense\": \"$it\"" } ?: ""
    return """{
  "id": "${manifest.id}",
  "name": "${manifest.name}",
  "version": "${manifest.version}",
  "author": "${manifest.author}",
  "description": "${manifest.description}"$ep$pro$req
}"""
}

private fun parseGitHubUrl(url: String): Pair<String, String>? {
    val clean = url.removePrefix("https://").removePrefix("http://").removePrefix("git@github.com:")
        .removePrefix("github.com/").removeSuffix(".git").trim('/')
    val parts = clean.split('/')
    return if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        parts[0] to parts[1]
    } else {
        null
    }
}

private fun registerImportedManifest(
    manifest: PluginManifest,
    plugins: MutableList<MarketplacePluginEntry>,
    editorBridge: StudioEditorBridge?,
    onSuccess: (String) -> Unit,
) {
    val newEntry = MarketplacePluginEntry(
        manifest = manifest,
        category = PluginCategory.Community,
        isPro = manifest.isPro,
        isInstalled = true,
        detailedDescription = manifest.description,
    )
    plugins.removeAll { it.manifest.id == manifest.id }
    plugins.add(newEntry)
    installDynamicPlugin(manifest, editorBridge)
    onSuccess("Successfully registered extension '${manifest.name}' (${manifest.id}).")
}
