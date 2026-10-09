/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.awakekt.awake.vulkan.enums

/**
 * A pixel or texel format (`VkFormat`): the channels, their bit widths and their numeric
 * interpretation. Compressed formats store fixed-size blocks of texels.
 *
 * @property value The raw integer value Vulkan uses for this format.
 */
enum class VkFormat(val value: Int) {
    /** The format is not specified. */
    VK_FORMAT_UNDEFINED(0),

    /**
     * Two-component, 8-bit packed unsigned normalized format. Components run from the most
     * significant bits down: 4 bits each of red and green.
     */
    VK_FORMAT_R4G4_UNORM_PACK8(1),

    /**
     * Four-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: 4 bits each of red, green, blue and alpha.
     */
    VK_FORMAT_R4G4B4A4_UNORM_PACK16(2),

    /**
     * Four-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: 4 bits each of blue, green, red and alpha.
     */
    VK_FORMAT_B4G4R4A4_UNORM_PACK16(3),

    /**
     * Three-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: red (5 bits), green (6 bits) and blue (5 bits).
     */
    VK_FORMAT_R5G6B5_UNORM_PACK16(4),

    /**
     * Three-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: blue (5 bits), green (6 bits) and red (5 bits).
     */
    VK_FORMAT_B5G6R5_UNORM_PACK16(5),

    /**
     * Four-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: red (5 bits), green (5 bits), blue (5 bits) and alpha (1 bit).
     */
    VK_FORMAT_R5G5B5A1_UNORM_PACK16(6),

    /**
     * Four-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: blue (5 bits), green (5 bits), red (5 bits) and alpha (1 bit).
     */
    VK_FORMAT_B5G5R5A1_UNORM_PACK16(7),

    /**
     * Four-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: alpha (1 bit), red (5 bits), green (5 bits) and blue (5 bits).
     */
    VK_FORMAT_A1R5G5B5_UNORM_PACK16(8),

    /** One-component, 8-bit unsigned normalized format with a 8-bit red component. */
    VK_FORMAT_R8_UNORM(9),

    /** One-component, 8-bit signed normalized format with a 8-bit red component. */
    VK_FORMAT_R8_SNORM(10),

    /** One-component, 8-bit unsigned scaled integer format with a 8-bit red component. */
    VK_FORMAT_R8_USCALED(11),

    /** One-component, 8-bit signed scaled integer format with a 8-bit red component. */
    VK_FORMAT_R8_SSCALED(12),

    /** One-component, 8-bit unsigned integer format with a 8-bit red component. */
    VK_FORMAT_R8_UINT(13),

    /** One-component, 8-bit signed integer format with a 8-bit red component. */
    VK_FORMAT_R8_SINT(14),

    /** One-component, 8-bit sRGB-encoded unsigned normalized format with a 8-bit red component. */
    VK_FORMAT_R8_SRGB(15),

    /**
     * Two-component, 16-bit unsigned normalized format with 8 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R8G8_UNORM(16),

    /**
     * Two-component, 16-bit signed normalized format with 8 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R8G8_SNORM(17),

    /**
     * Two-component, 16-bit unsigned scaled integer format with 8 bits each of red and green,
     * stored in that order in memory.
     */
    VK_FORMAT_R8G8_USCALED(18),

    /**
     * Two-component, 16-bit signed scaled integer format with 8 bits each of red and green, stored
     * in that order in memory.
     */
    VK_FORMAT_R8G8_SSCALED(19),

    /**
     * Two-component, 16-bit unsigned integer format with 8 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R8G8_UINT(20),

    /**
     * Two-component, 16-bit signed integer format with 8 bits each of red and green, stored in that
     * order in memory.
     */
    VK_FORMAT_R8G8_SINT(21),

    /**
     * Two-component, 16-bit sRGB-encoded unsigned normalized format with 8 bits each of red and
     * green, stored in that order in memory.
     */
    VK_FORMAT_R8G8_SRGB(22),

    /**
     * Three-component, 24-bit unsigned normalized format with 8 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R8G8B8_UNORM(23),

    /**
     * Three-component, 24-bit signed normalized format with 8 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R8G8B8_SNORM(24),

    /**
     * Three-component, 24-bit unsigned scaled integer format with 8 bits each of red, green and
     * blue, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8_USCALED(25),

    /**
     * Three-component, 24-bit signed scaled integer format with 8 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R8G8B8_SSCALED(26),

    /**
     * Three-component, 24-bit unsigned integer format with 8 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R8G8B8_UINT(27),

    /**
     * Three-component, 24-bit signed integer format with 8 bits each of red, green and blue, stored
     * in that order in memory.
     */
    VK_FORMAT_R8G8B8_SINT(28),

    /**
     * Three-component, 24-bit sRGB-encoded unsigned normalized format with 8 bits each of red,
     * green and blue, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8_SRGB(29),

    /**
     * Three-component, 24-bit unsigned normalized format with 8 bits each of blue, green and red,
     * stored in that order in memory.
     */
    VK_FORMAT_B8G8R8_UNORM(30),

    /**
     * Three-component, 24-bit signed normalized format with 8 bits each of blue, green and red,
     * stored in that order in memory.
     */
    VK_FORMAT_B8G8R8_SNORM(31),

    /**
     * Three-component, 24-bit unsigned scaled integer format with 8 bits each of blue, green and
     * red, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8_USCALED(32),

    /**
     * Three-component, 24-bit signed scaled integer format with 8 bits each of blue, green and red,
     * stored in that order in memory.
     */
    VK_FORMAT_B8G8R8_SSCALED(33),

    /**
     * Three-component, 24-bit unsigned integer format with 8 bits each of blue, green and red,
     * stored in that order in memory.
     */
    VK_FORMAT_B8G8R8_UINT(34),

    /**
     * Three-component, 24-bit signed integer format with 8 bits each of blue, green and red, stored
     * in that order in memory.
     */
    VK_FORMAT_B8G8R8_SINT(35),

    /**
     * Three-component, 24-bit sRGB-encoded unsigned normalized format with 8 bits each of blue,
     * green and red, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8_SRGB(36),

    /**
     * Four-component, 32-bit unsigned normalized format with 8 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8A8_UNORM(37),

    /**
     * Four-component, 32-bit signed normalized format with 8 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8A8_SNORM(38),

    /**
     * Four-component, 32-bit unsigned scaled integer format with 8 bits each of red, green, blue
     * and alpha, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8A8_USCALED(39),

    /**
     * Four-component, 32-bit signed scaled integer format with 8 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8A8_SSCALED(40),

    /**
     * Four-component, 32-bit unsigned integer format with 8 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8A8_UINT(41),

    /**
     * Four-component, 32-bit signed integer format with 8 bits each of red, green, blue and alpha,
     * stored in that order in memory.
     */
    VK_FORMAT_R8G8B8A8_SINT(42),

