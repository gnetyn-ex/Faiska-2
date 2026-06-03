package com.example.ui.theme

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
    primary = MultimeterYellow,
    onPrimary = SlateDeepBackground,
    primaryContainer = SlateSubtleSurface,
    onPrimaryContainer = MultimeterYellow,
    secondary = TerminalTextGreen,
    onSecondary = SlateDeepBackground,
    background = SlateDeepBackground,
    onBackground = SlateOnSurface,
    surface = SlateCardSurface,
    onSurface = SlateOnSurface,
    surfaceVariant = SlateSubtleSurface,
    onSurfaceVariant = SlateOnSurfaceMuted,
    outline = SlateBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricOrange,
    onPrimary = SlateOnSurface,
    primaryContainer = ColorHelper.fromHex("#FFF0E0"),
    onPrimaryContainer = ElectricOrange,
    secondary = DarkSteel,
    onSecondary = SlateOnSurface,
    background = ColorHelper.fromHex("#F8FAFC"),
    onBackground = ColorHelper.fromHex("#0F172A"),
    surface = ColorHelper.fromHex("#FFFFFF"),
    onSurface = ColorHelper.fromHex("#1E293B"),
    surfaceVariant = ColorHelper.fromHex("#F1F5F9"),
    onSurfaceVariant = ColorHelper.fromHex("#64748B"),
    outline = ColorHelper.fromHex("#CBD5E1")
)

// Simple object inline to bypass dynamic hex imports if needed
object ColorHelper {
    fun fromHex(colorString: String): androidx.compose.ui.graphics.Color {
        return androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorString))
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We can allow true/false
    dynamicColor: Boolean = false, // Set to false to enforce our beautiful custom branding!
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // Let's default to the premium dark multimeter look, it is much more visually cohesive and immersive!
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
