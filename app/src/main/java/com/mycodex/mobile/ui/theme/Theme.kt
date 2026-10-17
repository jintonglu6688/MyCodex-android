package com.mycodex.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun color(argb: Long) = Color(argb)

internal val DarkColorScheme = darkColorScheme(
    primary = color(AppDarkPrimaryArgb),
    onPrimary = color(AppDarkOnPrimaryArgb),
    primaryContainer = color(AppDarkPrimaryContainerArgb),
    onPrimaryContainer = color(AppDarkOnPrimaryContainerArgb),
    secondary = color(AppDarkSecondaryArgb),
    onSecondary = color(AppDarkOnSecondaryArgb),
    secondaryContainer = color(AppDarkSecondaryContainerArgb),
    onSecondaryContainer = color(AppDarkOnSecondaryContainerArgb),
    tertiary = color(AppDarkTertiaryArgb),
    onTertiary = color(AppDarkOnTertiaryArgb),
    tertiaryContainer = color(AppDarkTertiaryContainerArgb),
    onTertiaryContainer = color(AppDarkOnTertiaryContainerArgb),
    background = color(AppDarkBackgroundArgb),
    onBackground = color(AppDarkOnBackgroundArgb),
    surface = color(AppDarkSurfaceArgb),
    onSurface = color(AppDarkOnSurfaceArgb),
    surfaceVariant = color(AppDarkSurfaceVariantArgb),
    onSurfaceVariant = color(AppDarkOnSurfaceVariantArgb),
    surfaceTint = color(AppDarkPrimaryArgb),
    inverseSurface = color(AppDarkInverseSurfaceArgb),
    inverseOnSurface = color(AppDarkInverseOnSurfaceArgb),
    inversePrimary = color(AppDarkInversePrimaryArgb),
    surfaceDim = color(AppDarkSurfaceContainerLowestArgb),
    surfaceBright = color(AppDarkSurfaceContainerHighestArgb),
    surfaceContainerLowest = color(AppDarkSurfaceContainerLowestArgb),
    surfaceContainerLow = color(AppDarkSurfaceContainerLowArgb),
    surfaceContainer = color(AppDarkSurfaceContainerArgb),
    surfaceContainerHigh = color(AppDarkSurfaceContainerHighArgb),
    surfaceContainerHighest = color(AppDarkSurfaceContainerHighestArgb),
    outline = color(AppDarkOutlineArgb),
    outlineVariant = color(AppDarkOutlineVariantArgb),
    error = color(AppDarkErrorArgb),
    onError = color(AppDarkOnErrorArgb),
    errorContainer = color(AppDarkErrorContainerArgb),
    onErrorContainer = color(AppDarkOnErrorContainerArgb),
    scrim = color(AppScrimArgb),
    primaryFixed = color(AppPrimaryFixedArgb),
    primaryFixedDim = color(AppPrimaryFixedDimArgb),
    onPrimaryFixed = color(AppOnPrimaryFixedArgb),
    onPrimaryFixedVariant = color(AppOnPrimaryFixedVariantArgb),
    secondaryFixed = color(AppSecondaryFixedArgb),
    secondaryFixedDim = color(AppSecondaryFixedDimArgb),
    onSecondaryFixed = color(AppOnSecondaryFixedArgb),
    onSecondaryFixedVariant = color(AppOnSecondaryFixedVariantArgb),
    tertiaryFixed = color(AppTertiaryFixedArgb),
    tertiaryFixedDim = color(AppTertiaryFixedDimArgb),
    onTertiaryFixed = color(AppOnTertiaryFixedArgb),
    onTertiaryFixedVariant = color(AppOnTertiaryFixedVariantArgb),
)

internal val LightColorScheme = lightColorScheme(
    primary = color(AppLightPrimaryArgb),
    onPrimary = color(AppLightOnPrimaryArgb),
    primaryContainer = color(AppLightPrimaryContainerArgb),
    onPrimaryContainer = color(AppLightOnPrimaryContainerArgb),
    secondary = color(AppLightSecondaryArgb),
    onSecondary = color(AppLightOnSecondaryArgb),
    secondaryContainer = color(AppLightSecondaryContainerArgb),
    onSecondaryContainer = color(AppLightOnSecondaryContainerArgb),
    tertiary = color(AppLightTertiaryArgb),
    onTertiary = color(AppLightOnTertiaryArgb),
    tertiaryContainer = color(AppLightTertiaryContainerArgb),
    onTertiaryContainer = color(AppLightOnTertiaryContainerArgb),
    background = color(AppLightBackgroundArgb),
    onBackground = color(AppLightOnBackgroundArgb),
    surface = color(AppLightSurfaceArgb),
    onSurface = color(AppLightOnSurfaceArgb),
    surfaceVariant = color(AppLightSurfaceVariantArgb),
    onSurfaceVariant = color(AppLightOnSurfaceVariantArgb),
    surfaceTint = color(AppLightPrimaryArgb),
    inverseSurface = color(AppLightInverseSurfaceArgb),
    inverseOnSurface = color(AppLightInverseOnSurfaceArgb),
    inversePrimary = color(AppLightInversePrimaryArgb),
    surfaceDim = color(AppLightSurfaceContainerHighestArgb),
    surfaceBright = color(AppLightSurfaceArgb),
    surfaceContainerLowest = color(AppLightSurfaceContainerLowestArgb),
    surfaceContainerLow = color(AppLightSurfaceContainerLowArgb),
    surfaceContainer = color(AppLightSurfaceContainerArgb),
    surfaceContainerHigh = color(AppLightSurfaceContainerHighArgb),
    surfaceContainerHighest = color(AppLightSurfaceContainerHighestArgb),
    outline = color(AppLightOutlineArgb),
    outlineVariant = color(AppLightOutlineVariantArgb),
    error = color(AppLightErrorArgb),
    onError = color(AppLightOnErrorArgb),
    errorContainer = color(AppLightErrorContainerArgb),
    onErrorContainer = color(AppLightOnErrorContainerArgb),
    scrim = color(AppScrimArgb),
    primaryFixed = color(AppPrimaryFixedArgb),
    primaryFixedDim = color(AppPrimaryFixedDimArgb),
    onPrimaryFixed = color(AppOnPrimaryFixedArgb),
    onPrimaryFixedVariant = color(AppOnPrimaryFixedVariantArgb),
    secondaryFixed = color(AppSecondaryFixedArgb),
    secondaryFixedDim = color(AppSecondaryFixedDimArgb),
    onSecondaryFixed = color(AppOnSecondaryFixedArgb),
    onSecondaryFixedVariant = color(AppOnSecondaryFixedVariantArgb),
    tertiaryFixed = color(AppTertiaryFixedArgb),
    tertiaryFixedDim = color(AppTertiaryFixedDimArgb),
    onTertiaryFixed = color(AppOnTertiaryFixedArgb),
    onTertiaryFixedVariant = color(AppOnTertiaryFixedVariantArgb),
)

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

const val MyCodexDynamicColorDefault = false

@Composable
fun MyCodexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = MyCodexDynamicColorDefault,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
