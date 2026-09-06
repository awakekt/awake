/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.ui.shadcn.theme.ShadcnMetrics
import com.awakekt.awake.ui.shadcn.theme.ShadcnPalette
import com.awakekt.awake.ui.shadcn.theme.ShadcnRadiusScale

/**
 * A neutral-first, shadcn-inspired design-system theme that lives OUTSIDE the engine core.
 */
object ShadcnTheme : ShadcnResolvedTheme by shadcnThemeData()

enum class ShadcnStylePreset(val label: String, internal val baseRadius: Dp, internal val metrics: ShadcnMetrics, internal val ringAlphaMultiplier: Float) {
    // Vega maps to upstream shadcn new-york-v4 metrics (radius: 10dp, card p-6: 24dp, badge: px-2 py-0.5).
    Vega(
        label = "Vega",
        baseRadius = 10f.dp,
        metrics = ShadcnMetrics(24f.dp, 16f.dp, 12f.dp, 8f.dp, 8f.dp, 2f.dp, 4f.dp),
        ringAlphaMultiplier = 1f,
    ),
    Nova(
        label = "Nova",
        baseRadius = 5f.dp,
        metrics = ShadcnMetrics(14f.dp, 18f.dp, 10f.dp, 7f.dp, 10f.dp, 4f.dp, 3.5f.dp),
        ringAlphaMultiplier = 1f,
    ),
    Maia(
        label = "Maia",
        baseRadius = 10f.dp,
        metrics = ShadcnMetrics(18f.dp, 22f.dp, 14f.dp, 9f.dp, 14f.dp, 6f.dp, 4.5f.dp),
        ringAlphaMultiplier = 1f,
    ),
    Lyra(
        label = "Lyra",
        baseRadius = 0f.dp,
        metrics = ShadcnMetrics(15f.dp, 18f.dp, 11f.dp, 7f.dp, 11f.dp, 4f.dp, 3.5f.dp),
        ringAlphaMultiplier = 0.9f,
    ),
    Mira(
        label = "Mira",
        baseRadius = 4f.dp,
        metrics = ShadcnMetrics(12f.dp, 15f.dp, 10f.dp, 6f.dp, 9f.dp, 4f.dp, 3f.dp),
        ringAlphaMultiplier = 0.85f,
    ),
    Luma(
        label = "Luma",
        baseRadius = 12f.dp,
        metrics = ShadcnMetrics(18f.dp, 22f.dp, 14f.dp, 9f.dp, 14f.dp, 6f.dp, 4.5f.dp),
        ringAlphaMultiplier = 0.75f,
    ),
    Sera(
        label = "Sera",
        baseRadius = 7f.dp,
        metrics = ShadcnMetrics(16f.dp, 20f.dp, 13f.dp, 8f.dp, 12f.dp, 5f.dp, 4f.dp),
        ringAlphaMultiplier = 0.85f,
    ),
    Rhea(
        label = "Rhea",
        baseRadius = 8f.dp,
        metrics = ShadcnMetrics(14f.dp, 18f.dp, 11f.dp, 7f.dp, 10f.dp, 4f.dp, 3.5f.dp),
        ringAlphaMultiplier = 0.85f,
    ),
}

enum class ShadcnBaseColor(val label: String, internal val hueDegrees: Float, internal val chroma: Float) {
    Neutral("Neutral", 0f, 0f),
    Stone("Stone", 35f, 0.012f),
    Zinc("Zinc", 285f, 0.012f),
    Mauve("Mauve", 315f, 0.018f),
    Olive("Olive", 130f, 0.016f),
    Mist("Mist", 225f, 0.015f),
    Taupe("Taupe", 28f, 0.018f),
}