    /**
     * Four-component, 32-bit sRGB-encoded unsigned normalized format with 8 bits each of red,
     * green, blue and alpha, stored in that order in memory.
     */
    VK_FORMAT_R8G8B8A8_SRGB(43),

    /**
     * Four-component, 32-bit unsigned normalized format with 8 bits each of blue, green, red and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8A8_UNORM(44),

    /**
     * Four-component, 32-bit signed normalized format with 8 bits each of blue, green, red and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8A8_SNORM(45),

    /**
     * Four-component, 32-bit unsigned scaled integer format with 8 bits each of blue, green, red
     * and alpha, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8A8_USCALED(46),

    /**
     * Four-component, 32-bit signed scaled integer format with 8 bits each of blue, green, red and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8A8_SSCALED(47),

    /**
     * Four-component, 32-bit unsigned integer format with 8 bits each of blue, green, red and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8A8_UINT(48),

    /**
     * Four-component, 32-bit signed integer format with 8 bits each of blue, green, red and alpha,
     * stored in that order in memory.
     */
    VK_FORMAT_B8G8R8A8_SINT(49),

    /**
     * Four-component, 32-bit sRGB-encoded unsigned normalized format with 8 bits each of blue,
     * green, red and alpha, stored in that order in memory.
     */
    VK_FORMAT_B8G8R8A8_SRGB(50),

    /**
     * Four-component, 32-bit packed unsigned normalized format. Components run from the most
     * significant bits down: 8 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A8B8G8R8_UNORM_PACK32(51),

    /**
     * Four-component, 32-bit packed signed normalized format. Components run from the most
     * significant bits down: 8 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A8B8G8R8_SNORM_PACK32(52),

    /**
     * Four-component, 32-bit packed unsigned scaled integer format. Components run from the most
     * significant bits down: 8 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A8B8G8R8_USCALED_PACK32(53),

    /**
     * Four-component, 32-bit packed signed scaled integer format. Components run from the most
     * significant bits down: 8 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A8B8G8R8_SSCALED_PACK32(54),

    /**
     * Four-component, 32-bit packed unsigned integer format. Components run from the most
     * significant bits down: 8 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A8B8G8R8_UINT_PACK32(55),

    /**
     * Four-component, 32-bit packed signed integer format. Components run from the most significant
     * bits down: 8 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A8B8G8R8_SINT_PACK32(56),

    /**
     * Four-component, 32-bit packed sRGB-encoded unsigned normalized format. Components run from
     * the most significant bits down: 8 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A8B8G8R8_SRGB_PACK32(57),

    /**
     * Four-component, 32-bit packed unsigned normalized format. Components run from the most
     * significant bits down: alpha (2 bits), red (10 bits), green (10 bits) and blue (10 bits).
     */
    VK_FORMAT_A2R10G10B10_UNORM_PACK32(58),

    /**
     * Four-component, 32-bit packed signed normalized format. Components run from the most
     * significant bits down: alpha (2 bits), red (10 bits), green (10 bits) and blue (10 bits).
     */
    VK_FORMAT_A2R10G10B10_SNORM_PACK32(59),

    /**
     * Four-component, 32-bit packed unsigned scaled integer format. Components run from the most
     * significant bits down: alpha (2 bits), red (10 bits), green (10 bits) and blue (10 bits).
     */
    VK_FORMAT_A2R10G10B10_USCALED_PACK32(60),

    /**
     * Four-component, 32-bit packed signed scaled integer format. Components run from the most
     * significant bits down: alpha (2 bits), red (10 bits), green (10 bits) and blue (10 bits).
     */
    VK_FORMAT_A2R10G10B10_SSCALED_PACK32(61),

    /**
     * Four-component, 32-bit packed unsigned integer format. Components run from the most
     * significant bits down: alpha (2 bits), red (10 bits), green (10 bits) and blue (10 bits).
     */
    VK_FORMAT_A2R10G10B10_UINT_PACK32(62),

    /**
     * Four-component, 32-bit packed signed integer format. Components run from the most significant
     * bits down: alpha (2 bits), red (10 bits), green (10 bits) and blue (10 bits).
     */
    VK_FORMAT_A2R10G10B10_SINT_PACK32(63),

    /**
     * Four-component, 32-bit packed unsigned normalized format. Components run from the most
     * significant bits down: alpha (2 bits), blue (10 bits), green (10 bits) and red (10 bits).
     */
    VK_FORMAT_A2B10G10R10_UNORM_PACK32(64),

    /**
     * Four-component, 32-bit packed signed normalized format. Components run from the most
     * significant bits down: alpha (2 bits), blue (10 bits), green (10 bits) and red (10 bits).
     */
    VK_FORMAT_A2B10G10R10_SNORM_PACK32(65),

    /**
     * Four-component, 32-bit packed unsigned scaled integer format. Components run from the most
     * significant bits down: alpha (2 bits), blue (10 bits), green (10 bits) and red (10 bits).
     */
    VK_FORMAT_A2B10G10R10_USCALED_PACK32(66),

    /**
     * Four-component, 32-bit packed signed scaled integer format. Components run from the most
     * significant bits down: alpha (2 bits), blue (10 bits), green (10 bits) and red (10 bits).
     */
    VK_FORMAT_A2B10G10R10_SSCALED_PACK32(67),

    /**
     * Four-component, 32-bit packed unsigned integer format. Components run from the most
     * significant bits down: alpha (2 bits), blue (10 bits), green (10 bits) and red (10 bits).
     */
    VK_FORMAT_A2B10G10R10_UINT_PACK32(68),

    /**
     * Four-component, 32-bit packed signed integer format. Components run from the most significant
     * bits down: alpha (2 bits), blue (10 bits), green (10 bits) and red (10 bits).
     */
    VK_FORMAT_A2B10G10R10_SINT_PACK32(69),

    /** One-component, 16-bit unsigned normalized format with a 16-bit red component. */
    VK_FORMAT_R16_UNORM(70),

    /** One-component, 16-bit signed normalized format with a 16-bit red component. */
    VK_FORMAT_R16_SNORM(71),

    /** One-component, 16-bit unsigned scaled integer format with a 16-bit red component. */
    VK_FORMAT_R16_USCALED(72),

