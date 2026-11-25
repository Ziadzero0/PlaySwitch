package com.playswitch.ziadzero.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    AUTO,
    DARK,
    LIGHT
}

@Immutable
data class StatusPalette(
    val successBg: Color,
    val successFg: Color,
    val successDot: Color,
    val warningBg: Color,
    val warningFg: Color,
    val dangerBg: Color,
    val dangerFg: Color,
    val dangerDot: Color,
    val brandBg: Color,
    val brandFg: Color,
    val isDark: Boolean
)

@Immutable
data class AccountTone(
    val chipBg: Color,
    val chipFg: Color,
    val dot: Color
)

private val LightAccountTones = listOf(
    AccountTone(Color(0xFFDBEAFE), Color(0xFF1E40AF), Color(0xFF2563EB)), // Blue
    AccountTone(Color(0xFFCCFBF1), Color(0xFF115E59), Color(0xFF0D9488)), // Teal
    AccountTone(Color(0xFFEDE9FE), Color(0xFF5B21B6), Color(0xFF7C3AED)), // Violet
    AccountTone(Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFFD97706)), // Amber
    AccountTone(Color(0xFFFCE7F3), Color(0xFF9D174D), Color(0xFFDB2777)), // Pink
    AccountTone(Color(0xFFE0E7FF), Color(0xFF3730A3), Color(0xFF4F46E5))  // Indigo
)

private val DarkAccountTones = listOf(
    AccountTone(Color(0xFF172D4E), Color(0xFF93C5FD), Color(0xFF60A5FA)), // Blue
    AccountTone(Color(0xFF123B39), Color(0xFF5EEAD4), Color(0xFF2DD4BF)), // Teal
    AccountTone(Color(0xFF2E1E59), Color(0xFFC4B5FD), Color(0xFFA78BFA)), // Violet
    AccountTone(Color(0xFF3F290C), Color(0xFFFCD34D), Color(0xFFFBBF24)), // Amber
    AccountTone(Color(0xFF491831), Color(0xFFF9A8D4), Color(0xFFF472B6)), // Pink
    AccountTone(Color(0xFF212656), Color(0xFFA5B4FC), Color(0xFF818CF8))  // Indigo
)

fun accountToneFor(email: String, orderedEmails: List<String> = emptyList(), isDark: Boolean): AccountTone {
    val palette = if (isDark) DarkAccountTones else LightAccountTones
    val knownIndex = orderedEmails.indexOfFirst { it.equals(email, ignoreCase = true) }
    val slot = if (knownIndex >= 0) {
        knownIndex % palette.size
    } else {
        (email.lowercase().hashCode() and 0x7FFFFFFF) % palette.size
    }
    return palette[slot]
}

private val LightStatusPalette = StatusPalette(
    successBg = Color(0xFFDCFCE7),
    successFg = Color(0xFF166534),
    successDot = Color(0xFF16A34A),
    warningBg = Color(0xFFFEF3C7),
    warningFg = Color(0xFF92400E),
    dangerBg = Color(0xFFFEE2E2),
    dangerFg = Color(0xFF991B1B),
    dangerDot = Color(0xFFDC2626),
    brandBg = Color(0xFFDBEAFE),
    brandFg = Color(0xFF1E40AF),
    isDark = false
)

private val DarkStatusPalette = StatusPalette(
    successBg = Color(0xFF113826),
    successFg = Color(0xFF6EE7B7),
    successDot = Color(0xFF34D399),
    warningBg = Color(0xFF3B2809),
    warningFg = Color(0xFFFCD34D),
    dangerBg = Color(0xFF421518),
    dangerFg = Color(0xFFFCA5A5),
    dangerDot = Color(0xFFF87171),
    brandBg = Color(0xFF172D4E),
    brandFg = Color(0xFF93C5FD),
    isDark = true
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

private val BrandLightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF082E6F),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFE2E8F0),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8)
)

private val BrandDarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF173A6E),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0E1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF1C222B),
    surfaceContainerHigh = Color(0xFF212832),
    surfaceVariant = Color(0xFF212832),
    onSurfaceVariant = Color(0xFF9DA7B3),
    outline = Color(0xFF3D4754)
)

@Composable
fun PlaySwitchTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) BrandDarkColors else BrandLightColors
    }

    val statusPalette = if (isDark) DarkStatusPalette else LightStatusPalette

    CompositionLocalProvider(LocalStatusPalette provides statusPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
