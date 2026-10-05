package com.juge.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.Random
import timber.log.Timber
import com.juge.app.WidgetCanvasRenderer.render

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object BookshelfRenderer {

    // 书香书架纵向几何：书籍区（书脊 + 横板）高度固定为 dp，不随组件变高而拉伸，
    // 组件多出来的高度全部留给下方摘录面板，因此尺寸变大时只有文本区域变大。
    // 组件高度不足时优先压缩书籍区，保证面板至少能显示正文。
    const val SHELF_BOOK_BAND_HEIGHT_DP = 90f // 组件顶部到横板上沿

    const val SHELF_BOARD_HEIGHT_DP = 4f // 横板厚度

    const val SHELF_BOOK_TOP_MARGIN_DP = 6f // 书脊顶部与组件顶部的留白

    const val SHELF_PANEL_GAP_DP = 6f // 横板下沿到面板上沿

    const val SHELF_PANEL_BOTTOM_MARGIN_DP = 6f // 面板下沿到组件底部

    const val SHELF_PANEL_MIN_HEIGHT_DP = 54f // 面板最小高度

    const val SHELF_PANEL_MIN_BAND_RATIO = 0.35f // 组件过矮时书籍区的最小占比

    const val SHELF_PANEL_TEXT_PAD_X_DP = 14f // 面板内正文左右留白

    const val SHELF_PANEL_TEXT_PAD_Y_DP = 6f // 面板内正文上下留白

    // 横向仍按比例：书架横向铺满组件宽度
    const val SHELF_BOARD_LEFT_RATIO = 0.035f

    const val SHELF_BOARD_RIGHT_RATIO = 0.965f

    const val SHELF_BOOKS_LEFT_RATIO = 0.07f

    const val SHELF_BOOKS_RIGHT_RATIO = 0.93f

    const val SHELF_PANEL_LEFT_RATIO = 0.055f

    const val SHELF_PANEL_RIGHT_RATIO = 0.945f

    // 书脊竖排书名的行距倍数：略大于字号即可，保持字符紧凑而不铺满整条书脊
    const val BOOK_TITLE_LINE_STEP_RATIO = 1.06f

    // 书架上的一本书：书名、书脊配色、相对最高书脊的高度比例
    data class ShelfBook(
        val title: String,
        val color: Int,
        val heightFactor: Float,
        val highlighted: Boolean = false
    )

    val SHELF_BOOKS: List<ShelfBook> = listOf(
        ShelfBook("活着", 0xFF3F3F3F.toInt(), 0.86f),
        ShelfBook("围城", 0xFF2B4A6B.toInt(), 0.77f),
        ShelfBook("百年孤独", 0xFF2F5D3A.toInt(), 0.92f),
        ShelfBook("小王子", 0xFFF0A81E.toInt(), 0.76f, highlighted = true),
        ShelfBook("人间失格", 0xFF8E2B2B.toInt(), 0.82f),
        ShelfBook("月亮与六便士", 0xFF8FB8D8.toInt(), 0.80f),
        ShelfBook("平凡的世界", 0xFFA83232.toInt(), 0.92f),
        ShelfBook("三体", 0xFF1F3D6E.toInt(), 0.87f),
        ShelfBook("解忧杂货店", 0xFFE0642E.toInt(), 0.80f),
        ShelfBook("追风筝的人", 0xFF2E9E6B.toInt(), 0.91f),
        ShelfBook("城南旧事", 0xFFF0A8C0.toInt(), 0.72f),
        ShelfBook("瓦尔登湖", 0xFF4E8C3A.toInt(), 0.92f),
        ShelfBook("红楼梦", 0xFF5B3E9E.toInt(), 0.93f)
    )

    // 书脊底色偏亮时改用深色书名，保证竖排书名始终清晰
    fun isLightSpine(color: Int): Boolean {
        val luminance = 0.299f * Color.red(color) / 255f +
            0.587f * Color.green(color) / 255f +
            0.114f * Color.blue(color) / 255f
        return luminance > 0.62f
    }

    // 书香书架：书架本身就是组件，四周保持透明（不画底色/描边/投影、不做形状裁切），
    // 只有横板与底部米色摘录面板是可着色区域，两者都按位图比例绘制，随组件尺寸一起缩放
    fun drawBookshelfChrome(
        canvas: Canvas,
        outerRect: RectF,
        densityScale: Float,
        style: WidgetStyle,
        context: Context
    ) {
        val fx = outerRect.left
        val fy = outerRect.top
        val fw = outerRect.width()
        val fh = outerRect.height()

        // 1. 书架横板：纵向位置固定，比书脊两侧略宽，下沿压一条深色细线做出板厚
        val shelfTop = bookshelfShelfTop(fy, fh, densityScale)
        val shelfHeight = bookshelfBoardHeight(densityScale)
        val shelfLeft = fx + fw * SHELF_BOARD_LEFT_RATIO
        val shelfRight = fx + fw * SHELF_BOARD_RIGHT_RATIO
        canvas.drawRect(shelfLeft, shelfTop, shelfRight, shelfTop + shelfHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9D4CB")
        })
        canvas.drawRect(shelfLeft, shelfTop + shelfHeight * 0.6f, shelfRight, shelfTop + shelfHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C2BBB0")
        })

        // 2. 书脊：13 本书按不同高度比例铺满书架宽度，其中「小王子」作为重点书目带浅色描边。
        // 最高一本由固定的书籍区高度决定，因此组件变高时书脊尺寸保持不变
        val booksLeft = fx + fw * SHELF_BOOKS_LEFT_RATIO
        val booksRight = fx + fw * SHELF_BOOKS_RIGHT_RATIO
        val slot = (booksRight - booksLeft) / SHELF_BOOKS.size
        val bookWidth = slot * 0.94f
        val maxBookHeight = (shelfTop - fy - SHELF_BOOK_TOP_MARGIN_DP * densityScale)
            .coerceAtLeast(4f * densityScale)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = style.font.getTypeface(context)
            textAlign = Paint.Align.CENTER
        }

        SHELF_BOOKS.forEachIndexed { index, book ->
            val bookHeight = maxBookHeight * book.heightFactor
            val left = booksLeft + slot * index + (slot - bookWidth) / 2f
            val right = left + bookWidth
            val top = shelfTop - bookHeight
            val radius = bookWidth * 0.06f

            canvas.drawRoundRect(RectF(left, top, right, shelfTop), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = book.color
            })

            if (book.highlighted) {
                val inset = 1.8f * densityScale
                canvas.drawRoundRect(
                    RectF(left + inset, top + inset, right - inset, shelfTop - inset),
                    radius,
                    radius,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.style = Paint.Style.STROKE
                        strokeWidth = 1.6f * densityScale
                        color = Color.parseColor("#FFF3D0")
                    }
                )
            }

            // 3. 竖排书名：字符之间只留紧凑行距，自书脊顶部向下排布，
            // 而不是把字符按书脊高度等距铺满；书名过长时整体缩小以容纳在书脊内
            val title = book.title
            val topPadding = (bookHeight * 0.07f).coerceAtLeast(2f * densityScale)
            val availableHeight = (bookHeight - topPadding * 2f).coerceAtLeast(1f)
            val textSize = minOf(bookWidth * 0.60f, availableHeight / (title.length * BOOK_TITLE_LINE_STEP_RATIO))
                .coerceAtLeast(5f * densityScale)
            titlePaint.textSize = textSize
            titlePaint.color = if (isLightSpine(book.color)) Color.parseColor("#2B2B2B") else Color.WHITE
            val centerX = (left + right) / 2f
            val lineStep = textSize * BOOK_TITLE_LINE_STEP_RATIO
            var baseline = top + topPadding + lineStep * 0.5f + textSize * 0.36f
            for (i in title.indices) {
                canvas.drawText(title[i].toString(), centerX, baseline, titlePaint)
                baseline += lineStep
            }
        }

        // 4. 底部米色摘录面板：正文由通用排版逻辑绘制在这块面板内，两者共用同一个面板矩形，
        // 面板高度随组件高度增长，正文可显示的行数随之增加
        val panel = bookshelfPanelRect(outerRect, densityScale)
        val panelRadius = minOf(panel.width(), panel.height()) * 0.12f
        canvas.drawRoundRect(
            panel,
            panelRadius,
            panelRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7F3EC") }
        )
    }

    // 书香书架横板上沿的纵向位置：书籍区高度固定为 SHELF_BOOK_BAND_HEIGHT_DP，
    // 组件不够高时压缩书籍区，优先保证摘录面板的最小高度
    fun bookshelfShelfTop(fy: Float, fh: Float, densityScale: Float): Float {
        val reserved = (SHELF_PANEL_MIN_HEIGHT_DP + SHELF_PANEL_GAP_DP + SHELF_PANEL_BOTTOM_MARGIN_DP + SHELF_BOARD_HEIGHT_DP) * densityScale
        val maxBand = (fh - reserved).coerceAtLeast(fh * SHELF_PANEL_MIN_BAND_RATIO)
        return fy + (SHELF_BOOK_BAND_HEIGHT_DP * densityScale).coerceAtMost(maxBand)
    }

    fun bookshelfBoardHeight(densityScale: Float): Float =
        (SHELF_BOARD_HEIGHT_DP * densityScale).coerceAtLeast(2f * densityScale)

    // 摘录面板矩形：上沿紧跟横板，下沿留出底部留白，中间全部属于文本显示区域
    fun bookshelfPanelRect(outerRect: RectF, densityScale: Float): RectF {
        val fx = outerRect.left
        val fy = outerRect.top
        val fw = outerRect.width()
        val fh = outerRect.height()
        val panelTop = bookshelfShelfTop(fy, fh, densityScale) +
            bookshelfBoardHeight(densityScale) +
            SHELF_PANEL_GAP_DP * densityScale
        val panelBottom = (fy + fh - SHELF_PANEL_BOTTOM_MARGIN_DP * densityScale)
            .coerceAtLeast(panelTop + 1f)
        return RectF(
            fx + fw * SHELF_PANEL_LEFT_RATIO,
            panelTop,
            fx + fw * SHELF_PANEL_RIGHT_RATIO,
            panelBottom
        )
    }

}