    /** One-component, 16-bit signed scaled integer format with a 16-bit red component. */
    VK_FORMAT_R16_SSCALED(73),

    /** One-component, 16-bit unsigned integer format with a 16-bit red component. */
    VK_FORMAT_R16_UINT(74),

    /** One-component, 16-bit signed integer format with a 16-bit red component. */
    VK_FORMAT_R16_SINT(75),

    /** One-component, 16-bit signed floating-point format with a 16-bit red component. */
    VK_FORMAT_R16_SFLOAT(76),

    /**
     * Two-component, 32-bit unsigned normalized format with 16 bits each of red and green, stored
     * in that order in memory.
     */
    VK_FORMAT_R16G16_UNORM(77),

    /**
     * Two-component, 32-bit signed normalized format with 16 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R16G16_SNORM(78),

    /**
     * Two-component, 32-bit unsigned scaled integer format with 16 bits each of red and green,
     * stored in that order in memory.
     */
    VK_FORMAT_R16G16_USCALED(79),

    /**
     * Two-component, 32-bit signed scaled integer format with 16 bits each of red and green, stored
     * in that order in memory.
     */
    VK_FORMAT_R16G16_SSCALED(80),

    /**
     * Two-component, 32-bit unsigned integer format with 16 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R16G16_UINT(81),

    /**
     * Two-component, 32-bit signed integer format with 16 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R16G16_SINT(82),

    /**
     * Two-component, 32-bit signed floating-point format with 16 bits each of red and green, stored
     * in that order in memory.
     */
    VK_FORMAT_R16G16_SFLOAT(83),

    /**
     * Three-component, 48-bit unsigned normalized format with 16 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R16G16B16_UNORM(84),

    /**
     * Three-component, 48-bit signed normalized format with 16 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R16G16B16_SNORM(85),

    /**
     * Three-component, 48-bit unsigned scaled integer format with 16 bits each of red, green and
     * blue, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16_USCALED(86),

    /**
     * Three-component, 48-bit signed scaled integer format with 16 bits each of red, green and
     * blue, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16_SSCALED(87),

    /**
     * Three-component, 48-bit unsigned integer format with 16 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R16G16B16_UINT(88),

    /**
     * Three-component, 48-bit signed integer format with 16 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R16G16B16_SINT(89),

    /**
     * Three-component, 48-bit signed floating-point format with 16 bits each of red, green and
     * blue, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16_SFLOAT(90),

    /**
     * Four-component, 64-bit unsigned normalized format with 16 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16A16_UNORM(91),

    /**
     * Four-component, 64-bit signed normalized format with 16 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16A16_SNORM(92),

    /**
     * Four-component, 64-bit unsigned scaled integer format with 16 bits each of red, green, blue
     * and alpha, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16A16_USCALED(93),

    /**
     * Four-component, 64-bit signed scaled integer format with 16 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16A16_SSCALED(94),

    /**
     * Four-component, 64-bit unsigned integer format with 16 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16A16_UINT(95),

    /**
     * Four-component, 64-bit signed integer format with 16 bits each of red, green, blue and alpha,
     * stored in that order in memory.
     */
    VK_FORMAT_R16G16B16A16_SINT(96),

    /**
     * Four-component, 64-bit signed floating-point format with 16 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R16G16B16A16_SFLOAT(97),

    /** One-component, 32-bit unsigned integer format with a 32-bit red component. */
    VK_FORMAT_R32_UINT(98),

    /** One-component, 32-bit signed integer format with a 32-bit red component. */
    VK_FORMAT_R32_SINT(99),

    /** One-component, 32-bit signed floating-point format with a 32-bit red component. */
    VK_FORMAT_R32_SFLOAT(100),

    /**
     * Two-component, 64-bit unsigned integer format with 32 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R32G32_UINT(101),

    /**
     * Two-component, 64-bit signed integer format with 32 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R32G32_SINT(102),

    /**
     * Two-component, 64-bit signed floating-point format with 32 bits each of red and green, stored
     * in that order in memory.
     */
    VK_FORMAT_R32G32_SFLOAT(103),

    /**
     * Three-component, 96-bit unsigned integer format with 32 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R32G32B32_UINT(104),

    /**
     * Three-component, 96-bit signed integer format with 32 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R32G32B32_SINT(105),

    /**
     * Three-component, 96-bit signed floating-point format with 32 bits each of red, green and
     * blue, stored in that order in memory.
     */
    VK_FORMAT_R32G32B32_SFLOAT(106),

    /**
     * Four-component, 128-bit unsigned integer format with 32 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R32G32B32A32_UINT(107),

    /**
     * Four-component, 128-bit signed integer format with 32 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R32G32B32A32_SINT(108),

    /**
     * Four-component, 128-bit signed floating-point format with 32 bits each of red, green, blue
     * and alpha, stored in that order in memory.
     */
    VK_FORMAT_R32G32B32A32_SFLOAT(109),

    /** One-component, 64-bit unsigned integer format with a 64-bit red component. */
    VK_FORMAT_R64_UINT(110),

    /** One-component, 64-bit signed integer format with a 64-bit red component. */
    VK_FORMAT_R64_SINT(111),

    /** One-component, 64-bit signed floating-point format with a 64-bit red component. */
    VK_FORMAT_R64_SFLOAT(112),

    /**
     * Two-component, 128-bit unsigned integer format with 64 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R64G64_UINT(113),

    /**
     * Two-component, 128-bit signed integer format with 64 bits each of red and green, stored in
     * that order in memory.
     */
    VK_FORMAT_R64G64_SINT(114),

    /**
     * Two-component, 128-bit signed floating-point format with 64 bits each of red and green,
     * stored in that order in memory.
     */
    VK_FORMAT_R64G64_SFLOAT(115),

    /**
     * Three-component, 192-bit unsigned integer format with 64 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R64G64B64_UINT(116),

    /**
     * Three-component, 192-bit signed integer format with 64 bits each of red, green and blue,
     * stored in that order in memory.
     */
    VK_FORMAT_R64G64B64_SINT(117),

    /**
     * Three-component, 192-bit signed floating-point format with 64 bits each of red, green and
     * blue, stored in that order in memory.
     */
    VK_FORMAT_R64G64B64_SFLOAT(118),

    /**
     * Four-component, 256-bit unsigned integer format with 64 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R64G64B64A64_UINT(119),

    /**
     * Four-component, 256-bit signed integer format with 64 bits each of red, green, blue and
     * alpha, stored in that order in memory.
     */
    VK_FORMAT_R64G64B64A64_SINT(120),

