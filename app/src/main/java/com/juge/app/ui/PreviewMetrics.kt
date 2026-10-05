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
     * 预览高度：行数一律以**系统上报的实时尺寸**为准，不读库里那份建配置时写死的 sizeType。
     *
     * @param spanY 桌面组件当前占用的网格行数；null 表示问不到（App 内预览的常态）
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
