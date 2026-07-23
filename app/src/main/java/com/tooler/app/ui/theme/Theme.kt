package com.tooler.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Nord is fundamentally a dark, arctic-bluish palette; dynamic color is deliberately disabled so
// the app keeps a fixed Nord identity regardless of the device wallpaper (matches ownscreen/noter/
// linker). Every role below is filled in explicitly, including the newer M3 "surface container"
// tiers — leaving any of those out doesn't leave a gap, darkColorScheme()/lightColorScheme() quietly
// substitute Material's own baseline (purple-tinted) defaults for whichever ones aren't passed.

private val NordDarkColorScheme = darkColorScheme(
    primary = nord8,
    onPrimary = nord0,
    primaryContainer = nord10,
    onPrimaryContainer = nord6,
    secondary = nord7,
    onSecondary = nord0,
    secondaryContainer = nord2,
    onSecondaryContainer = nord6,
    tertiary = nord9,
    onTertiary = nord0,
    tertiaryContainer = nord2,
    onTertiaryContainer = nord6,
    background = nord0,
    onBackground = nord6,
    surface = nord1,
    onSurface = nord4,
    surfaceVariant = nord2,
    onSurfaceVariant = nord4,
    surfaceTint = nord8,
    surfaceDim = nord0,
    surfaceBright = nord2,
    surfaceContainerLowest = nord0,
    surfaceContainerLow = nord0,
    surfaceContainer = nord1,
    surfaceContainerHigh = nord1,
    surfaceContainerHighest = nord2,
    error = nord11,
    onError = nord6,
    errorContainer = nord11,
    onErrorContainer = nord6,
    outline = nord3,
    outlineVariant = nord2,
    inverseSurface = nord6,
    inverseOnSurface = nord0,
    inversePrimary = nord10
)

private val NordLightColorScheme = lightColorScheme(
    primary = nord10,
    onPrimary = nord6,
    primaryContainer = nord8,
    onPrimaryContainer = nord0,
    secondary = nord7,
    onSecondary = nord0,
    secondaryContainer = nord5,
    onSecondaryContainer = nord0,
    tertiary = nord9,
    onTertiary = nord6,
    tertiaryContainer = nord5,
    onTertiaryContainer = nord0,
    background = nord6,
    onBackground = nord0,
    surface = nord5,
    onSurface = nord1,
    surfaceVariant = nord4,
    onSurfaceVariant = nord1,
    surfaceTint = nord10,
    surfaceDim = nord4,
    surfaceBright = nord6,
    surfaceContainerLowest = nord6,
    surfaceContainerLow = nord5,
    surfaceContainer = nord4,
    surfaceContainerHigh = nord4,
    surfaceContainerHighest = nord4,
    error = nord11,
    onError = nord6,
    errorContainer = nord11,
    onErrorContainer = nord6,
    outline = nord3,
    outlineVariant = nord4,
    inverseSurface = nord0,
    inverseOnSurface = nord6,
    inversePrimary = nord8
)

@Composable
fun ToolerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NordDarkColorScheme else NordLightColorScheme,
        typography = ToolerTypography,
        content = content
    )
}