    /**
     * Four-component, 256-bit signed floating-point format with 64 bits each of red, green, blue
     * and alpha, stored in that order in memory.
     */
    VK_FORMAT_R64G64B64A64_SFLOAT(121),

    /**
     * Three-component, 32-bit packed unsigned floating-point format. Components run from the most
     * significant bits down: blue (10 bits), green (11 bits) and red (11 bits).
     */
    VK_FORMAT_B10G11R11_UFLOAT_PACK32(122),

    /**
     * Three-component, 32-bit packed unsigned floating-point format: a 5-bit shared exponent and
     * 9-bit mantissas for blue, green and red.
     */
    VK_FORMAT_E5B9G9R9_UFLOAT_PACK32(123),

    /** A 16-bit unsigned normalized depth format. */
    VK_FORMAT_D16_UNORM(124),

    /**
     * A 32-bit packed depth format with a 24-bit unsigned normalized depth component and 8 unused
     * bits.
     */
    VK_FORMAT_X8_D24_UNORM_PACK32(125),

    /** A 32-bit signed floating-point depth format. */
    VK_FORMAT_D32_SFLOAT(126),

    /** An 8-bit unsigned integer stencil format. */
    VK_FORMAT_S8_UINT(127),

    /**
     * Combined depth/stencil format with a 16-bit unsigned normalized depth component and an 8-bit
     * unsigned integer stencil component.
     */
    VK_FORMAT_D16_UNORM_S8_UINT(128),

    /**
     * Combined depth/stencil format with a 24-bit unsigned normalized depth component and an 8-bit
     * unsigned integer stencil component.
     */
    VK_FORMAT_D24_UNORM_S8_UINT(129),

    /**
     * Combined depth/stencil format with a 32-bit signed floating-point depth component and an
     * 8-bit unsigned integer stencil component.
     */
    VK_FORMAT_D32_SFLOAT_S8_UINT(130),

    /**
     * BC1 block-compressed RGB texels, unsigned normalized: each 4x4 block of texels takes 8 bytes.
     */
    VK_FORMAT_BC1_RGB_UNORM_BLOCK(131),

    /**
     * BC1 block-compressed RGB texels, sRGB-encoded unsigned normalized: each 4x4 block of texels
     * takes 8 bytes.
     */
    VK_FORMAT_BC1_RGB_SRGB_BLOCK(132),

    /**
     * BC1 block-compressed RGB plus 1-bit alpha texels, unsigned normalized: each 4x4 block of
     * texels takes 8 bytes.
     */
    VK_FORMAT_BC1_RGBA_UNORM_BLOCK(133),

    /**
     * BC1 block-compressed RGB plus 1-bit alpha texels, sRGB-encoded unsigned normalized: each 4x4
     * block of texels takes 8 bytes.
     */
    VK_FORMAT_BC1_RGBA_SRGB_BLOCK(134),

    /**
     * BC2 block-compressed RGBA with explicit alpha texels, unsigned normalized: each 4x4 block of
     * texels takes 16 bytes.
     */
    VK_FORMAT_BC2_UNORM_BLOCK(135),

    /**
     * BC2 block-compressed RGBA with explicit alpha texels, sRGB-encoded unsigned normalized: each
     * 4x4 block of texels takes 16 bytes.
     */
    VK_FORMAT_BC2_SRGB_BLOCK(136),

    /**
     * BC3 block-compressed RGBA with interpolated alpha texels, unsigned normalized: each 4x4 block
     * of texels takes 16 bytes.
     */
    VK_FORMAT_BC3_UNORM_BLOCK(137),

    /**
     * BC3 block-compressed RGBA with interpolated alpha texels, sRGB-encoded unsigned normalized:
     * each 4x4 block of texels takes 16 bytes.
     */
    VK_FORMAT_BC3_SRGB_BLOCK(138),

    /**
     * BC4 block-compressed single-channel (red) texels, unsigned normalized: each 4x4 block of
     * texels takes 8 bytes.
     */
    VK_FORMAT_BC4_UNORM_BLOCK(139),

    /**
     * BC4 block-compressed single-channel (red) texels, signed normalized: each 4x4 block of texels
     * takes 8 bytes.
     */
    VK_FORMAT_BC4_SNORM_BLOCK(140),

    /**
     * BC5 block-compressed two-channel (red and green) texels, unsigned normalized: each 4x4 block
     * of texels takes 16 bytes.
     */
    VK_FORMAT_BC5_UNORM_BLOCK(141),

    /**
     * BC5 block-compressed two-channel (red and green) texels, signed normalized: each 4x4 block of
     * texels takes 16 bytes.
     */
    VK_FORMAT_BC5_SNORM_BLOCK(142),

    /**
     * BC6H block-compressed RGB high-dynamic-range texels, unsigned floating-point: each 4x4 block
     * of texels takes 16 bytes.
     */
    VK_FORMAT_BC6H_UFLOAT_BLOCK(143),

    /**
     * BC6H block-compressed RGB high-dynamic-range texels, signed floating-point: each 4x4 block of
     * texels takes 16 bytes.
     */
    VK_FORMAT_BC6H_SFLOAT_BLOCK(144),

    /**
     * BC7 block-compressed RGBA texels, unsigned normalized: each 4x4 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_BC7_UNORM_BLOCK(145),

    /**
     * BC7 block-compressed RGBA texels, sRGB-encoded unsigned normalized: each 4x4 block of texels
     * takes 16 bytes.
     */
    VK_FORMAT_BC7_SRGB_BLOCK(146),

    /** ETC2 block-compressed RGB texels, unsigned normalized: each 4x4 block takes 8 bytes. */
    VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK(147),

    /**
     * ETC2 block-compressed RGB texels, sRGB-encoded unsigned normalized: each 4x4 block takes 8
     * bytes.
     */
    VK_FORMAT_ETC2_R8G8B8_SRGB_BLOCK(148),

    /**
     * ETC2 block-compressed RGB plus 1-bit alpha texels, unsigned normalized: each 4x4 block takes
     * 8 bytes.
     */
    VK_FORMAT_ETC2_R8G8B8A1_UNORM_BLOCK(149),

    /**
     * ETC2 block-compressed RGB plus 1-bit alpha texels, sRGB-encoded unsigned normalized: each 4x4
     * block takes 8 bytes.
     */
    VK_FORMAT_ETC2_R8G8B8A1_SRGB_BLOCK(150),

    /** ETC2 block-compressed RGBA texels, unsigned normalized: each 4x4 block takes 16 bytes. */
    VK_FORMAT_ETC2_R8G8B8A8_UNORM_BLOCK(151),

