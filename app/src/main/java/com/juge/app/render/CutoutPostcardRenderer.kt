package com.juge.app.render

import android.graphics.RectF

/**
 * 底部面板型明信片（青年雕塑）：上半是素材图，下半是**代码绘制**的正文面板。
 *
 * 面板不烘进素材，「小组件背景颜色」可以直接改下半部分；画面板和正文排版共用这一份几何，
 * 避免两处比例漂移。
 *
 * 青年雕塑的上半是**不透明**雕塑图（没有 alpha），整卡投影照常绘制。
 */
internal object CutoutPostcardRenderer {

    /** 面板上沿：素材上方 130/250 = 52% 是图片，面板从这里开始 */
    const val PANEL_TOP_RATIO = 0.52f

    /** 正文上沿：面板顶部再留 4%，文字不贴图片下沿 */
    const val TEXT_TOP_RATIO = 0.56f

    /** 正文可用高度：到卡片 90% 为止 */
    const val TEXT_HEIGHT_RATIO = 0.34f

    /** 正文左右留白 */
    const val TEXT_SIDE_RATIO = 0.08f

    /** 下半正文面板：从左到右铺满，上沿在 52%，下沿到卡片底 */
    fun panelRect(outerRect: RectF): RectF = RectF(
        outerRect.left,
        outerRect.top + outerRect.height() * PANEL_TOP_RATIO,
        outerRect.right,
        outerRect.bottom
    )
}
