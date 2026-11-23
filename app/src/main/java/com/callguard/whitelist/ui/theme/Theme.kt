package com.callguard.whitelist.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val SafeGreen = Color(0xFF1F9D6B)
val WarnAmber = Color(0xFFE0A030)
val DangerRed = Color(0xFFD64545)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6B59),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4EEDC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635C),
    secondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFF41627A),
    background = Color(0xFFF6FAF8),
    surface = Color(0xFFF6FAF8),
    surfaceVariant = Color(0xFFDBE5E0),
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D7BF),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFA2F2DA),
    secondary = Color(0xFFB2CCC3),
    secondaryContainer = Color(0xFF344B45),
    tertiary = Color(0xFFA9CBE5),
    background = Color(0xFF101614),
    surface = Color(0xFF101614),
    surfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB),
)

@Composable
fun CallGuardTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