    /**
     * ETC2 block-compressed RGBA texels, sRGB-encoded unsigned normalized: each 4x4 block takes 16
     * bytes.
     */
    VK_FORMAT_ETC2_R8G8B8A8_SRGB_BLOCK(152),

    /**
     * EAC block-compressed single-channel texels with 11-bit precision, unsigned normalized: each
     * 4x4 block takes 8 bytes.
     */
    VK_FORMAT_EAC_R11_UNORM_BLOCK(153),

    /**
     * EAC block-compressed single-channel texels with 11-bit precision, signed normalized: each 4x4
     * block takes 8 bytes.
     */
    VK_FORMAT_EAC_R11_SNORM_BLOCK(154),

    /**
     * EAC block-compressed two-channel texels with 11-bit precision, unsigned normalized: each 4x4
     * block takes 16 bytes.
     */
    VK_FORMAT_EAC_R11G11_UNORM_BLOCK(155),

    /**
     * EAC block-compressed two-channel texels with 11-bit precision, signed normalized: each 4x4
     * block takes 16 bytes.
     */
    VK_FORMAT_EAC_R11G11_SNORM_BLOCK(156),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 4x4 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_4x4_UNORM_BLOCK(157),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 4x4 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_4x4_SRGB_BLOCK(158),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 5x4 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_5x4_UNORM_BLOCK(159),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 5x4 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_5x4_SRGB_BLOCK(160),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 5x5 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_5x5_UNORM_BLOCK(161),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 5x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_5x5_SRGB_BLOCK(162),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 6x5 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_6x5_UNORM_BLOCK(163),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 6x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_6x5_SRGB_BLOCK(164),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 6x6 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_6x6_UNORM_BLOCK(165),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 6x6 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_6x6_SRGB_BLOCK(166),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 8x5 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_8x5_UNORM_BLOCK(167),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 8x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_8x5_SRGB_BLOCK(168),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 8x6 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_8x6_UNORM_BLOCK(169),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 8x6 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_8x6_SRGB_BLOCK(170),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 8x8 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_8x8_UNORM_BLOCK(171),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 8x8 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_8x8_SRGB_BLOCK(172),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 10x5 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_10x5_UNORM_BLOCK(173),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 10x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_10x5_SRGB_BLOCK(174),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 10x6 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_10x6_UNORM_BLOCK(175),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 10x6 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_10x6_SRGB_BLOCK(176),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 10x8 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_10x8_UNORM_BLOCK(177),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 10x8 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_10x8_SRGB_BLOCK(178),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 10x10 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_10x10_UNORM_BLOCK(179),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 10x10 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_10x10_SRGB_BLOCK(180),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 12x10 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_12x10_UNORM_BLOCK(181),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 12x10 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_12x10_SRGB_BLOCK(182),

    /**
     * ASTC block-compressed texels (LDR, unsigned normalized): each 12x12 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_12x12_UNORM_BLOCK(183),

    /**
     * ASTC block-compressed texels (LDR, sRGB-encoded): each 12x12 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_12x12_SRGB_BLOCK(184),

    /**
     * Interleaved single plane in G0 B G1 R order; 4:2:2 subsampling, with chroma halved
     * horizontally; 8-bit samples, unsigned normalized.
     */
    VK_FORMAT_G8B8G8R8_422_UNORM(1000156000),

    /**
     * Interleaved single plane in B G0 R G1 order; 4:2:2 subsampling, with chroma halved
     * horizontally; 8-bit samples, unsigned normalized.
     */
    VK_FORMAT_B8G8R8G8_422_UNORM(1000156001),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:0
     * subsampling, with chroma halved horizontally and vertically; 8-bit samples, unsigned
     * normalized.
     */
    VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM(1000156002),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:0 subsampling, with chroma halved
     * horizontally and vertically; 8-bit samples, unsigned normalized.
     */
    VK_FORMAT_G8_B8R8_2PLANE_420_UNORM(1000156003),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:2
     * subsampling, with chroma halved horizontally; 8-bit samples, unsigned normalized.
     */
    VK_FORMAT_G8_B8_R8_3PLANE_422_UNORM(1000156004),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:2 subsampling, with chroma halved
     * horizontally; 8-bit samples, unsigned normalized.
     */
    VK_FORMAT_G8_B8R8_2PLANE_422_UNORM(1000156005),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:4:4 sampling,
     * with chroma at full resolution; 8-bit samples, unsigned normalized.
     */
    VK_FORMAT_G8_B8_R8_3PLANE_444_UNORM(1000156006),

    /**
     * One-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: red (10 bits) and unused padding (6 bits).
     */
    VK_FORMAT_R10X6_UNORM_PACK16(1000156007),

    /**
     * Two-component unsigned normalized format of red and green, each 10-bit sample held in the
     * high bits of its own 16-bit word.
     */
    VK_FORMAT_R10X6G10X6_UNORM_2PACK16(1000156008),

    /**
     * Four-component unsigned normalized format of red, green, blue and alpha, each 10-bit sample
     * held in the high bits of its own 16-bit word.
     */
    VK_FORMAT_R10X6G10X6B10X6A10X6_UNORM_4PACK16(1000156009),

    /**
     * Interleaved single plane in G0 B G1 R order; 4:2:2 subsampling, with chroma halved
     * horizontally; 10-bit samples held in the high 10 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G10X6B10X6G10X6R10X6_422_UNORM_4PACK16(1000156010),

    /**
     * Interleaved single plane in B G0 R G1 order; 4:2:2 subsampling, with chroma halved
     * horizontally; 10-bit samples held in the high 10 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_B10X6G10X6R10X6G10X6_422_UNORM_4PACK16(1000156011),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:0
     * subsampling, with chroma halved horizontally and vertically; 10-bit samples held in the high
     * 10 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_420_UNORM_3PACK16(1000156012),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:0 subsampling, with chroma halved
     * horizontally and vertically; 10-bit samples held in the high 10 bits of 16-bit words,
     * unsigned normalized.
     */
    VK_FORMAT_G10X6_B10X6R10X6_2PLANE_420_UNORM_3PACK16(1000156013),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:2
     * subsampling, with chroma halved horizontally; 10-bit samples held in the high 10 bits of
     * 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_422_UNORM_3PACK16(1000156014),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:2 subsampling, with chroma halved
     * horizontally; 10-bit samples held in the high 10 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G10X6_B10X6R10X6_2PLANE_422_UNORM_3PACK16(1000156015),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:4:4 sampling,
     * with chroma at full resolution; 10-bit samples held in the high 10 bits of 16-bit words,
     * unsigned normalized.
     */
    VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_444_UNORM_3PACK16(1000156016),

