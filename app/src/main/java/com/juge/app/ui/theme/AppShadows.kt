package com.juge.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 的投影令牌。
 *
 * 卡片「浮」在页面上靠的是低透明度、大半径的弥散阴影，不是 Material elevation
 * （浅色底上 elevation 会沿光锥糊出一圈灰雾，此前在抽屉上踩过这个坑）。
 * 统一从 [spotShadow]/[ambientShadow] 取色（中性黑低透明度变体），
 * 圆角外围必须 `clip = false`，否则阴影被自己的容器裁掉。
 *
 * API 28+ 才支持给阴影染色；更低版本系统自动回退为默认黑影，透明度偏移已把
 * 默认黑影的浓度计算在内，无需分支。
 *
 * 阴影基色改用**中性黑**（原先带蓝调的 slate ink）：暗色模式改成纯黑背景后，
 * 任何蓝调影子都会在近黑底上偏冷/偏紫；中性黑在日夜两边都是最不容易出错的影子色。
 * 深色下卡片分层主要靠「卡片比底亮」这一层。
 */
object AppShadows {

    /** 阴影基色：中性黑的低透明度变体，比纯黑柔和 */
    val ambientShadow = Color(0x14000000) // 约 8%
    val spotShadow = Color(0x26000000) // 约 15%

    /** 区块卡片：贴在页面上但微微浮起 */
    val card: Dp = 8.dp

    /** 弹窗/浮层：离页面更高，影子更宽更淡 */
    val dialog: Dp = 16.dp

    /** 小控件（页签滑块、胶囊按钮）：几乎贴面，只留一条薄影 */
    val control: Dp = 3.dp
}
