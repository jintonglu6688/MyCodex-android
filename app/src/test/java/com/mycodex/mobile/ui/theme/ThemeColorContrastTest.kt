package com.mycodex.mobile.ui.theme

import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ColorScheme
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorContrastTest {
    @Test
    fun lightThemeTextPairsMeetContrastBaseline() {
        val pairs = listOf(
            "primary" to (AppLightOnPrimaryArgb to AppLightPrimaryArgb),
            "primary container" to (AppLightOnPrimaryContainerArgb to AppLightPrimaryContainerArgb),
            "secondary" to (AppLightOnSecondaryArgb to AppLightSecondaryArgb),
            "secondary container" to (AppLightOnSecondaryContainerArgb to AppLightSecondaryContainerArgb),
            "tertiary" to (AppLightOnTertiaryArgb to AppLightTertiaryArgb),
            "tertiary container" to (AppLightOnTertiaryContainerArgb to AppLightTertiaryContainerArgb),
            "background" to (AppLightOnBackgroundArgb to AppLightBackgroundArgb),
            "surface" to (AppLightOnSurfaceArgb to AppLightSurfaceArgb),
            "surface variant" to (AppLightOnSurfaceVariantArgb to AppLightSurfaceVariantArgb),
            "transparent row secondary" to (AppLightOnSurfaceVariantArgb to AppLightBackgroundArgb),
            "transparent row footer" to (AppLightPrimaryArgb to AppLightBackgroundArgb),
            "error" to (AppLightOnErrorArgb to AppLightErrorArgb),
            "error container" to (AppLightOnErrorContainerArgb to AppLightErrorContainerArgb),
            "page error" to (AppLightErrorArgb to AppLightBackgroundArgb),
            "inverse" to (AppLightInverseOnSurfaceArgb to AppLightInverseSurfaceArgb),
            "disabled button" to (AppLightOnSurfaceVariantArgb to AppLightSurfaceContainerHighestArgb),
        )
        pairs.forEach { (label, colors) -> assertContrast("light $label", colors.first, colors.second) }
        assertContrast("light outline", AppLightOutlineArgb, AppLightSurfaceArgb, 3.0)
    }

    @Test
    fun darkThemeTextPairsMeetContrastBaseline() {
        val pairs = listOf(
            "primary" to (AppDarkOnPrimaryArgb to AppDarkPrimaryArgb),
            "primary container" to (AppDarkOnPrimaryContainerArgb to AppDarkPrimaryContainerArgb),
            "secondary" to (AppDarkOnSecondaryArgb to AppDarkSecondaryArgb),
            "secondary container" to (AppDarkOnSecondaryContainerArgb to AppDarkSecondaryContainerArgb),
            "tertiary" to (AppDarkOnTertiaryArgb to AppDarkTertiaryArgb),
            "tertiary container" to (AppDarkOnTertiaryContainerArgb to AppDarkTertiaryContainerArgb),
            "background" to (AppDarkOnBackgroundArgb to AppDarkBackgroundArgb),
            "surface" to (AppDarkOnSurfaceArgb to AppDarkSurfaceArgb),
            "surface variant" to (AppDarkOnSurfaceVariantArgb to AppDarkSurfaceVariantArgb),
            "transparent row secondary" to (AppDarkOnSurfaceVariantArgb to AppDarkBackgroundArgb),
            "transparent row footer" to (AppDarkPrimaryArgb to AppDarkBackgroundArgb),
            "error" to (AppDarkOnErrorArgb to AppDarkErrorArgb),
            "error container" to (AppDarkOnErrorContainerArgb to AppDarkErrorContainerArgb),
            "page error" to (AppDarkErrorArgb to AppDarkBackgroundArgb),
            "inverse" to (AppDarkInverseOnSurfaceArgb to AppDarkInverseSurfaceArgb),
            "disabled button" to (AppDarkOnSurfaceVariantArgb to AppDarkSurfaceContainerHighestArgb),
        )
        pairs.forEach { (label, colors) -> assertContrast("dark $label", colors.first, colors.second) }
        assertContrast("dark outline", AppDarkOutlineArgb, AppDarkSurfaceArgb, 3.0)
    }

    @Test
    fun fixedRolesMeetContrastBaseline() {
        assertContrast("fixed primary", AppOnPrimaryFixedArgb, AppPrimaryFixedArgb)
        assertContrast("fixed primary variant", AppOnPrimaryFixedVariantArgb, AppPrimaryFixedArgb)
        assertContrast("fixed secondary", AppOnSecondaryFixedArgb, AppSecondaryFixedArgb)
        assertContrast("fixed secondary variant", AppOnSecondaryFixedVariantArgb, AppSecondaryFixedArgb)
        assertContrast("fixed tertiary", AppOnTertiaryFixedArgb, AppTertiaryFixedArgb)
        assertContrast("fixed tertiary variant", AppOnTertiaryFixedVariantArgb, AppTertiaryFixedArgb)
    }

    @Test
    fun colorSchemesUseTheExactSpecifiedRoles() {
        assertEquals(expectedLightRoles(), LightColorScheme.roles())
        assertEquals(expectedDarkRoles(), DarkColorScheme.roles())
    }

    @Test
    fun everyPaletteRoleIsOpaqueAndDynamicColorDefaultsOff() {
        listOf(LightColorScheme, DarkColorScheme).forEach { scheme ->
            scheme.roles()
                .filterKeys { it != "scrim" }
                .forEach { (name, argb) ->
                    assertEquals("$name must be opaque", 0xFF, (argb ushr 24).toInt())
                }
        }
        assertFalse(MyCodexDynamicColorDefault)
    }

    @Test
    fun actualContainerOutlineInverseAndFixedRolesMeetContrastRequirements() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (name, scheme) ->
            listOf(
                "primary container" to (scheme.onPrimaryContainer.argb() to scheme.primaryContainer.argb()),
                "secondary container" to (scheme.onSecondaryContainer.argb() to scheme.secondaryContainer.argb()),
                "tertiary container" to (scheme.onTertiaryContainer.argb() to scheme.tertiaryContainer.argb()),
                "error container" to (scheme.onErrorContainer.argb() to scheme.errorContainer.argb()),
                "inverse" to (scheme.inverseOnSurface.argb() to scheme.inverseSurface.argb()),
                "primary fixed" to (scheme.onPrimaryFixed.argb() to scheme.primaryFixed.argb()),
                "secondary fixed" to (scheme.onSecondaryFixed.argb() to scheme.secondaryFixed.argb()),
                "tertiary fixed" to (scheme.onTertiaryFixed.argb() to scheme.tertiaryFixed.argb()),
            ).forEach { (role, colors) ->
                assertContrast("$name $role", colors.first, colors.second)
            }
            assertContrast("$name outline", scheme.outline.argb(), scheme.surface.argb(), 3.0)
        }
    }

    @Test
    fun phaseTwoSurfacePairsMeetContrastRequirements() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (name, scheme) ->
            listOf(
                "top bar title" to (scheme.onSurface.argb() to scheme.surfaceContainerLowest.argb()),
                "top bar action" to (scheme.primary.argb() to scheme.surfaceContainerLowest.argb()),
                "unselected tab" to (scheme.onSurfaceVariant.argb() to scheme.surfaceContainer.argb()),
                "selected tab" to (scheme.primary.argb() to scheme.surfaceContainer.argb()),
                "device card" to (scheme.onPrimaryContainer.argb() to scheme.primaryContainer.argb()),
                "group title" to (scheme.onSurface.argb() to scheme.surfaceContainerLow.argb()),
                "group body" to (scheme.onSurfaceVariant.argb() to scheme.surfaceContainerLow.argb()),
            ).forEach { (role, colors) ->
                assertContrast("$name $role", colors.first, colors.second)
            }
            assertContrast(
                "$name selected tab icon",
                scheme.primary.argb(),
                scheme.primaryContainer.argb(),
                3.0,
            )
        }
    }

    private fun ColorScheme.roles(): Map<String, Long> = linkedMapOf(
        "primary" to primary.argb(),
        "onPrimary" to onPrimary.argb(),
        "primaryContainer" to primaryContainer.argb(),
        "onPrimaryContainer" to onPrimaryContainer.argb(),
        "secondary" to secondary.argb(),
        "onSecondary" to onSecondary.argb(),
        "secondaryContainer" to secondaryContainer.argb(),
        "onSecondaryContainer" to onSecondaryContainer.argb(),
        "tertiary" to tertiary.argb(),
        "onTertiary" to onTertiary.argb(),
        "tertiaryContainer" to tertiaryContainer.argb(),
        "onTertiaryContainer" to onTertiaryContainer.argb(),
        "error" to error.argb(),
        "onError" to onError.argb(),
        "errorContainer" to errorContainer.argb(),
        "onErrorContainer" to onErrorContainer.argb(),
        "background" to background.argb(),
        "onBackground" to onBackground.argb(),
        "surface" to surface.argb(),
        "onSurface" to onSurface.argb(),
        "surfaceVariant" to surfaceVariant.argb(),
        "onSurfaceVariant" to onSurfaceVariant.argb(),
        "surfaceTint" to surfaceTint.argb(),
        "inverseSurface" to inverseSurface.argb(),
        "inverseOnSurface" to inverseOnSurface.argb(),
        "inversePrimary" to inversePrimary.argb(),
        "outline" to outline.argb(),
        "outlineVariant" to outlineVariant.argb(),
        "scrim" to scrim.argb(),
        "surfaceBright" to surfaceBright.argb(),
        "surfaceDim" to surfaceDim.argb(),
        "surfaceContainer" to surfaceContainer.argb(),
        "surfaceContainerHigh" to surfaceContainerHigh.argb(),
        "surfaceContainerHighest" to surfaceContainerHighest.argb(),
        "surfaceContainerLow" to surfaceContainerLow.argb(),
        "surfaceContainerLowest" to surfaceContainerLowest.argb(),
        "primaryFixed" to primaryFixed.argb(),
        "primaryFixedDim" to primaryFixedDim.argb(),
        "onPrimaryFixed" to onPrimaryFixed.argb(),
        "onPrimaryFixedVariant" to onPrimaryFixedVariant.argb(),
        "secondaryFixed" to secondaryFixed.argb(),
        "secondaryFixedDim" to secondaryFixedDim.argb(),
        "onSecondaryFixed" to onSecondaryFixed.argb(),
        "onSecondaryFixedVariant" to onSecondaryFixedVariant.argb(),
        "tertiaryFixed" to tertiaryFixed.argb(),
        "tertiaryFixedDim" to tertiaryFixedDim.argb(),
        "onTertiaryFixed" to onTertiaryFixed.argb(),
        "onTertiaryFixedVariant" to onTertiaryFixedVariant.argb(),
    )

    private fun expectedLightRoles(): Map<String, Long> = linkedMapOf(
        "primary" to AppLightPrimaryArgb, "onPrimary" to AppLightOnPrimaryArgb,
        "primaryContainer" to AppLightPrimaryContainerArgb,
        "onPrimaryContainer" to AppLightOnPrimaryContainerArgb,
        "secondary" to AppLightSecondaryArgb, "onSecondary" to AppLightOnSecondaryArgb,
        "secondaryContainer" to AppLightSecondaryContainerArgb,
        "onSecondaryContainer" to AppLightOnSecondaryContainerArgb,
        "tertiary" to AppLightTertiaryArgb, "onTertiary" to AppLightOnTertiaryArgb,
        "tertiaryContainer" to AppLightTertiaryContainerArgb,
        "onTertiaryContainer" to AppLightOnTertiaryContainerArgb,
        "error" to AppLightErrorArgb, "onError" to AppLightOnErrorArgb,
        "errorContainer" to AppLightErrorContainerArgb,
        "onErrorContainer" to AppLightOnErrorContainerArgb,
        "background" to AppLightBackgroundArgb, "onBackground" to AppLightOnBackgroundArgb,
        "surface" to AppLightSurfaceArgb, "onSurface" to AppLightOnSurfaceArgb,
        "surfaceVariant" to AppLightSurfaceVariantArgb,
        "onSurfaceVariant" to AppLightOnSurfaceVariantArgb,
        "surfaceTint" to AppLightPrimaryArgb,
        "inverseSurface" to AppLightInverseSurfaceArgb,
        "inverseOnSurface" to AppLightInverseOnSurfaceArgb,
        "inversePrimary" to AppLightInversePrimaryArgb,
        "outline" to AppLightOutlineArgb, "outlineVariant" to AppLightOutlineVariantArgb,
        "scrim" to AppScrimArgb,
        "surfaceBright" to AppLightSurfaceArgb,
        "surfaceDim" to AppLightSurfaceContainerHighestArgb,
        "surfaceContainer" to AppLightSurfaceContainerArgb,
        "surfaceContainerHigh" to AppLightSurfaceContainerHighArgb,
        "surfaceContainerHighest" to AppLightSurfaceContainerHighestArgb,
        "surfaceContainerLow" to AppLightSurfaceContainerLowArgb,
        "surfaceContainerLowest" to AppLightSurfaceContainerLowestArgb,
        "primaryFixed" to AppPrimaryFixedArgb, "primaryFixedDim" to AppPrimaryFixedDimArgb,
        "onPrimaryFixed" to AppOnPrimaryFixedArgb,
        "onPrimaryFixedVariant" to AppOnPrimaryFixedVariantArgb,
        "secondaryFixed" to AppSecondaryFixedArgb,
        "secondaryFixedDim" to AppSecondaryFixedDimArgb,
        "onSecondaryFixed" to AppOnSecondaryFixedArgb,
        "onSecondaryFixedVariant" to AppOnSecondaryFixedVariantArgb,
        "tertiaryFixed" to AppTertiaryFixedArgb,
        "tertiaryFixedDim" to AppTertiaryFixedDimArgb,
        "onTertiaryFixed" to AppOnTertiaryFixedArgb,
        "onTertiaryFixedVariant" to AppOnTertiaryFixedVariantArgb,
    )

    private fun expectedDarkRoles(): Map<String, Long> = linkedMapOf(
        "primary" to AppDarkPrimaryArgb, "onPrimary" to AppDarkOnPrimaryArgb,
        "primaryContainer" to AppDarkPrimaryContainerArgb,
        "onPrimaryContainer" to AppDarkOnPrimaryContainerArgb,
        "secondary" to AppDarkSecondaryArgb, "onSecondary" to AppDarkOnSecondaryArgb,
        "secondaryContainer" to AppDarkSecondaryContainerArgb,
        "onSecondaryContainer" to AppDarkOnSecondaryContainerArgb,
        "tertiary" to AppDarkTertiaryArgb, "onTertiary" to AppDarkOnTertiaryArgb,
        "tertiaryContainer" to AppDarkTertiaryContainerArgb,
        "onTertiaryContainer" to AppDarkOnTertiaryContainerArgb,
        "error" to AppDarkErrorArgb, "onError" to AppDarkOnErrorArgb,
        "errorContainer" to AppDarkErrorContainerArgb,
        "onErrorContainer" to AppDarkOnErrorContainerArgb,
        "background" to AppDarkBackgroundArgb, "onBackground" to AppDarkOnBackgroundArgb,
        "surface" to AppDarkSurfaceArgb, "onSurface" to AppDarkOnSurfaceArgb,
        "surfaceVariant" to AppDarkSurfaceVariantArgb,
        "onSurfaceVariant" to AppDarkOnSurfaceVariantArgb,
        "surfaceTint" to AppDarkPrimaryArgb,
        "inverseSurface" to AppDarkInverseSurfaceArgb,
        "inverseOnSurface" to AppDarkInverseOnSurfaceArgb,
        "inversePrimary" to AppDarkInversePrimaryArgb,
        "outline" to AppDarkOutlineArgb, "outlineVariant" to AppDarkOutlineVariantArgb,
        "scrim" to AppScrimArgb,
        "surfaceBright" to AppDarkSurfaceContainerHighestArgb,
        "surfaceDim" to AppDarkSurfaceContainerLowestArgb,
        "surfaceContainer" to AppDarkSurfaceContainerArgb,
        "surfaceContainerHigh" to AppDarkSurfaceContainerHighArgb,
        "surfaceContainerHighest" to AppDarkSurfaceContainerHighestArgb,
        "surfaceContainerLow" to AppDarkSurfaceContainerLowArgb,
        "surfaceContainerLowest" to AppDarkSurfaceContainerLowestArgb,
        "primaryFixed" to AppPrimaryFixedArgb, "primaryFixedDim" to AppPrimaryFixedDimArgb,
        "onPrimaryFixed" to AppOnPrimaryFixedArgb,
        "onPrimaryFixedVariant" to AppOnPrimaryFixedVariantArgb,
        "secondaryFixed" to AppSecondaryFixedArgb,
        "secondaryFixedDim" to AppSecondaryFixedDimArgb,
        "onSecondaryFixed" to AppOnSecondaryFixedArgb,
        "onSecondaryFixedVariant" to AppOnSecondaryFixedVariantArgb,
        "tertiaryFixed" to AppTertiaryFixedArgb,
        "tertiaryFixedDim" to AppTertiaryFixedDimArgb,
        "onTertiaryFixed" to AppOnTertiaryFixedArgb,
        "onTertiaryFixedVariant" to AppOnTertiaryFixedVariantArgb,
    )

    private fun androidx.compose.ui.graphics.Color.argb(): Long =
        toArgb().toUInt().toLong()

    private fun assertContrast(label: String, foreground: Long, background: Long, minimum: Double = 4.5) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast $ratio is below $minimum", ratio >= minimum)
    }

    private fun contrastRatio(foreground: Long, background: Long): Double {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun Long.luminance(): Double =
        0.2126 * linearSrgbChannel(16) +
            0.7152 * linearSrgbChannel(8) +
            0.0722 * linearSrgbChannel(0)

    private fun Long.linearSrgbChannel(shift: Int): Double {
        val channel = ((this shr shift) and 0xFF) / 255.0
        return if (channel <= 0.03928) channel / 12.92
        else ((channel + 0.055) / 1.055).pow(2.4)
    }
}