    /**
     * One-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: red (12 bits) and unused padding (4 bits).
     */
    VK_FORMAT_R12X4_UNORM_PACK16(1000156017),

    /**
     * Two-component unsigned normalized format of red and green, each 12-bit sample held in the
     * high bits of its own 16-bit word.
     */
    VK_FORMAT_R12X4G12X4_UNORM_2PACK16(1000156018),

    /**
     * Four-component unsigned normalized format of red, green, blue and alpha, each 12-bit sample
     * held in the high bits of its own 16-bit word.
     */
    VK_FORMAT_R12X4G12X4B12X4A12X4_UNORM_4PACK16(1000156019),

    /**
     * Interleaved single plane in G0 B G1 R order; 4:2:2 subsampling, with chroma halved
     * horizontally; 12-bit samples held in the high 12 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G12X4B12X4G12X4R12X4_422_UNORM_4PACK16(1000156020),

    /**
     * Interleaved single plane in B G0 R G1 order; 4:2:2 subsampling, with chroma halved
     * horizontally; 12-bit samples held in the high 12 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_B12X4G12X4R12X4G12X4_422_UNORM_4PACK16(1000156021),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:0
     * subsampling, with chroma halved horizontally and vertically; 12-bit samples held in the high
     * 12 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_420_UNORM_3PACK16(1000156022),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:0 subsampling, with chroma halved
     * horizontally and vertically; 12-bit samples held in the high 12 bits of 16-bit words,
     * unsigned normalized.
     */
    VK_FORMAT_G12X4_B12X4R12X4_2PLANE_420_UNORM_3PACK16(1000156023),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:2
     * subsampling, with chroma halved horizontally; 12-bit samples held in the high 12 bits of
     * 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_422_UNORM_3PACK16(1000156024),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:2 subsampling, with chroma halved
     * horizontally; 12-bit samples held in the high 12 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G12X4_B12X4R12X4_2PLANE_422_UNORM_3PACK16(1000156025),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:4:4 sampling,
     * with chroma at full resolution; 12-bit samples held in the high 12 bits of 16-bit words,
     * unsigned normalized.
     */
    VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_444_UNORM_3PACK16(1000156026),

    /**
     * Interleaved single plane in G0 B G1 R order; 4:2:2 subsampling, with chroma halved
     * horizontally; 16-bit samples, unsigned normalized.
     */
    VK_FORMAT_G16B16G16R16_422_UNORM(1000156027),

    /**
     * Interleaved single plane in B G0 R G1 order; 4:2:2 subsampling, with chroma halved
     * horizontally; 16-bit samples, unsigned normalized.
     */
    VK_FORMAT_B16G16R16G16_422_UNORM(1000156028),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:0
     * subsampling, with chroma halved horizontally and vertically; 16-bit samples, unsigned
     * normalized.
     */
    VK_FORMAT_G16_B16_R16_3PLANE_420_UNORM(1000156029),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:0 subsampling, with chroma halved
     * horizontally and vertically; 16-bit samples, unsigned normalized.
     */
    VK_FORMAT_G16_B16R16_2PLANE_420_UNORM(1000156030),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:2:2
     * subsampling, with chroma halved horizontally; 16-bit samples, unsigned normalized.
     */
    VK_FORMAT_G16_B16_R16_3PLANE_422_UNORM(1000156031),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:2:2 subsampling, with chroma halved
     * horizontally; 16-bit samples, unsigned normalized.
     */
    VK_FORMAT_G16_B16R16_2PLANE_422_UNORM(1000156032),

    /**
     * Three planes: separate luma (G), blue-difference (B) and red-difference (R); 4:4:4 sampling,
     * with chroma at full resolution; 16-bit samples, unsigned normalized.
     */
    VK_FORMAT_G16_B16_R16_3PLANE_444_UNORM(1000156033),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:4:4 sampling, with chroma at full
     * resolution; 8-bit samples, unsigned normalized.
     */
    VK_FORMAT_G8_B8R8_2PLANE_444_UNORM(1000330000),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:4:4 sampling, with chroma at full
     * resolution; 10-bit samples held in the high 10 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G10X6_B10X6R10X6_2PLANE_444_UNORM_3PACK16(1000330001),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:4:4 sampling, with chroma at full
     * resolution; 12-bit samples held in the high 12 bits of 16-bit words, unsigned normalized.
     */
    VK_FORMAT_G12X4_B12X4R12X4_2PLANE_444_UNORM_3PACK16(1000330002),

    /**
     * Two planes: luma (G) then interleaved chroma (B, R); 4:4:4 sampling, with chroma at full
     * resolution; 16-bit samples, unsigned normalized.
     */
    VK_FORMAT_G16_B16R16_2PLANE_444_UNORM(1000330003),

    /**
     * Four-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: 4 bits each of alpha, red, green and blue.
     */
    VK_FORMAT_A4R4G4B4_UNORM_PACK16(1000340000),

    /**
     * Four-component, 16-bit packed unsigned normalized format. Components run from the most
     * significant bits down: 4 bits each of alpha, blue, green and red.
     */
    VK_FORMAT_A4B4G4R4_UNORM_PACK16(1000340001),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 4x4 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_4x4_SFLOAT_BLOCK(1000066000),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 5x4 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_5x4_SFLOAT_BLOCK(1000066001),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 5x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_5x5_SFLOAT_BLOCK(1000066002),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 6x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_6x5_SFLOAT_BLOCK(1000066003),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 6x6 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_6x6_SFLOAT_BLOCK(1000066004),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 8x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_8x5_SFLOAT_BLOCK(1000066005),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 8x6 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_8x6_SFLOAT_BLOCK(1000066006),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 8x8 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_8x8_SFLOAT_BLOCK(1000066007),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 10x5 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_10x5_SFLOAT_BLOCK(1000066008),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 10x6 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_10x6_SFLOAT_BLOCK(1000066009),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 10x8 block of texels takes 16 bytes.
     */
    VK_FORMAT_ASTC_10x8_SFLOAT_BLOCK(1000066010),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 10x10 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_10x10_SFLOAT_BLOCK(1000066011),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 12x10 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_12x10_SFLOAT_BLOCK(1000066012),

    /**
     * ASTC block-compressed texels (HDR, floating-point): each 12x12 block of texels takes 16
     * bytes.
     */
    VK_FORMAT_ASTC_12x12_SFLOAT_BLOCK(1000066013),

