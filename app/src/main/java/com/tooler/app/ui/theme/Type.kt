package com.tooler.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tooler.app.R

val MartianMono = FontFamily(
    Font(R.font.martian_mono_regular, FontWeight.Normal),
    Font(R.font.martian_mono_medium, FontWeight.Medium)
)

private val defaultTypography = Typography()

val ToolerTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = MartianMono),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = MartianMono),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = MartianMono),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = MartianMono),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = MartianMono),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = MartianMono),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = MartianMono),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = MartianMono),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = MartianMono),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = MartianMono, fontWeight = FontWeight.Medium)
)
