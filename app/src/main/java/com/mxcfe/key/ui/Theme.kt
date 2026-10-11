package com.mxcfe.key.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mxcfe.key.prefs.Prefs

internal const val PageBackgroundLight = 0xFFEEF1F4
internal const val PageBackgroundDark = 0xFF101416

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4A63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E6F0),
    onPrimaryContainer = Color(0xFF062433),
    inversePrimary = Color(0xFF9FCBE4),
    secondary = Color(0xFF1F6B45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5EDE0),
    onSecondaryContainer = Color(0xFF062816),
    tertiary = Color(0xFF8A5A2A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8E3CC),
    onTertiaryContainer = Color(0xFF2C1700),
    background = Color(PageBackgroundLight),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(PageBackgroundLight),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3E8),
    onSurfaceVariant = Color(0xFF4A5158),
    surfaceTint = Color(0xFF1B4A63),
    inverseSurface = Color(0xFF2E3338),
    inverseOnSurface = Color(0xFFF2F4F6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF747B82),
    outlineVariant = Color(0xFFC9D0D6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7F9FB),
    surfaceDim = Color(0xFFD8DCE1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F6F8),
    surfaceContainer = Color(0xFFE6EAEE),
    surfaceContainerHigh = Color(0xFFE0E4E8),
    surfaceContainerHighest = Color(0xFFD8DCE1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCBE4),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF1B4A63),
    onPrimaryContainer = Color(0xFFD5E6F0),
    inversePrimary = Color(0xFF1B4A63),
    secondary = Color(0xFF8FCBAA),
    onSecondary = Color(0xFF003820),
    secondaryContainer = Color(0xFF1B4D32),
    onSecondaryContainer = Color(0xFFD5EDE0),
    tertiary = Color(0xFFF0BC8A),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3E12),
    onTertiaryContainer = Color(0xFFF8E3CC),
    background = Color(PageBackgroundDark),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(PageBackgroundDark),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF3E464C),
    onSurfaceVariant = Color(0xFFB8BEC4),
    surfaceTint = Color(0xFF9FCBE4),
    inverseSurface = Color(0xFFE6E8EA),
    inverseOnSurface = Color(0xFF2E3338),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B939A),
    outlineVariant = Color(0xFF3A444C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363C40),
    surfaceDim = Color(PageBackgroundDark),
    surfaceContainerLowest = Color(0xFF1C2328),
    surfaceContainerLow = Color(0xFF181E22),
    surfaceContainer = Color(0xFF242B30),
    surfaceContainerHigh = Color(0xFF2A3238),
    surfaceContainerHighest = Color(0xFF353D43),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

fun prefersDark(context: Context, mode: String): Boolean {
    return when (mode) {
        Prefs.THEME_DARK -> true
        Prefs.THEME_LIGHT -> false
        else -> {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }
}

fun applyWindowBackground(activity: Activity) {
    val dark = prefersDark(activity, Prefs(activity).themeMode())
    activity.window.decorView.setBackgroundColor(
        if (dark) PageBackgroundDark.toInt() else PageBackgroundLight.toInt(),
    )
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    controller.isAppearanceLightStatusBars = !dark
    controller.isAppearanceLightNavigationBars = !dark
}

@Composable
fun SideKeyTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        Prefs.THEME_DARK -> true
        Prefs.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
