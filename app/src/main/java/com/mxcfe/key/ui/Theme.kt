package com.mxcfe.key.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.mxcfe.key.prefs.Prefs

@Composable
fun SideKeyTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        Prefs.THEME_DARK -> true
        Prefs.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