    /** PVRTC1 block-compressed texels at 2 bits per texel, unsigned normalized. */
    VK_FORMAT_PVRTC1_2BPP_UNORM_BLOCK_IMG(1000054000),

    /** PVRTC1 block-compressed texels at 4 bits per texel, unsigned normalized. */
    VK_FORMAT_PVRTC1_4BPP_UNORM_BLOCK_IMG(1000054001),

    /** PVRTC2 block-compressed texels at 2 bits per texel, unsigned normalized. */
    VK_FORMAT_PVRTC2_2BPP_UNORM_BLOCK_IMG(1000054002),

    /** PVRTC2 block-compressed texels at 4 bits per texel, unsigned normalized. */
    VK_FORMAT_PVRTC2_4BPP_UNORM_BLOCK_IMG(1000054003),

    /** PVRTC1 block-compressed texels at 2 bits per texel, sRGB-encoded unsigned normalized. */
    VK_FORMAT_PVRTC1_2BPP_SRGB_BLOCK_IMG(1000054004),

    /** PVRTC1 block-compressed texels at 4 bits per texel, sRGB-encoded unsigned normalized. */
    VK_FORMAT_PVRTC1_4BPP_SRGB_BLOCK_IMG(1000054005),

    /** PVRTC2 block-compressed texels at 2 bits per texel, sRGB-encoded unsigned normalized. */
    VK_FORMAT_PVRTC2_2BPP_SRGB_BLOCK_IMG(1000054006),

    /** PVRTC2 block-compressed texels at 4 bits per texel, sRGB-encoded unsigned normalized. */
    VK_FORMAT_PVRTC2_4BPP_SRGB_BLOCK_IMG(1000054007),

    /** Alias of [VK_FORMAT_ASTC_4x4_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_4x4_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_4x4_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_5x4_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_5x4_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_5x4_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_5x5_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_5x5_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_5x5_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_6x5_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_6x5_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_6x5_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_6x6_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_6x6_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_6x6_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_8x5_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_8x5_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_8x5_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_8x6_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_8x6_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_8x6_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_8x8_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_8x8_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_8x8_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_10x5_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_10x5_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_10x5_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_10x6_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_10x6_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_10x6_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_10x8_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_10x8_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_10x8_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_10x10_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_10x10_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_10x10_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_12x10_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_12x10_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_12x10_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_ASTC_12x12_SFLOAT_BLOCK]; both names carry the same value. */
    VK_FORMAT_ASTC_12x12_SFLOAT_BLOCK_EXT(VK_FORMAT_ASTC_12x12_SFLOAT_BLOCK.value),

    /** Alias of [VK_FORMAT_G8B8G8R8_422_UNORM]; both names carry the same value. */
    VK_FORMAT_G8B8G8R8_422_UNORM_KHR(VK_FORMAT_G8B8G8R8_422_UNORM.value),

    /** Alias of [VK_FORMAT_B8G8R8G8_422_UNORM]; both names carry the same value. */
    VK_FORMAT_B8G8R8G8_422_UNORM_KHR(VK_FORMAT_B8G8R8G8_422_UNORM.value),

    /** Alias of [VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM]; both names carry the same value. */
    VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM_KHR(VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM.value),

    /** Alias of [VK_FORMAT_G8_B8R8_2PLANE_420_UNORM]; both names carry the same value. */
    VK_FORMAT_G8_B8R8_2PLANE_420_UNORM_KHR(VK_FORMAT_G8_B8R8_2PLANE_420_UNORM.value),

    /** Alias of [VK_FORMAT_G8_B8_R8_3PLANE_422_UNORM]; both names carry the same value. */
    VK_FORMAT_G8_B8_R8_3PLANE_422_UNORM_KHR(VK_FORMAT_G8_B8_R8_3PLANE_422_UNORM.value),

    /** Alias of [VK_FORMAT_G8_B8R8_2PLANE_422_UNORM]; both names carry the same value. */
    VK_FORMAT_G8_B8R8_2PLANE_422_UNORM_KHR(VK_FORMAT_G8_B8R8_2PLANE_422_UNORM.value),

    /** Alias of [VK_FORMAT_G8_B8_R8_3PLANE_444_UNORM]; both names carry the same value. */
    VK_FORMAT_G8_B8_R8_3PLANE_444_UNORM_KHR(VK_FORMAT_G8_B8_R8_3PLANE_444_UNORM.value),

    /** Alias of [VK_FORMAT_R10X6_UNORM_PACK16]; both names carry the same value. */
    VK_FORMAT_R10X6_UNORM_PACK16_KHR(VK_FORMAT_R10X6_UNORM_PACK16.value),

    /** Alias of [VK_FORMAT_R10X6G10X6_UNORM_2PACK16]; both names carry the same value. */
    VK_FORMAT_R10X6G10X6_UNORM_2PACK16_KHR(VK_FORMAT_R10X6G10X6_UNORM_2PACK16.value),

    /** Alias of [VK_FORMAT_R10X6G10X6B10X6A10X6_UNORM_4PACK16]; both names carry the same value. */
    VK_FORMAT_R10X6G10X6B10X6A10X6_UNORM_4PACK16_KHR(VK_FORMAT_R10X6G10X6B10X6A10X6_UNORM_4PACK16.value),

