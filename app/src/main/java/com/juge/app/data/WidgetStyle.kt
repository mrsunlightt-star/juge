package com.juge.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import java.util.Locale

// 预定义字体枚举
enum class WidgetFont(
    val fontPath: String?,
    val displayName: String,
    // 纯拉丁字体：没有汉字字形，中文靠系统字体回退。选项名与界面提示据此区分，
    // 避免用户选了「英文衬线」却发现中文金句毫无变化。
    val latinOnly: Boolean = false,
) {
    DEFAULT(null, "系统默认"),
    SOURCE_HAN_SANS("fonts/SourceHanSansCN-Regular.otf", "思源黑体"),
    SOURCE_HAN_SERIF("fonts/SourceHanSerifCN-Regular.otf", "思源宋体"),
    LXGW_NEO_ZHISONG("fonts/LXGWNeoZhiSong.ttf", "霞鹜新致宋"),
    LXGW_NEO_XIHEI_SCREEN("fonts/LXGWNeoXiHeiScreen.ttf", "霞鹜新晰黑 Screen"),
    // 枚举名保持 LXGW_WENKAI 不变：旧存档按 name 序列化，改名会让老用户字体选择回退为默认
    LXGW_WENKAI("fonts/LXGWWenKai-Regular.ttf", "霞鹜文楷"),
    MASHAN_ZHENG("fonts/MaShanZheng-Regular.ttf", "毛笔楷书"),
    // 台湾繁体圆体，简体字库覆盖有限（约 5528/8968），冷僻与部分常用简体字会回退系统字体
    JF_OPEN_HUNINN("fonts/JFOpenHuninn-Regular.ttf", "jf open 粉圆"),
    // 以下两款是纯拉丁字体（OFL 1.1，子集后合计约 200KB）：只覆盖 ASCII 与西欧字母，
    // 中文金句仍回退系统字体，所以显示名统一带「英文」前缀。
    // 新增枚举一律追加在末尾——枚举顺序即字体选项顺序，插到中间会改变老用户看到的排布。
    EB_GARAMOND("fonts/EBGaramond-Regular.ttf", "英文衬线 Garamond", latinOnly = true),
    CAVEAT("fonts/Caveat-Regular.ttf", "英文手写 Caveat", latinOnly = true);

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
    BLUE_NOTE("蓝色便签"),
    NIUPI_SHOUZHANG("撕边牛皮手账"),
    CLASSROOM_BLACKBOARD("教室黑板"),
    BOOKSHELF("书香书架"),
    PLUSH_FOREST("毛绒森林"),
    SUBOR_CONSOLE("小霸王游戏机"),
    WEATHER_BOX("天气盒子"),
    WINTER_PALACE("雪落宫墙"),
    DEEP_SEA("深海鲸歌"),
    SUMMER_SEA("夏天的海"),
    SUMMER_LOTUS("夏日荷花"),
    // 春天与小狗：绿框白卡 + 上沿草丛花枝 + 右上角探头柯基（整幅抠图素材）
    SPRING_DOG("春天与小狗"),
    // 萌宠乐园：整幅毛毡风插画——粉色花边毛毡框 + 框顶六只毛毡小动物（猫狗兔鼠狐熊猫），
    // 框内奶油毛毡面板放正文。素材原稿是纯白底（同样被压成无 alpha 的 RGB），已抠掉。
    PET_PARK("萌宠乐园"),
    // 可爱四小只（2026-10-07 改版）：四个圆形头像**横排**在卡片左下角（原来是左侧 2×2 网格），
    // 正文落在头像上方整幅留白区。头像方块几何取自设计稿 1824×912：
    // 每块 170×151、1px #C9C9C9 描边、圆角 3，横排于 x 49/239/429/619、y 689。
    CUTE_FOUR_KIDS("可爱四小只"),
    // 青年雕塑（2026-10-21 改版）：整幅明信片——纯白卡面 + 上方约 52% 的雕塑图
    //（内嵌 1920×1280 原图，肩部在 52% 处裁齐），下方整片留白放正文。素材 2048×2048。
    // presetId 保持不变，已落桌面/已保存的旧组件读档时直接跟到新造型，
    // 不必重新套用预设。
    // ⚠️ 这是一款 **4×4 专属**风格：设计稿就是 250×250 方版，按产品决定**不出 4×2 版**。
    // 它在 4×2 组件上会被横向拉宽约 97%，这是已知且被接受的，**不要**给它补横版素材。
    YOUTH_SCULPTURE("青年雕塑"),
    // 蜡笔彩虹框：整幅手绘素材——蓝/绿/粉三色蜡笔波浪描边 + 框内近白纸面，
    // 左上角一颗红蜡笔爱心、右下角一只黄蜡笔气球。素材原稿 2048×1152 是 16:9 画布、
    // 框只占中间一条（四周透明留白 7%~27%），入库前已按内容框裁到 1826×627。
    CRAYON_FRAME("蜡笔彩虹框"),
    // 跨栏小子：明信片——上半是跨栏少年 + 栏杆的**真透明抠图**（不规则形状，透出壁纸），
    // 下半是素材自带的白色记分牌正文框（素材 1536×1536：牌面 x 186~1356 / y 670~1405）。
    // 牌面与牌角圆角都在素材里，不铺背景色；牌角本身是圆角，不强制直角。
    HURDLE_BOY("跨栏小子"),
    // 可爱Doro：萌宠——原生 2048×2048 方形构图（粉发毛绒角色 + 奶油色留言板），
    // 角色在上、留言板占下方约 42%~94%，四周透明；正文直接写在留言板里。
    // 2026-10-21 用设计师新出的方形版替换了原来的 2:1 裁切放大版，不再需要裁两侧；
    // 2026-10-22 换成同构图的 2048×2048 高清版。
    CUTE_DORO("可爱Doro"),
    // 蓝色鲸鱼：4×2 萌宠卡——浅灰圆角卡 + #4998CD 描边 + 右下角小鲸鱼，
    // 卡面与描边都烘在素材里（设计稿 250×110），右上/左上留白放正文。
    BLUE_WHALE("蓝色鲸鱼"),
    // 春天到了：2048×2048 真透明萌宠花框，中央自带浅色卡面，正文直接落在卡面内。
    SPRING_ARRIVAL("春天到了"),
    // 太空小狗：2048×2048 真透明萌宠花框，中央是透明窗口；代码补一层浅色承托层保证正文可读。
    SPACE_DOG("太空小狗"),
    // Q萌海盗团：2048×2048 真透明萌宠花框，中央是透明窗口；同样由代码补正文承托层。
    Q_PIRATE_CREW("Q萌海盗团"),
    // 中华田园犬：2176×1088 真透明萌宠花框，黄色骨头/爪印边框 + 顶部探头小狗；
    // 中央透明窗口直接透出壁纸，默认 4×2。
    CHINESE_RURAL_DOG("中华田园犬"),
    // 熊猫宝宝：2176×1088 真透明萌宠花框，竹节边框 + 顶部趴着的熊猫；
    // 框内自带米色纸面承载正文，默认 4×2。
    PANDA_BABY("熊猫宝宝"),
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

/**
 * 文字阴影设置。
 *
 * 标 [Immutable]：这是个 flat 的值对象（五个基本类型字段），Compose 编译器
 * 默认会对「不在本模块内声明」的类保守处理；加上注解后，[WidgetStyle] 作为
 * 参数传递时才能被判定为稳定类型，跳过不必要的重组。
 */
@Immutable
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

/**
 * 整体组件风格配置。
 *
 * 标 [Immutable] 是这个类能否参与 Compose 跳过重组的**前提**：它有 29 个字段、
 * 其中还带一个 `List<Int>?`（[gradientColors]），编译器无法自行证明其稳定性，
 * 于是把 [WidgetStyle] 当参数的组件（AdjustTabContent / WidgetPreviewPager /
 * PresetRow…）在父级每次重组时都全量重跑。
 *
 * 这个承诺是成立的：所有字段都是 val，`List<Int>?` 也只在这里读不写
 * （构造时传进来的列表不会被本类修改）。要改成可变集合时请同步去掉注解。
 */
@Immutable
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
    /**
     * 垂直对齐：CENTER（默认，文本在安全区内垂直居中）/ TOP（从安全区最顶端开始向下排）。
     *
     * 老存档没有这个字段时读成 CENTER，保证升级后排版与旧版完全一致；
     * 只有用户在「个性定制 → 文本内容与字形定制 → 垂直位置」显式选择顶部对齐才会变成 TOP。
     */
    val verticalAlign: String = "CENTER", // CENTER, TOP
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
     * 主体四周透明的形状（羽毛信纸/撕纸/牛皮/书架）不支持背景色：
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
        append(fontColor).append('|').append(textAlign).append('|').append(verticalAlign).append('|')
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
            put("verticalAlign", verticalAlign)
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
            if (jsonStr.isNullOrEmpty()) return DEFAULT
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
                    // 未知值（旧版本/手改数据）一律降级为 CENTER，与旧版排版一致
                    verticalAlign = when (json.optString("verticalAlign", "CENTER").uppercase(Locale.ROOT)) {
                        "TOP" -> "TOP"
                        else -> "CENTER"
                    },
                    shadow = if (json.has("shadow")) {
                        TextShadow.fromJson(json.getJSONObject("shadow"))
                    } else {
                        TextShadow()
                    },
                    // 反序列化新增字段并兼容处理默认值
                    // gradientColors 逐元素尽力解析：数组里混入一个非整型（旧版本/手改数据）
                    // 只丢掉颜色数组这一项，不再让整个 catch 把全份样式替换成默认
                    gradientColors = if (json.has("gradientColors") && !json.isNull("gradientColors")) {
                        runCatching {
                            val arr = json.getJSONArray("gradientColors")
                            List(arr.length()) { arr.getInt(it) }
                        }.getOrNull()
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
                parsed.caughtUpWithPreset()
                    // 2026-10-24 按产品要求所有组件停用背景渐变 / 背景模糊 / 背景压暗：
                    // 读档一律清零，存档里遗留的取值不再生效（渲染通路与字段保留，仅作
                    // JSON 兼容；预设也已全部不声明这三项）。指纹串仍包含它们，取值恒定
                    // 不影响缩略图缓存命中。
                    .copy(gradientColors = null, bgBlurRadius = 0f, bgScrimAlpha = 0f)
            } catch (e: Exception) {
                DEFAULT
            }
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

        /**
         * 预设改版记录：一款预设被重新设计后，**上一版**的形状与铺图方式。
         *
         * 只用来识别旧存档——当前预设定义里已经不再有这些取值，命中即说明这份存档
         * 来自改版之前。新增一条 = 某款预设改到了「形状 / 铺图方式」这一层，
         * 需要已落桌面的旧组件跟着走（见 [caughtUpWithPreset]）。
         */
        private data class PresetLegacyLook(
            val presetId: String,
            /** 上一版的形状；只作为识别特征，不再被任何预设使用 */
            val shape: WidgetShape,
            /** 上一版的铺图方式；null 表示这次改版没动它，不参与迁移 */
            val bgImageScaleMode: ImageScaleMode? = null
        )

        private val PRESET_LEGACY_LOOKS = listOf(
            // 可爱四小只 2026-10-07 改版：四个头像从「左侧 2×2 网格」的左右分割卡
            // （SPLIT_CARD_HORIZONTAL + 等比完整显示）改成「左下角横排一排」
            // （CUTE_FOUR_KIDS + 拉伸铺满整卡）。presetId 不变，老组件靠这条迁移跟上。
            PresetLegacyLook(
                presetId = "p_cute_four_kids",
                shape = WidgetShape.SPLIT_CARD_HORIZONTAL,
                bgImageScaleMode = ImageScaleMode.CENTER_FIT
            ),
        )

        /**
         * 已下架预设的就近替代款（旧 presetId → 替代预设的 presetId）。
         *
         * 预设从列表删除后，已保存 / 已落桌面的旧样式副本还拿着旧 id：渲染器按存档字段
         * 兜底渲染会得到「隐形卡片 + 悬浮文字」（如萌宠猫咪趴：形状枚举已删、底色透明），
         * 引用到的素材文件也已随下架删除。读档时（[caughtUpWithPreset]）整份换成替代预设，
         * 只保留用户可调的圆角与背景不透明度。
         */
        private val REMOVED_PRESET_FALLBACKS = mapOf(
            // 萌宠猫咪趴、可爱猫咪 2026-10-24 下架 → 同为萌宠主题的毛绒小狗
            "p_pet_cat_nap" to "p_fluffy_dog",
            "p_cute_cat" to "p_fluffy_dog",
            // 信纸 2026-10-24 下架 → 观感最接近的白底居中卡片（纯色圆角，免费款）
            "p_ruled_paper" to "p_pure_round",
            // 2026-11-16 下架：旧组件整份迁移到观感最接近的现有预设，避免素材删除后渲染成透明坏卡
            "p_sky_blue" to "p_pure_round",
            "p_giant_sword" to "p_spring_dog",
            "p_texture_landscape" to "p_pure_round",
            "p_happy_dog" to "p_fluffy_dog",
            "p_panda_bamboo" to "p_pet_park",
            "p_boy_room" to "p_youth_sculpture",
            "p_postcard_note" to "p_bookshelf",
            "p_v_for_vendetta" to "p_fight_club",
            "p_la_la_land" to "p_fight_club",
            // 2026-11-17 下架：旧组件迁移到同形象家族的现有预设
            "p_fluffy_cat" to "p_fluffy_dog",
            "p_keep_smiling" to "p_pure_round",
            "p_after_school" to "p_youth_sculpture",
        )

        /**
         * 预设改版迁移（读档时调用）：把已保存 / 已落桌面的旧样式副本追上当前预设定义。
         *
         * 为什么需要它：`safeEnum` 只兜「字段值已经不合法」（如形状被下架），兜不住
         * 「字段合法但过时」。预设改版后，桌面上的老组件会拿着旧形状去渲染**新素材**——
         * 可爱四小只就是这么坏掉的：存档里还是左右分割卡，素材却已换成头像条。
         *
         * 字段的迁移边界不同，差别就在于**用户能不能自己调**：
         * - **形状**不是用户可调项（界面上没有形状选择器，形状只随预设来），
         *   存档里只要还是上一版的形状，就一定是过时的，直接换成当前预设的形状；
         * - **铺图方式**用户可调，仅在形状尚未迁移且铺图方式仍为旧预设值时追平；
         * - **方版素材字段**（presetImageResNameSquare）用户不可调、又是后加的字段，
         *   旧存档没有它就会在 4×4 上继续用横版素材（纵向拉 76%），从当前预设补齐即可；
         * - **投影 / 描边**（showCardShadow、cardBorderWidthDp、cardBorderColor）用户不可调，
         *   始终以当前预设为准：纯色圆角去掉了这两项之后，桌面上已落位的旧组件读档即跟随，
         *   不会继续带着灰边和 4dp 内缩。
         *
         * 找不到 presetId 对应的预设时：已下架的走 [REMOVED_PRESET_FALLBACKS] 换成替代款，
         * 用户的自定义样式（无 id 或 id 不认识）整体跳过。
         */
        private fun WidgetStyle.caughtUpWithPreset(): WidgetStyle {
            val id = presetId ?: return this
            // 已下架预设：整份换成替代预设，只保留用户可调的圆角与背景不透明度
            REMOVED_PRESET_FALLBACKS[id]?.let { fallbackId ->
                val replacement = PRESETS.firstOrNull { it.presetId == fallbackId }
                    ?: return this
                return replacement.copy(
                    cornerRadiusDp = cornerRadiusDp,
                    backgroundOpacity = backgroundOpacity
                )
            }
            val current = PRESETS.firstOrNull { it.presetId == id } ?: return this
            var out = this.copy(
                showCardShadow = current.showCardShadow,
                cardBorderWidthDp = current.cardBorderWidthDp,
                cardBorderColor = current.cardBorderColor
            )
            PRESET_LEGACY_LOOKS.firstOrNull { it.presetId == id && it.shape == out.shape }?.let { legacy ->
                out = out.copy(shape = current.shape)
                val legacyMode = legacy.bgImageScaleMode
                if (legacyMode != null &&
                    out.bgImageScaleMode == legacyMode &&
                    current.bgImageScaleMode != legacyMode
                ) {
                    out = out.copy(bgImageScaleMode = current.bgImageScaleMode)
                }
            }
            if (out.presetImageResNameSquare == null) {
                out = out.copy(presetImageResNameSquare = current.presetImageResNameSquare)
            }
            return out
        }

        // ── 内置风格目录已拆到 WidgetStyleCatalog（见 WidgetStyleCatalog.kt）──────
        // 这里保留同名入口，调用点无需改动。

        val PRESETS: List<WidgetStyle> get() = WidgetStyleCatalog.PRESETS

        val DEFAULT: WidgetStyle get() = WidgetStyleCatalog.DEFAULT

        fun supportsBackgroundColor(shape: WidgetShape): Boolean =
            WidgetStyleCatalog.supportsBackgroundColor(shape)

        fun isProPreset(style: WidgetStyle): Boolean = WidgetStyleCatalog.isProPreset(style)

        val ILLUSTRATION_PRESETS: List<Pair<String, String>> get() = WidgetStyleCatalog.ILLUSTRATION_PRESETS

        fun illustrationStyle(resName: String, current: WidgetStyle): WidgetStyle =
            WidgetStyleCatalog.illustrationStyle(resName, current)

        val CLASSIC_PRESETS: List<Pair<String, WidgetStyle>> get() = WidgetStyleCatalog.CLASSIC_PRESETS

        val POSTCARD_LEADING_PRESETS: List<Pair<String, WidgetStyle>> get() = WidgetStyleCatalog.POSTCARD_LEADING_PRESETS

        val POSTCARD_CODE_PRESETS: List<Pair<String, WidgetStyle>> get() = WidgetStyleCatalog.POSTCARD_CODE_PRESETS

        val POSTCARD_RENDERED_PRESETS: List<Pair<String, WidgetStyle>> get() = WidgetStyleCatalog.POSTCARD_RENDERED_PRESETS

        val PET_PRESETS: List<Pair<String, WidgetStyle>> get() = WidgetStyleCatalog.PET_PRESETS
    }
}
