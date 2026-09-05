/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "MatchingDeclarationName", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui.dialogs

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.editor.core.license.AwakeLicense
import io.github.awakelab.awake.editor.core.license.AwakeLicensePayload
import io.github.awakelab.awake.editor.core.license.AwakeLicenseRegistry
import io.github.awakelab.awake.editor.core.license.AwakeLicenseStatus
import io.github.awakelab.awake.editor.core.license.AwakeLicenseVerifier
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnDialog
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

/** Local mutable state tracking activation feedback message. */
private class LicenseUiState(
    var feedbackMessage: String? = null,
    var isError: Boolean = false,
)

/**
 * Studio License Management Dialog for viewing active license status, entering Pro keys,
 * and managing Awake Pro / Enterprise entitlements.
 *
 * @param visible Controls whether the license dialog is open.
 * @param onDismissRequest Callback to close the dialog.
 * @param modifier Custom layout modifier.
 * @param width Dialog width (`520.dp` by default).
 */
context(_: Composer)
internal fun StudioLicenseDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 520.dp,
) {
    val licenseInputState = remember { TextFieldState("") }
    val uiState = remember { LicenseUiState() }

    ShadcnDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
        id = "studio-license-dialog",
    ) {
        header {
            LicenseHeader(onDismiss = onDismissRequest)
        }
        ShadcnSeparator()
        LicensePlanCard(uiState)

        val feedback = uiState.feedbackMessage
        if (feedback != null) {
            LicenseFeedbackMessage(feedback, uiState.isError)
        }

        if (!AwakeLicenseRegistry.isProActive) {
            LicenseProFeatureList()
            LicenseActivationInput(licenseInputState, uiState)
        }
    }
}

context(_: Composer)
private fun LicenseHeader(onDismiss: () -> Unit) {
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
                ShadcnText("Awake Pro Licensing", variant = ShadcnTextVariant.H3)
                ShadcnBadge("STUDIO TIER", variant = ShadcnBadgeVariant.Secondary)
            }
            shadcnMuted("Manage commercial studio licenses and enterprise entitlements.")
        }
        ShadcnButton("X", variant = ShadcnButtonVariant.Ghost, size = ShadcnButtonSizeVariant.Sm, onClick = onDismiss)
    }
}

context(_: Composer)
private fun LicensePlanCard(uiState: LicenseUiState) {
    ShadcnCard(modifier = Modifier.fillMaxWidth(), contentPadding = Tw.Spacing.s4) {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    ShadcnText("Active License Plan", variant = ShadcnTextVariant.Muted)
                    ShadcnText(AwakeLicenseRegistry.planName, variant = ShadcnTextVariant.H4)
                }
                if (AwakeLicenseRegistry.isProActive) {
                    ShadcnBadge("PRO ACTIVE", variant = ShadcnBadgeVariant.Default)
                } else {
                    ShadcnBadge("COMMUNITY (FREE)", variant = ShadcnBadgeVariant.Secondary)
                }
            }

            val active = AwakeLicenseRegistry.activePayload
            if (active != null) {
                LicenseActiveDetails(active, uiState)
            }
        }
    }
}

context(_: Composer)
private fun LicenseActiveDetails(active: AwakeLicensePayload, uiState: LicenseUiState) {
    ShadcnSeparator()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
    ) {
        ShadcnText("Licensed to: ${active.licensee}", variant = ShadcnTextVariant.Small)
        ShadcnText("${active.seats} Seats", variant = ShadcnTextVariant.Small)
    }
    if (active.entitlements.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1)) {
            active.entitlements.forEach { entitlement ->
                ShadcnBadge(entitlement, variant = ShadcnBadgeVariant.Outline)
            }
        }
    }
    ShadcnButton(
        "Deactivate License",
        variant = ShadcnButtonVariant.Outline,
        size = ShadcnButtonSizeVariant.Sm,
        onClick = {
            AwakeLicenseRegistry.deactivate()
            uiState.feedbackMessage = "Commercial license deactivated."
            uiState.isError = false
        },
    )
}

context(_: Composer)
private fun LicenseProFeatureList() {
    Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
        ShadcnText("Awake Pro Capabilities:", variant = ShadcnTextVariant.Small)
        LicenseFeatureItem("- Royalty-Free Commercial Game Distribution")
        LicenseFeatureItem("- Visual Shader Graph & Node Dialogue Graph Editors")
        LicenseFeatureItem("- Jolt Ragdoll Simulation & Dynamic Character Controller")
        LicenseFeatureItem("- Large-Scale World Cell Streaming & Terrain Clipmaps")
    }
}

context(_: Composer)
private fun LicenseFeatureItem(label: String) {
    ShadcnText(label, variant = ShadcnTextVariant.Muted)
}

context(_: Composer)
private fun LicenseFeedbackMessage(message: String, isError: Boolean) {
    ShadcnText(
        message,
        variant = if (isError) ShadcnTextVariant.Small else ShadcnTextVariant.Muted,
    )
}

context(_: Composer)
private fun LicenseActivationInput(licenseInputState: TextFieldState, uiState: LicenseUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
        ShadcnText("Activate License Certificate", variant = ShadcnTextVariant.Small)
        ShadcnInput(
            state = licenseInputState,
            placeholder = "Paste .awake-license certificate JSON...",
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnButton(
                "Activate License",
                size = ShadcnButtonSizeVariant.Sm,
                onClick = {
                    val text = licenseInputState.text.trim()
                    if (text.isBlank()) {
                        uiState.feedbackMessage = "Please paste a license certificate."
                        uiState.isError = true
                    } else {
                        val result = AwakeLicenseRegistry.activateFromJson(text)
                        if (result is AwakeLicenseStatus.Valid) {
                            uiState.feedbackMessage = "Successfully activated Awake Pro!"
                            uiState.isError = false
                        } else {
                            uiState.feedbackMessage =
                                (result as? AwakeLicenseStatus.Invalid)?.reason ?: "Invalid license certificate."
                            uiState.isError = true
                        }
                    }
                },
            )

            ShadcnButton(
                "Load Studio Dev Pro",
                variant = ShadcnButtonVariant.Ghost,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = {
                    val devPayload = AwakeLicensePayload(
                        licensee = "Awake Studio Developer",
                        plan = "AwakePro Studio",
                        seats = 10,
                        entitlements = setOf("awake.pro.ragdoll", "awake.pro.worldstream", "awake.pro.marketplace"),
                    )
                    val sig = AwakeLicenseVerifier.computeSignature(devPayload)
                    val license = AwakeLicense(devPayload, sig)
                    AwakeLicenseRegistry.activate(license)
                    uiState.feedbackMessage = "Loaded development Awake Pro certificate."
                    uiState.isError = false
                },
            )
        }
    }
}
