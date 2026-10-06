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
    ZHU_QING_SI_ZHI("竹青撕纸"),
    NIUPI_SHOUZHANG("撕边牛皮手账"),
    CLASSROOM_BLACKBOARD("教室黑板"),
    BOOKSHELF("书香书架"),
    GIANT_SWORD("巨剑"),
    PLUSH_FOREST("毛绒森林"),
    SUBOR_CONSOLE("小霸王游戏机"),
    STICKER_SCENE("贴纸夜景"),
    CITY_CUTOUT("城市剪影"),
    WEATHER_BOX("天气盒子"),
    WINTER_PALACE("雪落宫墙"),
    DEEP_SEA("深海鲸歌"),
    SUMMER_SEA("夏天的海"),
    SUMMER_LOTUS("夏日荷花"),
    // 春天与小狗：绿框白卡 + 上沿草丛花枝 + 右上角探头柯基（整幅抠图素材）
    SPRING_DOG("春天与小狗")
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
    val lineSpacingMultiplier: Float = 1.0f,
    val letterSpacing: Float = 0f,
    // 预设身份标识：套用内置预设时携带，copy() 微调后依然保留，
    // 用于精确判定样式是否源自 PRO 预设（字段匹配可被微调绕过）
    val presetId: String? = null
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
                val parsed = WidgetStyle(
                    shape = safeEnum(json.optString("shape", WidgetShape.RECTANGLE.name), WidgetShape.RECTANGLE),
                    presetId = if (json.has("presetId") && !json.isNull("presetId")) {
                        json.optString("presetId").takeIf { it.isNotEmpty() }
                    } else {
                        null
                    },
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

        // 内置风格预设
        // 每条都带一个手写的固定 presetId（如 p_pure_round）作为身份标识：套用后随 copy() 保留，
        // 付费判定依据身份而不是字段值（字段匹配可被"改一个字段"绕过），也不依赖列表位置。
        val PRESETS: List<WidgetStyle> = listOf(
            // 免费默认风格：纯色圆角
            WidgetStyle(
                presetId = "p_pure_round",
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
                shape = WidgetShape.HANDBOOK_TAPE, // 手账胶带
                cornerRadiusDp = 12f,
                backgroundColor = Color.parseColor("#FCF6E5"),
                fontColor = Color.parseColor("#8A6F4E"),
                font = WidgetFont.LXGW_WENKAI,
                fontBold = true,
                showCardShadow = true,
                cardBorderWidthDp = 1.5f,
                cardBorderColor = Color.parseColor("#DCD0BA"),
                authorSignature = "—— 手账心情"
            ), // 5. 复古手账风格 (PRO)
            WidgetStyle(
                presetId = "p_postcard_note",
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
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.parseColor("#3A4D5C"),
                authorSignature = "—— 晨曦日出",
                presetImageResName = "bg_illustration_1"
            ), // 12. 晨曦画报风格 (PRO)
            WidgetStyle(
                presetId = "p_healing_sunset",
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.parseColor("#6B4C4C"),
                authorSignature = "—— 治愈落日",
                presetImageResName = "bg_illustration_2"
            ), // 13. 治愈画报风格 (PRO)
            WidgetStyle(
                presetId = "p_starry_forest",
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.parseColor("#1B2845"),
                authorSignature = "—— 星空森林",
                presetImageResName = "bg_illustration_3"
            ), // 14. 星空画报风格 (PRO)

            WidgetStyle(
                presetId = "p_sky_blue",
                shape = WidgetShape.RECTANGLE,
                backgroundColor = Color.parseColor("#1E6DD0"),
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontColor = Color.WHITE,
                authorSignature = "—— 天空之蓝",
                presetImageResName = "rectangle_1"
            ), // 15. 天空蓝风格 (PRO)
            WidgetStyle(
                presetId = "p_blue_sky_clouds",
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 蓝天白云",
                presetImageResName = "blue_sky_clouds"
            ), // 19. 蓝天白云风格 (PRO)
            WidgetStyle(
                presetId = "p_fight_club",
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 搏击俱乐部",
                presetImageResName = "boji_julebu"
            ), // 21. 搏击俱乐部风格 (PRO)
            WidgetStyle(
                presetId = "p_breaking_bad",
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 绝命毒师",
                presetImageResName = "breaking_bad"
            ), // 22. 绝命毒师风格 (PRO)
            WidgetStyle(
                presetId = "p_v_for_vendetta",
                shape = WidgetShape.SPLIT_CARD,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— V字仇杀队",
                presetImageResName = "v_for_vendetta"
            ), // 23. V字仇杀队风格 (PRO)
            WidgetStyle(
                presetId = "p_la_la_land",
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
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 毛绒小狗",
                presetImageResName = "fluffy_dog"
            ), // 毛绒小狗 (PRO)
            WidgetStyle(
                presetId = "p_happy_dog",
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
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 毛绒猫咪",
                presetImageResName = "fluffy_cat"
            ), // 毛绒猫咪 (PRO)
            WidgetStyle(
                presetId = "p_happy_daily",
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                font = WidgetFont.LXGW_WENKAI,
                fontColor = Color.parseColor("#374151"),
                authorSignature = "—— 天天开心",
                presetImageResName = "happy_daily"
            ), // 天天开心 (PRO)

            // 羽毛信纸 (PRO)：信纸即卡片(信纸外透明透桌面)，文字落信纸内部
            WidgetStyle(
                presetId = "p_feather_letter",
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
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                backgroundColor = Color.WHITE,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT, // 四小只头像为透明圆形抠图，等比完整显示不被裁切
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
            // 竹青撕纸 (PRO)：米白锯齿撕纸即卡片(外透明透桌面)，文字落纸面中部
            WidgetStyle(
                presetId = "p_zhu_qing_si_zhi",
                shape = WidgetShape.ZHU_QING_SI_ZHI,
                cornerRadiusDp = 8f,
                backgroundColor = android.graphics.Color.TRANSPARENT,
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.STRETCH,
                font = WidgetFont.SOURCE_HAN_SERIF,
                fontSizeSp = 20f,
                fontColor = Color.parseColor("#3A4A3A"),
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "zhu_qing_si_zhi",
                authorSignature = "—— 竹青撕纸"
            ), // 33. 竹青撕纸 (PRO)
            // 撕边牛皮手账 (PRO)：牛皮纸撕边即卡片(外透明透桌面)，文字落纸面中部
            WidgetStyle(
                presetId = "p_niupi_shouzhang",
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
                authorSignature = "—— 毛绒森林"
            ), // 40. 毛绒森林 (PRO)
            // 小霸王游戏机 (PRO)：3D 渲染的实物模型——机身居中、左右各一只手柄，正文落在机身屏幕上
            WidgetStyle(
                presetId = "p_subor_console",
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
            // 贴纸夜景 (PRO)：人物与路灯做成带白边的贴纸，站在一层路面上，路面之下是文本框
            WidgetStyle(
                presetId = "p_sticker_lalaland",
                shape = WidgetShape.STICKER_SCENE,
                // 文本框圆角：0 = 四角斜切一刀的剪纸直角；调大后四角改圆弧（见 paperCutBoxPath）
                cornerRadiusDp = 0f,
                backgroundColor = Color.WHITE, // 文本框底色，可随「小组件背景颜色」自定义
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                // 贴纸按等比完整显示：4×2 / 4×4 都不会被拉伸或裁切
                bgImageScaleMode = ImageScaleMode.CENTER_FIT,
                font = WidgetFont.DEFAULT,
                fontSizeSp = 17f,
                fontColor = Color.parseColor("#2F3542"),
                textAlign = "CENTER",
                showCardShadow = true,
                cardBorderWidthDp = 0f,
                presetImageResName = "sticker_lalaland",
                authorSignature = "—— 贴纸夜景"
            ), // 42. 贴纸夜景 (PRO)
            // 城市微缩（CITY_CUTOUT）一族：抠掉天空的微缩城市按原比例摆进组件，
            // 天空透明处露出壁纸。**一个风格一张素材**，没有按尺寸换素材那套机制。
            // 城市与文字区怎么接由渲染器按素材名认领（见 cityJunctionOf），各风格各一套：
            // 上海走江面倒影，认不出来的走柔和暗裙。
            // 圆角滑条统一作用在文字栏底部两角。
            // 「浙江」：微缩浙江做城市剪影，落到青黛色文字栏上
            WidgetStyle(
                presetId = "p_zhejiang_cutout",
                shape = WidgetShape.CITY_CUTOUT,
                cornerRadiusDp = 12f,
                // 青黛色文字栏：压在城郭之下，配浙江的青山绿水，冷绿底衬暖白字
                backgroundColor = Color.parseColor("#1E3A32"),
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#EAF3EC"),
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "zhejiang_city_cutout",
                authorSignature = "—— 浙江剪影"
            ), // 44. 浙江剪影 (PRO)
            // 北京微缩：同样是城市微缩（路线 E），但衔接走 FADE，且文字栏直接取**模型底座自己的
            // 米杏色**（#FAF0D9，从素材底座采样）。于是"底座 → 文字栏"连成一片、没有分界，
            // 读起来就是这座微缩北京长在纸上、题词就写在底座上。
            WidgetStyle(
                presetId = "p_beijing_cutout",
                shape = WidgetShape.CITY_CUTOUT,
                cornerRadiusDp = 12f,
                backgroundColor = Color.parseColor("#FAF0D9"),
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                // 暖褐色字：米杏底上的"墨色"，与底座的金色描边、朱红宫墙同一暖调
                fontColor = Color.parseColor("#4A3B2B"),
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "beijing_city_cutout",
                authorSignature = "—— 北京微缩"
            ), // 48. 北京微缩 (PRO)
            // 天气盒子 (PRO)：白色盒体正面挖出一个内凹的方形小腔，腔底是蓝天微缩城市
            // （云/太阳/楼群），腔体四周留白即正文区。盒体、腔体内凹的暗角与高光全部由
            // 渲染器按当前组件尺寸现算，素材只是腔底那一块画面，因此 4×2 / 4×4 都不会拉伸。
            WidgetStyle(
                presetId = "p_weather_box",
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
            // 上海微缩 (PRO)：微缩上海（外滩 + 黄浦江 + 陆家嘴）整块抠图，
            // 走路线 E 的 **WATER** 衔接（cityJunctionOf 按素材名认领）——
            // 城市底边就是水线，下面接倒影 + 波纹 + 渐入深水，文字浮在江面上。
            // 文字栏底色 = 那池"深水"（素材水体是深蓝，这里取同色系更深的蓝）。
            WidgetStyle(
                presetId = "p_shanghai_cutout",
                shape = WidgetShape.CITY_CUTOUT,
                cornerRadiusDp = 12f,
                backgroundColor = Color.parseColor("#16293D"), // 深水色：与素材水体同色系
                backgroundOpacity = 1f,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP,
                font = WidgetFont.LXGW_WENKAI,
                fontSizeSp = 18f,
                fontColor = Color.parseColor("#E6EEF6"), // 冷白题字：压深水江面
                textAlign = "CENTER",
                showCardShadow = false,
                cardBorderWidthDp = 0f,
                presetImageResName = "shanghai_city_cutout",
                authorSignature = "—— 上海微缩"
            ) // 55. 上海微缩 (PRO)
        )

        // 免费预设：按身份 id 判定，未激活用户可直接套用。
        // 当前规则：只有「纯色圆角」免费，其余风格一律会员专属。
        // 字体、字号、颜色、圆角、不透明度等细节调整不在此列，全部免费。
        private val FREE_PRESET_IDS = setOf(
            "p_pure_round"    // 纯色圆角
        )

        // 主体四周透明的形状：这些形状的插画/装饰并不铺满整个组件位图，背景色只会在主体外围
        // 露出一圈圆角卡片（信纸/撕纸/牛皮/猫咪趴/书架），因此不支持设置背景色。
        // 与「外框圆角」的禁用名单不同（撕裂纸片圆角不可调，但背景色就是它的纸面颜色，仍可用）。
        private val SHAPES_WITHOUT_BACKGROUND_COLOR = setOf(
            WidgetShape.FEATHER_LETTER,
            WidgetShape.PET_CAT_NAP,
            WidgetShape.ZHU_QING_SI_ZHI,
            WidgetShape.NIUPI_SHOUZHANG,
            WidgetShape.BOOKSHELF,
            WidgetShape.GIANT_SWORD,
            WidgetShape.PLUSH_FOREST,
            WidgetShape.SUBOR_CONSOLE,
            // 春天与小狗：绿框白卡是素材本身的一部分，卡外透明处透出壁纸；
            // 再铺一层背景色会在卡外露出一圈色块
            WidgetShape.SPRING_DOG
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
            "blue_sky_clouds" to "蓝天白云",
            "boji_julebu" to "搏击俱乐部",
            "breaking_bad" to "绝命毒师",
            "v_for_vendetta" to "V字仇杀队",
            "aile_zhi_cheng" to "爱乐之城"
        )

        // 首页与快捷面板共用的推荐预设（按风格分类）。
        // 插图类按资源名定位而不是按下标，避免在 PRESETS 中新增预设时索引漂移导致展示错位。
        val CLASSIC_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "纯色圆角" to PRESETS[0],
            "拟物撕纸" to PRESETS[1],
            "复古手账" to PRESETS[2],
            "天天开心" to (PRESETS.firstOrNull { it.presetImageResName == "happy_daily" } ?: PRESETS[0]),
            "羽毛信纸" to (PRESETS.firstOrNull { it.shape == WidgetShape.FEATHER_LETTER } ?: PRESETS[0]),
            "复古像素" to (PRESETS.firstOrNull { it.shape == WidgetShape.PIXEL_RETRO } ?: PRESETS[0]),
            "蓝色便签" to (PRESETS.firstOrNull { it.shape == WidgetShape.BLUE_NOTE } ?: PRESETS[0]),
            "竹青撕纸" to (PRESETS.firstOrNull { it.shape == WidgetShape.ZHU_QING_SI_ZHI } ?: PRESETS[0]),
            "撕边牛皮手账" to (PRESETS.firstOrNull { it.shape == WidgetShape.NIUPI_SHOUZHANG } ?: PRESETS[0]),
            "教室黑板" to (PRESETS.firstOrNull { it.shape == WidgetShape.CLASSROOM_BLACKBOARD } ?: PRESETS[0]),
            "巨剑" to (PRESETS.firstOrNull { it.shape == WidgetShape.GIANT_SWORD } ?: PRESETS[0]),
            "小霸王游戏机" to (PRESETS.firstOrNull { it.shape == WidgetShape.SUBOR_CONSOLE } ?: PRESETS[0]),
            "贴纸夜景" to (PRESETS.firstOrNull { it.shape == WidgetShape.STICKER_SCENE } ?: PRESETS[0]),
            "春天与小狗" to (PRESETS.firstOrNull { it.presetId == "p_spring_dog" } ?: PRESETS[0]),
        )

        // 明信片风格行里的代码绘制预设：没有插图素材，由 WidgetCanvasRenderer 直接绘制整幅组件，
        // 按桌面 4×3 规格以 4:3 比例预览。与插图素材项同排展示，排在插图之前。
        val POSTCARD_CODE_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "蓝色画报" to (PRESETS.firstOrNull { it.presetId == "p_postcard_note" } ?: PRESETS[0]),
            "书香书架" to (PRESETS.firstOrNull { it.shape == WidgetShape.BOOKSHELF } ?: PRESETS[0])
        )

        // 代码绘制预设的缩略图渲染尺寸：按桌面 4×3 的设计尺寸渲染后再缩小显示，
        // 使缩略图里的书脊/文本面板比例与桌面组件一致
        const val POSTCARD_CODE_RENDER_WIDTH_DP = 240
        const val POSTCARD_CODE_RENDER_HEIGHT_DP = 180
        // 天气盒子固定按 4×4 渲染：素材是横幅（2.09:1），在扁组件上腔体只能居中、
        // 两侧留白过多且正文被压扁；竖版才能把「蓝天腔体 + 下方留白」的比例拉舒服
        const val WEATHER_BOX_RENDER_WIDTH_DP = 240
        const val WEATHER_BOX_RENDER_HEIGHT_DP = 240

        // 明信片风格行里的「微缩城市 / 立体场景」预设：缩略图必须**按组件真实渲染**
        // （而非裁原图），否则形状/铺图方式的差异在缩略图上完全看不出来。按 presetId 定位，
        // 避免 PRESETS 新增条目时索引漂移。
        // 天气盒子是纯代码绘制的立体盒（白盒体 + 内凹腔体），素材比例锁死为横幅，
        // 在 4×2 这类扁组件上腔体只能居中、两侧留白过多，故归到明信片行按 4×4 渲染。
        val POSTCARD_RENDERED_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
            "浙江剪影" to (PRESETS.firstOrNull { it.presetId == "p_zhejiang_cutout" } ?: PRESETS[0]),
            "北京微缩" to (PRESETS.firstOrNull { it.presetId == "p_beijing_cutout" } ?: PRESETS[0]),
            "天气盒子" to (PRESETS.firstOrNull { it.presetId == "p_weather_box" } ?: PRESETS[0]),
            "雪落宫墙" to (PRESETS.firstOrNull { it.presetId == "p_winter_palace" } ?: PRESETS[0]),
            "深海鲸歌" to (PRESETS.firstOrNull { it.presetId == "p_deep_sea" } ?: PRESETS[0]),
            "夏天的海" to (PRESETS.firstOrNull { it.presetId == "p_summer_sea" } ?: PRESETS[0]),
            "夏日荷花" to (PRESETS.firstOrNull { it.presetId == "p_summer_lotus" } ?: PRESETS[0]),
            "上海微缩" to (PRESETS.firstOrNull { it.presetId == "p_shanghai_cutout" } ?: PRESETS[0])
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
            "毛绒森林" to (PRESETS.firstOrNull { it.shape == WidgetShape.PLUSH_FOREST } ?: PRESETS[0])
        )
    }
}
