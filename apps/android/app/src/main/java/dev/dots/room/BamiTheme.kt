package dev.dots.room

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Paperlogy=FontFamily(Font(R.font.paperlogy_regular,FontWeight.Normal),Font(R.font.paperlogy_semibold,FontWeight.SemiBold))
private val base=Typography()
val BamiTypography=Typography(
 displayLarge=base.displayLarge.copy(fontFamily=Paperlogy),displayMedium=base.displayMedium.copy(fontFamily=Paperlogy),displaySmall=base.displaySmall.copy(fontFamily=Paperlogy),
 headlineLarge=base.headlineLarge.copy(fontFamily=Paperlogy),headlineMedium=base.headlineMedium.copy(fontFamily=Paperlogy),headlineSmall=base.headlineSmall.copy(fontFamily=Paperlogy),
 titleLarge=base.titleLarge.copy(fontFamily=Paperlogy),titleMedium=base.titleMedium.copy(fontFamily=Paperlogy),titleSmall=base.titleSmall.copy(fontFamily=Paperlogy),
 bodyLarge=base.bodyLarge.copy(fontFamily=Paperlogy),bodyMedium=base.bodyMedium.copy(fontFamily=Paperlogy),bodySmall=base.bodySmall.copy(fontFamily=Paperlogy),
 labelLarge=base.labelLarge.copy(fontFamily=Paperlogy),labelMedium=base.labelMedium.copy(fontFamily=Paperlogy),labelSmall=base.labelSmall.copy(fontFamily=Paperlogy)
)