enum class ShadcnAccent(val label: String, internal val darkPrimary: Color?, internal val darkOnPrimary: Color?, internal val lightPrimary: Color?, internal val lightOnPrimary: Color?) {
    Base("Base", null, null, null, null),
    Amber("Amber", Color.fromHex(0xF59E0B), Color.fromHex(0x0F172A), Color.fromHex(0xD97706), Color.White),
    Blue("Blue", Color.fromHex(0x3B82F6), Color.fromHex(0x0F172A), Color.fromHex(0x2563EB), Color.White),
    Cyan("Cyan", Color.fromHex(0x06B6D4), Color.fromHex(0x0F172A), Color.fromHex(0x0891B2), Color.White),
    Emerald("Emerald", Color.fromHex(0x10B981), Color.fromHex(0x0F172A), Color.fromHex(0x059669), Color.White),
    Fuchsia("Fuchsia", Color.fromHex(0xD8B4FE), Color.fromHex(0x0F172A), Color.fromHex(0xC084FC), Color.White),
    Green("Green", Color.fromHex(0x22C55E), Color.fromHex(0x0F172A), Color.fromHex(0x16A34A), Color.White),
    Indigo("Indigo", Color.fromHex(0x6366F1), Color.fromHex(0x0F172A), Color.fromHex(0x4F46E5), Color.White),
    Lime("Lime", Color.fromHex(0xA3E635), Color.fromHex(0x0F172A), Color.fromHex(0x84CC16), Color.fromHex(0x0F172A)),
    Orange("Orange", Color.fromHex(0xF97316), Color.fromHex(0x0F172A), Color.fromHex(0xEA580C), Color.White),
    Pink("Pink", Color.fromHex(0xEC4899), Color.fromHex(0x0F172A), Color.fromHex(0xDB2777), Color.White),
    Purple("Purple", Color.fromHex(0xA855F7), Color.fromHex(0x0F172A), Color.fromHex(0x9333EA), Color.White),
    Red("Red", Color.fromHex(0xEF4444), Color.fromHex(0x0F172A), Color.fromHex(0xDC2626), Color.White),
    Rose("Rose", Color.fromHex(0xF43F5E), Color.fromHex(0x0F172A), Color.fromHex(0xE11D48), Color.White),
    Sky("Sky", Color.fromHex(0x38BDF8), Color.fromHex(0x0F172A), Color.fromHex(0x0284C7), Color.White),
    Teal("Teal", Color.fromHex(0x14B8A6), Color.fromHex(0x0F172A), Color.fromHex(0x0D9488), Color.White),
    Violet("Violet", Color.fromHex(0x8B5CF6), Color.fromHex(0x0F172A), Color.fromHex(0x7C3AED), Color.White),
    Yellow("Yellow", Color.fromHex(0xFACC15), Color.fromHex(0x0F172A), Color.fromHex(0xCA8A04), Color.fromHex(0x0F172A)),
}

data class ShadcnThemeConfig(
    val preset: ShadcnStylePreset = ShadcnStylePreset.Vega,
    val baseColor: ShadcnBaseColor = ShadcnBaseColor.Neutral,
    val accent: ShadcnAccent = ShadcnAccent.Base,
    val dark: Boolean = true,
)

/** Builds a complete, runtime-free Shadcn theme value for [shadcnTheme]. */
fun shadcnThemeValues(
    preset: ShadcnStylePreset = ShadcnStylePreset.Vega,
    baseColor: ShadcnBaseColor = ShadcnBaseColor.Neutral,
    accent: ShadcnAccent = ShadcnAccent.Base,
    dark: Boolean = true,
): ShadcnThemeValues {
    val core = shadcnThemeData(
        ShadcnThemeConfig(
            preset = preset,
            baseColor = baseColor,
            accent = accent,
            dark = dark,
        ),
    )
    return ShadcnThemeValues(resolved = core)
}

interface ShadcnResolvedTheme {
    val config: ShadcnThemeConfig
    val palette: ShadcnPalette
    val radii: ShadcnRadiusScale
    val metrics: ShadcnMetrics

    val background: Color get() = palette.background
    val foreground: Color get() = palette.foreground
    val primary: Color get() = palette.primary
    val primaryForeground: Color get() = palette.primaryForeground
    val secondary: Color get() = palette.secondary
    val secondaryForeground: Color get() = palette.secondaryForeground
    val muted: Color get() = palette.muted
    val mutedForeground: Color get() = palette.mutedForeground
    val accent: Color get() = palette.accent
    val accentForeground: Color get() = palette.accentForeground
    val destructive: Color get() = palette.destructive
    val destructiveForeground: Color get() = palette.destructiveForeground
    val border: Color get() = palette.border
    val card: Color get() = palette.card
    val onCard: Color get() = palette.cardForeground
    val popover: Color get() = palette.popover
    val onPopover: Color get() = palette.popoverForeground
    val sidebar: Color get() = palette.sidebar
    val onSidebar: Color get() = palette.sidebarForeground
    val sidebarAccent: Color get() = palette.sidebarAccent
    val onSidebarAccent: Color get() = palette.sidebarAccentForeground
    val sidebarBorder: Color get() = palette.sidebarBorder
    val sidebarRing: Color get() = palette.sidebarRing
    val input: Color get() = palette.input
    val ring: Color get() = palette.ring

