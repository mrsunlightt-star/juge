package com.juge.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 当前是否夜间模式：给需要「模式」本身（而非某个颜色）的场景用，如英雄区压暗、状态栏图标 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

// Material colorScheme 从调色板接线，保证弹窗等吃主题色的组件与自绘令牌同源（日夜都是）。
private fun lightMaterialScheme(p: AppPalette) = lightColorScheme(
    primary = p.accentBlue,
    secondary = p.accentFillStrong,
    tertiary = p.successGreen,
    background = p.surfaceMuted,
    surface = p.cardBg,
    surfaceVariant = p.surfaceSubtle,
    onPrimary = Color.White,
    onBackground = p.textPrimary,
    onSurface = p.textPrimary,
    onSurfaceVariant = p.textSecondary,
    outline = p.borderBlue,
    outlineVariant = p.borderBlue,
    error = p.dangerRed,
    onError = Color.White
)

private fun darkMaterialScheme(p: AppPalette) = darkColorScheme(
    primary = p.accentBlue,
    secondary = p.accentFillStrong,
    tertiary = p.successGreen,
    background = p.darkBg,
    surface = p.cardBg,
    surfaceVariant = p.surfaceSubtle,
    onPrimary = p.darkBg, // 夜间强调蓝是亮色，配深墨字
    onBackground = p.textPrimary,
    onSurface = p.textPrimary,
    onSurfaceVariant = p.textSecondary,
    outline = p.borderBlue,
    outlineVariant = p.borderBlue,
    error = p.dangerRed,
    onError = p.darkBg
)

@Composable
fun MyApplicationTheme(
    // 夜色模式入口：MainActivity 按用户手动开关传入（不跟随系统，见 .scratch 讨论记录）。
    // 调色板与 Material colorScheme 同步切换——此前只锁浅色是因为自绘令牌不随主题，
    // 现在整套令牌都走 LocalAppPalette，深色弹窗+浅色文字的冲突根源已消除。
    darkTheme: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) AppPalette.Dark else AppPalette.Light
    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalAppDarkTheme provides darkTheme,
    ) {
        val colorScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> darkMaterialScheme(palette)
            else -> lightMaterialScheme(palette)
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
