package com.juge.app.ui

import com.juge.app.data.WidgetShape

/**
 * 预览区高度映射。
 *
 * 从 MainActivity 里那个 800+ 行的 MainAppScreen 中抽出：这是纯粹的
 * 「形状 + 网格行数 → 显示高度」计算，不依赖 Android，可以直接单测。
 * 抽出来的另一个好处是让「4×2 不该按 4×4 的高度留白」这条规则有回归保护——
 * 之前正是这类尺寸映射出过预览被拉成近方形的问题。
 */
object PreviewMetrics {

    /** 预览显示区域的最小高度 */
    const val MIN_PREVIEW_HEIGHT_DP = 180

    /** 显示盒基础高度与上限 */
    const val PREVIEW_BOX_BASE_DP = 244
    const val PREVIEW_BOX_MAX_DP = 268

    /** 横向分割卡是固定高度，不随行数变化 */
    private const val SPLIT_CARD_HORIZONTAL_HEIGHT_DP = 130
    private const val BASE_HEIGHT_DP = 60
    private const val ROW_HEIGHT_DP = 50

    /** App 内预览（没有桌面组件可问）时的兜底行数：按声明的默认落位 4×2 */
    const val DEFAULT_SPAN_Y = 2

    /**
     * 组件声明高度（dp）→ 网格行数：与桌面按 dp 推行数的换算一致。
     * 4×4 入口声明 250dp → 4 行；4×2 入口声明 110dp → 2 行。
     */
    fun spanYForHeightDp(heightDp: Int): Int = ((heightDp + 30) / 70).coerceIn(2, 4)

    /**
     * 预览高度：行数来自组件**声明的默认尺寸**（见 spanYForHeightDp），
     * 不读库里那份建配置时写死的 sizeType，也不读桌面实时尺寸——两者都会让
     * 同一个组件的预览在「方形」与「圆角矩形」之间来回跳。
     *
     * @param spanY 组件声明尺寸对应的网格行数（见 [spanYForHeightDp]）；
     *   null 按 [DEFAULT_SPAN_Y] 兜底，纯函数的兜底分支，调用方现在总能拿到声明尺寸
     */
    fun previewHeightDp(shape: WidgetShape, spanY: Int?): Int {
        val rows = (spanY ?: DEFAULT_SPAN_Y).coerceIn(2, 4)
        val adaptive = when (shape) {
            WidgetShape.SPLIT_CARD_HORIZONTAL -> SPLIT_CARD_HORIZONTAL_HEIGHT_DP
            else -> BASE_HEIGHT_DP + rows * ROW_HEIGHT_DP
        }
        return adaptive.coerceAtLeast(MIN_PREVIEW_HEIGHT_DP)
    }

    /** 显示盒高度：夹在基础高度与上限之间，保证更高的规格（如 4×4）不被裁切 */
    fun previewBoxHeightDp(previewHeightDp: Int): Int =
        PREVIEW_BOX_BASE_DP
            .coerceAtLeast(previewHeightDp + 8)
            .coerceAtMost(PREVIEW_BOX_MAX_DP)
}