    /** Base tint for drop shadows -- callers apply their own alpha (e.g. `shadow.withAlpha(0.16f)`). */
    val shadow: Color get() = palette.shadow

    /** Complete modal/sheet scrim color, ready to draw with no further alpha applied. */
    val overlay: Color get() = palette.overlay
}

private fun shadcnThemeData(config: ShadcnThemeConfig = ShadcnThemeConfig()): ShadcnResolvedTheme = ConfiguredShadcnTheme(config)

private class ConfiguredShadcnTheme(override val config: ShadcnThemeConfig) : ShadcnResolvedTheme {
    override val radii: ShadcnRadiusScale = ShadcnRadiusScale.fromBase(config.preset.baseRadius)
    override val metrics: ShadcnMetrics = config.preset.metrics
    override val palette: ShadcnPalette = createPalette(config)
}

private fun createPalette(config: ShadcnThemeConfig): ShadcnPalette {
    val dark = config.dark
    // Looked up, not derived. shadcn hand-tunes hue AND chroma per token within one base colour --
    // real `stone`'s foreground is oklch(0.147 0.004 49.25) while its primary is
    // oklch(0.216 0.006 56.043), different on both axes -- so the single hue/chroma pair this used
    // to scale by fixed multipliers could not reproduce it, and six of the seven base colours
    // drifted. `Neutral` was the exception only because its hue and chroma are both zero.
    val tokens = ShadcnReferenceTokens.BY_BASE_COLOR
        .getValue(config.baseColor.name.lowercase())
        .let { if (dark) it.dark else it.light }

    fun token(name: String): Color = tokens.getValue(name).toColor()

    val background = token("background")
    val foreground = token("foreground")
    val secondary = token("secondary")
    val secondaryForeground = token("secondary-foreground")
    val accentSurface = token("accent")
    val border = token("border")

    // Accent overlays the looked-up defaults; `ShadcnAccent`'s 17 overrides are hand-picked
    // Tailwind values outside `themes.ts`, so they are not in the table and never will be.
    val accentPrimary = if (dark) config.accent.darkPrimary else config.accent.lightPrimary
    val accentOnPrimary = if (dark) config.accent.darkOnPrimary else config.accent.lightOnPrimary
    val primary = accentPrimary ?: token("primary")
    val primaryForeground = accentOnPrimary ?: token("primary-foreground")
    val ring = (accentPrimary ?: token("ring"))
        .withAlpha(if (accentPrimary != null) 1f else config.preset.ringAlphaMultiplier)

    return ShadcnPalette(
        background = background,
        foreground = foreground,
        // The three below are not shadcn CSS variables and are computed on purpose.
        // Elevation over the page, not a theme colour: a fixed black at alpha in both modes.
        shadow = Color.Black,
        overlay = Color.Black.withAlpha(0.5f),
        primary = primary,
        primaryForeground = primaryForeground,
        secondary = secondary,
        secondaryForeground = secondaryForeground,
        muted = token("muted"),
        mutedForeground = token("muted-foreground"),
        accent = accentSurface,
        accentForeground = token("accent-foreground"),
        destructive = token("destructive"),
        // shadcn has no `--destructive-foreground` at all -- its destructive buttons hardcode white
        // through Tailwind. Ours is computed because there is nothing upstream to look up.
        destructiveForeground = if (dark) oklch(0.985f, 0f) else oklch(0.97f, 0.01f, 17f),
        border = border,
        ring = ring,
        input = token("input"),
        card = token("card"),
        cardForeground = token("card-foreground"),
        popover = token("popover"),
        popoverForeground = token("popover-foreground"),
        sidebar = token("sidebar"),
        sidebarForeground = token("sidebar-foreground"),
        sidebarPrimary = token("sidebar-primary"),
        sidebarPrimaryForeground = token("sidebar-primary-foreground"),
        sidebarAccent = token("sidebar-accent"),
        sidebarAccentForeground = token("sidebar-accent-foreground"),
        sidebarBorder = token("sidebar-border"),
        sidebarRing = token("sidebar-ring"),
    )
}

/** shadcn v4's checkbox corner: a literal `rounded-[4px]`, not a step on the radius scale. */
