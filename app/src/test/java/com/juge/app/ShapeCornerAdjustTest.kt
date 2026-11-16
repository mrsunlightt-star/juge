package com.juge.app

import com.juge.app.data.WidgetShape
import com.juge.app.render.traits
import com.juge.app.ui.adjust.canAdjustCardCorner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「外框圆角大小」步进器与渲染层强制直角的名单必须对得上。
 *
 * 这两份名单各自维护（一份在 `ShapeTraits.SQUARE_CORNER_SHAPES`，一份在
 * `ui/adjust/ShapeBackgroundCard.kt` 的 [canAdjustCardCorner]），历史上漂移过三次：
 * 整幅插画类形状曾多次漏登记，表现是「按钮看着能动、画面没反应」——
 * 用户只会以为功能坏了，而测试全绿。
 *
 * 判据是单向的：**强制直角的形状必须禁用圆角步进器**。
 * 反向不成立——椭圆/撕纸/书架禁用圆角步进器是因为它们本就没有可调的外框，与强制直角无关。
 */
class ShapeCornerAdjustTest {

    @Test
    fun `渲染层强制直角的形状不得开放圆角步进器`() {
        val forced = WidgetShape.entries.filter { it.traits().forcesSquareCorners }
        assertTrue("强制直角的名单不应为空，否则这条断言失去意义", forced.isNotEmpty())
        for (shape in forced) {
            assertFalse(
                "$shape 在渲染层强制直角（SQUARE_CORNER_SHAPES），圆角步进器却仍可操作",
                canAdjustCardCorner(shape),
            )
        }
    }

    @Test
    fun `没有外框可调的形状同样禁用圆角步进器`() {
        // 椭圆/撕纸/书架的轮廓另有来源，与强制直角是两回事，这里分别钉住
        for (shape in listOf(WidgetShape.ELLIPSE, WidgetShape.TORN_PAPER, WidgetShape.BOOKSHELF)) {
            assertFalse("$shape 没有可调的外框，步进器应禁用", canAdjustCardCorner(shape))
        }
    }

    @Test
    fun `普通圆角矩形仍可调圆角`() {
        // 反向哨兵：整份名单写成「全都禁用」也能让上面两条通过，这里确保步进器没有被整体关掉
        for (shape in listOf(
            WidgetShape.RECTANGLE,
            WidgetShape.HANDBOOK_TAPE,
            WidgetShape.BLUE_NOTE,
            WidgetShape.SPLIT_CARD,
            WidgetShape.YOUTH_SCULPTURE,
        )) {
            assertTrue("$shape 的圆角由用户设置决定，步进器应可用", canAdjustCardCorner(shape))
        }
    }
}
