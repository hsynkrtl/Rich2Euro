package de.mrt.rich2euro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BullionGoldPrimaryDark,
    onPrimary = OnBullionGoldPrimaryDark,
    primaryContainer = BullionGoldContainerDark,
    onPrimaryContainer = OnBullionGoldContainerDark,
    secondary = SilverNickelSecondaryDark,
    onSecondary = OnSilverNickelSecondaryDark,
    secondaryContainer = SilverNickelContainerDark,
    onSecondaryContainer = OnSilverNickelContainerDark,
    tertiary = MintEmeraldTertiaryDark,
    onTertiary = OnMintEmeraldTertiaryDark,
    tertiaryContainer = MintEmeraldContainerDark,
    onTertiaryContainer = OnMintEmeraldContainerDark,
    error = WarningCrimsonDark,
    onError = OnWarningCrimsonDark,
    errorContainer = WarningCrimsonContainerDark,
    onErrorContainer = OnWarningCrimsonContainerDark,
    background = VaultBackgroundDark,
    onBackground = OnVaultBackgroundDark,
    surface = VaultSurfaceDark,
    onSurface = OnVaultSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = EuroNavyPrimaryLight,
    onPrimary = OnEuroNavyPrimaryLight,
    primaryContainer = EuroNavyContainerLight,
    onPrimaryContainer = OnEuroNavyContainerLight,
    secondary = BullionGoldSecondaryLight,
    onSecondary = OnBullionGoldSecondaryLight,
    secondaryContainer = BullionGoldContainerLight,
    onSecondaryContainer = OnBullionGoldContainerLight,
    tertiary = MintEmeraldTertiaryLight,
    onTertiary = OnMintEmeraldTertiaryLight,
    tertiaryContainer = MintEmeraldContainerLight,
    onTertiaryContainer = OnMintEmeraldContainerLight,
    error = WarningCrimsonLight,
    onError = OnWarningCrimsonLight,
    errorContainer = WarningCrimsonContainerLight,
    onErrorContainer = OnWarningCrimsonContainerLight,
    background = ParchmentBackgroundLight,
    onBackground = OnParchmentBackgroundLight,
    surface = AlabasterSurfaceLight,
    onSurface = OnAlabasterSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

@Composable
fun Rich2EuroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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
        typography = Rich2EuroTypography,
        shapes = Rich2EuroShapes,
        content = content
    )
}
