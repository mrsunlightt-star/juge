package com.juge.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

// 预定义字体枚举
enum class WidgetFont(val fontPath: String?, val displayName: String) {
    DEFAULT(null, "系统默认"),
    SOURCE_HAN_SANS("fonts/SourceHanSansCN-Regular.otf", "思源黑体"),
    SOURCE_HAN_SERIF("fonts/SourceHanSerifCN-Regular.otf", "思源宋体"),
    LXGW_NEO_ZHISONG("fonts/LXGWNeoZhiSong.ttf", "霞鹜新致宋"),
    LXGW_NEO_XIHEI_SCREEN("fonts/LXGWNeoXiHeiScreen.ttf", "霞鹜新晰黑 Screen"),
    // 枚举名保持 LXGW_WENKAI 不变：旧存档按 name 序列化，改名会让老用户字体选择回退为默认
    LXGW_WENKAI("fonts/LXGWWenKai-Regular.ttf", "霞鹜文楷"),
    MASHAN_ZHENG("fonts/MaShanZheng-Regular.ttf", "毛笔楷书"),
    // 台湾繁体圆体，简体字库覆盖有限（约 5528/8968），冷僻与部分常用简体字会回退系统字体
    JF_OPEN_HUNINN("fonts/JFOpenHuninn-Regular.ttf", "jf open 粉圆");

    fun getTypeface(context: Context): Typeface {
        if (fontPath != null) {
            typefaceCache[fontPath]?.let { return it }
            try {
                val typeface = Typeface.createFromAsset(context.assets, fontPath)
                typefaceCache[fontPath] = typeface
                return typeface
            } catch (e: Exception) {
                // 读取失败则优雅降级
            }
        }
        return if (this == SOURCE_HAN_SERIF) Typeface.SERIF else Typeface.DEFAULT
    }

    companion object {
        // createFromAsset 是昂贵的 IO 操作，按路径缓存避免重复创建
        private val typefaceCache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()
    }
}

// 预定义形状枚举
enum class WidgetShape(val displayName: String) {
    RECTANGLE("矩形"),
    ELLIPSE("椭圆形"),
    HANDBOOK_TAPE("手账胶带"),
    TORN_PAPER("撕裂纸片"),
    SPLIT_CARD("图文明信片"),
    SPLIT_CARD_HORIZONTAL("左右分割明信片"),
    FEATHER_LETTER("羽毛信纸"),
    PIXEL_RETRO("复古像素"),
    PET_CAT_NAP("萌宠猫咪趴"),
    BLUE_NOTE("蓝色便签"),
    NIUPI_SHOUZHANG("撕边牛皮手账"),
    CLASSROOM_BLACKBOARD("教室黑板"),
    BOOKSHELF("书香书架"),
    GIANT_SWORD("巨剑"),
    PLUSH_FOREST("毛绒森林"),
    SUBOR_CONSOLE("小霸王游戏机"),
    WEATHER_BOX("天气盒子"),
    WINTER_PALACE("雪落宫墙"),
    DEEP_SEA("深海鲸歌"),
    SUMMER_SEA("夏天的海"),
    SUMMER_LOTUS("夏日荷花"),
    // 春天与小狗：绿框白卡 + 上沿草丛花枝 + 右上角探头柯基（整幅抠图素材）
    SPRING_DOG("春天与小狗"),
    // 竹林熊猫：整幅贴纸风插画——竹框 + 框顶五只探头熊猫 + 四角竹叶，框内奶油面板放正文。
    // 素材原稿四周的白灰棋盘格是"假透明"（PNG 被压成 RGB），已按低饱和+高亮+边界连通抠掉。
    PANDA_BAMBOO("竹林熊猫"),
    // 萌宠乐园：整幅毛毡风插画——粉色花边毛毡框 + 框顶六只毛毡小动物（猫狗兔鼠狐熊猫），
    // 框内奶油毛毡面板放正文。素材原稿是纯白底（同样被压成无 alpha 的 RGB），已抠掉。
    PET_PARK("萌宠乐园"),
    // 可爱四小只（2026-10-07 改版）：四个圆形头像**横排**在卡片左下角（原来是左侧 2×2 网格），
    // 正文落在头像上方整幅留白区。头像方块几何取自设计稿 1824×912：
    // 每块 170×151、1px #C9C9C9 描边、圆角 3，横排于 x 49/239/429/619、y 689。
    CUTE_FOUR_KIDS("可爱四小只"),
    // 青年雕塑：整幅贴图——浅灰卡面 + 上半身青年雕塑像（带白描边的抠图），下半一张白色面板放正文，
    // 面板中央有一枚 10% 不透明度的星形纹样（设计稿自带，刻意保留）。素材 2048×2048。
    // ⚠️ 这是一款 **4×4 专属**风格：设计稿只有方版，按产品决定**不出 4×2 版**（"没有这个东西"）。
    // 它在 4×2 组件上会被横向拉宽约 97%，这是已知且被接受的，**不要**给它补横版素材。
    YOUTH_SCULPTURE("青年雕塑"),
    // 蜡笔彩虹框：整幅手绘素材——蓝/绿/粉三色蜡笔波浪描边 + 框内近白纸面，
    // 左上角一颗红蜡笔爱心、右下角一只黄蜡笔气球。素材原稿 2048×1152 是 16:9 画布、
    // 框只占中间一条（四周透明留白 7%~27%），入库前已按内容框裁到 1826×627。
    CRAYON_FRAME("蜡笔彩虹框"),
}

/**
 * 组件的**最佳显示尺寸**：App 内预览按它出图，不跟桌面组件的尺寸走。
 *
 * 一款风格只对应一个尺寸——素材/设计稿的原生画布（见 docs/component-style-guide.md §0.1）。
 * 预览位图与预览区高度都由它推导（ui/main/WidgetPreviewPager、ui/PreviewMetrics）。
 * 两个取值与 widget_info.xml / widget_info_compact.xml 声明的入口尺寸一致。
 */
enum class WidgetDisplaySize(val widthDp: Int, val heightDp: Int) {
    /** 4×2：250×110dp（与紧凑入口 widget_info_compact.xml 的 minWidth/minHeight 一致） */
    WIDE_4X2(250, 110),

    /** 4×4：250×250dp（与主入口 widget_info.xml 的 minWidth/minHeight 一致） */
    SQUARE_4X4(250, 250);
}

// 图片缩放模式
enum class ImageScaleMode {
    STRETCH, // 拉伸
    CENTER_CROP, // 裁剪
    CENTER_FIT, // 等比全部完整显示(左右留透明)
    CENTER_CROP_TOP, // 等比铺满(覆盖)但顶部对齐：顶部主体(如趴着的猫)不裁切，多余高度从底部裁掉
    TILE // 平铺
}

// 文字阴影设置
data class TextShadow(
    val enabled: Boolean = false,
    val color: Int = Color.parseColor("#80000000"),
    val radius: Float = 4f,
    val dx: Float = 2f,
    val dy: Float = 2f
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("enabled", enabled)
            put("color", color)
            put("radius", radius.toDouble())
            put("dx", dx.toDouble())
            put("dy", dy.toDouble())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TextShadow {
            return TextShadow(
                enabled = json.optBoolean("enabled", false),
                color = json.optInt("color", Color.parseColor("#80000000")),
                radius = json.optDouble("radius", 4.0).toFloat(),
                dx = json.optDouble("dx", 2.0).toFloat(),
                dy = json.optDouble("dy", 2.0).toFloat()
            )
        }
    }
}

