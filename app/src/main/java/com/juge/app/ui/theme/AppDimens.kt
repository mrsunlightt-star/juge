package com.juge.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 全 App 的形状与间距令牌。
 *
 * 历史界面里圆角有 8/9/10/12/14/16/20/25 多种、间距有 4~24 各种随手值；
 * 统一收口后：卡片 16、按钮 12、胶囊/缩略图 10、弹窗 20、底部抽屉 32。
 */
object AppShape {
    val card = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(10.dp)
    val thumbnail = RoundedCornerShape(10.dp)
    val badge = RoundedCornerShape(6.dp)
    val dialog = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val pill = RoundedCornerShape(percent = 50)
    val check = RoundedCornerShape(5.dp)
    val handle = RoundedCornerShape(2.dp)
}

object AppSpacing {
    /** 页面左右安全边距 */
    val screen = 16.dp

    /** 卡片内边距 */
    val cardPadding = 16.dp

    /** 页面内区块之间的间距 */
    val sectionGap = 16.dp

    /** 区块内控件之间的间距 */
    val itemGap = 10.dp

    /** 区块内需要呼吸感的行间距 */
    val controlGap = 12.dp

    /** 紧密关联元素（图标与文字、小标签） */
    val tight = 4.dp

    /** 图标与文字之间的标准间距 */
    val iconGap = 6.dp
}
