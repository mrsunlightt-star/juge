package com.juge.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.juge.app.ui.AppMenuPositionProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ⋮ 溢出菜单的落点计算（[AppMenuPositionProvider]）。
 *
 * 纯几何：喂 px 坐标断言弹窗窗口偏移与缩放原点，不需要真机/Robolectric。
 * density 固定 2：8dp 安全边距 = 16px、4dp 间距 = 8px、20dp 阴影留白 = 40px。
 *
 * 卡片尺寸以 `popupContentSize - 2 × 留白` 的形式喂入（与运行时一致：Popup 按内容测量，
 * 内容 = 卡片 + 四周留白），所以这里不重复推算「行高 × 项数」，避免把实现里的算术抄一遍。
 */
class AppMenuPlacementTest {

    private val shadowRoomPx = 40

    /** 卡片 300×200（px）→ Popup 内容尺寸 = 卡片 + 2 × 40 */
    private fun contentOf(cardWidth: Int, cardHeight: Int) =
        IntSize(cardWidth + shadowRoomPx * 2, cardHeight + shadowRoomPx * 2)

    private val content = contentOf(cardWidth = 300, cardHeight = 200)

    private fun place(
        anchor: IntRect,
        window: IntSize,
        popupContentSize: IntSize = content,
        originState: MutableState<TransformOrigin> = mutableStateOf(TransformOrigin.Center),
    ) = AppMenuPositionProvider(
        density = Density(2f),
        shadowRoomPx = shadowRoomPx,
        originState = originState,
    ).calculatePosition(
        anchorBounds = anchor,
        windowSize = window,
        layoutDirection = LayoutDirection.Ltr,
        popupContentSize = popupContentSize,
    )

    @Test
    fun `默认挂在按钮下方且右边缘对齐`() {
        val origin = mutableStateOf(TransformOrigin.Center)
        val offset = place(
            anchor = IntRect(left = 400, top = 100, right = 900, bottom = 180),
            window = IntSize(width = 1080, height = 2000),
            originState = origin,
        )
        // x = 按钮右边缘 900 - 卡片宽 300 = 600（窗口内，不钳制）
        // y = 按钮下沿 180 + 4dp 间距 8 = 188；返回值再外扩阴影留白 40
        assertEquals(600 - shadowRoomPx, offset.x)
        assertEquals(188 - shadowRoomPx, offset.y)
        assertEquals("向下弹出时原点在卡片右上角", TransformOrigin(1f, 0f), origin.value)
    }

    @Test
    fun `右边距不足时卡片向左钳制在窗口内`() {
        val offset = place(
            anchor = IntRect(left = 980, top = 100, right = 1080, bottom = 180),
            window = IntSize(width = 1080, height = 2000),
        )
        // 1080 - 300 = 780 > 1080 - 16 - 300 = 764 → 钳到 764
        assertEquals(764 - shadowRoomPx, offset.x)
    }

    @Test
    fun `下方放不下时翻到按钮上方`() {
        val origin = mutableStateOf(TransformOrigin.Center)
        val offset = place(
            anchor = IntRect(left = 400, top = 250, right = 900, bottom = 330),
            window = IntSize(width = 1080, height = 400),
            originState = origin,
        )
        // 下方可用 400 - 16 - 338 = 46 < 200，上方可用 250 - 8 - 16 = 226 ≥ 200 → 翻到上方
        // y = 250 - 8 - 200 = 42
        assertEquals(42 - shadowRoomPx, offset.y)
        assertEquals("向上弹出时原点在卡片右下角", TransformOrigin(1f, 1f), origin.value)
    }

    @Test
    fun `上下都放不下时贴窗口下沿且原点取靠近按钮的一侧`() {
        val origin = mutableStateOf(TransformOrigin.Center)
        val offset = place(
            anchor = IntRect(left = 400, top = 120, right = 900, bottom = 200),
            window = IntSize(width = 1080, height = 300),
            originState = origin,
        )
        // 上下都塞不下：y = 300 - 16 - 200 = 84（< 按钮上沿 120 → 原点取右下角）
        assertEquals(84 - shadowRoomPx, offset.y)
        assertEquals(TransformOrigin(1f, 1f), origin.value)
    }

    @Test
    fun `窗口更矮时兜底贴下沿且原点改为右上角`() {
        val origin = mutableStateOf(TransformOrigin.Center)
        val offset = place(
            // 按钮就在窗口顶部：兜底位置 y 落在按钮上沿之下 → 卡片从右上角长出来
            anchor = IntRect(left = 400, top = 20, right = 900, bottom = 100),
            window = IntSize(width = 1080, height = 300),
            originState = origin,
        )
        // y = 300 - 16 - 200 = 84 ≥ 按钮上沿 20 → pivotFractionY = 0（卡片在按钮下方）
        assertEquals(84 - shadowRoomPx, offset.y)
        assertEquals(TransformOrigin(1f, 0f), origin.value)
    }

    @Test
    fun `卡片高度取实测内容_行间距也算进去`() {
        // 3 项菜单 = 44×3 行 + 上下 8dp + 2×2dp 行距 = 144dp = 288px；
        // 若有人把行距漏掉（280px）或在定位里另算一遍「行高 × 项数」，这里会露馅
        val threeItems = contentOf(cardWidth = 300, cardHeight = 288)
        val origin = mutableStateOf(TransformOrigin.Center)
        val offset = place(
            anchor = IntRect(left = 400, top = 100, right = 900, bottom = 180),
            window = IntSize(width = 1080, height = 420),
            popupContentSize = threeItems,
            originState = origin,
        )
        // 下方可用 420 - 16 - 188 = 216 < 288、上方 -196 → 兜底贴下沿：420 - 16 - 288 = 116
        assertEquals(116 - shadowRoomPx, offset.y)
        assertEquals(TransformOrigin(1f, 0f), origin.value)
    }
}