// 整体组件风格配置
data class WidgetStyle(
    val shape: WidgetShape = WidgetShape.RECTANGLE,
    val cornerRadiusDp: Float = 12f, // 圆角设置 0-30dp
    val backgroundColor: Int = Color.parseColor("#F5F5F5"), // RGB背景颜色
    val backgroundOpacity: Float = 1.0f, // 0.0 - 1.0
    val backgroundImagePath: String? = null, // 自定义背景图路径
    val bgImageScaleMode: ImageScaleMode = ImageScaleMode.CENTER_CROP,
    val font: WidgetFont = WidgetFont.DEFAULT,
    val fontSizeSp: Float = 19f, // 12-48sp
    val fontBold: Boolean = false,
    val fontItalic: Boolean = false,
    val fontColor: Int = Color.parseColor("#333333"), // 字体颜色
    val textAlign: String = "CENTER", // LEFT, CENTER, RIGHT, JUSTIFY（两端对齐，API 26+ 生效）
    val shadow: TextShadow = TextShadow(),
    val gradientColors: List<Int>? = null,
    val gradientAngle: Float = 45f,
    val bgBlurRadius: Float = 0f,
    val bgScrimAlpha: Float = 0f,
    val showCardShadow: Boolean = false,
    val cardBorderWidthDp: Float = 0f,
    val cardBorderColor: Int = android.graphics.Color.TRANSPARENT,
    val textureType: String = "NONE",
    val authorSignature: String? = null,
    val presetImageResName: String? = null,
    // 方版（4×4）专用素材。组件比例接近方形时优先用它，否则用 presetImageResName（横版）。
    //
    // **一款风格只出一份素材的代价是免不了的变形**：4×2 卡面 1.97:1、4×4 卡面 1:1，
    // 拿一张 1.75:1 的素材两边用，4×4 上要纵向拉 76%——熊猫会被拉成瘦高条。
    // 所以整幅贴图类风格要同时上两个尺寸，就得各出一份，方版放这里。
    //
    // ⚠️ 方版素材的**正文安全区必须落在与横版相同的相对位置**（见对应形状在
    // CardTextRenderer.textBoxFor 里的比例），这样切换两张素材时文字不用重排、也不用改代码。
    val presetImageResNameSquare: String? = null,
    val lineSpacingMultiplier: Float = 1.0f,
    val letterSpacing: Float = 0f,
    // 预设身份标识：套用内置预设时携带，copy() 微调后依然保留，
    // 用于精确判定样式是否源自 PRO 预设（字段匹配可被微调绕过）
    val presetId: String? = null,
    /**
     * 组件的**最佳显示尺寸**：App 内预览按它出图，与桌面组件的入口/实时尺寸无关。
     *
     * 一款风格只对应一个尺寸——素材/设计稿的原生画布（见 docs/component-style-guide.md §0.1）：
     * 整幅方版素材（2048×2048）的风格取 [WidgetDisplaySize.SQUARE_4X4]，横版素材（≈2:1）的取
     * [WidgetDisplaySize.WIDE_4X2]；无素材的全代码风格跟随 App 默认落位 4×2。
     *
     * **不落 JSON**：读档时按 presetId 从当前预设定义解析（[bestDisplaySizeForPreset]），
     * 因此预设改尺寸后，已保存、已落桌面的样式立即跟着变，不必重新套用预设；
     * 无 presetId 的旧存档与自定义样式按 4×2 兜底。
     */
    val bestDisplaySize: WidgetDisplaySize = WidgetDisplaySize.WIDE_4X2
) {
    /**
     * 主体四周透明的形状（信纸/撕纸/牛皮/猫咪趴/书架）不支持背景色：
     * 统一清空为透明，否则背景色会在主体外围露出一圈包裹卡片。
     */
    fun withoutUnsupportedBackgroundColor(): WidgetStyle =
        if (supportsBackgroundColor(shape)) this else copy(backgroundColor = Color.TRANSPARENT)

    /**
     * 渲染指纹：把参与绘制的字段按**固定顺序**拼成一个稳定字符串，用作预览位图的缓存 key。
     *
     * 刻意不用 [toJsonString]：org.json 的 key 顺序跨进程不稳定，拿它的哈希当缓存 key，
     * 同一份样式在两次冷启动之间会算出不同的值，缓存永远不命中。
     * 这里只依赖字段值本身，因此跨进程、跨版本都稳定。
     */
    fun cacheFingerprint(): String = buildString {
        append(shape.name).append('|').append(presetId ?: "").append('|')
        append(cornerRadiusDp).append('|').append(backgroundColor).append('|')
        append(backgroundOpacity).append('|').append(backgroundImagePath ?: "").append('|')
        append(bgImageScaleMode.name).append('|').append(font.name).append('|')
        append(fontSizeSp).append('|').append(fontBold).append('|').append(fontItalic).append('|')
        append(fontColor).append('|').append(textAlign).append('|')
        append(shadow.enabled).append('|').append(shadow.color).append('|')
        append(shadow.radius).append('|').append(shadow.dx).append('|').append(shadow.dy).append('|')
        append(gradientColors?.joinToString(",") ?: "").append('|')
        append(gradientAngle).append('|').append(bgBlurRadius).append('|')
        append(bgScrimAlpha).append('|').append(showCardShadow).append('|')
        append(cardBorderWidthDp).append('|').append(cardBorderColor).append('|')
        append(textureType).append('|').append(presetImageResName ?: "").append('|')
        append(presetImageResNameSquare ?: "").append('|')
        append(lineSpacingMultiplier).append('|').append(letterSpacing)
    }

    fun toJsonString(): String {
        return JSONObject().apply {
            put("shape", shape.name)
            put("presetId", presetId ?: JSONObject.NULL)
            put("cornerRadiusDp", cornerRadiusDp.toDouble())
            put("backgroundColor", backgroundColor)
            put("backgroundOpacity", backgroundOpacity.toDouble())
            put("backgroundImagePath", backgroundImagePath ?: "")
            put("bgImageScaleMode", bgImageScaleMode.name)
            put("font", font.name)
            put("fontSizeSp", fontSizeSp.toDouble())
            put("fontBold", fontBold)
            put("fontItalic", fontItalic)
            put("fontColor", fontColor)
            put("textAlign", textAlign)
            put("shadow", shadow.toJson())
            
            // 序列化新增字段
            if (gradientColors != null) {
                put("gradientColors", org.json.JSONArray(gradientColors))
            } else {
                put("gradientColors", JSONObject.NULL)
            }
            put("gradientAngle", gradientAngle.toDouble())
            put("bgBlurRadius", bgBlurRadius.toDouble())
            put("bgScrimAlpha", bgScrimAlpha.toDouble())
            put("showCardShadow", showCardShadow)
            put("cardBorderWidthDp", cardBorderWidthDp.toDouble())
            put("cardBorderColor", cardBorderColor)
            put("textureType", textureType)
            put("authorSignature", authorSignature ?: JSONObject.NULL)
            put("presetImageResName", presetImageResName ?: JSONObject.NULL)
            put("presetImageResNameSquare", presetImageResNameSquare ?: JSONObject.NULL)
            put("lineSpacingMultiplier", lineSpacingMultiplier.toDouble())
            put("letterSpacing", letterSpacing.toDouble())
        }.toString()
    }

    companion object {

        // 单个枚举字段非法（旧版本数据、被篡改）时仅降级该字段，不影响整体样式
        private inline fun <reified T : Enum<T>> safeEnum(name: String, fallback: T): T =
            try { enumValueOf<T>(name) } catch (e: IllegalArgumentException) { fallback }

        fun fromJsonString(jsonStr: String?): WidgetStyle {
            if (jsonStr.isNullOrEmpty()) return WidgetStyle()
            return try {
                val json = JSONObject(jsonStr)
                val parsedPresetId = if (json.has("presetId") && !json.isNull("presetId")) {
                    json.optString("presetId").takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                val parsed = WidgetStyle(
                    shape = safeEnum(json.optString("shape", WidgetShape.RECTANGLE.name), WidgetShape.RECTANGLE),
                    presetId = parsedPresetId,
                    bestDisplaySize = bestDisplaySizeForPreset(parsedPresetId),
                    cornerRadiusDp = json.optDouble("cornerRadiusDp", 12.0).toFloat(),
                    backgroundColor = json.optInt("backgroundColor", Color.parseColor("#F5F5F5")),
                    backgroundOpacity = json.optDouble("backgroundOpacity", 1.0).toFloat(),
                    backgroundImagePath = json.optString("backgroundImagePath", "").takeIf { it.isNotEmpty() },
                    bgImageScaleMode = safeEnum(json.optString("bgImageScaleMode", ImageScaleMode.CENTER_CROP.name), ImageScaleMode.CENTER_CROP),
                    font = try {
                        val fontStr = json.optString("font", WidgetFont.DEFAULT.name)
                        val mappedFontStr = when (fontStr) {
                            "SERIF" -> "SOURCE_HAN_SERIF"
                            // 旧版本曾用 "SANS_SERIF" 表示系统默认字体，并非思源黑体
                            // （思源黑体的枚举名为 SOURCE_HAN_SANS），这里保持映射到 DEFAULT。
                            "SANS_SERIF" -> "DEFAULT"
                            else -> fontStr
                        }
                        WidgetFont.valueOf(mappedFontStr)
                    } catch (e: Exception) {
                        WidgetFont.DEFAULT
                    },
                    fontSizeSp = json.optDouble("fontSizeSp", 19.0).toFloat(),
                    fontBold = json.optBoolean("fontBold", false),
                    fontItalic = json.optBoolean("fontItalic", false),
                    fontColor = json.optInt("fontColor", Color.parseColor("#333333")),
                    textAlign = json.optString("textAlign", "CENTER"),
                    shadow = if (json.has("shadow")) {
                        TextShadow.fromJson(json.getJSONObject("shadow"))
                    } else {
                        TextShadow()
                    },
                    // 反序列化新增字段并兼容处理默认值
                    gradientColors = if (json.has("gradientColors") && !json.isNull("gradientColors")) {
                        val arr = json.getJSONArray("gradientColors")
                        List(arr.length()) { arr.getInt(it) }
                    } else {
                        null
                    },
                    gradientAngle = json.optDouble("gradientAngle", 45.0).toFloat(),
                    bgBlurRadius = json.optDouble("bgBlurRadius", 0.0).toFloat(),
                    bgScrimAlpha = json.optDouble("bgScrimAlpha", 0.0).toFloat(),
                    showCardShadow = json.optBoolean("showCardShadow", false),
                    cardBorderWidthDp = json.optDouble("cardBorderWidthDp", 0.0).toFloat(),
                    cardBorderColor = json.optInt("cardBorderColor", android.graphics.Color.TRANSPARENT),
                    textureType = json.optString("textureType", "NONE"),
                    authorSignature = if (json.has("authorSignature") && !json.isNull("authorSignature")) {
                        json.getString("authorSignature")
                    } else {
                        null
                    },
                    presetImageResName = if (json.has("presetImageResName") && !json.isNull("presetImageResName")) {
                        json.getString("presetImageResName")
                    } else {
                        null
                    },
                    presetImageResNameSquare = if (json.has("presetImageResNameSquare") && !json.isNull("presetImageResNameSquare")) {
                        json.getString("presetImageResNameSquare")
                    } else {
                        null
                    },
                    lineSpacingMultiplier = json.optDouble("lineSpacingMultiplier", 1.0).toFloat().coerceIn(0.5f, 3.0f),
                    letterSpacing = json.optDouble("letterSpacing", 0.0).toFloat().coerceIn(0f, 20f)
                )
                parsed.upgradedForFreeStyle()
            } catch (e: Exception) {
                WidgetStyle()
            }
        }

        /**
         * 老数据补丁：免费风格的浅阴影 + 细描边是后来才加入的视觉定义，
         * 早期落库/落组件的样式副本里这两项仍是关闭状态。
         * 读取时统一补齐，让已经放到桌面的旧组件不必重新套用预设也能立刻获得立体感。
         */
        private fun WidgetStyle.upgradedForFreeStyle(): WidgetStyle {
            if (presetId == null || presetId !in FREE_PRESET_IDS) return this
            if (showCardShadow && cardBorderWidthDp > 0f) return this
            return copy(
                showCardShadow = true,
                cardBorderWidthDp = if (cardBorderWidthDp > 0f) cardBorderWidthDp else 1f,
                cardBorderColor = if (cardBorderColor != Color.TRANSPARENT) {
                    cardBorderColor
                } else {
                    Color.parseColor("#140F172A")
                }
            )
        }

        /**
         * 读档时解析最佳显示尺寸（**不落 JSON** 的派生属性）：
         * 按 presetId 查当前预设定义，预设改了尺寸即对所有已保存/已落桌面的样式生效；
         * 无 presetId 的旧存档与自定义样式按 4×2 兜底。
         */
        private fun bestDisplaySizeForPreset(presetId: String?): WidgetDisplaySize {
            if (presetId == null) return WidgetDisplaySize.WIDE_4X2
            return PRESETS.firstOrNull { it.presetId == presetId }?.bestDisplaySize
                ?: WidgetDisplaySize.WIDE_4X2
        }

        // 内置风格预设
        // 每条都带一个手写的固定 presetId（如 p_pure_round）作为身份标识：套用后随 copy() 保留，
        // 付费判定依据身份而不是字段值（字段匹配可被"改一个字段"绕过），也不依赖列表位置。
        val PRESETS: List<WidgetStyle> = listOf(
            // 免费默认风格：纯色圆角
            WidgetStyle(
                presetId = "p_pure_round",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 无素材：跟随 App 默认落位 4×2
                shape = WidgetShape.RECTANGLE,
                cornerRadiusDp = 12f,
                backgroundColor = Color.parseColor("#FFFFFF"),
                fontColor = Color.parseColor("#1F2937"),
                font = WidgetFont.DEFAULT,
                fontSizeSp = 19f,
                fontBold = false,
                // 免费默认风格补上浅阴影 + 细描边：桌面组件不再是一张"贴平"的白纸
                showCardShadow = true,
                cardBorderWidthDp = 1f,
                cardBorderColor = Color.parseColor("#140F172A"),
                textAlign = "CENTER"
            ), // 0. 纯色圆角 (免费)
            WidgetStyle(
                presetId = "p_torn_paper",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2,
                shape = WidgetShape.TORN_PAPER, // 拟物撕纸
                cornerRadiusDp = 16f,
                backgroundColor = Color.parseColor("#E2EAD8"),
                fontColor = Color.parseColor("#5E6852"),
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 19f,
                textureType = "PAPER",
                showCardShadow = true,
                authorSignature = "—— 撕纸手账"
            ), // 4. 拟物撕纸风格 (PRO)
            WidgetStyle(
                presetId = "p_handbook_tape",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2,
                shape = WidgetShape.HANDBOOK_TAPE, // 手账胶带
                cornerRadiusDp = 12f,
                backgroundColor = Color.parseColor("#FCF6E5"),
                fontColor = Color.parseColor("#8A6F4E"),
                font = WidgetFont.LXGW_WENKAI,
                fontBold = true,
                showCardShadow = true,
                // 原先有一条 1.5dp 的米色描边（#DCD0BA）。描边改为「只画在卡面内」之后，
                // 它从"半条压在卡上、半条压在壁纸上"变成整条实心落在卡面上，可见宽度翻倍，
                // 在桌面上显成卡片外一圈浅色带。这层厚度感本来也不是必需的，直接去掉，卡片更干净。
                cardBorderWidthDp = 0f,
                authorSignature = "—— 手账心情"
            ), // 5. 复古手账风格 (PRO)
            WidgetStyle(
                presetId = "p_postcard_note",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：4×2 下图片区被裁掉近一半、正文只剩一行
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                gradientColors = listOf(Color.parseColor("#1B2845"), Color.parseColor("#274060")),
                gradientAngle = 45f,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.parseColor("#1F1F1F"),
                authorSignature = "—— 明信片寄语"
            ), // 10. 蓝色画报风格 (PRO)
            WidgetStyle(
                presetId = "p_dawn_sunrise",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：图片带在 4×4 下才不被裁掉一半
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.parseColor("#3A4D5C"),
                authorSignature = "—— 晨曦日出",
                presetImageResName = "bg_illustration_1"
            ), // 12. 晨曦画报风格 (PRO)
            WidgetStyle(
                presetId = "p_healing_sunset",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.parseColor("#6B4C4C"),
                authorSignature = "—— 治愈落日",
                presetImageResName = "bg_illustration_2"
            ), // 13. 治愈画报风格 (PRO)
            WidgetStyle(
                presetId = "p_starry_forest",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.parseColor("#1B2845"),
                authorSignature = "—— 星空森林",
                presetImageResName = "bg_illustration_3"
            ), // 14. 星空画报风格 (PRO)

            WidgetStyle(
                presetId = "p_sky_blue",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 底图是 300×120 的矢量素材（2.5:1），4×2 下即原生比例
                shape = WidgetShape.RECTANGLE,
                backgroundColor = Color.parseColor("#1E6DD0"),
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.WHITE,
                authorSignature = "—— 天空之蓝",
                presetImageResName = "rectangle_1"
            ), // 15. 天空蓝风格 (PRO)
            WidgetStyle(
                presetId = "p_fight_club",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：实景照片在 4×4 的图区里只裁 16%、4×2 下要裁掉 47%
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 搏击俱乐部",
                presetImageResName = "boji_julebu"
            ), // 21. 搏击俱乐部风格 (PRO)
            WidgetStyle(
                presetId = "p_breaking_bad",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 绝命毒师",
                presetImageResName = "breaking_bad"
            ), // 22. 绝命毒师风格 (PRO)
            WidgetStyle(
                presetId = "p_v_for_vendetta",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— V字仇杀队",
                presetImageResName = "v_for_vendetta"
            ), // 23. V字仇杀队风格 (PRO)
            WidgetStyle(
                presetId = "p_la_la_land",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 爱乐之城",
                presetImageResName = "aile_zhi_cheng"
            ), // 24. 爱乐之城风格 (PRO)

            // 新风格：经典左右分割（会员专属）
            WidgetStyle(
                presetId = "p_cute_cat",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 左栏人像素材（0.95:1）：4×2 的左栏比例与素材最接近
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT, // 萌宠主体已抠底透明，等比完整显示避免裁切头/脸
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 可爱猫咪",
                presetImageResName = "cute_cat"
            ), // 可爱猫咪 (PRO)
            WidgetStyle(
                presetId = "p_fluffy_dog",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2,
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 毛绒小狗",
                presetImageResName = "fluffy_dog"
            ), // 毛绒小狗 (PRO)
            WidgetStyle(
                presetId = "p_happy_dog",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2,
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT, // 萌宠主体已抠底透明，等比完整显示避免裁切头/脸
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 快乐小狗",
                presetImageResName = "happy_dog"
            ), // 快乐小狗 (PRO)
            WidgetStyle(
                presetId = "p_fluffy_cat",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2,
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 毛绒猫咪",
                presetImageResName = "fluffy_cat"
            ), // 毛绒猫咪 (PRO)

            // 羽毛信纸 (PRO)：信纸即卡片(信纸外透明透桌面)，文字落信纸内部
            WidgetStyle(
                presetId = "p_feather_letter",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 横版素材 2051×1313：4×2 下等比铺满，横向只多铺约 45%
                shape = WidgetShape.FEATHER_LETTER,
                cornerRadiusDp = 8f,
                backgroundColor = android.graphics.Color.TRANSPARENT, // 信纸外透明
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH, // 信纸图铺满卡片区
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 20f,
                fontColor = Color.parseColor("#5C4A3A"), // 信纸上的深褐色文字
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "feather_letter",
                authorSignature = "—— 羽毛信纸"
            ), // 29. 羽毛信纸 (PRO)

            // 复古像素 (PRO)：像素风方框(红框+虚线+薄荷绿底)即卡片，文字落框内留白
            WidgetStyle(
                presetId = "p_pixel_retro",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 横版素材 1825×793（2.3:1）
                shape = WidgetShape.PIXEL_RETRO,
                cornerRadiusDp = 8f,
                backgroundColor = android.graphics.Color.TRANSPARENT, // 框外透明
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH, // 像素图铺满
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 22f,
                fontColor = Color.parseColor("#1B2A4A"), // 深蓝黑,匹配像素风
                textAlign = "CENTER",
                fontBold = true,
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "pixel_retro",
                authorSignature = "—— 复古像素"
            ), // 30. 复古像素 (PRO)

            // 萌宠猫咪趴 (PRO)：橘猫趴在渐变卡片顶，卡片做主体，文字落卡片中下部(避开猫)
            WidgetStyle(
                presetId = "p_pet_cat_nap",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 素材 1414×994（1.42）更接近方形：4×4 下猫更大、正文也放得下
                shape = WidgetShape.PET_CAT_NAP,
                cornerRadiusDp = 8f,
                backgroundColor = android.graphics.Color.TRANSPARENT, // 卡片外透明
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP_TOP, // 等比铺满且顶部对齐：渐变卡片铺满整组件宽、趴在顶部的猫不被裁切
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 22f,
                fontColor = Color.parseColor("#5C6270"), // 深灰,匹配渐变卡片
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "cute_cat_lying",
                authorSignature = "—— 萌宠猫咪趴"
            ), // 31. 萌宠猫咪趴 (PRO)

            // 可爱四小只 (PRO)：四个圆形萌宠头像(企鹅帽/蓝绿双马尾/黄脸/粉发双丸子)排成 2×2 网格，左图右文
            WidgetStyle(
                presetId = "p_cute_four_kids",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 4×2 专属：设计稿就是 1824×912（2:1），不出 4×4 版
                // 2026-10-07 改版：四个头像从「左侧 2×2 网格」改成「左下角横排一排」（设计稿 1824×912）。
                // 刻意保留 presetId 不变——已用这款的组件、以及按身份判定的会员状态都不受影响。
                //
                // ⚠️ 这是一款**4×2 专属**风格，按产品决定**不出 4×4 版**（设计之初就是 2:1 画布）。
                // 它在 4×4 组件上会被纵向拉 94%，这是已知且被接受的，**不要**给它补方版素材、
                // 也不要把它当成"缺一份方版"来修。
                shape = WidgetShape.CUTE_FOUR_KIDS,
                backgroundColor = Color.WHITE,
                bgImageScaleMode = ImageScaleMode.STRETCH, // 头像条按 2:1 设计，铺满整卡
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 19f,
                fontColor = Color.parseColor("#374151"),
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "cute_four_kids",
                authorSignature = "—— 可爱四小只"
            ), // 可爱四小只 (PRO)

            // 蓝色便签 (PRO)：SVG 设计稿还原 — #43A8F0 蓝底圆角 + 顶部 NOTE + 右上信息钮 + 底部米白手写签条
            WidgetStyle(
                presetId = "p_blue_note",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 设计稿 578×363（横版）：NOTE 区与底部签条按它的比例现画
                shape = WidgetShape.BLUE_NOTE,
                cornerRadiusDp = 16f,
                backgroundColor = Color.parseColor("#43A8F0"),
                backgroundOpacity = 1f,
                font = WidgetFont.DEFAULT,
                fontSizeSp = 19f,
                fontColor = Color.WHITE,
                textAlign = "CENTER",
                fontBold = false,
                showCardShadow = true,
                cardBorderWidthDp = 0f,
                authorSignature = "—— 蓝色便签"
            ), // 32. 蓝色便签 (PRO)
            // 撕边牛皮手账 (PRO)：牛皮纸撕边即卡片(外透明透桌面)，文字落纸面中部
            WidgetStyle(
                presetId = "p_niupi_shouzhang",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 横版素材 2048×1037（1.98:1）
                shape = WidgetShape.NIUPI_SHOUZHANG,
                cornerRadiusDp = 8f,
                backgroundColor = android.graphics.Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 20f,
                fontColor = Color.parseColor("#5C4632"),
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "niupi_shouzhang",
                authorSignature = "—— 撕边牛皮手账"
            ), // 34. 撕边牛皮手账 (PRO)
            // 得意doro (PRO)：实景照片左图右文，保留人物与草地背景
            WidgetStyle(
                presetId = "p_deyi_doro",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2,
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP, // 整幅实景照片铺满左栏，无透明抠图
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 得意doro",
                presetImageResName = "deyi_doro"
            ), // 35. 得意doro (PRO)
            // 教室黑板 (PRO)：黑板即卡片，粉笔白字写在绿色板面上
            WidgetStyle(
                presetId = "p_classroom_blackboard",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 横版素材 2048×931（2.2:1）
                shape = WidgetShape.CLASSROOM_BLACKBOARD,
                cornerRadiusDp = 8f,
                backgroundColor = android.graphics.Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 20f,
                fontColor = Color.parseColor("#F2F4EE"),
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "classroom_blackboard",
                authorSignature = "—— 教室黑板"
            ), // 36. 教室黑板 (PRO)
            // 书香书架 (PRO)：书架本身就是组件——顶部一排彩色书脊立在横板上，底部米色摘录面板承载正文。
            // 四周保持透明（无底色/描边/投影），摘录面板与文字区域一起随组件尺寸缩放
            WidgetStyle(
                presetId = "p_bookshelf",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 设计为 4×3：比 4×2 更接近方形，书架多一排、正文放得下
                shape = WidgetShape.BOOKSHELF,
                cornerRadiusDp = 0f,
                backgroundColor = Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.DEFAULT,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#2B2B2B"),
                textAlign = "LEFT",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                authorSignature = "—— 书香书架"
            ), // 37. 书香书架 (PRO)
            // 巨剑 (PRO)：武士扛巨剑横贯画面，正文压在剑身金属面上（左侧人物留白，文字区只取剑身）
            WidgetStyle(
                presetId = "p_giant_sword",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 横版素材 1024×585（1.75:1）
                shape = WidgetShape.GIANT_SWORD,
                cornerRadiusDp = 0f,
                backgroundColor = Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 16f,
                fontColor = Color.parseColor("#EFEDE6"),
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "giant_sword",
                // 剑身是深灰金属面，浅色文字加一层暗描边保证可读性
                shadow = TextShadow(
                    enabled = true,
                    color = Color.parseColor("#8A000000"),
                    radius = 3f,
                    dx = 0f,
                    dy = 1f
                ),
                authorSignature = "—— 巨剑"
            ), // 39. 巨剑 (PRO)
            // 毛绒森林 (PRO)：毛绒粉边卡片 + 顶部小树蘑菇，正文落在奶油色毛绒面板内
            WidgetStyle(
                presetId = "p_plush_forest",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 方版素材已交付（1536×1536）：4×4 上不再把 1.75 的横版纵向拉 76%
                shape = WidgetShape.PLUSH_FOREST,
                cornerRadiusDp = 0f,
                backgroundColor = Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#6B4A34"),
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "plush_forest",
                // 方版（4×4）：真透明底 1:1 图，避免 1.75 的横版在 4×4 上被纵向拉 76%
                presetImageResNameSquare = "plush_forest_square",
                authorSignature = "—— 毛绒森林"
            ), // 40. 毛绒森林 (PRO)
            // 小霸王游戏机 (PRO)：3D 渲染的实物模型——机身居中、左右各一只手柄，正文落在机身屏幕上
            WidgetStyle(
                presetId = "p_subor_console",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 产品点名（2026-10-08）：方形下模型更大、屏幕放得下两行正文；4×2 只剩一行、两侧还是大片留白
                shape = WidgetShape.SUBOR_CONSOLE,
                cornerRadiusDp = 0f,
                backgroundColor = Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                // 整幅实物模型等比完整显示：组件是 4×3 还是 4×4 都不会被拉伸或裁切
                bgImageScaleMode = ImageScaleMode.CENTER_FIT,
                font = WidgetFont.DEFAULT,
                fontSizeSp = 16f,
                fontColor = Color.parseColor("#A8FF9A"), // 绿色荧光：模拟单色显像管通电显示
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "subor_console",
                // 屏幕为通电的绿色荧光屏，给文字叠一层绿色荧光晕（半径由渲染器按密度放大）
                shadow = TextShadow(
                    enabled = true,
                    color = Color.parseColor("#9970FF6E"),
                    radius = 4f,
                    dx = 0f,
                    dy = 0f
                ),
                authorSignature = "—— 小霸王游戏机"
            ), // 41. 小霸王游戏机 (PRO)
            // 天气盒子 (PRO)：白色盒体正面挖出一个内凹的方形小腔，腔底是蓝天微缩城市
            // （云/太阳/楼群），腔体四周留白即正文区。盒体、腔体内凹的暗角与高光全部由
            // 渲染器按当前组件尺寸现算，素材只是腔底那一块画面，因此 4×2 / 4×4 都不会拉伸。
            WidgetStyle(
                presetId = "p_weather_box",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：腔体在上、正文在下
                shape = WidgetShape.WEATHER_BOX,
                cornerRadiusDp = 20f,
                backgroundColor = Color.parseColor("#FFFFFF"), // 盒面：白，可跟随「背景颜色」自定义
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                // 腔底素材按原比例完整显示，两侧余量由盒面白色兜住，不会裁掉云和楼
                bgImageScaleMode = ImageScaleMode.CENTER_FIT,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#3A4757"), // 冷灰蓝：与腔底蓝天同一色温
                textAlign = "CENTER",
                showCardShadow = true,
                cardBorderWidthDp = 0f,
                presetImageResName = "weather_box_cavity",
                authorSignature = "—— 天气盒子"
            ), // 49. 天气盒子 (PRO)
            // 雪落宫墙 (PRO)：整卡米色卡纸，木框雪景宫墙照片按真实比例摆在上方，
            // 梅枝贴右下角。相框与梅枝都是带羽化米边的抠图图层（羽化边与卡纸同色，
            // 任意组件比例都不露接缝），卡纸底色由「背景颜色」绘制（默认米白），
            // 文字落在相框下方、梅枝以左的留白区。
            WidgetStyle(
                presetId = "p_winter_palace",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：画框在上、正文在下
                shape = WidgetShape.WINTER_PALACE,
                cornerRadiusDp = 16f,
                backgroundColor = Color.parseColor("#EEE6E2"), // 米色卡纸，与素材羽化边同色
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 16f,
                fontColor = Color.parseColor("#6B5748"), // 暖棕题字：压米色卡纸，与宫墙红同暖调
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                textureType = "PAPER", // 卡纸的细颗粒：与素材羽化边的纸纹衔接
                presetImageResName = "winter_frame",
                authorSignature = "—— 雪落宫墙"
            ), // 50. 雪落宫墙 (PRO)
            // 深海鲸歌 (PRO)：整卡白蓝卡纸，木框日光海面照片按真实比例摆上方，
            // 鲸影淡入右下角。与雪落宫墙共用「画框卡片」分层渲染：
            // 相框与鲸影都是带羽化卡纸边的抠图图层，文字落在相框下方整幅留白带。
            WidgetStyle(
                presetId = "p_deep_sea",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：画框在上、正文在下
                shape = WidgetShape.DEEP_SEA,
                cornerRadiusDp = 16f,
                backgroundColor = Color.parseColor("#E5E5F8"), // 白蓝卡纸，与素材羽化边同色
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 16f,
                fontColor = Color.parseColor("#33475E"), // 深海蓝题字：压白蓝卡纸
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "deepsea_frame",
                authorSignature = "—— 深海鲸歌"
            ), // 51. 深海鲸歌 (PRO)
            // 夏天的海 (PRO)：整卡浅灰蓝卡纸，木框林间海径照片按真实比例摆上方，
            // 浪花与绿叶淡入右下角。画框卡片族，与雪落宫墙共用分层渲染。
            WidgetStyle(
                presetId = "p_summer_sea",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：画框在上、正文在下
                shape = WidgetShape.SUMMER_SEA,
                cornerRadiusDp = 16f,
                backgroundColor = Color.parseColor("#DAD8E6"), // 浅灰蓝卡纸，与素材羽化边同色
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 16f,
                fontColor = Color.parseColor("#2E4D5E"), // 海色题字：青蓝压灰蓝卡纸
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                textureType = "PAPER",
                presetImageResName = "summersea_frame",
                authorSignature = "—— 夏天的海"
            ), // 52. 夏天的海 (PRO)
            // 夏日荷花 (PRO)：整卡淡紫卡纸，木框荷塘照片按真实比例摆上方，
            // 荷影淡入右下角。画框卡片族，与雪落宫墙共用分层渲染。
            WidgetStyle(
                presetId = "p_summer_lotus",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 明信片行按 4×4 设计：画框在上、正文在下
                shape = WidgetShape.SUMMER_LOTUS,
                cornerRadiusDp = 16f,
                backgroundColor = Color.parseColor("#D7D4EA"), // 淡紫卡纸，与素材羽化边同色
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 16f,
                fontColor = Color.parseColor("#6E4557"), // 荷色题字：藕紫压淡紫卡纸
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                textureType = "PAPER",
                presetImageResName = "lotus_frame",
                authorSignature = "—— 夏日荷花"
            ), // 53. 夏日荷花 (PRO)
            // 春天与小狗 (PRO)：整幅抠图素材——绿框白卡 + 上沿草丛麦穗粉花 + 右上角探头柯基。
            // 与「毛绒森林」同一套设计语言（素材比例 1.79:1），同样按 STRETCH 铺满：
            // 4×2 上横向只多铺 27%，是这个系列一贯的表现；4×4 的观感也与毛绒森林一致。
            // 卡外已抠成透明（透出壁纸），所以不设背景色、也不跟随外框圆角。
            WidgetStyle(
                presetId = "p_spring_dog",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // ⚠️ 方版素材未交付：4×4 会把横版纵向拉 76%（见 style-guide §0.1.1），方版到货后改 SQUARE_4X4
                shape = WidgetShape.SPRING_DOG,
                cornerRadiusDp = 12f,
                backgroundColor = android.graphics.Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#2F6B45"), // 深绿题字：压白卡面，与绿框同调
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "spring_dog",
                authorSignature = "—— 春天与小狗"
            ), // 54. 春天与小狗 (PRO)
            // 信纸 (PRO)：一张横线信纸。纸色由「背景颜色」给（默认白），横线是矢量素材，
            // 栅格化按组件尺寸现算，因此 4×2 / 4×4 / 用户拖大都不糊。
            // 铺图刻意用 CENTER_CROP 而不是 STRETCH：4×2 下是等比放大后裁中间一段，
            // 横线维持 19/250 的原间距、只是少露几条；STRETCH 会把 11 条压成 4 条，密得不像信纸。
            WidgetStyle(
                presetId = "p_ruled_paper",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 按 4×2 设计：CENTER_CROP 保住横线原间距
                shape = WidgetShape.RECTANGLE,
                cornerRadiusDp = 8f,
                backgroundColor = Color.parseColor("#FFFFFF"),
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#3B3F46"), // 中性深灰：与横线的灰同调，不抢字
                textAlign = "CENTER",
                showCardShadow = true,
                cardBorderWidthDp = 0f,
                presetImageResName = "ruled_paper",
                authorSignature = "—— 信纸"
            ), // 58. 信纸 (PRO)
            // 纹理山水 (PRO)：整卡贴图。素材是设计稿里那张横线水彩山水，
            // 已经按设计稿的拉伸方式**预烘**成 222:90 的比例（设计稿把源图的一段横向切片
            // 非等比拉宽铺进图片区，直接裁原图会丢掉上半的雾带与下半的重山，构图对不上）。
            // 白框不烘进素材、改用卡片描边：这样圆角滑条一动，框跟着走圆角而不是被切成直角。
            // ⚠️ 这是 4×2 原生的风格：源图那一段只有 1152×679 真实像素，4×4 下方形裁切要放大 2.3x，会软。
            WidgetStyle(
                presetId = "p_texture_landscape",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 4×2 原生：源切片仅 1152×679 真实像素，方形裁切要放大 2.3x，会软
                shape = WidgetShape.RECTANGLE,
                cornerRadiusDp = 10f,
                backgroundColor = Color.parseColor("#DDDECD"), // 素材平均色：调低不透明度时褪成同色
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#3C4A3E"), // 深松绿：压雾带与远山都读得出
                textAlign = "CENTER",
                showCardShadow = true,
                // 设计稿的白框是 225 画布上的 1.5 单位 = 图片宽的 0.676%；
                // 换算到 4×2 的卡面（内缩后 296dp 宽）≈ 2.0dp 可见宽度。
                // cardBorderWidthDp 现在就是「可见宽度」，所以填 2.0 即等于设计稿。
                cardBorderWidthDp = 2.0f,
                cardBorderColor = Color.parseColor("#FFFFFF"),
                presetImageResName = "texture_landscape",
                authorSignature = "—— 纹理山水"
            ), // 59. 纹理山水 (PRO)
            // 竹林熊猫 (PRO)：整幅贴纸风插画——竹框 + 框顶五只探头熊猫 + 四角竹叶，
            // 框内奶油面板承载正文。素材比例 1.757:1，与「春天与小狗」「毛绒森林」同一套语言，
            // 同样按 STRETCH 铺满：4×2 上横向多铺约 29%，4×4 上纵向多铺约 76%（熊猫会显得瘦高）。
            // 框外已抠成透明（透出壁纸），所以不设背景色、也不跟随外框圆角。
            WidgetStyle(
                presetId = "p_panda_bamboo",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 方版素材已交付（1536×1536）：4×4 上不再把 1.757 的横版纵向拉 76%
                shape = WidgetShape.PANDA_BAMBOO,
                cornerRadiusDp = 12f,
                backgroundColor = android.graphics.Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#3E4A35"), // 墨绿题字：压奶油面板，与竹叶同调
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "panda_bamboo",
                // 方版（4×4）：真透明底 1:1 图
                presetImageResNameSquare = "panda_bamboo_square",
                authorSignature = "—— 竹林熊猫"
            ), // 60. 竹林熊猫 (PRO)
            // 萌宠乐园 (PRO)：整幅毛毡风插画——粉色花边毛毡框 + 框顶六只毛毡小动物，框内奶油面板放正文。
            // 素材比例 1.757:1，与「竹林熊猫」「毛绒森林」「春天与小狗」同一套语言，同样 STRETCH 铺满。
            // 框外已抠成透明（透出壁纸），所以不设背景色、也不跟随外框圆角。
            WidgetStyle(
                presetId = "p_pet_park",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 方版素材已交付（1536×1536）；产品明确：萌宠乐园的最佳显示尺寸是 4:4
                shape = WidgetShape.PET_PARK,
                cornerRadiusDp = 12f,
                backgroundColor = android.graphics.Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#7A5A55"), // 暖棕题字：压奶油毛毡面，与粉框同暖调
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "pet_park",
                // 方版（4×4）：用户按 2048×2048 规格另出的一张真透明底方图。
                // 有了它，4×4 上不再把 1.757 的横版纵向拉 76%，毛毡小动物保持本来比例。
                presetImageResNameSquare = "pet_park_square",
                authorSignature = "—— 萌宠乐园"
            ), // 61. 萌宠乐园 (PRO)
            // 青年雕塑 (免费)：整幅贴图——浅灰卡面 + 上半身雕塑像，下半白面板放正文。
            // 素材 2048×2048，只出了 4×4 方版（见 WidgetShape.YOUTH_SCULPTURE 的说明），
            // 因此 presetImageResName 直接指向方版素材，不填预设的方版字段。
            //
            // 底色写成素材卡面自己的浅灰：不透明度调低时整卡褪成同色，而不是露出上一个风格的底色。
            // 阴影 + 1dp 细描边与「纯色圆角」同款——免费风格的既有视觉定义，
            // 老存档读取时 upgradedForFreeStyle() 也会把这两项补齐，预设不写就会在重载时跳一下。
            WidgetStyle(
                presetId = "p_youth_sculpture",
                bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 4×4 专属：设计稿只有 2048×2048 方版，不出 4×2 版
                shape = WidgetShape.YOUTH_SCULPTURE,
                cornerRadiusDp = 12f, // 设计稿的「12dp 圆角参考」
                backgroundColor = Color.parseColor("#F5F5F5"),
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.SOURCE_HAN_SERIF, // 宋体：与纪念碑式的雕塑气质一致
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#3F3B36"), // 暖石灰：压白色面板
                textAlign = "CENTER",
                showCardShadow = true,
                cardBorderWidthDp = 1f,
                cardBorderColor = Color.parseColor("#140F172A"),
                presetImageResName = "youth_sculpture",
                authorSignature = "—— 青年雕塑"
            ), // 62. 青年雕塑 (免费)
            // 蜡笔彩虹框 (PRO)：整幅手绘素材——蓝/绿/粉三色蜡笔波浪描边，框内近白纸面，
            // 左上角一颗红蜡笔爱心、右下角一只黄蜡笔气球。
            //
            // 素材原稿 2048×1152 是 16:9 画布，框只占中间一条（上下各留 21%~27% 透明），
            // 整幅入库铺满后框会缩到中间、四周空一圈，所以**按内容框裁到 1826×627** 再入库。
            // 裁后 2.91:1、4×2 卡面 2.27:1，按 STRETCH 铺满：4×2 上纵向多铺约 28%
            // （爱心与气球略变高；蜡笔线条本来就粗细不匀，这点变形看不出来）。
            // ⚠️ 4×4 上纵向要拉近 2.9 倍，爱心会拉成细条——**产品决定这款风格不出 4×4 版**
            // （2026-10-08：同「纹理山水」「可爱四小只」，不要替它补方版素材）。
            //
            // 框外已抠成透明（透出壁纸），所以不设背景色、也不跟随外框圆角。
            WidgetStyle(
                presetId = "p_crayon_frame",
                bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 产品点名：最佳显示尺寸 4:2（4×2 原生，无方版素材）
                shape = WidgetShape.CRAYON_FRAME,
                cornerRadiusDp = 12f,
                backgroundColor = android.graphics.Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#3B4A5A"), // 铅笔灰蓝：压白纸面，与蓝蜡笔同调
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "crayon_frame",
                authorSignature = "—— 蜡笔彩虹框"
            ), // 63. 蜡笔彩虹框 (PRO)
        )

        // 免费预设：按身份 id 判定，未激活用户可直接套用。
        // 当前规则：「纯色圆角」与「青年雕塑」免费，其余风格一律会员专属。
        // 字体、字号、颜色、圆角、不透明度等细节调整不在此列，全部免费。
        private val FREE_PRESET_IDS = setOf(
            "p_pure_round",      // 纯色圆角
            "p_youth_sculpture"  // 青年雕塑
        )

        // 主体四周透明的形状：这些形状的插画/装饰并不铺满整个组件位图，背景色只会在主体外围
        // 露出一圈圆角卡片（信纸/撕纸/牛皮/猫咪趴/书架），因此不支持设置背景色。
        // 与「外框圆角」的禁用名单不同（撕裂纸片圆角不可调，但背景色就是它的纸面颜色，仍可用）。
        private val SHAPES_WITHOUT_BACKGROUND_COLOR = setOf(
            WidgetShape.FEATHER_LETTER,
            WidgetShape.PET_CAT_NAP,
            WidgetShape.NIUPI_SHOUZHANG,
            WidgetShape.BOOKSHELF,
            WidgetShape.GIANT_SWORD,
            WidgetShape.PLUSH_FOREST,
            WidgetShape.SUBOR_CONSOLE,
            // 春天与小狗：绿框白卡是素材本身的一部分，卡外透明处透出壁纸；
            // 再铺一层背景色会在卡外露出一圈色块
            WidgetShape.SPRING_DOG,
            // 竹林熊猫：同理——竹框内奶油面板是素材自带的，框外（熊猫、竹叶周围）透明处透壁纸
            WidgetShape.PANDA_BAMBOO,
            // 萌宠乐园：同理——毛毡框内面板是素材自带的，框外（小动物周围）透明处透壁纸
            WidgetShape.PET_PARK,
            // 蜡笔彩虹框：同理——蜡笔框外一侧是透明的（透出壁纸），
            // 再铺一层背景色会在蜡笔描边外露出一圈方角色块
            WidgetShape.CRAYON_FRAME
        )

        /** 该形状是否支持设置背景色 */
        fun supportsBackgroundColor(shape: WidgetShape): Boolean =
            shape !in SHAPES_WITHOUT_BACKGROUND_COLOR

        /**
         * 判断一个样式是否属于付费预设。
         * 主路径按 presetId 身份判定：套用预设后任意微调（字号、粗细、圆角等）仍能正确识别。
         * 仅当 styleJson 没有 presetId（升级前的老数据）时才回退到字段匹配兜底。
         */
        fun isProPreset(style: WidgetStyle): Boolean {
            val pid = style.presetId
            if (pid != null) {
                return pid !in FREE_PRESET_IDS
            }
            // 旧版本数据兜底：扩展到全部视觉标识字段，缩小字段微调绕过的空间
            val index = PRESETS.indexOfFirst {
                it.shape == style.shape &&
                    it.backgroundColor == style.backgroundColor &&
                    it.fontColor == style.fontColor &&
                    it.font == style.font &&
                    it.fontBold == style.fontBold &&
                    it.fontItalic == style.fontItalic &&
                    it.gradientColors == style.gradientColors &&
                    it.presetImageResName == style.presetImageResName &&
                    it.textureType == style.textureType &&
                    it.authorSignature == style.authorSignature &&
                    it.cardBorderWidthDp == style.cardBorderWidthDp &&
                    it.cardBorderColor == style.cardBorderColor &&
                    it.showCardShadow == style.showCardShadow
            }
            return index != -1 && PRESETS[index].presetId !in FREE_PRESET_IDS
        }

        // 明信片/画报风格可选插图（资源名 → 展示名）。
        // 主界面与快捷面板共用同一份列表，避免两处硬编码各自维护导致入口间素材不一致。
        // 注意：萌宠/角色类素材(guga_doro、cute_cat、fluffy_dog、happy_dog、fluffy_cat)归入
        // 下方的 PET_PRESETS(萌宠风格)，这里刻意排除，避免分类不干净、同一批图重复出现在明信片行。
        val ILLUSTRATION_PRESETS: List<Pair<String, String>> = listOf(
            "bg_illustration_1" to "晨曦日出",
            "bg_illustration_2" to "治愈落日",
            "bg_illustration_3" to "星空森林",
            "rectangle_1" to "天空蓝",
            "boji_julebu" to "搏击俱乐部",
            "breaking_bad" to "绝命毒师",
            "v_for_vendetta" to "V字仇杀队",
            "aile_zhi_cheng" to "爱乐之城"
        )

        /**
         * 插图素材 → 套用后的风格：形状、配色、字体一律取**素材自己的预设定义**，
         * 背景图改由素材资源名提供（自定义背景图路径清空）。
         *
         * 刻意不从当前样式 copy：上一个风格的底色会被带过来，明信片下半的
         * 文字显示区就会跟着变成深墨/黑色，与素材缩略图（预设原样渲染）完全对不上。
         * 明信片类预设的底色都声明为白色，只有素材本身设计成别的颜色时（如天空蓝）才不是。
         *
         * 圆角与背景不透明度仍沿用用户当前设置（与其他预设行一致），由调用方 copy 覆盖。
         */
        fun illustrationStyle(resName: String, current: WidgetStyle): WidgetStyle {
            val preset = PRESETS.firstOrNull { it.presetImageResName == resName }
                ?: return current.copy(
                    shape = WidgetShape.SPLIT_CARD,
                    presetImageResName = resName,
                    backgroundImagePath = null,
                    bgImageScaleMode = ImageScaleMode.CENTER_CROP
                )
            return preset.copy(backgroundImagePath = null)
        }

        // 首页与快捷面板共用的推荐预设（按风格分类）。
        // 插图类按资源名定位而不是按下标，避免在 PRESETS 中新增预设时索引漂移导致展示错位。
        val CLASSIC_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "纯色圆角" to PRESETS[0],
            "拟物撕纸" to PRESETS[1],
            "复古手账" to PRESETS[2],
            "羽毛信纸" to (PRESETS.firstOrNull { it.shape == WidgetShape.FEATHER_LETTER } ?: PRESETS[0]),
            "复古像素" to (PRESETS.firstOrNull { it.shape == WidgetShape.PIXEL_RETRO } ?: PRESETS[0]),
            "蓝色便签" to (PRESETS.firstOrNull { it.shape == WidgetShape.BLUE_NOTE } ?: PRESETS[0]),
            "撕边牛皮手账" to (PRESETS.firstOrNull { it.shape == WidgetShape.NIUPI_SHOUZHANG } ?: PRESETS[0]),
            "教室黑板" to (PRESETS.firstOrNull { it.shape == WidgetShape.CLASSROOM_BLACKBOARD } ?: PRESETS[0]),
            "巨剑" to (PRESETS.firstOrNull { it.shape == WidgetShape.GIANT_SWORD } ?: PRESETS[0]),
            "小霸王游戏机" to (PRESETS.firstOrNull { it.shape == WidgetShape.SUBOR_CONSOLE } ?: PRESETS[0]),
            "春天与小狗" to (PRESETS.firstOrNull { it.presetId == "p_spring_dog" } ?: PRESETS[0]),
            "纹理山水" to (PRESETS.firstOrNull { it.presetId == "p_texture_landscape" } ?: PRESETS[0]),
            "蜡笔彩虹框" to (PRESETS.firstOrNull { it.presetId == "p_crayon_frame" } ?: PRESETS[0]),
        )

        // 明信片行**首位**的整幅贴图预设：按产品要求排在明信片风格第一个。
        // 与 POSTCARD_RENDERED_PRESETS 同样按组件真实渲染缩略图——整幅铺贴 + 正文面板的观感，
        // 裁原图是看不出来的。与代码绘制组分开列，避免"这组都是代码画的"的说法被说破。
        val POSTCARD_LEADING_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "青年雕塑" to (PRESETS.firstOrNull { it.presetId == "p_youth_sculpture" } ?: PRESETS[0])
        )

        // 明信片风格行里的代码绘制预设：没有插图素材，由 WidgetCanvasRenderer 直接绘制整幅组件。
        // 与插图素材项同排展示，排在插图之前。缩略图尺寸不与桌面 4×3 挂钩：
        // 行内统一按 150×80 渲染（见 PostcardPresetsRow），组件内容在渲染时按画布自适应重排。
        val POSTCARD_CODE_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "蓝色画报" to (PRESETS.firstOrNull { it.presetId == "p_postcard_note" } ?: PRESETS[0]),
            "书香书架" to (PRESETS.firstOrNull { it.shape == WidgetShape.BOOKSHELF } ?: PRESETS[0])
        )

        // 明信片风格行里的「立体场景」预设：缩略图必须**按组件真实渲染**
        // （而非裁原图），否则形状/铺图方式的差异在缩略图上完全看不出来。按 presetId 定位，
        // 避免 PRESETS 新增条目时索引漂移。
        val POSTCARD_RENDERED_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "天气盒子" to (PRESETS.firstOrNull { it.presetId == "p_weather_box" } ?: PRESETS[0]),
            "雪落宫墙" to (PRESETS.firstOrNull { it.presetId == "p_winter_palace" } ?: PRESETS[0]),
            "深海鲸歌" to (PRESETS.firstOrNull { it.presetId == "p_deep_sea" } ?: PRESETS[0]),
            "夏天的海" to (PRESETS.firstOrNull { it.presetId == "p_summer_sea" } ?: PRESETS[0]),
            "夏日荷花" to (PRESETS.firstOrNull { it.presetId == "p_summer_lotus" } ?: PRESETS[0]),
            "信纸" to (PRESETS.firstOrNull { it.presetId == "p_ruled_paper" } ?: PRESETS[0])
        )

        // 萌宠风格：动物/角色类卡通插画
        val PET_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "得意doro" to (PRESETS.firstOrNull { it.presetImageResName == "deyi_doro" } ?: PRESETS[0]),
            "可爱猫咪" to (PRESETS.firstOrNull { it.presetImageResName == "cute_cat" } ?: PRESETS[0]),
            "毛绒小狗" to (PRESETS.firstOrNull { it.presetImageResName == "fluffy_dog" } ?: PRESETS[0]),
            "快乐小狗" to (PRESETS.firstOrNull { it.presetImageResName == "happy_dog" } ?: PRESETS[0]),
            "毛绒猫咪" to (PRESETS.firstOrNull { it.presetImageResName == "fluffy_cat" } ?: PRESETS[0]),
            "萌宠猫咪趴" to (PRESETS.firstOrNull { it.shape == WidgetShape.PET_CAT_NAP } ?: PRESETS[0]),
            "可爱四小只" to (PRESETS.firstOrNull { it.presetImageResName == "cute_four_kids" } ?: PRESETS[0]),
            "毛绒森林" to (PRESETS.firstOrNull { it.shape == WidgetShape.PLUSH_FOREST } ?: PRESETS[0]),
            "竹林熊猫" to (PRESETS.firstOrNull { it.presetId == "p_panda_bamboo" } ?: PRESETS[0]),
            "萌宠乐园" to (PRESETS.firstOrNull { it.presetId == "p_pet_park" } ?: PRESETS[0])
        )
    }
}