    /**
     * Alias of [VK_FORMAT_G10X6B10X6G10X6R10X6_422_UNORM_4PACK16]; both names carry the same value.
     */
    VK_FORMAT_G10X6B10X6G10X6R10X6_422_UNORM_4PACK16_KHR(
        VK_FORMAT_G10X6B10X6G10X6R10X6_422_UNORM_4PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_B10X6G10X6R10X6G10X6_422_UNORM_4PACK16]; both names carry the same value.
     */
    VK_FORMAT_B10X6G10X6R10X6G10X6_422_UNORM_4PACK16_KHR(
        VK_FORMAT_B10X6G10X6R10X6G10X6_422_UNORM_4PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_420_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_420_UNORM_3PACK16_KHR(
        VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_420_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G10X6_B10X6R10X6_2PLANE_420_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G10X6_B10X6R10X6_2PLANE_420_UNORM_3PACK16_KHR(
        VK_FORMAT_G10X6_B10X6R10X6_2PLANE_420_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_422_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_422_UNORM_3PACK16_KHR(
        VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_422_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G10X6_B10X6R10X6_2PLANE_422_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G10X6_B10X6R10X6_2PLANE_422_UNORM_3PACK16_KHR(
        VK_FORMAT_G10X6_B10X6R10X6_2PLANE_422_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_444_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_444_UNORM_3PACK16_KHR(
        VK_FORMAT_G10X6_B10X6_R10X6_3PLANE_444_UNORM_3PACK16.value,
    ),

    /** Alias of [VK_FORMAT_R12X4_UNORM_PACK16]; both names carry the same value. */
    VK_FORMAT_R12X4_UNORM_PACK16_KHR(VK_FORMAT_R12X4_UNORM_PACK16.value),

    /** Alias of [VK_FORMAT_R12X4G12X4_UNORM_2PACK16]; both names carry the same value. */
    VK_FORMAT_R12X4G12X4_UNORM_2PACK16_KHR(VK_FORMAT_R12X4G12X4_UNORM_2PACK16.value),

    /** Alias of [VK_FORMAT_R12X4G12X4B12X4A12X4_UNORM_4PACK16]; both names carry the same value. */
    VK_FORMAT_R12X4G12X4B12X4A12X4_UNORM_4PACK16_KHR(VK_FORMAT_R12X4G12X4B12X4A12X4_UNORM_4PACK16.value),

    /**
     * Alias of [VK_FORMAT_G12X4B12X4G12X4R12X4_422_UNORM_4PACK16]; both names carry the same value.
     */
    VK_FORMAT_G12X4B12X4G12X4R12X4_422_UNORM_4PACK16_KHR(
        VK_FORMAT_G12X4B12X4G12X4R12X4_422_UNORM_4PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_B12X4G12X4R12X4G12X4_422_UNORM_4PACK16]; both names carry the same value.
     */
    VK_FORMAT_B12X4G12X4R12X4G12X4_422_UNORM_4PACK16_KHR(
        VK_FORMAT_B12X4G12X4R12X4G12X4_422_UNORM_4PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_420_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_420_UNORM_3PACK16_KHR(
        VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_420_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G12X4_B12X4R12X4_2PLANE_420_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G12X4_B12X4R12X4_2PLANE_420_UNORM_3PACK16_KHR(
        VK_FORMAT_G12X4_B12X4R12X4_2PLANE_420_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_422_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_422_UNORM_3PACK16_KHR(
        VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_422_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G12X4_B12X4R12X4_2PLANE_422_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G12X4_B12X4R12X4_2PLANE_422_UNORM_3PACK16_KHR(
        VK_FORMAT_G12X4_B12X4R12X4_2PLANE_422_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_444_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_444_UNORM_3PACK16_KHR(
        VK_FORMAT_G12X4_B12X4_R12X4_3PLANE_444_UNORM_3PACK16.value,
    ),

    /** Alias of [VK_FORMAT_G16B16G16R16_422_UNORM]; both names carry the same value. */
    VK_FORMAT_G16B16G16R16_422_UNORM_KHR(VK_FORMAT_G16B16G16R16_422_UNORM.value),

    /** Alias of [VK_FORMAT_B16G16R16G16_422_UNORM]; both names carry the same value. */
    VK_FORMAT_B16G16R16G16_422_UNORM_KHR(VK_FORMAT_B16G16R16G16_422_UNORM.value),

    /** Alias of [VK_FORMAT_G16_B16_R16_3PLANE_420_UNORM]; both names carry the same value. */
    VK_FORMAT_G16_B16_R16_3PLANE_420_UNORM_KHR(VK_FORMAT_G16_B16_R16_3PLANE_420_UNORM.value),

    /** Alias of [VK_FORMAT_G16_B16R16_2PLANE_420_UNORM]; both names carry the same value. */
    VK_FORMAT_G16_B16R16_2PLANE_420_UNORM_KHR(VK_FORMAT_G16_B16R16_2PLANE_420_UNORM.value),

    /** Alias of [VK_FORMAT_G16_B16_R16_3PLANE_422_UNORM]; both names carry the same value. */
    VK_FORMAT_G16_B16_R16_3PLANE_422_UNORM_KHR(VK_FORMAT_G16_B16_R16_3PLANE_422_UNORM.value),

    /** Alias of [VK_FORMAT_G16_B16R16_2PLANE_422_UNORM]; both names carry the same value. */
    VK_FORMAT_G16_B16R16_2PLANE_422_UNORM_KHR(VK_FORMAT_G16_B16R16_2PLANE_422_UNORM.value),

    /** Alias of [VK_FORMAT_G16_B16_R16_3PLANE_444_UNORM]; both names carry the same value. */
    VK_FORMAT_G16_B16_R16_3PLANE_444_UNORM_KHR(VK_FORMAT_G16_B16_R16_3PLANE_444_UNORM.value),

    /** Alias of [VK_FORMAT_G8_B8R8_2PLANE_444_UNORM]; both names carry the same value. */
    VK_FORMAT_G8_B8R8_2PLANE_444_UNORM_EXT(VK_FORMAT_G8_B8R8_2PLANE_444_UNORM.value),

    /**
     * Alias of [VK_FORMAT_G10X6_B10X6R10X6_2PLANE_444_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G10X6_B10X6R10X6_2PLANE_444_UNORM_3PACK16_EXT(
        VK_FORMAT_G10X6_B10X6R10X6_2PLANE_444_UNORM_3PACK16.value,
    ),

    /**
     * Alias of [VK_FORMAT_G12X4_B12X4R12X4_2PLANE_444_UNORM_3PACK16]; both names carry the same
     * value.
     */
    VK_FORMAT_G12X4_B12X4R12X4_2PLANE_444_UNORM_3PACK16_EXT(
        VK_FORMAT_G12X4_B12X4R12X4_2PLANE_444_UNORM_3PACK16.value,
    ),

    /** Alias of [VK_FORMAT_G16_B16R16_2PLANE_444_UNORM]; both names carry the same value. */
    VK_FORMAT_G16_B16R16_2PLANE_444_UNORM_EXT(VK_FORMAT_G16_B16R16_2PLANE_444_UNORM.value),

    /** Alias of [VK_FORMAT_A4R4G4B4_UNORM_PACK16]; both names carry the same value. */
    VK_FORMAT_A4R4G4B4_UNORM_PACK16_EXT(VK_FORMAT_A4R4G4B4_UNORM_PACK16.value),

    /** Alias of [VK_FORMAT_A4B4G4R4_UNORM_PACK16]; both names carry the same value. */
    VK_FORMAT_A4B4G4R4_UNORM_PACK16_EXT(VK_FORMAT_A4B4G4R4_UNORM_PACK16.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_FORMAT_MAX_ENUM(0x7FFFFFFF),
}
