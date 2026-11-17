package com.juge.app.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.juge.app.ui.theme.AppMotion.ThemeMs

/** 当前是否夜间模式：给需要「模式」本身（而非某个颜色）的场景用，如英雄区压暗、状态栏图标 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

/**
 * 日夜切换的实际动画进度：0 = 完全日间，1 = 完全夜间。
 *
 * [LocalAppDarkTheme] 是目标模式，切换瞬间就会翻转；颜色不能直接依赖它，否则会在
 * 色板动画之外突然跳到终态。需要按模式插值颜色或透明度的场景应使用本进度。
 */
val LocalAppDarkThemeProgress = staticCompositionLocalOf { 0f }

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
    val target = if (darkTheme) AppPalette.Dark else AppPalette.Light
    val darkThemeProgress by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(ThemeMs, easing = AppMotion.easing),
        label = "darkThemeProgress",
    )
    // 日/夜切换的过渡：调色板颜色逐项插值（tween 420ms），单棵界面树不动。
    // 不用 Crossfade 包内容——那会让新主题的树枝重新 remember，主界面选中项/页签/弹窗状态全丢。
    val palette = animatedPalette(target)
    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalAppDarkTheme provides darkTheme,
        LocalAppDarkThemeProgress provides darkThemeProgress,
        // 夜色字重：整体 -40%（FontWeight × 0.6）。日间为 1f，等价于不缩放。
        LocalAppFontWeightScale provides if (darkTheme) AppType.DarkWeightScale else 1f,
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
            typography = appTypography(),
            content = content
        )
    }
}

/**
 * 把目标调色板的每个颜色包成 420ms 的动画值：日夜切换时全 app 颜色平滑过渡。
 * 37 个 animateColorAsState 只在值变化时运行；静止时它们只是读缓存，无开销。
 */
@Composable
private fun animatedPalette(target: AppPalette): AppPalette {
    @Composable
    fun anim(c: Color, label: String) =
        animateColorAsState(c, tween(ThemeMs, easing = AppMotion.easing), label = label).value

    return AppPalette(
        darkBg = anim(target.darkBg, "darkBg"),
        panelTrack = anim(target.panelTrack, "panelTrack"),
        cardBg = anim(target.cardBg, "cardBg"),
        surfaceRaised = anim(target.surfaceRaised, "surfaceRaised"),
        accentBlue = anim(target.accentBlue, "accentBlue"),
        accentFillStrong = anim(target.accentFillStrong, "accentFillStrong"),
        borderBlue = anim(target.borderBlue, "borderBlue"),
        sheetHandle = anim(target.sheetHandle, "sheetHandle"),
        textWhite = anim(target.textWhite, "textWhite"),
        textGray = anim(target.textGray, "textGray"),
        mintInk = anim(target.mintInk, "mintInk"),
        selectBlue = anim(target.selectBlue, "selectBlue"),
        textTertiary = anim(target.textTertiary, "textTertiary"),
        surfaceSubtle = anim(target.surfaceSubtle, "surfaceSubtle"),
        surfaceMuted = anim(target.surfaceMuted, "surfaceMuted"),
        fieldBg = anim(target.fieldBg, "fieldBg"),
        fieldBgFocused = anim(target.fieldBgFocused, "fieldBgFocused"),
        dangerRed = anim(target.dangerRed, "dangerRed"),
        disabledGray = anim(target.disabledGray, "disabledGray"),
        warningInk = anim(target.warningInk, "warningInk"),
        warningBg = anim(target.warningBg, "warningBg"),
        warningBorder = anim(target.warningBorder, "warningBorder"),
        premiumGold = anim(target.premiumGold, "premiumGold"),
        premiumGoldDeep = anim(target.premiumGoldDeep, "premiumGoldDeep"),
        premiumGoldInk = anim(target.premiumGoldInk, "premiumGoldInk"),
        premiumGoldEnd = anim(target.premiumGoldEnd, "premiumGoldEnd"),
        successGreen = anim(target.successGreen, "successGreen"),
        brandInk = anim(target.brandInk, "brandInk"),
        brandInkSoft = anim(target.brandInkSoft, "brandInkSoft"),
        trackGray = anim(target.trackGray, "trackGray"),
        chipTextInk = anim(target.chipTextInk, "chipTextInk"),
        auroraMint = anim(target.auroraMint, "auroraMint"),
        auroraSky = anim(target.auroraSky, "auroraSky"),
        auroraMist = anim(target.auroraMist, "auroraMist"),
        overlayCard = anim(target.overlayCard, "overlayCard"),
        drawerGlassBg = anim(target.drawerGlassBg, "drawerGlassBg"),
        drawerGlassTint = anim(target.drawerGlassTint, "drawerGlassTint"),
    )
}
