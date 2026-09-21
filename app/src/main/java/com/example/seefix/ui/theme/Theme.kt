package com.example.seefix.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkFieldColorScheme = darkColorScheme(
    primary = FieldYellowPrimary,
    onPrimary = FieldYellowOnPrimary,
    primaryContainer = FieldYellowContainer,
    onPrimaryContainer = FieldYellowOnContainer,
    secondary = FieldCyanSecondary,
    onSecondary = FieldCyanOnSecondary,
    secondaryContainer = FieldCyanContainer,
    onSecondaryContainer = FieldCyanOnContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightFieldColorScheme = lightColorScheme(
    primary = FieldYellowPrimary,
    onPrimary = FieldYellowOnPrimary,
    primaryContainer = FieldYellowOnContainer,
    onPrimaryContainer = FieldYellowContainer,
    secondary = FieldCyanSecondary,
    onSecondary = FieldCyanOnSecondary,
    secondaryContainer = FieldCyanOnContainer,
    onSecondaryContainer = FieldCyanContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

@Composable
fun SeeFixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkFieldColorScheme
        else -> LightFieldColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
