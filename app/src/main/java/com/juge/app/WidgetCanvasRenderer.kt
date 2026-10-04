package com.juge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.LinearGradient
import android.graphics.Matrix
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
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.Random

object WidgetCanvasRenderer {

    private const val RENDER_DENSITY_SCALE = 1.5f
    private const val MIN_BITMAP_SIZE = 300
    private const val SPLIT_CARD_RATIO = 0.48f
    private const val SPLIT_CARD_HORIZONTAL_RATIO = 0.333f
    // 检测近白相框阈值：RGB 均大于该值视为“留白像素”
    private const val LIGHT_BORDER_THRESHOLD = 228
    private const val TORN_SEED = 42L
    private const val TEXTURE_SEED = 2026L
    private const val FONT_SIZE_SCALE = 1.2f
    private const val QUOTE_MARK_FONT_SIZE_SCALE = 3.5f
    private const val DEFAULT_OUTER_CORNER_RADIUS_DP = 16f
    // 卡片四周留出的内边距：让卡片不铺满整幅组件位图，从而给投影留出可见空间。
    // 阴影绘制在位图内部，若卡片满幅则阴影会被位图边界裁掉，组件看起来就是"贴平"的。
    private const val CARD_INSET_DP = 4f
    private const val QUOTE_ALPHA = 25
    private const val TORN_BORDER_ALPHA = 220
    private const val TAPE_LINE_ALPHA = 45

    // 书香书架纵向几何：书籍区（书脊 + 横板）高度固定为 dp，不随组件变高而拉伸，
    // 组件多出来的高度全部留给下方摘录面板，因此尺寸变大时只有文本区域变大。
    // 组件高度不足时优先压缩书籍区，保证面板至少能显示正文。
    private const val SHELF_BOOK_BAND_HEIGHT_DP = 90f // 组件顶部到横板上沿
    private const val SHELF_BOARD_HEIGHT_DP = 4f // 横板厚度
    private const val SHELF_BOOK_TOP_MARGIN_DP = 6f // 书脊顶部与组件顶部的留白
    private const val SHELF_PANEL_GAP_DP = 6f // 横板下沿到面板上沿
    private const val SHELF_PANEL_BOTTOM_MARGIN_DP = 6f // 面板下沿到组件底部
    private const val SHELF_PANEL_MIN_HEIGHT_DP = 54f // 面板最小高度
    private const val SHELF_PANEL_MIN_BAND_RATIO = 0.35f // 组件过矮时书籍区的最小占比
    private const val SHELF_PANEL_TEXT_PAD_X_DP = 14f // 面板内正文左右留白
    private const val SHELF_PANEL_TEXT_PAD_Y_DP = 6f // 面板内正文上下留白
    // 横向仍按比例：书架横向铺满组件宽度
    private const val SHELF_BOARD_LEFT_RATIO = 0.035f
    private const val SHELF_BOARD_RIGHT_RATIO = 0.965f
    private const val SHELF_BOOKS_LEFT_RATIO = 0.07f
    private const val SHELF_BOOKS_RIGHT_RATIO = 0.93f
    private const val SHELF_PANEL_LEFT_RATIO = 0.055f
    private const val SHELF_PANEL_RIGHT_RATIO = 0.945f
    // 书脊竖排书名的行距倍数：略大于字号即可，保持字符紧凑而不铺满整条书脊
    private const val BOOK_TITLE_LINE_STEP_RATIO = 1.06f

    // 小霸王游戏机：subor_console.png 的宽高比，以及机身屏幕（文本区）在素材中的相对位置，
    // 均由出图/合成时测得。素材换成新图后需同步更新这几个比例。
    // 素材四周预留了透明留白，避免 4×2 这类偏宽组件里机身/手柄贴住组件上下边缘
    private const val SUBOR_IMAGE_ASPECT = 1.6043f
    private const val SUBOR_SCREEN_LEFT_RATIO = 0.2893f
    private const val SUBOR_SCREEN_RIGHT_RATIO = 0.7173f
    private const val SUBOR_SCREEN_TOP_RATIO = 0.1176f
    private const val SUBOR_SCREEN_BOTTOM_RATIO = 0.5882f
    private const val SUBOR_SCREEN_TEXT_PAD_X_DP = 6f
    private const val SUBOR_SCREEN_TEXT_PAD_Y_DP = 4f
    // 通电显像管的荧光屏配色：屏幕永远是绿色荧光（模拟已开机），与用户可调的字色无关
    private val SUBOR_SCREEN_CORE_COLOR = 0xFF46A857.toInt() // 中心亮绿
    private val SUBOR_SCREEN_MID_COLOR = 0xFF1C6B2C.toInt()
    private val SUBOR_SCREEN_EDGE_COLOR = 0xFF04140A.toInt() // 边缘近黑，形成曲面暗角
    private const val SUBOR_GLOW_RADIUS_DP = 7f
    private const val SUBOR_SCREEN_FILL_ALPHA = 236
    private const val SUBOR_SCANLINE_SPACING_DP = 2.6f
    private const val SUBOR_SCANLINE_ALPHA = 44
    private const val SUBOR_GLASS_SHEEN_ALPHA = 26

    // 贴纸夜景：人物与路灯直接站在文本框这张"纸"的上沿，花枝垂在文本框下沿。
    // 各比例均相对组件位图，4×2 与 4×4 共用同一套，只按高度缩放。
    private const val STICKER_ART_ASPECT = 1293f / 1200f // 贴纸素材(人物+路灯)宽高比
    private const val STICKER_ART_HEIGHT_RATIO = 0.60f // 贴纸高度占组件高度
    private const val STICKER_ART_SINK_RATIO = 0.015f // 贴纸底部下沉量：白边压进纸里，看起来才像站在纸上
    private const val STICKER_GROUND_RATIO = 0.60f // 落地线 = 文本框上沿 = 贴纸脚底
    private const val STICKER_TEXT_TOP_RATIO = 0.60f
    private const val STICKER_TEXT_BOTTOM_RATIO = 0.90f
    private const val STICKER_TEXT_LEFT_RATIO = 0.05f
    private const val STICKER_TEXT_RIGHT_RATIO = 0.95f
    private const val STICKER_TEXT_PAD_X_DP = 12f
    private const val STICKER_TEXT_PAD_Y_DP = 8f
    // 灯光落点的参考线：光柱与人物受光都收在落地线上，人才是"站在光里"
    private const val STICKER_BEAM_BOTTOM_RATIO = 0.60f
    // 路灯灯罩在贴纸素材里的水平位置（用于把光斑打在灯的正下方）
    private const val STICKER_LAMP_CX_RATIO = 0.917f
    private const val STICKER_COUPLE_CX_RATIO = 0.23f // 人物在贴纸素材里的水平位置（接触阴影用）
    private const val STICKER_LAMP_HEAD_CY_RATIO = 0.105f // 灯罩中心在贴纸素材里的垂直位置
    // 灯光：从灯罩斜向下铺开一束光柱，落在人物与路灯之间，营造"在路灯下跳舞"的氛围
    private const val STICKER_BEAM_FAR_LEFT_RATIO = -0.86f // 光束远端左边界（自灯心起算，相对贴纸宽度）
    private const val STICKER_BEAM_FAR_RIGHT_RATIO = -0.30f // 光束远端右边界：整束光瞄准人物
    private const val STICKER_BEAM_BLUR_DP = 6f // 光柱边缘柔化
    // 衰减要缓：人物离灯罩约 3/4 个光柱长，衰减太陡光还没走到人身上就没了
    private const val STICKER_BEAM_FALLOFF = 1.7f // 径向渐变半径 = 光柱长 × 该系数
    private const val STICKER_LIT_FALLOFF = 2.4f // 人身上的光衰减更缓，两个人受光才均匀
    private val STICKER_BEAM_STOPS = floatArrayOf(0f, 0.28f, 0.55f, 0.8f, 0.93f, 1f)
    private const val STICKER_BEAM_RGB = 0xE0BE7E // 灯光主色（暖白偏琥珀）
    private val STICKER_BEAM_RAMP = intArrayOf(0x33, 0x2B, 0x1A, 0x0B, 0x04, 0x00)
    // 落在人物身上的受光：同一束光再叠一层，只保留人物像素，人是"被灯照着"而不是站在光旁边
    private val STICKER_LIT_RAMP = intArrayOf(0x3E, 0x38, 0x2C, 0x1E, 0x0E, 0x00)
    private const val STICKER_HALO_ALPHA = 0x3C // 灯罩外圈的暖光晕
    private const val STICKER_HALO_RADIUS_RATIO = 0.22f
    private val STICKER_HALO_COLOR = 0xFFE7B0.toInt()
    // 文本框：纸张剪纸。四角剪口 + 纸纹 + 白描边，避免一片纯色的"素净"
    private const val STICKER_TEXT_EDGE_DP = 2.5f
    private const val STICKER_TEXT_SNIP_DP = 7f // 四角剪掉的小口
    private const val STICKER_PAPER_GRAIN_TILE = 128 // 纸纹贴片边长（px）
    // 垂在文本框下沿的一束玫瑰：藤蔓从左沿贴着下沿拖出，玫瑰花冠大小依次递减
    private const val STICKER_ROSE_DP = 10.5f // 主玫瑰花冠半径
    private const val STICKER_ROSE_OUTLINE_DP = 1.1f
    private val STICKER_ROSE_COLOR = 0xC2566B.toInt()
    private val STICKER_ROSE_PETAL = 0xE38C9C.toInt()
    private val STICKER_ROSE_CORE = 0x93394C.toInt()
    private val STICKER_LEAF_COLOR = 0x4C7A55.toInt()
    private val STICKER_VINE_COLOR = 0x3F6146.toInt()

    // ==================== 城市微缩（CITY_CUTOUT） ====================
    // 一个风格一张素材：presetImageResName 就是素材名，抠掉天空的微缩城市按**原始宽高比**
    // 摆进组件，天空透明处露出壁纸。没有"按尺寸换素材"那套机制 —— 素材比例与组件比例
    // 不合时，由 drawCityArt 用「等比 contain + 底边贴住衔接线」消化：宁可两侧留壁纸，
    // 也不把天际线切平（切平就没有剪影了）。
    //
    // 城市与文字区怎么接，是**每个风格自己的设计**，不共用画法、也不共用配比：
    //   SOIL —— 江西「城市剪影」：山地古城像从地里整块挖出来，接草皮 → 浅壤 → 深土的剖面
    //   WATER —— 上海「上海微缩」：现代城市立在黄浦江面上，接倒影与波纹，向下渐入深水面
    //   FADE —— 用户自定义素材：不假造任何材质，只把城市底边柔进背景色
    // 新增城市风格时在 cityJunctionOf 里认领一种，别默认套土层。
    private enum class CityJunction { SOIL, WATER, FADE }

    // —— 江西·土层剖面 ——
    // 关键一：**起伏在地层的下沿，不在上沿**。上沿严格平直、紧贴城市底边
    //（草皮顶色从素材底边取色做无缝过渡），下沿才是块状不规则的轮廓，
    // 文字区贴着那条轮廓往下长 —— 这样城市与地层是"同一块地块"，
    // 文字框的上边形状就是地块的断面形状。
    // 关键二：三段配比 **城市 50% / 地层 5% / 文字 45%**，按组件实际像素高度切。
    private const val SOIL_ART_RATIO = 0.50f
    private const val SOIL_WALL_RATIO = 0.05f
    private const val SOIL_GRASS_RATIO = 0.20f
    private const val SOIL_EDGE_TRANS_DP = 4f // 素材底边色 → 草皮色的无缝过渡高度
    // 地层下沿的轮廓：粗块（大起伏）+ 细块（碎起伏）叠加，再滑动平均磨圆 →
    // 圆润的下垂地块，而不是城墙垛口。单频方波太机械，真实地块断面是几块深的夹着几块浅的。
    // 幅度必须随 5% 的薄地层同步收小，否则起伏会翻出地层、把草皮顶穿。
    private const val SOIL_RIDGE_AMP_RATIO = 0.32f
    private const val SOIL_RIDGE_AMP_MIN_DP = 2.5f
    private const val SOIL_RIDGE_AMP_MAX_DP = 6.5f
    private const val SOIL_RIDGE_BLOCK_DP = 18f // 粗块宽
    private const val SOIL_RIDGE_EDGE = 0.20f // 块间过渡带占比（越小越像垂直陡壁）
    private const val SOIL_RIDGE_FINE_BLOCK_DP = 8f // 细块宽
    private const val SOIL_RIDGE_COARSE_MIX = 0.76f // 粗块权重
    private const val SOIL_RIDGE_SMOOTH_DP = 2.5f // 磨圆窗口
    private const val SOIL_RIDGE_SAMPLE_DP = 0.5f // 轮廓采样步长
    private const val SOIL_GRASS_RIDGE_SCALE = 0.55f // 草皮下沿起伏衰减（凸处草皮薄、凹处厚）
    private const val CITY_NOISE_SEED = 2026f // 固定种子：每次渲染必须同一条曲线，否则桌面组件会抖
    private val SOIL_GRASS_COLOR = 0xFF7E9C4E.toInt()
    private val SOIL_TOPSOIL_COLOR = 0xFFB07E52.toInt()
    // 接触阴影：城市底边往下叠淡暗色，柔化"插画底边"与草皮之间那条直切。
    // 上限被草皮高度夹住（见 drawSoilStrata）——5% 地层里草皮只有 1% 组件高，
    // 阴影一旦压满整个草皮，绿色就全被吃掉了。
    private const val SOIL_SHADOW_DP = 3f
    private const val SOIL_SHADOW_MAX_ALPHA = 52
    private const val SOIL_SHADOW_STEPS = 5
    private val SOIL_SHADOW_COLOR = 0xFF241B12.toInt()
    // 地层外轮廓的暗边：沿下沿轮廓往上叠渐暗，给下垂的凸块做出体积（只压最外层，
    // 草皮与浅壤之间的分界保持干净）
    private const val SOIL_EDGE_SHADE_DP = 5f
    private const val SOIL_EDGE_SHADE_ALPHA = 54
    private const val SOIL_EDGE_SHADE_STEPS = 6
    // 深土层质感：等厚沉积层理 + 低密度土壤颗粒 + 底部压暗，避免一大块纯色显得空
    private const val SOIL_STRATA_SPACING_DP = 22f
    private const val SOIL_STRATA_ALPHA = 58
    private const val SOIL_STRATA_FOLLOW = 0.5f
    private const val SOIL_SPECKLE_BOX_DP = 22f
    private const val SOIL_SPECKLE_DENSITY = 0.24f
    private const val SOIL_SPECKLE_RADIUS_DP = 1.5f
    private const val SOIL_SPECKLE_ALPHA = 46
    private const val SOIL_VIGNETTE_ALPHA = 24
    private val SOIL_DARK_GRAIN = 0xFF221A12.toInt()
    private val SOIL_LIGHT_GRAIN = 0xFF967E62.toInt()

    // —— 上海·水面与倒影 ——
    // 城市底边就是水线。水线以下：先一段被压暗、竖向压缩、模糊过的**镜像倒影**，
    // 再叠横向波纹高光，最后向下渐进"小组件背景颜色"那池深水，文字浮在水面上。
    // 现代玻璃楼群做土层剖面会读成"城市被挖出来"，很突兀；立在水面上才是陆家嘴。
    private const val WATER_ART_RATIO = 0.52f // 水线在组件高度上的位置（城市占这个高度）
    private const val WATER_REFLECT_RATIO = 0.16f // 倒影画多高（占组件高）
    private const val WATER_REFLECT_SRC_RATIO = 0.42f // 取城市下部多少高度做倒影源
    // 文字只让出组件高的 7%，其余压给倒影的尾巴：倒影本来就靠向下溶解收尾，
    // 文字落在它淡掉的那段上正好，不必为它单独腾出一整条带 ——
    // 腾多了 4×2 就只剩一行字的位置（18sp 一行约 100px，扁组件根本经不起让）。
    private const val WATER_TEXT_BAND_RATIO = 0.07f
    private const val WATER_REFLECT_ALPHA = 74 // 倒影本体透明度
    private const val WATER_RIPPLE_COUNT = 7 // 波纹条数
    private const val WATER_RIPPLE_THIN_DP = 1.1f // 波纹基础粗细
    private const val WATER_RIPPLE_ALPHA = 34
    private const val WATER_RIPPLE_SALT = 41f // cityHash 的盐：与土层的轮廓噪声分开
    private val WATER_SURFACE_COLOR = 0xFF2C5C72.toInt() // 水线处偏亮的江面
    private val WATER_HILITE_COLOR = 0xFFBFE3F0.toInt() // 波纹高光
    private const val WATER_LINE_ALPHA = 70 // 水线本身那道亮边
    private const val WATER_VIGNETTE_ALPHA = 70 // 底部压暗，把文字从水面里托出来

    // —— 各衔接共用 ——
    private const val CITY_TEXT_PAD_X_DP = 16f
    // 上下内边距只保底"一行正文不贴边"即可：FADE 下城市要尽量顶满宽度，
    // 文字区每省 1dp 内边距，城市就能多占一截宽度（见 fadeArtHeight）。
    private const val CITY_TEXT_PAD_Y_DP = 5f
    private const val FADE_ART_RATIO = 0.55f // FADE 衔接：城市占组件高度的**下限**
    private const val CITY_FADE_BAND_RATIO = 0.10f // FADE 衔接：城市底边柔进背景色的带高
    // 一行正文的高度 ≈ 字号 × 该系数（含行距）。用来估文字区下限，宁可估大不估小。
    private const val FADE_LINE_HEIGHT_FACTOR = 1.1f
    // 垫平带取色时认定"内容"的最低 alpha：软边/噪声边缘像素不算内容，免得取到半透明的脏色。
    private const val CITY_PAD_ALPHA_MIN = 128

    // 江西·土层：素材底边过渡色带的缓存（key = 素材实例 + 目标像素尺寸）。
    // 缓存出来的 bitmap 会交给 Canvas.drawBitmap，硬件加速下 DisplayList 仍持有它的引用，
    // 所以**不能**主动 recycle —— 换新的一份时把旧的引用丢掉、交给 GC 即可。
    private var soilFadeKey: Int = 0
    private var soilFadeBitmap: Bitmap? = null

    // 城市微缩：素材"底边垫平带"的缓存（key = 素材实例 + 像素尺寸）。
    // 同样交给 Canvas 绘制过，不能主动 recycle。
    private var cityPadKey: Int = 0
    private var cityPadBitmap: Bitmap? = null

    // ==================== 天气盒子（WEATHER_BOX） ====================
    // 白色盒体正面挖一个内凹方腔，腔底铺 weather_box_cavity 素材（蓝天微缩城市）。
    // 关键：**腔体始终保持素材的宽高比**，只在盒体里水平居中、上下按比例分配。
    // 若让腔体去适应组件的宽高比（像 4×2 那样摊成 3.5:1），素材就填不满腔体，
    // 两侧会露出盒面空白、把"内凹"读成两条孤立暗带。锁死比例后素材永远恰好填满腔口，
    // 素材自带的四周暗角正好落在腔口边缘，凹感自然且 4×2 / 4×4 都不会变形。
    // 素材比例由 weather_box_cavity.webp 实测（1414×676），换图后需同步。
    private const val WEATHER_BOX_CAVITY_ASPECT = 1414f / 676f
    // 腔体在盒体里的垂直位置与高度上限：顶部留 3.1%，腔高不超过盒高的 66%，
    // 剩下的留给正文，保证 4×4 这类高组件也放得下一行字
    private const val WEATHER_BOX_CAVITY_TOP_RATIO = 0.031f
    // 腔高占盒高的比例。素材是横幅（2.09:1）：组件越扁，腔体越容易被宽度卡住、
    // 两侧留白越多，所以给到 0.66 让 4×2 下腔体接近顶满宽度；
    // 而正文区至少要留得下一行 18sp（约 30dp），否则文字会被挤出盒体下沿。
    private const val WEATHER_BOX_CAVITY_HEIGHT_RATIO = 0.66f
    private const val WEATHER_BOX_CAVITY_MAX_HEIGHT_RATIO = 0.66f
    // 腔体左右最少留边（占盒宽），与设计稿的 2.7% 同量级
    private const val WEATHER_BOX_CAVITY_SIDE_RATIO = 0.027f
    // 腔口圆角：内凹开口的圆角略小于外框，弱化"贴纸感"
    private const val WEATHER_BOX_CAVITY_RADIUS_DP = 10f
    // 内壁遮光带厚度（腔口向内渐暗的一条），按密度放大。
    // 素材边缘已自带一圈暗角，这里只补很浅的一层，主要是为了让上沿的暗更连贯
    private const val WEATHER_BOX_INNER_WALL_DP = 5f
    private const val WEATHER_BOX_INNER_SHADOW_ALPHA = 90
    // 腔体下沿高光：光从腔口下沿反弹进来的一条亮边，是"凹"的主要立体线索
    private const val WEATHER_BOX_RIM_LIGHT_DP = 2.5f
    private val WEATHER_BOX_INNER_SHADOW = 0xFF5A6478.toInt()
    private val WEATHER_BOX_RIM_LIGHT = 0xFFFFFFFF.toInt()
    // 正文区（腔体下方留白）内边距
    private const val WEATHER_BOX_TEXT_PAD_X_DP = 16f
    private const val WEATHER_BOX_TEXT_PAD_Y_DP = 8f
    // 正文区最小净高：一行 18sp 正文（含行距）约需此高度。
    // 腔体高度按它反推，确保扁组件（4×2）下文字不会被挤出盒体下沿
    private const val WEATHER_BOX_TEXT_MIN_HEIGHT_DP = 26f

    // ==================== 天气盒子 ====================

    // ==================== 画框卡片（雪落宫墙 / 深海鲸歌共用） ====================
    // 同一族整卡设计：卡纸由「背景颜色」绘制（含纸张颗粒），木框照片按真实比例
    // 摆上方，点缀层（梅枝/鲸影）贴右下角。两个图层都是带羽化卡纸边的抠图
    // （羽化边与卡纸同色），任意组件宽高比下都不露接缝。几何比例各自从设计稿实测。
    private data class FramedCardSpec(
        val frameAsset: String,
        val accentAsset: String,
        val frameAspect: Float,      // 相框补丁宽高比（含羽化边）
        val frameWFrac: Float,       // 相框补丁宽 / 卡宽
        val woodTopFrac: Float,      // 木框上沿 / 卡高（定位基准）
        val woodInset: Float,        // 木框上沿在补丁内的纵向占比
        val accentAspect: Float,     // 点缀层宽高比
        val accentWFrac: Float,      // 点缀层宽 / 卡宽（右下贴边）
        val frameShadow: Int         // 相框投影色（#AARRGGBB）
    )

    // 雪落宫墙：米色卡纸 + 木框雪景宫墙 + 梅枝（设计稿 1570×1122）
    private val WINTER_PALACE_SPEC = FramedCardSpec(
        frameAsset = "winter_frame", accentAsset = "winter_branch",
        frameAspect = 1280f / 648f, frameWFrac = 0.9324f,
        woodTopFrac = 0.0392f, woodInset = 0.03504f,
        accentAspect = 945f / 306f, accentWFrac = 0.6019f,
        frameShadow = 0x4D2E241C
    )

    // 深海鲸歌：白蓝卡纸 + 木框深海日光海面 + 鲸影（设计稿 1509×1126）
    private val DEEP_SEA_SPEC = FramedCardSpec(
        frameAsset = "deepsea_frame", accentAsset = "deepsea_whale",
        frameAspect = 1280f / 664f, frameWFrac = 0.9417f,
        woodTopFrac = 0.0293f, woodInset = 0.03523f,
        accentAspect = 915f / 405f, accentWFrac = 0.6064f,
        frameShadow = 0x452A3648
    )

    // 夏天的海：浅灰蓝卡纸 + 木框林间海径 + 浪花绿叶（设计稿 1508×1162）
    private val SUMMER_SEA_SPEC = FramedCardSpec(
        frameAsset = "summersea_frame", accentAsset = "summersea_accent",
        frameAspect = 1280f / 677f, frameWFrac = 0.9463f,
        woodTopFrac = 0.0508f, woodInset = 0.03444f,
        accentAspect = 875f / 396f, accentWFrac = 0.5802f,
        frameShadow = 0x43291F15
    )

    // 夏日荷花：淡紫卡纸 + 木框荷塘 + 荷影（设计稿 1512×1161）
    private val SUMMER_LOTUS_SPEC = FramedCardSpec(
        frameAsset = "lotus_frame", accentAsset = "lotus_accent",
        frameAspect = 1280f / 674f, frameWFrac = 0.9444f,
        woodTopFrac = 0.0500f, woodInset = 0.03453f,
        accentAspect = 938f / 398f, accentWFrac = 0.6204f,
        frameShadow = 0x42282838
    )

    private const val FRAMED_CARD_TEXT_MIN_HEIGHT_DP = 24f   // 正文最小净高（扁组件保底一行）

    // ==================== 画框卡片 ====================

    /**
     * 内凹腔体在盒体里的矩形，**宽高比恒等于素材比例**。
     *
     * 高度从两个约束里取较小值：一个是按盒高分的上限（让腔体在 4×4 这类高组件上
     * 不会过大），另一个是**给正文预留出的净高度**——扁组件（4×2）按比例分完腔高后
     * 剩下的空间不足一行字，文字会被挤出盒体，所以这里先把正文需要的高度扣掉。
     * 宽度再由高度按素材比例反推，超出可用宽度（左右留边后）则以宽度为准回调高度。
     */
    private fun weatherBoxCavityRect(outerRect: RectF, densityScale: Float): RectF {
        val availWidth = outerRect.width() * (1f - 2f * WEATHER_BOX_CAVITY_SIDE_RATIO)
        val top = outerRect.top + outerRect.height() * WEATHER_BOX_CAVITY_TOP_RATIO
        val ratioCap = outerRect.height() * WEATHER_BOX_CAVITY_MAX_HEIGHT_RATIO

        // 正文净高：一行 18sp 正文（含行距与上下内边距）所需的高度
        val textPadY = WEATHER_BOX_TEXT_PAD_Y_DP * densityScale
        val textMinHeight = WEATHER_BOX_TEXT_MIN_HEIGHT_DP * densityScale
        val textCap = outerRect.bottom - textPadY - textMinHeight - top

        var height = minOf(outerRect.height() * WEATHER_BOX_CAVITY_HEIGHT_RATIO, ratioCap, textCap)
        if (height <= 0f) {
            // 组件过矮（扣掉正文后放不下腔体）：腔体退让，保证正文仍有位置
            height = textCap.coerceAtLeast(outerRect.height() * 0.4f)
        }

        var width = height * WEATHER_BOX_CAVITY_ASPECT
        if (width > availWidth) {
            width = availWidth
            height = width / WEATHER_BOX_CAVITY_ASPECT
        }
        val cx = outerRect.centerX()
        return RectF(
            cx - width / 2f,
            top,
            cx + width / 2f,
            top + height
        )
    }

    /**
     * 画内凹腔体。腔体尺寸与素材比例一致，因此素材直接铺满整个腔口：
     * 素材四周自带的暗角正好压在腔口边缘，读起来就是腔壁遮光。
     * 这里再补两笔：上沿一条浅暗（让顶部转折更连贯）与下沿一条亮边
     * （光从腔口下沿反弹回来）——这条亮边是让平面矩形读成"凹"的关键。
     */
    private fun drawWeatherBoxCavity(
        canvas: Canvas,
        cavity: RectF,
        bitmap: Bitmap?,
        style: WidgetStyle,
        densityScale: Float,
        alpha: Int
    ) {
        val radius = WEATHER_BOX_CAVITY_RADIUS_DP * densityScale
        val clip = Path().apply {
            addRoundRect(cavity, radius, radius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clip)

        if (bitmap != null && !bitmap.isRecycled) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                this.alpha = alpha
            }
            // 腔体比例 == 素材比例，故直接铺满腔口（dst 与 cavity 重合）
            canvas.drawBitmap(bitmap, null, cavity, paint)
        } else {
            // 素材缺失时铺一层天空蓝兜底，避免腔体变成一块空洞
            canvas.drawRect(
                cavity,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFA8C0E0.toInt()
                    this.alpha = alpha
                }
            )
        }

        // 上沿浅暗：衔接盒面与腔壁的转折
        val wall = WEATHER_BOX_INNER_WALL_DP * densityScale
        val shadeAlpha = (alpha * WEATHER_BOX_INNER_SHADOW_ALPHA / 255f).toInt()
        val shade = LinearGradient(
            cavity.left, cavity.top, cavity.left, cavity.top + wall,
            WEATHER_BOX_INNER_SHADOW, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            cavity.left, cavity.top, cavity.right, cavity.top + wall,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = shade; this.alpha = shadeAlpha }
        )

        // 腔口下沿高光：贴着腔体底边、向上淡出的一条亮边
        val rimH = WEATHER_BOX_RIM_LIGHT_DP * densityScale
        val rim = LinearGradient(
            cavity.bottom, 0f, cavity.bottom - rimH, 0f,
            WEATHER_BOX_RIM_LIGHT, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            cavity.left, cavity.bottom - rimH, cavity.right, cavity.bottom,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = rim
                this.alpha = (alpha * 0.65f).toInt()
            }
        )

        canvas.restore()
    }

    /**
     * 画框卡片：按当前组件尺寸现算三个区域。
     *
     * 相框按真实比例摆上方（宽随组件，超高时按"正文最小净高"收缩并保持居中）；
     * 点缀层贴右下角（宽随组件，与相框重叠时让位收缩）；文字落在相框下方的整幅
     * 留白带（梅枝/鲸影虚影极淡，文字直接压上去）。全部随组件比例自适应。
     */
    private data class FramedCardLayout(
        val frameRect: RectF,
        val accentRect: RectF,
        val textRect: RectF
    )

    private fun framedCardRects(outerRect: RectF, densityScale: Float, spec: FramedCardSpec): FramedCardLayout {
        val w = outerRect.width()
        val h = outerRect.height()

        // —— 相框：宽随组件，超高时为正文让位收缩 ——
        var frameW = w * spec.frameWFrac
        var frameH = frameW / spec.frameAspect
        val frameTopBase = maxOf(4f * densityScale, h * spec.woodTopFrac - spec.woodInset * frameH)
        val maxFrameH = h - frameTopBase - FRAMED_CARD_TEXT_MIN_HEIGHT_DP * densityScale - 4f * densityScale
        if (frameH > maxFrameH) {
            frameH = maxFrameH.coerceAtLeast(10f * densityScale)
            frameW = frameH * spec.frameAspect
        }
        // 收缩后木框内缩量随补丁变小，顶沿要按最终尺寸重算
        val frameTop = maxOf(4f * densityScale, h * spec.woodTopFrac - spec.woodInset * frameH)
        val frameLeft = outerRect.left + (w - frameW) / 2f
        val frameRect = RectF(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH)

        // —— 点缀层：贴右下角，与相框重叠时让位收缩 ——
        var accentW = w * spec.accentWFrac
        var accentH = accentW / spec.accentAspect
        val maxAccentH = h - frameRect.bottom - 2f * densityScale
        if (accentH > maxAccentH) {
            accentH = maxAccentH.coerceAtLeast(8f * densityScale)
            accentW = accentH * spec.accentAspect
        }
        val accentRect = RectF(outerRect.right - accentW, outerRect.bottom - accentH, outerRect.right, outerRect.bottom)

        // —— 文字：相框下方的整幅留白带。点缀层的浓墨部分只在最右下角，
        // 虚影极淡，文字压上去不影响可读（用户确认不再避让） ——
        val textLeft = outerRect.left + maxOf(14f * densityScale, w * 0.05f)
        val textRight = outerRect.right - maxOf(12f * densityScale, w * 0.035f)
        val textTop = frameRect.bottom + 3f * densityScale
        val textBottom = outerRect.bottom - maxOf(5f * densityScale, h * 0.025f)
        val textRect = RectF(
            textLeft,
            minOf(textTop, textBottom - 18f * densityScale),
            textRight,
            textBottom
        )
        return FramedCardLayout(frameRect, accentRect, textRect)
    }

    /** 画框卡片：卡纸已由背景色铺好，这里叠相框（带投影）与点缀层两块抠图。 */
    private fun drawFramedCard(
        canvas: Canvas,
        context: Context,
        outerPath: Path,
        spec: FramedCardSpec,
        layout: FramedCardLayout,
        targetWidth: Int,
        targetHeight: Int,
        densityScale: Float,
        alpha: Int
    ) {
        val frame = getPresetImage(context, spec.frameAsset, targetWidth, targetHeight)
        val accent = getPresetImage(context, spec.accentAsset, targetWidth, targetHeight)

        val saveCount = canvas.save()
        canvas.clipPath(outerPath)

        // 相框投影：木框压在卡纸上的落影，向下柔散；补丁的羽化卡纸边会盖住投影内侧。
        // 画笔透明=只落投影不画形状，避免在羽化边下垫出异色
        if (frame != null && !frame.isRecycled) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.TRANSPARENT
                setShadowLayer(9f * densityScale, 0f, 5f * densityScale, spec.frameShadow)
            }
            canvas.drawRect(layout.frameRect, shadowPaint)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            canvas.drawBitmap(frame, null, layout.frameRect, paint)
        }

        if (accent != null && !accent.isRecycled) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            canvas.drawBitmap(accent, null, layout.accentRect, paint)
        }

        canvas.restoreToCount(saveCount)
    }

    fun render(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap {
        // 将 dp 尺寸转为像素，增加 RENDER_DENSITY_SCALE 倍分辨率防止桌面模糊
        val scale = context.resources.displayMetrics.density
        val densityScale = scale * RENDER_DENSITY_SCALE
        val targetWidth = (widthDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val targetHeight = (heightDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)

        // 注意：此 Bitmap 由调用方负责回收。调用方在使用完毕后应调用 bitmap.recycle() 以释放内存。
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. 绘制背景区域与形状裁切路径
        val path = Path()
        // 只对"纯圆角矩形"这一族的形状做内缩：它们的内容完全按 rectF/outerRect 布局，内缩不会溢出；
        // 其余形状（撕纸/八角/信纸/萌宠/像素/书架等）有各自按整幅位图绘制的装饰，保持满幅以免错位
        val usesInsetCard = style.shape == WidgetShape.RECTANGLE ||
            style.shape == WidgetShape.HANDBOOK_TAPE ||
            style.shape == WidgetShape.SPLIT_CARD ||
            style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL ||
            // 天气盒子：盒体与腔体都按 outerRect 布局，内缩安全；预设开了投影，
            // 不内缩的话阴影会被位图边界裁掉，盒体看起来是"贴平"的
            style.shape == WidgetShape.WEATHER_BOX
        val cardInset = if (usesInsetCard) CARD_INSET_DP * densityScale else 0f
        val offsetY = cardInset
        val rectF = RectF(cardInset, offsetY, targetWidth - cardInset, targetHeight - cardInset)

        // 巨剑/毛绒森林/小霸王游戏机是整幅插画（剑身横贯、毛绒小树在顶部、实物模型铺满），
        // 圆角裁剪会把主体切掉。预设套用时会继承上一个风格的圆角值，
        // 这里统一强制按直角渲染，避免旧数据/跨风格套用后画面被裁。
        // 贴纸夜景整幅透明、不画外框，它的圆角滑条作用在文本框上（见 paperCutBoxPath），不在此列。
        // 城市剪影同理：没有卡片外框，圆角滑条作用在文字栏底部两角（见 cityBarPath）。
        val effectiveCornerRadiusDp =
            if (style.shape == WidgetShape.GIANT_SWORD || style.shape == WidgetShape.PLUSH_FOREST ||
                style.shape == WidgetShape.SUBOR_CONSOLE || style.shape == WidgetShape.CITY_CUTOUT
            ) 0f
            else style.cornerRadiusDp

        when (style.shape) {
            WidgetShape.RECTANGLE, WidgetShape.HANDBOOK_TAPE, WidgetShape.SPLIT_CARD, WidgetShape.SPLIT_CARD_HORIZONTAL, WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.BLUE_NOTE, WidgetShape.ZHU_QING_SI_ZHI, WidgetShape.NIUPI_SHOUZHANG, WidgetShape.CLASSROOM_BLACKBOARD, WidgetShape.BOOKSHELF, WidgetShape.GIANT_SWORD, WidgetShape.PLUSH_FOREST, WidgetShape.SUBOR_CONSOLE, WidgetShape.STICKER_SCENE, WidgetShape.CITY_CUTOUT, WidgetShape.WEATHER_BOX, WidgetShape.WINTER_PALACE, WidgetShape.DEEP_SEA, WidgetShape.SUMMER_SEA, WidgetShape.SUMMER_LOTUS -> {
                val rx = effectiveCornerRadiusDp * densityScale
                if (rx <= 0f) {
                    path.addRect(rectF, Path.Direction.CW)
                } else {
                    path.addRoundRect(rectF, rx, rx, Path.Direction.CW)
                }
            }
            WidgetShape.TORN_PAPER -> {
                val tornPath = generateTornPath(targetWidth.toFloat(), targetHeight.toFloat(), densityScale)
                path.set(tornPath)
            }
            WidgetShape.FEATHER_LETTER -> {
                // 羽毛信纸：居中撕纸信纸矩形，四周留出卡片边距
                drawFeatherLetterPath(path, targetWidth.toFloat(), targetHeight.toFloat(), densityScale)
            }
            WidgetShape.ELLIPSE -> {
                path.addOval(rectF, Path.Direction.CW)
            }
        }

        // 1.5 绘制全局卡片大背景（防止气泡等非铺满形状在外部露出黑色透明像素）
        val outerPath = Path()
        val outerRx = when (style.shape) {
            // 与上方形状路径保持同一份名单：这些形状的外框圆角都跟随用户的圆角设置，
            // 否则圆角滑条对复古像素 / 萌宠猫咪 / 竹青撕纸等形状完全不生效
            WidgetShape.RECTANGLE, WidgetShape.HANDBOOK_TAPE,
            WidgetShape.SPLIT_CARD, WidgetShape.SPLIT_CARD_HORIZONTAL, WidgetShape.BLUE_NOTE,
            WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.ZHU_QING_SI_ZHI, WidgetShape.NIUPI_SHOUZHANG, WidgetShape.CLASSROOM_BLACKBOARD, WidgetShape.BOOKSHELF, WidgetShape.GIANT_SWORD, WidgetShape.PLUSH_FOREST, WidgetShape.SUBOR_CONSOLE, WidgetShape.STICKER_SCENE, WidgetShape.CITY_CUTOUT, WidgetShape.WEATHER_BOX, WidgetShape.WINTER_PALACE, WidgetShape.DEEP_SEA, WidgetShape.SUMMER_SEA, WidgetShape.SUMMER_LOTUS ->
                effectiveCornerRadiusDp * densityScale
            else -> DEFAULT_OUTER_CORNER_RADIUS_DP * densityScale
        }
        val outerRect = RectF(cardInset, offsetY, targetWidth - cardInset, targetHeight - cardInset)
        if (outerRx <= 0f) {
            outerPath.addRect(outerRect, Path.Direction.CW)
        } else {
            outerPath.addRoundRect(outerRect, outerRx, outerRx, Path.Direction.CW)
        }
        
        // 主体四周透明的形状不支持背景色：渲染时强制按透明处理，
        // 让已经落库/落到桌面的旧组件不必重新保存也不会露出包裹卡片
        val effectiveBgColor = if (WidgetStyle.supportsBackgroundColor(style.shape)) {
            style.backgroundColor
        } else {
            Color.TRANSPARENT
        }

        // 绘制卡片软阴影（移至 clip 外部以防被气泡边界截断）
        // 贴纸夜景整幅是透明底，阴影只该跟着文本框走，因此单独在文本框绘制处处理
        // 城市剪影同理：整幅没有卡片外框，只有剪影 + 文字栏，不画整卡投影
        if (style.showCardShadow && style.shape != WidgetShape.STICKER_SCENE &&
            style.shape != WidgetShape.CITY_CUTOUT
        ) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = effectiveBgColor
                if (Color.alpha(effectiveBgColor) < 255) {
                    color = effectiveBgColor or 0xFF000000.toInt()
                }
                setShadowLayer(
                    6f * densityScale,
                    0f,
                    3f * densityScale,
                    Color.parseColor("#40000000")
                )
            }
            canvas.drawPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath, shadowPaint)
        }

        // 填充全局大底色（颜色与透明度）
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (style.gradientColors != null && style.gradientColors.size >= 2 &&
            WidgetStyle.supportsBackgroundColor(style.shape)
        ) {
            val w = rectF.width()
            val h = rectF.height()
            val r = Math.sqrt((w * w + h * h).toDouble()) / 2.0
            val cx = rectF.centerX()
            val cy = rectF.centerY()
            val angleRad = Math.toRadians(style.gradientAngle.toDouble())
            val cos = Math.cos(angleRad)
            val sin = Math.sin(angleRad)
            val x0 = (cx - cos * r).toFloat()
            val y0 = (cy - sin * r).toFloat()
            val x1 = (cx + cos * r).toFloat()
            val y1 = (cy + sin * r).toFloat()
            
            val colors = style.gradientColors.toIntArray()
            bgPaint.shader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        } else {
            bgPaint.color = effectiveBgColor
        }
        bgPaint.alpha = alpha
        // 背景色为透明时不填充，避免 alpha 被强制为 255 后把透明底画成黑色。
        // 贴纸夜景整幅是透明底，背景色只作用于文本框（下方单独绘制），这里不铺整卡底色
        // 城市剪影的背景色只作用于文字栏（下方单独绘制），同样不铺整卡底色
        if (Color.alpha(effectiveBgColor) > 0 && style.shape != WidgetShape.STICKER_SCENE &&
            style.shape != WidgetShape.CITY_CUTOUT
        ) {
            if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                // 图文明信片：文字显示区（下半/右半）的底色由下方 panelPaint 单独绘制，
                // 这里只铺图片区，避免同一底色叠两遍导致不透明度失真
                canvas.save()
                canvas.clipPath(outerPath)
                canvas.drawRect(splitImageRect(style.shape, outerRect), bgPaint)
                canvas.restore()
            } else {
                canvas.drawPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath, bgPaint)
            }
        }

        // 尝试加载背景图片（优先使用自定义路径，次之使用内置预设插画名）
        var bgBitmap: Bitmap? = null
        var bgFromCache = false
        if (!style.backgroundImagePath.isNullOrEmpty()) {
            try {
                val file = File(style.backgroundImagePath)
                if (file.exists()) {
                    bgBitmap = decodeFileSampled(file.absolutePath, targetWidth, targetHeight)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load background image from path")
            }
        } else {
            val resName = if (style.shape == WidgetShape.CITY_CUTOUT) {
                // 城市微缩：一个风格一张素材，presetImageResName 就是素材名
                style.presetImageResName
            } else if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                style.presetImageResName ?: "bg_illustration_1"
            } else {
                style.presetImageResName
            }
            if (!resName.isNullOrEmpty()) {
                try {
                    bgBitmap = getPresetImage(context, resName, targetWidth, targetHeight)
                    bgFromCache = bgBitmap != null
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to load preset background image")
                }
            }
        }

        // 绘制背景图片（若有）
        // 贴纸夜景的素材是抠出的人物+路灯，位置/大小由下方贴纸逻辑单独计算，不走这里的整卡铺图
        // 城市剪影的素材要按剪影带单独铺（且必须跳过 detectLightBorder，见 drawCityArt）
        // 天气盒子同理：素材只铺进内凹腔体，盒面留白由背景色负责
        // 雪落宫墙：相框与梅枝两块图层由下方单独摆放，卡纸由背景色负责
        if (bgBitmap != null && style.shape != WidgetShape.STICKER_SCENE &&
            style.shape != WidgetShape.CITY_CUTOUT && style.shape != WidgetShape.WEATHER_BOX &&
            style.shape != WidgetShape.WINTER_PALACE && style.shape != WidgetShape.DEEP_SEA &&
            style.shape != WidgetShape.SUMMER_SEA && style.shape != WidgetShape.SUMMER_LOTUS
        ) {
            canvas.save()
            canvas.clipPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath)
            if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                drawBgImage(canvas, bgBitmap, splitImageRect(style.shape, outerRect), style)
            } else {
                drawBgImage(canvas, bgBitmap, if (style.shape == WidgetShape.TORN_PAPER) rectF else outerRect, style)
            }
            canvas.restore()

            // 自定义路径图片随本次渲染释放；预设图由缓存统一管理
            if (!bgFromCache && !bgBitmap.isRecycled) {
                bgBitmap.recycle()
            }
        }

        // 小霸王游戏机：素材的显像管玻璃原本是"未通电"的深灰玻璃，
        // 这里在屏幕区域内叠一层绿色荧光底，让屏幕看起来是开机的
        if (style.shape == WidgetShape.SUBOR_CONSOLE) {
            drawSuborScreenGlow(canvas, suborScreenRect(outerRect), densityScale)
        }

        // 图文明信片：文字显示区（下半/右半）的底色由代码绘制，不是背景图片的一部分，
        // 因此跟随"小组件背景颜色"自定义，并同样受背景不透明度控制。
        // 预设背景色为白色，默认观感与旧版一致。
        if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
            val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundColor
                this.alpha = alpha
            }
            canvas.save()
            canvas.clipPath(outerPath)
            if (style.shape == WidgetShape.SPLIT_CARD) {
                val dividerY = outerRect.top + outerRect.height() * SPLIT_CARD_RATIO
                val bottomRect = RectF(
                    outerRect.left,
                    dividerY,
                    outerRect.right,
                    outerRect.bottom
                )
                canvas.drawRect(bottomRect, panelPaint)

                // 绘制 1px 精致分割线
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E5E7EB") // 使用更苹果风格的浅灰边线 (#E5E7EB)
                    strokeWidth = 1f * densityScale
                    this.style = Paint.Style.STROKE
                }
                canvas.drawLine(outerRect.left, dividerY, outerRect.right, dividerY, linePaint)
            } else {
                val dividerX = outerRect.left + outerRect.width() * SPLIT_CARD_HORIZONTAL_RATIO
                val rightRect = RectF(
                    dividerX,
                    outerRect.top,
                    outerRect.right,
                    outerRect.bottom
                )
                canvas.drawRect(rightRect, panelPaint)
            }
            canvas.restore()
        }

        // 贴纸夜景：文本框（直角剪边）→ 贴纸（人物+路灯站在纸上，带白色描边）→ 花枝垂在文本框下沿。
        // 组件整幅透明，只有文本框是实体色块，背景色/不透明度都只作用于文本框。
        if (style.shape == WidgetShape.STICKER_SCENE) {
            val artRect = stickerArtRect(outerRect)
            // 光柱在人物之下：人是站在光里的剪影，而不是被光糊住
            drawStickerLightBeam(canvas, outerRect, artRect, densityScale, alpha)

            val textBox = stickerTextBoxRect(outerRect)
            // 纸张剪纸：默认四角剪掉一小块；圆角滑条调大后四角改为圆弧（半径见 paperCutBoxPath）
            val snip = STICKER_TEXT_SNIP_DP * densityScale
            val borderPath = paperCutBoxPath(textBox, snip, style.cornerRadiusDp * densityScale)
            if (style.showCardShadow) {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.backgroundColor
                    setShadowLayer(6f * densityScale, 0f, 3f * densityScale, Color.parseColor("#40000000"))
                }
                canvas.drawPath(borderPath, shadowPaint)
            }
            val textBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundColor
                this.alpha = alpha
            }
            canvas.drawPath(borderPath, textBoxPaint)

            // 纸纹：只铺在纸面里，让底色不再是一块干净的单色
            val grain = paperGrainPaint(alpha)
            val grainLayer = canvas.save()
            canvas.clipPath(borderPath)
            canvas.drawRect(textBox, grain)
            canvas.restoreToCount(grainLayer)

            // 剪纸白边：与贴纸的白色描边呼应，让文本框也像"剪下来贴上去"的纸片
            val edgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.alpha = alpha
                strokeWidth = STICKER_TEXT_EDGE_DP * densityScale
                this.style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(borderPath, edgeStroke)

            // 接触阴影：人物与路灯是踩在这张纸上的，脚下压一层软阴影才站得住
            drawStickerContactShadow(canvas, textBox, borderPath, artRect, alpha)

            if (bgBitmap != null) {
                val artPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    this.alpha = alpha
                }
                canvas.drawBitmap(bgBitmap, null, artRect, artPaint)
                drawStickerLightOnArt(canvas, outerRect, artRect, bgBitmap, densityScale, alpha)
                if (!bgFromCache && !bgBitmap.isRecycled) {
                    bgBitmap.recycle()
                }
            }
            drawStickerLampHalo(canvas, artRect)
            // 玫瑰画在最后：垂在文本框下沿
            drawStickerRoses(canvas, textBox, densityScale, alpha)
        }

        // 城市微缩：先把抠掉天空的城市按原比例铺上（天空透明处露出壁纸），
        // 再按**这个风格自己的**衔接把城市底边接进文字栏。
        if (style.shape == WidgetShape.CITY_CUTOUT) {
            val junction = cityJunctionOf(style)
            val artRect = cityArtRect(outerRect, junction, style, densityScale, bgBitmap)
            if (bgBitmap != null && !bgBitmap.isRecycled) {
                // 先垫平素材底边的透明垫高区，再画城市：垫平带画在模型之下，
                // 被模型实体盖住的部分不可见，只有露在衔接线上方的那截把壁纸挡掉。
                if (cityNeedsBottomPad(style)) {
                    drawCityBottomPad(canvas, bgBitmap, artRect, style)
                }
                drawCityArt(canvas, bgBitmap, artRect, style)
            }
            when (junction) {
                CityJunction.SOIL -> drawSoilStrata(
                    canvas, outerRect, artRect, bgBitmap, densityScale, style, bgPaint)
                CityJunction.WATER -> drawCityWater(
                    canvas, outerRect, artRect, bgBitmap, densityScale, style, bgPaint)
                CityJunction.FADE -> drawCityFade(
                    canvas, outerRect, artRect, densityScale, style, bgPaint)
            }
            if (bgBitmap != null && !bgFromCache && !bgBitmap.isRecycled) {
                bgBitmap.recycle()
            }
        }

        // 蓝色便签：在蓝色大底上追加顶部 NOTE 区域与底部米白签条
        if (style.shape == WidgetShape.BLUE_NOTE) {
            drawBlueNoteChrome(canvas, targetWidth.toFloat(), targetHeight.toFloat(), outerRect, outerPath, densityScale, style, context)
        }

        // 书香书架：顶部彩色书脊立在横板上，底部米色摘录面板
        if (style.shape == WidgetShape.BOOKSHELF) {
            drawBookshelfChrome(canvas, outerRect, densityScale, style, context)
        }

        // 天气盒子：白色盒体正面挖一个内凹方腔，腔底铺蓝天微缩城市素材，
        // 腔口画内壁暗面 + 下沿高光，形成"凹进去"的体积感；腔下留白即正文区。
        if (style.shape == WidgetShape.WEATHER_BOX) {
            val cavity = weatherBoxCavityRect(outerRect, densityScale)
            drawWeatherBoxCavity(canvas, cavity, bgBitmap, style, densityScale, alpha)
            if (bgBitmap != null && !bgFromCache && !bgBitmap.isRecycled) {
                bgBitmap.recycle()
            }
        }

        // 画框卡片族（雪落宫墙/深海鲸歌/夏天的海/夏日荷花）：卡纸由背景色铺好
        //（含纸张颗粒），这里叠相框与点缀层
        if (style.shape == WidgetShape.WINTER_PALACE || style.shape == WidgetShape.DEEP_SEA ||
            style.shape == WidgetShape.SUMMER_SEA || style.shape == WidgetShape.SUMMER_LOTUS
        ) {
            val spec = when (style.shape) {
                WidgetShape.WINTER_PALACE -> WINTER_PALACE_SPEC
                WidgetShape.DEEP_SEA -> DEEP_SEA_SPEC
                WidgetShape.SUMMER_SEA -> SUMMER_SEA_SPEC
                else -> SUMMER_LOTUS_SPEC
            }
            val layout = framedCardRects(outerRect, densityScale, spec)
            drawFramedCard(canvas, context, outerPath, spec, layout, targetWidth, targetHeight, densityScale, alpha)
        }

        // 羽毛信纸：使用透自信纸抠图作背景（走上方背景图绘制逻辑），透明区透底色

        // 绘制纸张颗粒/纤维纹理 (作用于全局大卡片上，效果更拟真一致)
        if (style.textureType == "PAPER" || style.textureType == "GRAIN") {
            drawPaperTexture(canvas, if (style.shape == WidgetShape.TORN_PAPER) rectF else outerRect, style.textureType, densityScale)
        }

        // 保存画布状态并应用形状剪裁
        canvas.save()
        canvas.clipPath(path)

        // 释放剪裁状态
        canvas.restore()

        // 绘制卡片描边 (Border)与撕裂白边
        if (style.shape == WidgetShape.TORN_PAPER) {
            val tornBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                this.strokeWidth = 1.0f * densityScale
                this.color = Color.parseColor("#EAEAEA")
                this.alpha = TORN_BORDER_ALPHA
            }
            canvas.drawPath(path, tornBorderPaint)
        }

        if (style.cardBorderWidthDp > 0f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                this.strokeWidth = style.cardBorderWidthDp * densityScale
                this.color = style.cardBorderColor
            }
            canvas.drawPath(path, borderPaint)
        }

        // 4. 准备绘制文字（新付费规则：预览任意风格、桌面默认免费、仅在“同步到桌面”时弹付费，渲染层不感知会员状态）
        val renderStyle = style

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.fontColor
            textSize = style.fontSizeSp * scale * FONT_SIZE_SCALE
            typeface = style.font.getTypeface(context)
            
            // 处理粗体与斜体
            var styleFlags = 0
            if (style.fontBold) styleFlags = styleFlags or android.graphics.Typeface.BOLD
            if (style.fontItalic) styleFlags = styleFlags or android.graphics.Typeface.ITALIC
            if (styleFlags != 0) {
                typeface = android.graphics.Typeface.create(style.font.getTypeface(context), styleFlags)
            }

            // 字间距
            letterSpacing = style.letterSpacing

            // 阴影设置
            if (style.shadow.enabled) {
                // 小霸王游戏机：显像管荧光晕。预设里的阴影半径是像素值，在 3x 密度下几乎看不见，
                // 这里按密度放大到 dp 级别，让文字真正"发"出绿色荧光
                val shadowRadius = if (style.shape == WidgetShape.SUBOR_CONSOLE) {
                    SUBOR_GLOW_RADIUS_DP * densityScale
                } else {
                    style.shadow.radius
                }
                setShadowLayer(
                    shadowRadius,
                    style.shadow.dx,
                    style.shadow.dy,
                    style.shadow.color
                )
            }
        }

        // 两端对齐（JUSTIFY）依赖 StaticLayout 的 justification 能力（API 26+），
        // 低版本回退为左对齐；词间距拉伸只在折行行生效，末行保持正常排布（标准行为）
        val alignKey = style.textAlign.uppercase(Locale.ROOT)
        val isJustify = alignKey == "JUSTIFY" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val textAlignment = when {
            alignKey == "LEFT" -> Layout.Alignment.ALIGN_NORMAL
            alignKey == "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
            isJustify -> Layout.Alignment.ALIGN_NORMAL
            else -> Layout.Alignment.ALIGN_CENTER
        }

        // 5. 绘制主体内容（新付费规则：桌面与预览均直接渲染真实风格；首次落地不在渲染层拦截或展示付费蒙层）
        // 天空之蓝 "NOTE" 标签绘制
        if (style.presetImageResName == "rectangle_1") {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14f * densityScale
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            canvas.drawText("NOTE", 20f * densityScale, 28f * densityScale, notePaint)
        }

            // B. 计算安全的文本安全排版边界，防止遮挡人物
            val paddingLeft: Float
            val paddingRight: Float
            val textWidth: Float
            val cardTop: Float
            val cardHeight: Float

            if (style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                paddingLeft = targetWidth * SPLIT_CARD_HORIZONTAL_RATIO + 12f * densityScale
                paddingRight = targetWidth - 12f * densityScale
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = 12f * densityScale
                cardHeight = targetHeight - 24f * densityScale
            } else if (style.shape == WidgetShape.SPLIT_CARD) {
                paddingLeft = 16f * densityScale
                paddingRight = targetWidth - 16f * densityScale
                textWidth = paddingRight - paddingLeft
                cardTop = targetHeight * SPLIT_CARD_RATIO + 12f * densityScale
                cardHeight = targetHeight - cardTop - 12f * densityScale
            } else if (style.shape == WidgetShape.FEATHER_LETTER) {
                // 羽毛信纸：文字落在信纸留白区（扩大区域，右侧多留避羽毛笔，顶部避开尖角）
                val verticalInset = targetHeight * 0.16f
                val leftLateral = targetWidth * 0.10f
                val rightLateral = targetWidth * 0.20f
                paddingLeft = leftLateral
                paddingRight = targetWidth - rightLateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset // 顶部让出信封尖角
                cardHeight = targetHeight - cardTop - targetHeight * 0.14f // 底部让出信纸下缘
            } else if (style.shape == WidgetShape.PIXEL_RETRO) {
                // 复古像素：文字落在薄荷绿背景区，避开四周深蓝虚线边框与红框
                val verticalInset = targetHeight * 0.12f
                val lateral = targetWidth * 0.10f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset // 顶部让出虚线边框
                cardHeight = targetHeight - cardTop - targetHeight * 0.12f // 底部让出虚线边框
            } else if (style.shape == WidgetShape.PET_CAT_NAP) {
                // 萌宠猫咪趴：橘猫趴在卡片顶部,文字落在卡片渐变中下部(避开猫)
                val verticalInset = targetHeight * 0.42f // 顶部让出猫
                val lateral = targetWidth * 0.12f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset
                cardHeight = targetHeight - cardTop - targetHeight * 0.08f // 底部少留
            } else if (style.shape == WidgetShape.BLUE_NOTE) {
                // 蓝色便签：顶部让出 NOTE + 信息钮，底部让出米白签条
                val footerH = targetHeight * (76f / 363f)
                val headerH = targetHeight * (76f / 363f) * 0.85f
                val lateral = targetWidth * 0.08f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = headerH
                cardHeight = targetHeight - cardTop - footerH - 8f * densityScale
            } else if (style.shape == WidgetShape.ZHU_QING_SI_ZHI || style.shape == WidgetShape.NIUPI_SHOUZHANG) {
                // 竹青撕纸 / 撕边牛皮手账：纸即主体，文字居中留白避开撕边与右下阴影
                val verticalInset = targetHeight * 0.11f
                val lateral = targetWidth * 0.11f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset
                cardHeight = targetHeight - cardTop - verticalInset
            } else if (style.shape == WidgetShape.CLASSROOM_BLACKBOARD) {
                // 教室黑板：文字写在绿色板面上，四周避开木框，底部让出粉笔槽
                val lateral = targetWidth * 0.09f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.12f
                cardHeight = targetHeight * 0.70f
            } else if (style.shape == WidgetShape.BOOKSHELF) {
                // 书香书架：正文落在底部米色摘录面板内。书籍区高度固定，面板吃掉组件多出来的高度，
                // 因此组件变高时只有文本区域变大（面板与正文区域用同一个面板矩形，保证文字不越界）
                val panel = bookshelfPanelRect(outerRect, densityScale)
                val textPadX = SHELF_PANEL_TEXT_PAD_X_DP * densityScale
                val textPadY = SHELF_PANEL_TEXT_PAD_Y_DP * densityScale
                paddingLeft = panel.left + textPadX
                paddingRight = panel.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = panel.top + textPadY
                cardHeight = (panel.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.GIANT_SWORD) {
                // 巨剑：左侧是扛剑武士，正文只压在右侧剑身金属面上，避开剑柄/护手与上下剑棱。
                // 剑身纵向只占画面约 1/3，这里把可用高度吃满，保证 4×2 规格下也能排出两行
                paddingLeft = targetWidth * 0.33f
                paddingRight = targetWidth * 0.93f
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.39f
                cardHeight = targetHeight * 0.33f
            } else if (style.shape == WidgetShape.PLUSH_FOREST) {
                // 毛绒森林：顶部毛绒小树/蘑菇与粉色花边不可压，正文落在奶油色毛绒面板内
                paddingLeft = targetWidth * 0.115f
                paddingRight = targetWidth * 0.885f
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.40f
                cardHeight = targetHeight * 0.47f
            } else if (style.shape == WidgetShape.SUBOR_CONSOLE) {
                // 小霸王游戏机：素材按 CENTER_FIT 等比完整显示，正文只落在机身屏幕的玻璃区域内。
                // 屏幕矩形由素材内测得的相对位置换算，组件是 4×3 还是 4×4 文字都始终贴在屏幕上
                val screen = suborScreenRect(outerRect)
                val textPadX = SUBOR_SCREEN_TEXT_PAD_X_DP * densityScale
                val textPadY = SUBOR_SCREEN_TEXT_PAD_Y_DP * densityScale
                paddingLeft = screen.left + textPadX
                paddingRight = screen.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = screen.top + textPadY
                cardHeight = (screen.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.STICKER_SCENE) {
                // 贴纸夜景：正文落在文本框内，四周留出内边距避免贴边
                val textBox = stickerTextBoxRect(outerRect)
                val textPadX = STICKER_TEXT_PAD_X_DP * densityScale
                val textPadY = STICKER_TEXT_PAD_Y_DP * densityScale
                paddingLeft = textBox.left + textPadX
                paddingRight = textBox.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = textBox.top + textPadY
                cardHeight = (textBox.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.CITY_CUTOUT) {
                // 城市微缩：正文落在衔接层之下——土层的轮廓谷底 / 倒影的最下沿。
                // 取的是这条带的**最低点**，所以文字绝不会压到剖面或倒影上。
                val junction = cityJunctionOf(style)
                val artRect = cityArtRect(
                    outerRect, junction, style, densityScale, bgBitmap?.takeIf { !it.isRecycled })
                val textBox = cityTextBoxRect(
                    outerRect, artRect, cityBand(junction, outerRect, densityScale))
                val textPadX = CITY_TEXT_PAD_X_DP * densityScale
                val textPadY = CITY_TEXT_PAD_Y_DP * densityScale
                paddingLeft = textBox.left + textPadX
                paddingRight = textBox.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = textBox.top + textPadY
                cardHeight = (textBox.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.WEATHER_BOX) {
                // 天气盒子：正文落在腔体下方的白色留白区。留白区从腔体下沿切起，
                // 因此组件变高时多出来的高度全给正文，腔体本身不会被拉长变形
                val cavity = weatherBoxCavityRect(outerRect, densityScale)
                val textPadX = WEATHER_BOX_TEXT_PAD_X_DP * densityScale
                val textPadY = WEATHER_BOX_TEXT_PAD_Y_DP * densityScale
                paddingLeft = outerRect.left + textPadX
                paddingRight = outerRect.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = cavity.bottom + textPadY
                cardHeight = (outerRect.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.WINTER_PALACE || style.shape == WidgetShape.DEEP_SEA ||
                style.shape == WidgetShape.SUMMER_SEA || style.shape == WidgetShape.SUMMER_LOTUS
            ) {
                // 画框卡片：正文落在相框下方的整幅留白带（与绘制层同一套布局）
                val spec = when (style.shape) {
                    WidgetShape.WINTER_PALACE -> WINTER_PALACE_SPEC
                    WidgetShape.DEEP_SEA -> DEEP_SEA_SPEC
                    WidgetShape.SUMMER_SEA -> SUMMER_SEA_SPEC
                    else -> SUMMER_LOTUS_SPEC
                }
                val layout = framedCardRects(outerRect, densityScale, spec)
                paddingLeft = layout.textRect.left
                paddingRight = layout.textRect.right
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = layout.textRect.top
                cardHeight = (layout.textRect.bottom - layout.textRect.top).coerceAtLeast(1f)
            } else {
                paddingLeft = 16f * densityScale
                paddingRight = targetWidth - 16f * densityScale
                textWidth = paddingRight - paddingLeft
                // 卡片内缩后文字区域同步内缩，避免长文本越过卡片下沿
                cardTop = cardInset
                cardHeight = targetHeight - 2f * cardInset
            }

            // 构建 StaticLayout 处理文本自动折行。
            // 文本行数超过显示区域可容纳的行数时，超出部分用省略号代替，
            // 而不是被画布底部硬裁切；空间足够时始终完整显示全部文本。
            val lineHeight = textPaint.fontSpacing * style.lineSpacingMultiplier
            val maxLines = if (lineHeight > 0f) {
                ((cardHeight - 8f * densityScale) / lineHeight).toInt().coerceAtLeast(1)
            } else {
                1
            }
            // 先按完整文本构建，用 lineCount 判断实际折行数是否超过显示区域可容纳行数。
            // 不能用显式换行符数量判断，否则无换行符的长文本永远不会触发省略号。
            val fullLayout = StaticLayout.Builder.obtain(content, 0, content.length, textPaint, textWidth.toInt())
                .setAlignment(textAlignment)
                .setLineSpacing(0f, style.lineSpacingMultiplier)
                .setIncludePad(true)
                .apply { if (isJustify) setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD) }
                .build()
            val staticLayout = if (fullLayout.lineCount > maxLines) {
                StaticLayout.Builder.obtain(content, 0, content.length, textPaint, textWidth.toInt())
                    .setAlignment(textAlignment)
                    .setLineSpacing(0f, style.lineSpacingMultiplier)
                    .setIncludePad(true)
                    .setMaxLines(maxLines)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .apply { if (isJustify) setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD) }
                    .build()
            } else {
                fullLayout
            }

            // 不在组件底部绘制作者签名/风格名，把这块空间预留给正文，让组件能容纳更多文字。
            // 签名仅作为数据保留在 WidgetStyle 中，不参与渲染。
            val totalContentHeight = staticLayout.height

            canvas.save()
            // 垂直居中计算
            val centerY = cardTop + (cardHeight - totalContentHeight) / 2f
            canvas.translate(paddingLeft, centerY.coerceAtLeast(cardTop + 4f * densityScale))
            staticLayout.draw(canvas)

            // 绘制艺术双引号
            if (style.showQuoteMark) {
                val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.fontColor
                    setAlpha(QUOTE_ALPHA)
                    textSize = style.fontSizeSp * scale * QUOTE_MARK_FONT_SIZE_SCALE
                    typeface = android.graphics.Typeface.create(style.font.getTypeface(context), android.graphics.Typeface.BOLD)
                }

                // 左上双引号
                val leftQuoteX = -12f * densityScale
                val leftQuoteY = 24f * densityScale
                canvas.drawText("“", leftQuoteX, leftQuoteY, quotePaint)

                // 右下双引号
                val rightQuoteX = textWidth - 28f * densityScale
                val rightQuoteY = totalContentHeight + 8f * densityScale
                canvas.drawText("”", rightQuoteX, rightQuoteY, quotePaint)
            }

            canvas.restore()

            // 小霸王游戏机：扫描线 + 暗角 + 玻璃反光压在文字之上，
            // 让文字看起来是"透过显像管玻璃"看到的，而不是贴在图上
            if (style.shape == WidgetShape.SUBOR_CONSOLE) {
                drawSuborCrtOverlay(canvas, suborScreenRect(outerRect), densityScale)
            }

            // C. 绘制手账胶带贴纸 (HANDBOOK_TAPE)
            if (style.shape == WidgetShape.HANDBOOK_TAPE) {
                drawTape(canvas, 24f * densityScale, 16f * densityScale, 70f * densityScale, 18f * densityScale, -18f, Color.parseColor("#80FFF176"), densityScale) // 左上角黄胶带
                drawTape(canvas, targetWidth - 24f * densityScale, targetHeight - 16f * densityScale, 70f * densityScale, 18f * densityScale, 18f, Color.parseColor("#80FF8A80"), densityScale) // 右下角粉胶带
            }

        return bitmap
    }

    // 图文明信片：背景插画所在的区域。下半（左右分割时为右半）留给代码绘制的文字显示区，
    // 底色铺图与图片绘制共用同一份矩形，避免两处比例各写一遍后失配。
    private fun splitImageRect(shape: WidgetShape, outerRect: RectF): RectF {
        return if (shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
            RectF(
                outerRect.left,
                outerRect.top,
                outerRect.left + outerRect.width() * SPLIT_CARD_HORIZONTAL_RATIO,
                outerRect.bottom
            )
        } else {
            RectF(
                outerRect.left,
                outerRect.top,
                outerRect.right,
                outerRect.top + outerRect.height() * SPLIT_CARD_RATIO
            )
        }
    }

    // 贴纸夜景：落地线的高度（= 文本框上沿 = 贴纸脚底所在位置）
    private fun stickerGroundY(outerRect: RectF): Float =
        outerRect.top + outerRect.height() * STICKER_GROUND_RATIO

    // 贴纸夜景：文本框矩形——上沿就是人物与路灯的落地线
    private fun stickerTextBoxRect(outerRect: RectF): RectF = RectF(
        outerRect.left + outerRect.width() * STICKER_TEXT_LEFT_RATIO,
        outerRect.top + outerRect.height() * STICKER_TEXT_TOP_RATIO,
        outerRect.left + outerRect.width() * STICKER_TEXT_RIGHT_RATIO,
        outerRect.top + outerRect.height() * STICKER_TEXT_BOTTOM_RATIO
    )

    // 贴纸夜景：贴纸（人物+路灯）的绘制矩形——按高度等比缩放、脚底压进路面上沿、水平居中
    private fun stickerArtRect(outerRect: RectF): RectF {
        val feet = stickerGroundY(outerRect) + outerRect.height() * STICKER_ART_SINK_RATIO
        val h = outerRect.height() * STICKER_ART_HEIGHT_RATIO
        val w = h * STICKER_ART_ASPECT
        val cx = outerRect.centerX()
        return RectF(cx - w / 2f, feet - h, cx + w / 2f, feet)
    }

    // 城市微缩：这个风格用哪种衔接。按素材名认领——素材就是那座城市的长相，
    // 衔接得跟着材质走：山地古城像从地里挖出来的，配土层剖面；
    // 玻璃楼群立在水边上，配江面倒影。认不出来的（用户自定义抠图）走 FADE，
    // 不假造任何材质，只把城市底边柔进背景色。
    private fun cityJunctionOf(style: WidgetStyle): CityJunction =
        when (style.presetImageResName) {
            "shanghai_city_cutout" -> CityJunction.WATER
            // 浙江素材是一整座**飘着的岛**：底边本来就是不规则轮廓，没有"城 ↔ 地/水"的
            // 平直接缝，硬接土层或倒影反而会在岛底两侧造出假材质。走 FADE，
            // 只把岛底柔进文字栏底色，读起来就是一座浮在夜色里的微缩浙江。
            "zhejiang_city_cutout" -> CityJunction.FADE
            // 北京素材底边是模型底座的一条平直边缘，底下没有"地/水"要接，
            // 且文字栏已经取了底座同色 —— 走 FADE 让底座柔进文字栏，接缝直接消失。
            "beijing_city_cutout" -> CityJunction.FADE
            else -> CityJunction.FADE
        }

    // 城市微缩：城市可占的矩形——通栏铺满宽度，**底边就是衔接线**。
    // 高度按衔接各自定：土层要留出剖面层，水面把城市压到水线上沿，
    // FADE 则按素材比例反推"铺满整宽"需要多高（见 fadeArtHeight）。
    private fun cityArtRect(
        outerRect: RectF,
        junction: CityJunction,
        style: WidgetStyle,
        densityScale: Float,
        artBitmap: Bitmap?
    ): RectF {
        val artH = when (junction) {
            CityJunction.SOIL -> outerRect.height() * SOIL_ART_RATIO
            CityJunction.WATER -> outerRect.height() * WATER_ART_RATIO
            CityJunction.FADE -> fadeArtHeight(outerRect, style, densityScale, artBitmap)
        }.coerceAtLeast(1f)
        return RectF(outerRect.left, outerRect.top, outerRect.right, outerRect.top + artH)
    }

    // FADE 衔接：城市矩形要多高，素材才能按原始宽高比**铺满整宽**。
    //
    // 铺满不是白给的——城市越高，留给文字区的高度越少，所以给它一道下限：
    // 至少放得下一行正文（按当前字号 + 上下内边距估算）。够，就把城市顶到满宽；
    // 不够，就退回等比留白（cityArtDst 的 contain 会自然接管，宁可两侧露壁纸
    // 也不把天际线切平）。
    //
    // 同时不允许比 FADE_ART_RATIO 更矮：4×4 这类高组件本来就已铺满，
    // 别反过来把它改小、把城市往上挪。
    private fun fadeArtHeight(
        outerRect: RectF,
        style: WidgetStyle,
        densityScale: Float,
        artBitmap: Bitmap?
    ): Float {
        val h = outerRect.height()
        val floor = h * FADE_ART_RATIO
        if (artBitmap == null || artBitmap.height <= 0 || outerRect.width() <= 0f) return floor
        val need = outerRect.width() / (artBitmap.width.toFloat() / artBitmap.height)
        val oneLine = style.fontSizeSp * densityScale * FADE_LINE_HEIGHT_FACTOR +
            2f * CITY_TEXT_PAD_Y_DP * densityScale
        val cap = (h - h * CITY_FADE_BAND_RATIO - oneLine).coerceAtLeast(floor)
        return need.coerceIn(floor, cap)
    }

    // 城市微缩：衔接带高度（衔接线 → 文字可用顶边）。文字不许压进倒影/剖面层，
    // 所以取这条带的最低点：土层轮廓的起伏谷底、倒影带的最下沿。
    private fun cityBand(junction: CityJunction, outerRect: RectF, densityScale: Float): Float =
        when (junction) {
            CityJunction.SOIL -> {
                val wall = soilWall(outerRect)
                wall + soilWallAmp(wall, densityScale) / 2f
            }
            CityJunction.WATER -> outerRect.height() * WATER_TEXT_BAND_RATIO
            CityJunction.FADE -> outerRect.height() * CITY_FADE_BAND_RATIO
        }

    // 城市剪影：地层厚度（px）——严格占组件高度的 5%（配比硬要求，不再设 dp 上下限：
    // 一旦设下限，4×2 那种扁组件的地层会被顶到 16% 以上，把文字区挤掉）
    private fun soilWall(outerRect: RectF): Float {
        return (outerRect.height() * SOIL_WALL_RATIO).coerceAtLeast(1f)
    }

    // 城市剪影：地层下沿轮廓的起伏幅度（px）
    private fun soilWallAmp(wall: Float, densityScale: Float): Float {
        val wallDp = wall / densityScale
        return (wallDp * SOIL_RIDGE_AMP_RATIO)
            .coerceIn(SOIL_RIDGE_AMP_MIN_DP, SOIL_RIDGE_AMP_MAX_DP) * densityScale
    }

    // 城市剪影：正文区矩形——地层（草皮 + 浅壤）之下，通栏到组件底部。
    // 传入的地层厚度是 wall + amp/2（轮廓最低点），这样文字绝不会压到浅壤上。
    private fun cityTextBoxRect(outerRect: RectF, artRect: RectF, band: Float): RectF = RectF(
        outerRect.left,
        artRect.bottom + band,
        outerRect.right,
        outerRect.bottom
    )

    // 城市剪影：底部两角按用户的圆角设置收圆，上沿（土层顶边）保持直角
    private fun cityBarPath(outerRect: RectF, top: Float, radius: Float): Path {
        val box = RectF(outerRect.left, top, outerRect.right, outerRect.bottom)
        val path = Path()
        val r = radius.coerceIn(0f, minOf(box.width(), box.height()) / 2f)
        if (r <= 0f) {
            path.addRect(box, Path.Direction.CW)
        } else {
            // 圆角数组顺序：左上x,左上y, 右上x,右上y, 右下x,右下y, 左下x,左下y
            path.addRoundRect(box, floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r), Path.Direction.CW)
        }
        return path
    }

    // 城市剪影：土层轮廓的取值——fract(sin(i·12.9898 + seed·78.233)·43758.5453)。
    // 与出图脚本 tools/juge_widget_cutout_preview.py 是同一算式、同一 seed，
    // 所以离线预览和这里跑出来是同一条曲线、同一批颗粒位置。
    private fun cityHash(i: Int, salt: Float = 0f): Float {
        val x = Math.sin(i * 12.9898 + (CITY_NOISE_SEED + salt) * 78.233) * 43758.5453
        return (x - Math.floor(x)).toFloat()
    }

    // 城市剪影：地层轮廓的块状函数 ∈[0,1]——块内基本恒定，块间窄带 smoothstep 陡降，
    // 形成"平顶块 + 陡壁 + 台阶"。幂次拉伸(hash^1.6)让多数块平缓、少数块明显下垂，
    // 等幅方波太机械，真实的地块断面就是几块深的夹着几块浅的。
    private fun ridgeBlock(x: Float, step: Float, edge: Float): Float {
        val t = x / step - 0.5f
        val i = Math.floor(t.toDouble()).toInt()
        val f = (t - i).coerceIn(0f, 1f)
        val a = Math.pow(cityHash(i, 21f).toDouble(), 1.6).toFloat()
        val b = Math.pow(cityHash(i + 1, 21f).toDouble(), 1.6).toFloat()
        if (f <= 1f - edge) return a
        val s = (f - (1f - edge)) / edge
        return a + (b - a) * (s * s * (3f - 2f * s))
    }

    /**
     * 城市剪影：地层下沿轮廓的偏移数组（相对基准线的 px 偏移，中心为 0）。
     *
     * 粗块（大起伏，决定地层的块状）+ 细块（碎起伏，让下沿不平板）叠加后做滑动平均，
     * 把方波磨圆 —— 不磨是城墙垛口，磨过之后才像被切开的下垂地块。
     *
     * 走固定 seed 的整数哈希而不是 Random：① 每次渲染同一条轮廓，桌面组件不会抖；
     * ② 与出图脚本 tools/juge_widget_cutout_terrain.py 同算式同 seed，离线预览即真机。
     */
    private fun soilRidgeWiggle(
        width: Float,
        sample: Float,
        amp: Float,
        coarseStep: Float,
        fineStep: Float,
        mix: Float,
        smoothPx: Float
    ): FloatArray {
        val count = (width / sample).toInt() + 2
        val raw = FloatArray(count)
        for (i in 0 until count) {
            val x = i * sample
            raw[i] = ridgeBlock(x, coarseStep, SOIL_RIDGE_EDGE) * mix +
                ridgeBlock(x, fineStep, 0.28f) * (1f - mix)
        }
        val win = (smoothPx / sample / 2f).toInt().coerceAtLeast(1)
        val out = FloatArray(count)
        for (i in 0 until count) {
            val lo = (i - win).coerceAtLeast(0)
            val hi = (i + win + 1).coerceAtMost(count)
            var sum = 0f
            for (k in lo until hi) sum += raw[k]
            out[i] = (sum / (hi - lo) - 0.5f) * amp
        }
        return out
    }

    // 城市剪影：由轮廓偏移数组派生一条地层分界线（基准 depth + 偏移 × 衰减）
    private fun strataEdgeYs(baseY: Float, depth: Float, wiggle: FloatArray, scale: Float): FloatArray {
        val out = FloatArray(wiggle.size)
        for (i in wiggle.indices) out[i] = baseY + depth + wiggle[i] * scale
        return out
    }

    // 城市剪影：由一条地层分界线生成"该线以下"的区域路径
    private fun strataRegionPath(
        edgeYs: FloatArray,
        left: Float,
        sample: Float,
        right: Float,
        bottom: Float
    ): Path {
        val p = Path()
        for (i in edgeYs.indices) {
            val x = (left + i * sample).coerceAtMost(right)
            val y = edgeYs[i]
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.lineTo(right, bottom)
        p.lineTo(left, bottom)
        p.close()
        return p
    }

    // 城市剪影：两条地层分界线之间的带状路径（草皮带 / 浅壤带 / 轮廓暗边）
    private fun strataBandPath(
        topYs: FloatArray,
        botYs: FloatArray,
        left: Float,
        sample: Float,
        right: Float
    ): Path {
        val p = Path()
        for (i in topYs.indices) {
            val x = (left + i * sample).coerceAtMost(right)
            val y = topYs[i]
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        val n = minOf(topYs.size, botYs.size)
        for (i in (n - 1) downTo 0) {
            val x = (left + i * sample).coerceAtMost(right)
            p.lineTo(x, botYs[i])
        }
        p.close()
        return p
    }

    /**
     * 江西·土层剖面：城市底边往下接「草皮 → 浅壤 → 深土」，
     * 最深一层用「小组件背景颜色」，文字就落在这一层里。
     * 这是**江西这一个风格**的衔接，不是城市类的通用做法——上海那版走水面。
     *
     * 整段地层只占组件高度的 5%（`SOIL_WALL_RATIO`），城市 50%、文字 45%。
     * 因为地层薄，腔体内的每一层都要按比例收：草皮 20%、接触阴影 ≤ 草皮 70%、
     * 过渡带 ≤ 草皮 45%，否则 1% 组件高的草皮会被上面几层叠满、绿意全无。
     *
     * 三条分界线共用同一条**块状轮廓偏移**（等厚地层），文字区的上边就是最外面那条轮廓，
     * 所以"文字框的上边形状"等于地块的断面形状 —— 两段是同一块地块，不会各说各话。
     *
     * 与更早版本（起伏做在土层顶边）的根本区别：**起伏搬到了下沿**。
     * 上沿严格平直、紧贴城市底边，再用素材底边色做一段无缝过渡，城市与地层才是一个整体。
     * 深土里再叠底部压暗 / 等厚沉积层理 / 土壤颗粒，避免一大块纯色显得空。
     */
    private fun drawSoilStrata(
        canvas: Canvas,
        outerRect: RectF,
        artRect: RectF,
        bgBitmap: Bitmap?,
        densityScale: Float,
        style: WidgetStyle,
        deepPaint: Paint
    ) {
        val left = outerRect.left
        val right = outerRect.right
        val bottom = outerRect.bottom
        val baseY = artRect.bottom
        val wall = soilWall(outerRect)
        val grassH = wall * SOIL_GRASS_RATIO
        val amp = soilWallAmp(wall, densityScale)
        val sample = SOIL_RIDGE_SAMPLE_DP * densityScale
        val soilAlpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (soilAlpha <= 0) return

        val wiggle = soilRidgeWiggle(
            outerRect.width(), sample, amp,
            SOIL_RIDGE_BLOCK_DP * densityScale,
            SOIL_RIDGE_FINE_BLOCK_DP * densityScale,
            SOIL_RIDGE_COARSE_MIX,
            SOIL_RIDGE_SMOOTH_DP * densityScale
        )
        val flatYs = FloatArray(wiggle.size) { baseY }
        // 草皮的起伏衰减：凸处草皮薄、凹处厚，符合真实剖面
        val grassYs = strataEdgeYs(baseY, grassH, wiggle, SOIL_GRASS_RIDGE_SCALE)
        val wallYs = strataEdgeYs(baseY, wall, wiggle, 1f)
        val deepTop = baseY + wall + amp / 2f

        canvas.save()
        // 裁剪范围包住接触阴影（在 baseY 之下）与底部两角圆角
        canvas.clipPath(
            cityBarPath(outerRect, baseY - 1f, style.cornerRadiusDp * densityScale)
        )

        // 1. 深土（= 文字区底色）：沿最外层轮廓以下铺满
        canvas.drawPath(strataRegionPath(wallYs, left, sample, right, bottom), deepPaint)

        // 2. 浅壤带：[baseY, 最外层轮廓]
        val topsoilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOIL_TOPSOIL_COLOR
            alpha = soilAlpha
        }
        canvas.drawPath(strataBandPath(flatYs, wallYs, left, sample, right), topsoilPaint)

        // 3. 草皮带：[baseY, 草皮分界]
        val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOIL_GRASS_COLOR
            alpha = soilAlpha
        }
        canvas.drawPath(strataBandPath(flatYs, grassYs, left, sample, right), grassPaint)

        // 4. 素材底边色 → 草皮色的无缝过渡，藏掉"插画底边"与"地层顶边"之间那条直切。
        //    高度被草皮高度夹死在 45% 以内 —— 5% 地层里草皮只有组件高的 1%，
        //    过渡带一旦超过草皮就会把整条草皮盖成素材的灰绿色。
        //    横向只铺**素材实际落位**那一段：素材等比 contain 后比组件窄时，
        //    把素材底行拉满全宽等于把过渡色涂到壁纸上去。
        val transH = minOf(SOIL_EDGE_TRANS_DP * densityScale, grassH * 0.45f)
        val artSpan = if (bgBitmap != null) cityArtDst(bgBitmap, artRect) else outerRect
        if (bgBitmap != null && transH > 1f) {
            drawSoilBottomFade(
                canvas, bgBitmap, artSpan.left, baseY, artSpan.right, transH, soilAlpha)
        }

        // 5. 接触阴影：城市底边往下压几 px 淡暗色，越靠上越深。
        //    同样被草皮高度夹住，否则阴影会吃掉整条草皮；横向同样只压城市那一档
        val shadowH = minOf(SOIL_SHADOW_DP * densityScale, grassH * 0.7f)
        val shadowSteps = SOIL_SHADOW_STEPS
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (k in 0 until shadowSteps) {
            val a = SOIL_SHADOW_MAX_ALPHA *
                Math.pow((1.0 - k.toDouble() / shadowSteps), 1.6)
            shadowPaint.color = SOIL_SHADOW_COLOR
            shadowPaint.alpha = (a * soilAlpha / 255.0).toInt()
            canvas.drawRect(
                artSpan.left,
                baseY + shadowH * k / shadowSteps,
                artSpan.right,
                baseY + shadowH * (k + 1) / shadowSteps,
                shadowPaint
            )
        }

        // 6. 地层外轮廓的暗边：沿最外层轮廓往上叠渐暗，给下垂的凸块做出体积。
        //    只压最外层 —— 草皮与浅壤之间的分界要保持干净，两条线都压就糊成一片。
        val shadeH = minOf(SOIL_EDGE_SHADE_DP * densityScale, (wall - grassH) * 0.9f)
        val shadeSteps = SOIL_EDGE_SHADE_STEPS
        val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (k in 0 until shadeSteps) {
            val a = SOIL_EDGE_SHADE_ALPHA *
                Math.pow((1.0 - k.toDouble() / shadeSteps), 1.4)
            shadePaint.color = SOIL_SHADOW_COLOR
            shadePaint.alpha = (a * soilAlpha / 255.0).toInt()
            val outer = -shadeH * k / shadeSteps
            val inner = -shadeH * (k + 1) / shadeSteps
            canvas.drawPath(
                strataBandPath(
                    FloatArray(wallYs.size) { wallYs[it] + inner },
                    FloatArray(wallYs.size) { wallYs[it] + outer },
                    left, sample, right
                ),
                shadePaint
            )
        }

        // 7. 深土区质感：底部压暗 → 等厚沉积层理 → 土壤颗粒
        val regionH = bottom - deepTop
        if (regionH > 6f * densityScale) {
            val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, deepTop, 0f, bottom,
                    Color.TRANSPARENT,
                    Color.argb(SOIL_VIGNETTE_ALPHA, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(strataRegionPath(wallYs, left, sample, right, bottom), vPaint)

            val lines = (regionH / (SOIL_STRATA_SPACING_DP * densityScale))
                .toInt().coerceIn(2, 8)
            val strataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = SOIL_DARK_GRAIN
                alpha = (SOIL_STRATA_ALPHA * soilAlpha / 255f).toInt()
                strokeWidth = 1.2f * densityScale
                this.style = Paint.Style.STROKE
            }
            for (k in 1..lines) {
                val y0 = deepTop + regionH * k / (lines + 1f)
                val path = Path()
                for (i in wallYs.indices) {
                    val x = (left + i * sample).coerceAtMost(right)
                    // 层理跟随地层下沿起伏但幅度衰减一半：看上去就是一套等厚地层
                    val y = (y0 + (wallYs[i] - baseY - wall) * SOIL_STRATA_FOLLOW)
                        .coerceIn(deepTop, bottom)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, strataPaint)
            }

            val box = SOIL_SPECKLE_BOX_DP * densityScale
            val radius = SOIL_SPECKLE_RADIUS_DP * densityScale
            val specklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (SOIL_SPECKLE_ALPHA * soilAlpha / 255f).toInt()
            }
            var j = 0
            var y = deepTop
            while (y < bottom) {
                var i = 0
                var x = left
                while (x < right) {
                    if (cityHash(i * 31 + j * 977, 7f) <= SOIL_SPECKLE_DENSITY) {
                        val px = x + cityHash(i * 13 + j * 71, 11f) * box
                        val py = y + cityHash(i * 17 + j * 53, 13f) * box
                        if (py < bottom) {
                            specklePaint.color = if (cityHash(i * 7 + j * 19, 17f) > 0.5f) {
                                SOIL_LIGHT_GRAIN
                            } else {
                                SOIL_DARK_GRAIN
                            }
                            canvas.drawCircle(px, py, radius, specklePaint)
                        }
                    }
                    i++
                    x += box
                }
                j++
                y += box
            }
        }
        canvas.restore()
    }

    /**
     * 上海·水面与倒影：城市底边就是水线，水线以下是一整片江面，文字浮在水面上。
     *
     * 为什么这里不用土层：这张图是玻璃幕墙的陆家嘴，把它"从地里整块挖出来"会读成
     * 一截断头楼坐在土上，很突兀。而真实建筑沙盘就是立在水景台座上的。
     * 水也顺带解决了土层那个通栏难题 —— 江面本来就比城市宽，铺满组件宽度是成立的；
     * 土层铺满则会在城市两侧露出"飘在壁纸上的一条草皮"。
     *
     * 四层：水体竖向渐变 → 城市下部镜像压扁成倒影 → 横向波纹把倒影打断 → 底部压暗托字。
     */
    private fun drawCityWater(
        canvas: Canvas,
        outerRect: RectF,
        artRect: RectF,
        art: Bitmap?,
        densityScale: Float,
        style: WidgetStyle,
        deepPaint: Paint
    ) {
        val left = outerRect.left
        val right = outerRect.right
        val bottom = outerRect.bottom
        val line = artRect.bottom
        val waterH = bottom - line
        val alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (waterH <= 1f || alpha <= 0) return
        val deepColor = deepPaint.color and 0x00FFFFFF

        canvas.save()
        canvas.clipPath(cityBarPath(outerRect, line - 1f, style.cornerRadiusDp * densityScale))

        // 1. 水体：水线处偏亮的江面 → 组件背景色那池深水
        val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, line, 0f, bottom, WATER_SURFACE_COLOR, deepColor, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawRect(left, line, right, bottom, waterPaint)

        // 2. 倒影：城市下部竖压 + 镜像贴在水线下方，只占城市那一档宽度
        if (art != null && !art.isRecycled) {
            val span = cityArtDst(art, artRect)
            val srcH = (art.height * WATER_REFLECT_SRC_RATIO).toInt().coerceIn(1, art.height)
            val reflectH = outerRect.height() * WATER_REFLECT_RATIO
            val flip = Matrix().apply {
                setScale(1f, -1f)
                postTranslate(0f, srcH.toFloat())
            }
            val band = Bitmap.createBitmap(art, 0, art.height - srcH, art.width, srcH, flip, true)
            try {
                canvas.drawBitmap(
                    band, null, RectF(span.left, line, span.right, line + reflectH),
                    Paint(Paint.FILTER_BITMAP_FLAG).apply {
                        this.alpha = (WATER_REFLECT_ALPHA * alpha / 255f).toInt()
                    }
                )
                // 倒影向下溶解进水里：再压一层水色渐变，越往下越实
                val dissolve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, line, 0f, line + reflectH,
                        Color.TRANSPARENT, deepColor, Shader.TileMode.CLAMP
                    )
                    this.alpha = alpha
                }
                canvas.drawRect(span.left, line, span.right, line + reflectH, dissolve)
            } finally {
                if (band !== art && !band.isRecycled) band.recycle()
            }
        }

        // 3. 波纹：横向亮线。越往下越宽、越淡、越粗——近处水面才看得清纹理
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WATER_HILITE_COLOR
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val width = right - left
        for (k in 0 until WATER_RIPPLE_COUNT) {
            val t = (k + 1f) / (WATER_RIPPLE_COUNT + 1f)
            val y = line + waterH * t
            val cx = (left + right) / 2f +
                (cityHash(k * 71, WATER_RIPPLE_SALT) - 0.5f) * width * 0.5f
            val len = width * (0.16f + 0.40f * cityHash(k * 13 + 5, WATER_RIPPLE_SALT))
            ripplePaint.strokeWidth = WATER_RIPPLE_THIN_DP * densityScale * (1f + t)
            ripplePaint.alpha = (WATER_RIPPLE_ALPHA * (1f - 0.55f * t) * alpha / 255f).toInt()
            canvas.drawLine(cx - len / 2f, y, cx + len / 2f, y, ripplePaint)
        }

        // 4. 水线：城市与江面交界那道亮边，把"贴在图上"变成"浮在水上"
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WATER_HILITE_COLOR
            this.alpha = (WATER_LINE_ALPHA * alpha / 255f).toInt()
            strokeWidth = 1.2f * densityScale
            this.style = Paint.Style.STROKE
        }
        canvas.drawLine(left, line, right, line, linePaint)

        // 5. 底部压暗：深水托住文字，长句也不会和波纹抢对比度
        val vigTop = line + waterH * 0.35f
        val vig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, vigTop, 0f, bottom,
                Color.TRANSPARENT, Color.argb(WATER_VIGNETTE_ALPHA, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(left, vigTop, right, bottom, vig)

        canvas.restore()
    }

    /**
     * 兜底衔接：素材名认不出是哪座城（用户自定义抠图）时，不假造任何材质 ——
     * 文字栏刷成背景色，城市底边往下压一段柔和暗裙就当它坐在那儿。
     */
    private fun drawCityFade(
        canvas: Canvas,
        outerRect: RectF,
        artRect: RectF,
        densityScale: Float,
        style: WidgetStyle,
        deepPaint: Paint
    ) {
        val line = artRect.bottom
        val alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        canvas.save()
        canvas.clipPath(cityBarPath(outerRect, line - 1f, style.cornerRadiusDp * densityScale))
        canvas.drawRect(outerRect.left, line, outerRect.right, outerRect.bottom, deepPaint)
        val bandH = outerRect.height() * CITY_FADE_BAND_RATIO
        val skirt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, line, 0f, line + bandH,
                Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawRect(outerRect.left, line, outerRect.right, line + bandH, skirt)
        canvas.restore()
    }

    /**
     * 江西·土层：取素材最底一行的颜色拉成一条色带，再向下渐入草皮色。
     * 城市底边（水面 / 广场 / 道路）与草皮之间的那条硬切就藏在这段过渡里。
     */
    private fun drawSoilBottomFade(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        height: Float,
        alpha: Int
    ) {
        val bw = bitmap.width
        val bh = bitmap.height
        val w = (right - left).toInt()
        val h = height.toInt()
        if (bw < 2 || bh < 2 || w < 2 || h < 1) return
        val key = System.identityHashCode(bitmap) * 31 + w * 1009 + h
        var band = soilFadeBitmap
        if (band == null || soilFadeKey != key) {
            val row = IntArray(bw)
            bitmap.getPixels(row, 0, bw, 0, bh - 1, bw, 1)
            val rowBmp = Bitmap.createBitmap(bw, 1, Bitmap.Config.ARGB_8888)
            rowBmp.setPixels(row, 0, bw, 0, 0, bw, 1)
            band = Bitmap.createScaledBitmap(rowBmp, w, h, true)
            rowBmp.recycle() // 只喂给 createScaledBitmap，没进过 Canvas，可安全回收
            soilFadeBitmap = band
            soilFadeKey = key
        }
        val fadeBand = band ?: return
        val dst = RectF(left, top, right, top + h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        canvas.drawBitmap(fadeBand, null, dst, paint)
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, top, 0f, top + h,
                Color.TRANSPARENT, SOIL_GRASS_COLOR, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawRect(dst, fade)
    }

    // 城市微缩：素材等比 contain 进城市矩形之后的**实际落位**——底边贴住衔接线、水平居中。
    // 土层的水线过渡带要按这个矩形铺，不能按整幅组件宽铺：素材左右留白时，
    // 把素材底行拉满全宽会让过渡色跑到壁纸上去。
    private fun cityArtDst(bitmap: Bitmap, rectF: RectF): RectF {
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0 || rectF.width() <= 0f || rectF.height() <= 0f) return rectF
        val scale = minOf(rectF.width() / bw, rectF.height() / bh)
        val w = bw * scale
        val h = bh * scale
        return RectF(rectF.centerX() - w / 2f, rectF.bottom - h, rectF.centerX() + w / 2f, rectF.bottom)
    }

    // 城市微缩：把素材**等比 contain** 进城市矩形，底边贴住衔接线、水平居中。
    //
    // 为什么不再裁高度：剪影的全部价值在于天际线是不规则的。一旦按宽度铺满、
    // 把多出来的高度从顶部切平，天线和楼顶就没了，天空抠得再干净也看不出镂空。
    // 所以素材比例与城市矩形不合时，宁可左右留白交给壁纸（读起来仍是"一座飘着的城市"），
    // 也不动天际线一根。
    //
    // 刻意跳过 detectLightBorder：那个检测器是给"自带白色相框的方形明信片插画"准备的，
    // 它只看左右边缘中线是否近白(RGB>228)，而剪影素材的边缘就是城市像素，
    // 一旦某块楼体/广场恰好是浅色就会被判成留白、从两侧往内裁。
    private fun drawCityArt(canvas: Canvas, bitmap: Bitmap, rectF: RectF, style: WidgetStyle) {
        val dst = cityArtDst(bitmap, rectF)
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    // 城市微缩：哪些素材需要"垫平底边"。
    //
    // 抠图后素材底边有两种：
    //   ① 噪声化的半透明软边（棋盘格抠图的残留，北京就是这种）—— 底边不是轮廓，是脏边。
    //      按像素底边对齐衔接线，这段透明区就在模型与文字栏之间露出一条壁纸，看着像上下没接上。
    //   ② 真实的轮廓（浙江是一整座飘着的岛，底边就是岛缘）—— 那是造型本身，垫平会把它拉成方块。
    // 所以只有 ① 走垫平，② 保持悬空。
    private fun cityNeedsBottomPad(style: WidgetStyle): Boolean =
        when (style.presetImageResName) {
            "beijing_city_cutout" -> true
            else -> false
        }

    // 城市微缩：把素材底边的透明垫高区垫平——画在模型之下、文字栏之上，
    // 用文字栏底色把壁纸挡掉，模型底座看起来就直接"坐"在文字栏上。
    //
    // 填的是**文字栏底色**而不是逐列取模型底边色：模型底边是噪声软边，
    // 逐列取色会把底座边缘那条朱红宫墙也一路拉成竖条（一道一道的脏streak），
    // 反而比缝隙更显眼。统一底色等于让底座边缘直接融进文字栏，正是这个风格要的"无分界"。
    private fun drawCityBottomPad(canvas: Canvas, bitmap: Bitmap, rectF: RectF, style: WidgetStyle) {
        val fillColor = if (WidgetStyle.supportsBackgroundColor(style.shape)) {
            style.backgroundColor
        } else {
            Color.TRANSPARENT
        }
        if (Color.alpha(fillColor) <= 0) return
        val pad = cityPadBand(bitmap) ?: return
        if (pad.isRecycled) return
        val dst = cityArtDst(bitmap, rectF)
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val scale = dst.height() / bitmap.height
        val top = dst.bottom - pad.height * scale
        if (top >= dst.bottom - 0.5f) return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
            colorFilter = PorterDuffColorFilter(fillColor, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(pad, null, RectF(dst.left, top, dst.right, dst.bottom), paint)
    }

    // 城市微缩：生成"底边垫平带"的**形状蒙版**——宽 = 素材宽，
    // 高 = 整幅最高的一条底边到素材底边的距离。有内容的列整列填白，没内容的列留透明，
    // 所以带子只覆盖模型所在的横向范围；拉伸后由 paint 的 ColorFilter 统一上色。
    private fun cityPadBand(bitmap: Bitmap): Bitmap? {
        val key = System.identityHashCode(bitmap) * 31 + bitmap.width * 1009 + bitmap.height
        cityPadBitmap?.let { if (!it.isRecycled && cityPadKey == key) return it }

        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 1 || bh <= 2) return null

        val rowBuf = IntArray(bw)
        val hasContent = BooleanArray(bw)
        var remaining = bw
        var minBottom = -1
        var y = bh - 1
        while (y >= 0 && remaining > 0) {
            bitmap.getPixels(rowBuf, 0, bw, 0, y, bw, 1)
            var foundThisRow = false
            for (x in 0 until bw) {
                if (!hasContent[x] && (rowBuf[x] ushr 24) >= CITY_PAD_ALPHA_MIN) {
                    hasContent[x] = true
                    remaining--
                    foundThisRow = true
                }
            }
            if (foundThisRow) minBottom = y
            y--
        }
        if (minBottom < 0) return null

        // 兜底：个别列只有一根细高物、底边高得离谱时，别把带子撑到半张素材高
        val padRows = (bh - 1 - minBottom).coerceAtMost(bh / 4)
        if (padRows <= 0) return null

        val pixels = IntArray(bw * padRows)
        for (x in 0 until bw) {
            if (!hasContent[x]) continue
            for (r in 0 until padRows) {
                pixels[r * bw + x] = 0xFFFFFFFF.toInt()
            }
        }

        val band = Bitmap.createBitmap(bw, padRows, Bitmap.Config.ARGB_8888)
        band.setPixels(pixels, 0, bw, 0, 0, bw, padRows)
        cityPadKey = key
        cityPadBitmap = band
        return band
    }

    /**
     * 贴纸夜景：文本框的纸片轮廓。
     *
     * 圆角滑条（cornerRadiusDp）作用于文本框四角：>0 时四角走圆弧，半径 = 剪纸口的收角幅度 + 滑条值，
     * 这样滑条从 0 到 30 全程都在生效；=0 时保持直角，只留四角那一刀斜切。
     */
    private fun paperCutBoxPath(rect: RectF, snip: Float, cornerRadius: Float): Path {
        val minSide = minOf(rect.width(), rect.height())
        return Path().apply {
            if (cornerRadius > 0f) {
                val r = (snip + cornerRadius).coerceAtMost(minSide * 0.5f)
                addRoundRect(rect, r, r, Path.Direction.CW)
            } else {
                val s = snip.coerceAtMost(minSide * 0.25f)
                moveTo(rect.left + s, rect.top)
                lineTo(rect.right - s, rect.top)
                lineTo(rect.right, rect.top + s)
                lineTo(rect.right, rect.bottom - s)
                lineTo(rect.right - s, rect.bottom)
                lineTo(rect.left + s, rect.bottom)
                lineTo(rect.left, rect.bottom - s)
                lineTo(rect.left, rect.top + s)
                close()
            }
        }
    }

    // 纸纹贴片只生成一次，之后复用
    private var paperGrainCache: Bitmap? = null

    /**
     * 贴纸夜景：纸纹贴片。
     * 细密噪点 + 短纤维，按 REPEAT 平铺，纸面才不是一块干净的单色。
     */
    private fun paperGrainBitmap(): Bitmap {
        paperGrainCache?.let { if (!it.isRecycled) return it }
        val size = STICKER_PAPER_GRAIN_TILE
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rnd = Random(20261001L)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val roll = rnd.nextInt(100)
            pixels[i] = when {
                roll < 30 -> (10 + rnd.nextInt(28)) shl 24 // 暗点
                roll < 46 -> ((8 + rnd.nextInt(20)) shl 24) or 0xFFFFFF // 亮点
                else -> 0
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)

        // 短纤维：纸浆的走向，比纯噪点更像纸
        val fiberCanvas = Canvas(bmp)
        val fiber = Paint().apply {
            strokeWidth = 1f
            strokeCap = Paint.Cap.ROUND
        }
        repeat(46) {
            val light = rnd.nextBoolean()
            fiber.color = ((6 + rnd.nextInt(16)) shl 24) or if (light) 0xFFFFFF else 0x000000
            val x = rnd.nextInt(size).toFloat()
            val y = rnd.nextInt(size).toFloat()
            val len = 5f + rnd.nextInt(16)
            val drift = (rnd.nextFloat() - 0.5f) * 0.5f * len
            fiberCanvas.drawLine(x, y, x + len, y + drift, fiber)
        }
        paperGrainCache = bmp
        return bmp
    }

    /** 贴纸夜景：铺在文本框上的纸纹画笔。贴片按原尺寸平铺，颗粒才够细，不会变成噪点。 */
    private fun paperGrainPaint(alpha: Int): Paint {
        val shader = BitmapShader(paperGrainBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            this.alpha = alpha
        }
    }

    /**
     * 贴纸夜景：人物与路灯踩在纸上的接触阴影。
     * 白描边贴纸直接压在纸面上容易"浮"起来，脚底压一层软阴影才站得住。
     * 阴影只画在纸片轮廓内，不会溢出纸外。
     */
    private fun drawStickerContactShadow(canvas: Canvas, textBox: RectF, paperPath: Path, artRect: RectF, alpha: Int) {
        val feetY = artRect.bottom
        val coupleCx = artRect.left + artRect.width() * STICKER_COUPLE_CX_RATIO
        val rx = artRect.width() * 0.28f
        val ry = textBox.height() * 0.055f

        val layer = canvas.save()
        canvas.clipPath(paperPath)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                coupleCx, feetY, rx,
                intArrayOf(0x66000000, 0x00000000), null, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        val ellipse = canvas.save()
        canvas.scale(1f, ry / rx, coupleCx, feetY)
        canvas.drawCircle(coupleCx, feetY, rx, paint)
        canvas.restoreToCount(ellipse)

        // 路灯底座下的一小块
        val lampCx = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val lampR = rx * 0.18f
        val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                lampCx, feetY, lampR,
                intArrayOf(0x59000000, 0x00000000), null, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawCircle(lampCx, feetY, lampR, lampPaint)

        canvas.restoreToCount(layer)
    }

    /**
     * 贴纸夜景：灯光渐变。
     * 以灯罩为圆心向外衰减，[ramp] 是各档不透明度，颜色统一用 [STICKER_BEAM_RGB]。
     */
    private fun stickerBeamGradient(headX: Float, headY: Float, bottom: Float, falloff: Float, ramp: IntArray): RadialGradient {
        val colors = IntArray(ramp.size) { (ramp[it] shl 24) or STICKER_BEAM_RGB }
        return RadialGradient(
            headX, headY, (bottom - headY) * falloff,
            colors, STICKER_BEAM_STOPS, Shader.TileMode.CLAMP
        )
    }

    /** 贴纸夜景：以灯罩为顶点、斜向人物铺开的光锥。 */
    private fun stickerBeamCone(outerRect: RectF, artRect: RectF): Path {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val bottom = outerRect.top + outerRect.height() * STICKER_BEAM_BOTTOM_RATIO
        val w = artRect.width()
        return Path().apply {
            moveTo(headX, headY)
            lineTo(headX + w * STICKER_BEAM_FAR_RIGHT_RATIO, bottom)
            lineTo(headX + w * STICKER_BEAM_FAR_LEFT_RATIO, bottom)
            close()
        }
    }

    /**
     * 贴纸夜景：空气里的光柱。
     * 用"以灯罩为圆心的径向渐变"做衰减——离灯越远越淡，再叠一层高斯模糊把锥形边缘化开，
     * 看起来是空气里的光而不是一块半透明色块。画在人物之下，人是站在光里。
     */
    private fun drawStickerLightBeam(canvas: Canvas, outerRect: RectF, artRect: RectF, densityScale: Float, alpha: Int) {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val bottom = outerRect.top + outerRect.height() * STICKER_BEAM_BOTTOM_RATIO

        val cone = stickerBeamCone(outerRect, artRect)

        val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            shader = stickerBeamGradient(headX, headY, bottom, STICKER_BEAM_FALLOFF, STICKER_BEAM_RAMP)
            maskFilter = BlurMaskFilter(STICKER_BEAM_BLUR_DP * densityScale, BlurMaskFilter.Blur.NORMAL)
            this.alpha = alpha
        }
        canvas.drawPath(cone, beam)
    }

    /**
     * 贴纸夜景：落在人物身上的受光。
     * 同一束光再画一次，但用贴纸自身的 alpha 当遮罩，只留在人物（和灯）的像素上——
     * 于是光是"照在"他们身上，而不是从他们身后透过去。
     */
    private fun drawStickerLightOnArt(canvas: Canvas, outerRect: RectF, artRect: RectF, art: Bitmap, densityScale: Float, alpha: Int) {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val bottom = outerRect.top + outerRect.height() * STICKER_BEAM_BOTTOM_RATIO

        val layer = canvas.saveLayer(artRect, null)
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            shader = stickerBeamGradient(headX, headY, bottom, STICKER_LIT_FALLOFF, STICKER_LIT_RAMP)
            maskFilter = BlurMaskFilter(STICKER_BEAM_BLUR_DP * densityScale, BlurMaskFilter.Blur.NORMAL)
            this.alpha = alpha
        }
        canvas.drawPath(stickerBeamCone(outerRect, artRect), lit)

        val mask = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(art, null, artRect, mask)
        canvas.restoreToCount(layer)
    }

    /** 贴纸夜景：灯罩外圈的暖光晕，压在最上层，灯才有"正在发光"的感觉。 */
    private fun drawStickerLampHalo(canvas: Canvas, artRect: RectF) {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val radius = artRect.width() * STICKER_HALO_RADIUS_RATIO
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            shader = RadialGradient(
                headX, headY, radius,
                intArrayOf(
                    (STICKER_HALO_ALPHA shl 24) or STICKER_HALO_COLOR,
                    (0x2A shl 24) or STICKER_HALO_COLOR,
                    (0x14 shl 24) or STICKER_HALO_COLOR,
                    (0x08 shl 24) or STICKER_HALO_COLOR,
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.3f, 0.55f, 0.78f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(headX, headY, radius, halo)
    }

    /**
     * 贴纸夜景：垂在文本框下沿的一束玫瑰。
     * 藤蔓贴着下沿往右拖、向下鼓出，花冠依次变小、整束坠在纸的下边；
     * 每片花瓣/叶子都描一圈白边，和人物、路灯一样是"剪下来贴上去"的纸片。
     */
    private fun drawStickerRoses(canvas: Canvas, textBox: RectF, densityScale: Float, alpha: Int) {
        // 花枝垂在文本框下方那点空当里：组件矮（4×2）时按高度收一收，花才不会掉出画面
        val r = minOf(STICKER_ROSE_DP * densityScale, textBox.height() * 0.14f)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
            style = Paint.Style.STROKE
            strokeWidth = STICKER_ROSE_OUTLINE_DP * densityScale
            strokeJoin = Paint.Join.ROUND
        }
        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_LEAF_COLOR
            this.alpha = alpha
        }
        val vinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_VINE_COLOR
            this.alpha = alpha
            style = Paint.Style.STROKE
            strokeWidth = r * 0.16f
            strokeCap = Paint.Cap.ROUND
        }

        val x0 = textBox.left + r * 1.15f
        val y0 = textBox.bottom

        // 藤蔓：贴着下沿往右拖一段，向下鼓出，末端带一个小卷
        val vine = Path().apply {
            moveTo(x0 - r * 0.2f, y0 - r * 0.35f)
            cubicTo(x0 + r * 1.6f, y0 + r * 1.15f,
                x0 + r * 3.4f, y0 - r * 0.35f,
                x0 + r * 4.6f, y0 + r * 0.55f)
            cubicTo(x0 + r * 5.3f, y0 + r * 1.0f,
                x0 + r * 5.2f, y0 - r * 0.15f,
                x0 + r * 4.35f, y0 + r * 0.05f)
        }
        canvas.drawPath(vine, vinePaint)

        // 叶子：沿藤蔓两侧各插几片，花枝贴着纸沿生长
        drawRoseLeaf(canvas, x0 + r * 2.3f, y0 + r * 0.85f, x0 + r * 3.5f, y0 + r * 1.75f, r * 0.34f, leafPaint, outline)
        drawRoseLeaf(canvas, x0 + r * 3.2f, y0 + r * 0.05f, x0 + r * 4.4f, y0 - r * 0.45f, r * 0.30f, leafPaint, outline)
        drawRoseLeaf(canvas, x0 + r * 1.0f, y0 + r * 0.15f, x0 + r * 0.05f, y0 - r * 0.5f, r * 0.30f, leafPaint, outline)
        drawRoseLeaf(canvas, x0 + r * 4.9f, y0 + r * 1.05f, x0 + r * 5.7f, y0 + r * 1.75f, r * 0.24f, leafPaint, outline)

        // 玫瑰：主花在左，右边两朵渐小，整束坠在文本框下边
        drawRose(canvas, x0 + r * 0.35f, y0 + r * 0.72f, r, outline, alpha)
        drawRose(canvas, x0 + r * 2.05f, y0 + r * 1.15f, r * 0.72f, outline, alpha)
        drawRose(canvas, x0 + r * 3.75f, y0 + r * 0.72f, r * 0.55f, outline, alpha)
    }

    /** 一片玫瑰叶：两段二次曲线拼成的柳叶形。 */
    private fun drawRoseLeaf(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, bulge: Float, fill: Paint, outline: Paint) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
        val nx = -dy / len * bulge
        val ny = dx / len * bulge
        val mx = (x0 + x1) / 2f
        val my = (y0 + y1) / 2f
        val leaf = Path().apply {
            moveTo(x0, y0)
            quadTo(mx + nx, my + ny, x1, y1)
            quadTo(mx - nx, my - ny, x0, y0)
            close()
        }
        canvas.drawPath(leaf, fill)
        canvas.drawPath(leaf, outline)
    }

    /**
     * 一朵玫瑰：波浪边的花体 + 从花心卷到花沿的螺旋。
     * 描边只走整朵花的剪影，花面上不留白线，避免把花瓣切成一块块。
     */
    private fun drawRose(canvas: Canvas, cx: Float, cy: Float, r: Float, outline: Paint, alpha: Int) {
        val deep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_ROSE_CORE
            this.alpha = alpha
        }
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_ROSE_PETAL
            this.alpha = alpha
        }

        // 剪影：圆花体 + 一圈波浪边，合成一个 Path，填色时自然取并集
        val body = Path().apply {
            addCircle(cx, cy, r * 0.86f, Path.Direction.CW)
            for (i in 0 until 6) {
                val a = Math.toRadians(i * 60.0 - 90.0)
                addCircle(
                    cx + (0.68f * r * Math.cos(a)).toFloat(),
                    cy + (0.68f * r * Math.sin(a)).toFloat(),
                    r * 0.36f, Path.Direction.CW
                )
            }
        }
        // 描边走整朵花的剪影：先铺一层白，再填花体色，白边只留在最外圈，
        // 花面上不会留下把花瓣切成一块块的白线
        val sticker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = outline.strokeWidth * 2f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(body, sticker)
        canvas.drawPath(body, light)

        // 花心螺旋：从花心一路卷到花沿，这是玫瑰最认得出的特征。
        // 半径按幂次收缩，花心卷得紧、外圈松得开，才不像机械等距的螺纹
        val rMax = r * 0.82f
        fun spiralOf(t0: Float, t1: Float): Path {
            val p = Path()
            val n = 56
            for (k in 0..n) {
                val t = t0 + (t1 - t0) * k / n
                val a = 2.2 * 2.0 * Math.PI * t
                val rr = rMax * Math.pow((1f - t).toDouble(), 1.35).toFloat()
                val x = cx + (rr * Math.cos(a)).toFloat()
                val y = cy + (rr * Math.sin(a)).toFloat()
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        fun spiralPaint(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_ROSE_COLOR
            this.alpha = alpha
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(spiralOf(0f, 0.56f), spiralPaint(r * 0.15f))
        canvas.drawPath(spiralOf(0.53f, 1f), spiralPaint(r * 0.10f))
        canvas.drawCircle(cx, cy, r * 0.10f, deep)
    }

    // 小霸王游戏机：素材里"显像管玻璃"（屏幕）在组件位图中的矩形。
    // 素材按 CENTER_FIT 等比完整显示，屏幕位置随组件宽高比变化，必须按同一套比例换算。
    private fun suborScreenRect(outerRect: RectF): RectF {
        val model = centerFitRect(outerRect, SUBOR_IMAGE_ASPECT)
        return RectF(
            model.left + model.width() * SUBOR_SCREEN_LEFT_RATIO,
            model.top + model.height() * SUBOR_SCREEN_TOP_RATIO,
            model.left + model.width() * SUBOR_SCREEN_RIGHT_RATIO,
            model.top + model.height() * SUBOR_SCREEN_BOTTOM_RATIO
        )
    }

    // 屏幕是圆角玻璃：按小圆角裁剪，避免绿色荧光溢出到机身红色边框上
    private fun clipSuborScreen(canvas: Canvas, screen: RectF) {
        val radius = Math.min(screen.width(), screen.height()) * 0.06f
        val clipPath = Path().apply { addRoundRect(screen, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clipPath)
    }

    /**
     * 通电显像管：在屏幕玻璃区域内叠一层绿色荧光底 + 中心亮斑。
     * 素材本身是"未通电"的深灰玻璃，这一步让它变成开机的绿屏。
     */
    private fun drawSuborScreenGlow(canvas: Canvas, screen: RectF, densityScale: Float) {
        if (screen.width() <= 0f || screen.height() <= 0f) return
        canvas.save()
        clipSuborScreen(canvas, screen)

        val cx = screen.centerX()
        val cy = screen.centerY()
        val radius = Math.max(screen.width(), screen.height()) * 0.62f

        val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(SUBOR_SCREEN_CORE_COLOR, SUBOR_SCREEN_MID_COLOR, SUBOR_SCREEN_EDGE_COLOR),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = SUBOR_SCREEN_FILL_ALPHA
        }
        canvas.drawRect(screen, screenPaint)

        // 中心亮斑：模拟电子束在屏幕中央聚集形成的高光
        val hotSpotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius * 0.5f,
                intArrayOf(0x55CCFFC2, 0x00CCFFC2),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(screen, hotSpotPaint)

        canvas.restore()
    }

    /**
     * 显像管观感层：扫描线 + 曲面暗角 + 玻璃反光，全部压在文字之上。
     * 这是"文字真的显示在屏幕上"的关键——扫描线横穿字形，视觉上把文字与屏幕焊在一起。
     */
    private fun drawSuborCrtOverlay(canvas: Canvas, screen: RectF, densityScale: Float) {
        if (screen.width() <= 0f || screen.height() <= 0f) return
        canvas.save()
        clipSuborScreen(canvas, screen)

        // 1. 扫描线：等距细横线，模拟显像管逐行扫描
        val spacing = (SUBOR_SCANLINE_SPACING_DP * densityScale).coerceAtLeast(1.5f)
        val scanlinePaint = Paint().apply {
            color = Color.BLACK
            alpha = SUBOR_SCANLINE_ALPHA
            strokeWidth = (spacing * 0.42f).coerceAtLeast(0.8f)
        }
        var y = screen.top
        while (y <= screen.bottom) {
            canvas.drawLine(screen.left, y, screen.right, y, scanlinePaint)
            y += spacing
        }

        // 2. 曲面暗角：中心透明、四周压暗，强化球面显像管的纵深感
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                screen.centerX(), screen.centerY(),
                Math.max(screen.width(), screen.height()) * 0.74f,
                intArrayOf(0x00000000, 0x00000000, 0x8F000000.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(screen, vignettePaint)

        // 3. 玻璃反光：左上角斜向柔光，恢复玻璃罩的质感
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                screen.left, screen.top,
                screen.left + screen.width() * 0.6f, screen.bottom,
                intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = SUBOR_GLASS_SHEEN_ALPHA
        }
        canvas.drawRect(screen, sheenPaint)

        canvas.restore()
    }

    // 绘制手账胶带折线撕裂边缘 Path
    private fun drawTape(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        angleDegrees: Float,
        color: Int,
        densityScale: Float
    ) {
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(angleDegrees)

        val halfW = width / 2f
        val halfH = height / 2f

        val path = Path()
        // 从左上角开始绘制。左侧是撕裂毛边。
        path.moveTo(-halfW, -halfH)
        
        // 左毛边：y从 -halfH 到 halfH。分 6 个波折段
        val segments = 6
        val step = height / segments
        for (i in 1..segments) {
            val currY = -halfH + i * step
            val shiftX = if (i % 2 == 1) 3f * densityScale else 0f
            path.lineTo(-halfW + shiftX, currY)
        }

        // 下平边
        path.lineTo(halfW, halfH)

        // 右毛边：y从 halfH 回到 -halfH。分 6 个波折段
        for (i in segments downTo 0) {
            val currY = -halfH + i * step
            val shiftX = if (i % 2 == 1) -3f * densityScale else 0f
            path.lineTo(halfW + shiftX, currY)
        }

        // 上平边已通过 close 闭合
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawPath(path, paint)

        // 绘制三条微弱的半透明白色光泽条纹纹理，增加纸胶带的真实感
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = Color.WHITE
            alpha = TAPE_LINE_ALPHA
            strokeWidth = 1.5f * densityScale
        }
        canvas.drawLine(-halfW + 12f * densityScale, -halfH + 2f * densityScale, -halfW + 22f * densityScale, halfH - 2f * densityScale, linePaint)
        canvas.drawLine(-halfW + 22f * densityScale, -halfH + 2f * densityScale, -halfW + 32f * densityScale, halfH - 2f * densityScale, linePaint)
        canvas.drawLine(halfW - 32f * densityScale, -halfH + 2f * densityScale, halfW - 22f * densityScale, halfH - 2f * densityScale, linePaint)

        canvas.restore()
    }

    // 书架上的一本书：书名、书脊配色、相对最高书脊的高度比例
    private data class ShelfBook(
        val title: String,
        val color: Int,
        val heightFactor: Float,
        val highlighted: Boolean = false
    )

    private val SHELF_BOOKS: List<ShelfBook> = listOf(
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
    private fun isLightSpine(color: Int): Boolean {
        val luminance = 0.299f * Color.red(color) / 255f +
            0.587f * Color.green(color) / 255f +
            0.114f * Color.blue(color) / 255f
        return luminance > 0.62f
    }

    // 书香书架：书架本身就是组件，四周保持透明（不画底色/描边/投影、不做形状裁切），
    // 只有横板与底部米色摘录面板是可着色区域，两者都按位图比例绘制，随组件尺寸一起缩放
    private fun drawBookshelfChrome(
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
    private fun bookshelfShelfTop(fy: Float, fh: Float, densityScale: Float): Float {
        val reserved = (SHELF_PANEL_MIN_HEIGHT_DP + SHELF_PANEL_GAP_DP + SHELF_PANEL_BOTTOM_MARGIN_DP + SHELF_BOARD_HEIGHT_DP) * densityScale
        val maxBand = (fh - reserved).coerceAtLeast(fh * SHELF_PANEL_MIN_BAND_RATIO)
        return fy + (SHELF_BOOK_BAND_HEIGHT_DP * densityScale).coerceAtMost(maxBand)
    }

    private fun bookshelfBoardHeight(densityScale: Float): Float =
        (SHELF_BOARD_HEIGHT_DP * densityScale).coerceAtLeast(2f * densityScale)

    // 摘录面板矩形：上沿紧跟横板，下沿留出底部留白，中间全部属于文本显示区域
    private fun bookshelfPanelRect(outerRect: RectF, densityScale: Float): RectF {
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

    // 蓝色便签贴纸：在圆角蓝底上绘制顶部 NOTE 行、右上信息钮、底部米色签条与手写签名
    private fun drawBlueNoteChrome(
        canvas: Canvas,
        width: Float,
        height: Float,
        outerRect: RectF,
        outerPath: Path,
        densityScale: Float,
        style: WidgetStyle,
        context: Context
    ) {
        canvas.save()
        canvas.clipPath(outerPath)

        val svgW = 578f
        val svgH = 363f
        val headerH = 76f
        val footerH = 76f
        val sx = width / svgW
        val sy = height / svgH
        val scaleText = (sx + sy) * 0.5f

        // 顶部 NOTE：左上 NOTE 大写，右上圆形信息钮（?）
        val noteSize = 22f * densityScale * (scaleText / (densityScale * 0.9f)).coerceIn(0.85f, 1.25f)
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = noteSize.coerceIn(12f * densityScale, 26f * densityScale)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val notePadX = 18f * sx
        val notePadY = 18f * sy + notePaint.textSize
        canvas.drawText("NOTE", outerRect.left + notePadX, outerRect.top + notePadY, notePaint)

        // 右上信息钮：白底圆 + 黄色 ? 造型，复刻 SVG 中 50×50 的圆形与外围刻度
        val badgeCx = outerRect.right - 28f * sx
        val badgeCy = outerRect.top + 30f * sy
        val badgeR = 18f * sx.coerceAtLeast(sy) * 0.9f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.FILL
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeR, ringPaint)
        // 外圈细环
        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f * densityScale
            alpha = 210
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeR + 2f * densityScale * 0.4f, ringStroke)

        val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8D000")
            textSize = badgeR * 1.45f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val qFm = qPaint.fontMetrics
        val qY = badgeCy - (qFm.ascent + qFm.descent) / 2f
        canvas.drawText("?", badgeCx, qY, qPaint)

        // 外围小刻度（8 个小短线，复刻 SVG 的发散点）
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8D000")
            strokeWidth = 1.4f * densityScale
            strokeCap = Paint.Cap.ROUND
        }
        val tickR = badgeR + 7f * sx
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45.0) - 22.5)
            val x0 = (badgeCx + Math.cos(a) * (tickR - 3f * sx)).toFloat()
            val y0 = (badgeCy + Math.sin(a) * (tickR - 3f * sy)).toFloat()
            val x1 = (badgeCx + Math.cos(a) * (tickR + 2f * sx)).toFloat()
            val y1 = (badgeCy + Math.sin(a) * (tickR + 2f * sy)).toFloat()
            canvas.drawLine(x0, y0, x1, y1, tickPaint)
        }

        // 底部米色签条：高度 76/363，颜色 #F7F4F0，内写 i + 句阁、手写签名（沿用 style.authorSignature）
        val footerTop = outerRect.bottom - footerH * sy
        val footerHpx = footerH * sy
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F4F0")
            this.style = Paint.Style.FILL
        }
        // 只在底部条区域填充，保持顶部仍为 #43A8F0
        canvas.drawRect(RectF(outerRect.left, footerTop, outerRect.right, outerRect.bottom), footerPaint)

        // 底部条内：底部基线的分隔细线（微灰）
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8E2DC")
            strokeWidth = 1f * densityScale
        }
        canvas.drawLine(outerRect.left, footerTop, outerRect.right, footerTop, linePaint)

        // 底部手写英文（对齐参考图：居中 "focus yourself"，毛笔楷书手写体）
        val footerText = "focus yourself"
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = (20f * sy).coerceIn(14f * densityScale, 26f * densityScale)
            textAlign = Paint.Align.CENTER
            typeface = try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/MaShanZheng-Regular.ttf")
                android.graphics.Typeface.create(tf, android.graphics.Typeface.ITALIC)
            } catch (_: Exception) {
                android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            }
        }
        val centerX = (outerRect.left + outerRect.right) / 2f
        val fm = sigPaint.fontMetrics
        val centerY = footerTop + footerHpx / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(footerText, centerX, centerY, sigPaint)

        canvas.restore()
    }
    private fun drawFeatherLetterPath(path: Path, width: Float, height: Float, densityScale: Float) {
        val marginX = width * 0.06f
        val marginTop = height * 0.05f
        val marginBottom = height * 0.06f
        val left = marginX
        val right = width - marginX
        val top = marginTop
        val bottom = height - marginBottom
        val jitter = 2.0f * densityScale

        path.reset()
        val random = java.util.Random(2024L)
        path.moveTo(left, top)
        // 上边缘
        var x = left
        while (x < right) {
            x += 8f * densityScale
            if (x > right) x = right
            val jy = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(x, top + jy)
        }
        // 右边缘
        var y = top
        while (y < bottom) {
            y += 8f * densityScale
            if (y > bottom) y = bottom
            val jx = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(right + jx, y)
        }
        // 下边缘
        x = right
        while (x > left) {
            x -= 8f * densityScale
            if (x < left) x = left
            val jy = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(x, bottom + jy)
        }
        // 左边缘
        y = bottom
        while (y > top) {
            y -= 8f * densityScale
            if (y < top) y = top
            val jx = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(left + jx, y)
        }
        path.close()
    }

    // 检测位图四周是否带有统一近白留白相框，若是则返回裁剪到内容区(去掉相框)的矩形。
    // 明信片正方形插画常自带白色描边，直接用会在大横幅两侧露出白边，这里沿中轴线向内收缩定位。
    // 通过一条水平中线探测左右边界、一条垂直中线探测上下边界，避免需要整图逐像素扫描。
    private fun detectLightBorder(bmp: Bitmap): android.graphics.Rect {
        val w = bmp.width
        val h = bmp.height
        val full = android.graphics.Rect(0, 0, w, h)
        if (w < 2 || h < 2) return full

        fun isLight(x: Int, y: Int): Boolean {
            val c = bmp.getPixel(x, y)
            return (c ushr 24) == 0xff &&
                android.graphics.Color.red(c) > LIGHT_BORDER_THRESHOLD &&
                android.graphics.Color.green(c) > LIGHT_BORDER_THRESHOLD &&
                android.graphics.Color.blue(c) > LIGHT_BORDER_THRESHOLD
        }
        if (!isLight(0, h / 2) && !isLight(w - 1, h / 2)) return full

        val midY = h / 2
        var left = 0
        while (left < w && isLight(left, midY)) left++
        var right = w - 1
        while (right > left && isLight(right, midY)) right--

        val midX = w / 2
        var top = 0
        while (top < h && isLight(midX, top)) top++
        var bottom = h - 1
        while (bottom > top && isLight(midX, bottom)) bottom--

        // 防御：若检测出的“相框”过大（说明整条中线接近同色，不是真正相框），回退为原图
        if (left > 0.30f * w || top > 0.30f * h) return full

        return android.graphics.Rect(left, top, right + 1, bottom + 1)
    }

    // 绘制背景图片缩放模式
    /**
     * CENTER_FIT 模式下整幅图等比完整显示后落在画布上的目标矩形（超出的方向留透明）。
     * 与 drawBgImage 的 CENTER_FIT 分支保持同一套算法，供需要贴合素材内固定位置（如机身屏幕）的形状复用。
     */
    private fun centerFitRect(rectF: RectF, imageAspect: Float): RectF {
        val targetRatio = rectF.width() / rectF.height()
        return if (imageAspect > targetRatio) {
            // 图更宽：以宽为准，上下留透明
            val dstH = rectF.width() / imageAspect
            val top = rectF.centerY() - dstH / 2f
            RectF(rectF.left, top, rectF.right, top + dstH)
        } else {
            // 图更高：以高为准，左右留透明
            val dstW = rectF.height() * imageAspect
            val left = rectF.centerX() - dstW / 2f
            RectF(left, rectF.top, left + dstW, rectF.bottom)
        }
    }

    private fun drawBgImage(canvas: Canvas, bitmap: Bitmap, rectF: RectF, style: WidgetStyle) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)

        val processedBitmap = if (style.bgBlurRadius > 0f) {
            val radius = style.bgBlurRadius.toInt().coerceIn(1, 25)
            // 先把工作图压到安全边长再模糊：fastBlur 需要多个与像素数等大的数组，
            // 高分辨率背景图直接模糊会造成内存峰值过高甚至 OOM；
            // 模糊本身是低频信息，缩小后模糊再绘制到卡片上视觉差异可忽略
            val maxSide = 800
            val blurScale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
            if (blurScale < 1f) {
                val sw = (bitmap.width * blurScale).toInt().coerceAtLeast(1)
                val sh = (bitmap.height * blurScale).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                val blurred = fastBlur(small, radius)
                small.recycle()
                blurred
            } else {
                fastBlur(bitmap, radius)
            }
        } else {
            bitmap
        }

        when (style.bgImageScaleMode) {
            ImageScaleMode.STRETCH -> {
                canvas.drawBitmap(processedBitmap, null, rectF, paint)
            }
            ImageScaleMode.CENTER_CROP -> {
                // 先检测并裁掉素材四周自带的近白留白相框（正方形明信片插画常见），
                // 再对剩余内容做铺满裁切，避免宽横幅两侧露出白边
                val content = detectLightBorder(processedBitmap)
                val cw = content.right - content.left
                val ch = content.bottom - content.top
                val targetRatio = rectF.width() / rectF.height()
                val srcRatio = cw.toFloat() / ch.toFloat()
                val srcRect = android.graphics.Rect()

                if (srcRatio > targetRatio) {
                    val srcWidth = (ch * targetRatio).toInt()
                    val left = content.left + (cw - srcWidth) / 2
                    srcRect.set(left, content.top, left + srcWidth, content.bottom)
                } else {
                    val srcHeight = (cw / targetRatio).toInt()
                    val top = content.top + (ch - srcHeight) / 2
                    srcRect.set(content.left, top, content.right, top + srcHeight)
                }
                canvas.drawBitmap(processedBitmap, srcRect, rectF, paint)
            }
            ImageScaleMode.CENTER_FIT -> {
                // 等比完整显示整幅图，超出的空白保留透明，避免主体被裁切或压扁
                val srcRect = android.graphics.Rect(0, 0, processedBitmap.width, processedBitmap.height)
                val dstRect = centerFitRect(
                    rectF,
                    processedBitmap.width.toFloat() / processedBitmap.height.toFloat()
                )
                canvas.drawBitmap(processedBitmap, srcRect, dstRect, paint)
            }
            ImageScaleMode.CENTER_CROP_TOP -> {
                // 等比铺满(覆盖)目标区域：先放大到两方向都 >= 目标，超出方向裁剪。
                // 与 CENTER_CROP 不同：垂直方向多余的高度从【底部】裁掉、锚点在顶部，
                // 让顶部主体(如趴在卡片上方的猫)完整保留，而 CENTER_FIT 会因组件过宽把整图缩得左右留白。
                val content = detectLightBorder(processedBitmap)
                val cw = content.right - content.left
                val ch = content.bottom - content.top
                val targetRatio = rectF.width() / rectF.height()
                val srcRatio = cw.toFloat() / ch.toFloat()
                val srcRect = android.graphics.Rect()

                if (srcRatio > targetRatio) {
                    // 源更宽：以宽为准铺满、上下选裁，但要保住顶部主体 → 从顶部取满高，不居中
                    val srcWidth = (ch * targetRatio).toInt()
                    val left = content.left + (cw - srcWidth) / 2
                    srcRect.set(left, content.top, left + srcWidth, content.bottom)
                } else {
                    // 源更高：以高为准铺满、左右居中裁 → 顶部锚定，底部超出的往下裁掉
                    val srcHeight = (cw / targetRatio).toInt()
                    srcRect.set(content.left, content.top, content.right, content.top + srcHeight)
                }
                canvas.drawBitmap(processedBitmap, srcRect, rectF, paint)
            }
            ImageScaleMode.TILE -> {
                val shader = BitmapShader(processedBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                tilePaint.shader = shader
                tilePaint.alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
                canvas.drawRect(rectF, tilePaint)
            }
        }

        if (processedBitmap != bitmap) {
            processedBitmap.recycle()
        }

        if (style.bgScrimAlpha > 0f) {
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = (style.bgScrimAlpha * 255).toInt().coerceIn(0, 255)
            }
            canvas.drawRect(rectF, scrimPaint)
        }
    }

    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) {
            return bitmap
        }

        val w = bitmap.width
        val h = bitmap.height

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        val vmax = IntArray(Math.max(w, h))

        val dv = IntArray(256 * div)
        for (i in 0 until 256 * div) {
            dv[i] = i / div
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (y in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }
        for (x in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = Math.max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
            }
            yi = x
            stackpointer = radius
            for (y in 0 until h) {
                val alphaPixel = pix[yi] and -0x1000000
                pix[yi] = alphaPixel or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmax[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmax[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    // 预设插画缓存：桌面组件每次刷新都会渲染，避免重复全尺寸解码。
    // key 必须带上目标尺寸，否则首次按小尺寸解码的位图会被 4×4 大组件复用，
    // 导致桌面显示被放大的模糊图。
    // 24MB：城市剪影一张全分辨率素材就有 3~8.5MB（1940×409 / 2048×762 / 2048×1038），
    // 桌面上同时存在 4×2/4×3/4×4 三种尺寸时三张都要驻留，16MB 会把最不常用的那张挤掉、
    // 每次刷新重新解码一遍 8MB 位图。
    private val presetImageCache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // 风格缩略图缓存：UI 里「经典/萌宠/明信片/插画」四行一共 20+ 张缩略图，
    // 每张都是一次完整的 Canvas 渲染（含解码素材、画纹理）。
    // 没有这层缓存时，每次打开面板所有缩略图一起在 Dispatchers.Default 上从零重画，
    // 表现为「先空白 1~2 秒再逐个补齐」。
    //
    // 缩略图总量只有约 1.2MB（150×80×4B × 约 26 张），远小于下面的容量上限，
    // 因此**永远不会触发 LruCache 的淘汰**。这不是巧合而是刻意设计：
    // 缩略图行位于 LazyColumn 内，滑出视口会销毁 composition、回来时 produceState 重跑，
    // 只要缓存还在就是秒命中，不会再等 1~2 秒。
    // 正因为条目不会被淘汰，才可以把缓存里的 Bitmap **原图**直接交给调用方（无需 copy），
    // 避免滚动时反复分配副本被 GC 回收。
    private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * 取一张已渲染好的风格缩略图。
     *
     * **直接返回缓存里的原图，不做 copy()**：
     * 缩略图只有 150×80，一条约 48KB，26 张合计约 1.2MB；每次命中都 copy 一份的话，
     * 列表滚动时会不断分配新位图、旧的被 GC 回收，表现为"滑走再滑回来又要等 1~2 秒"。
     * 之所以敢直接给原图，是因为本缓存**关闭了 LruCache 的条目回收**
     * （entryRemoved=false，见 thumbnailCache 定义）：条目只增不减、不会被淘汰，
     * 原图一旦创建就一直在，调用方持有的引用始终有效。
     * 代价是这 1.2MB 常驻，换来滚动零重渲染。
     *
     * **锁只保护缓存的读写，绝不能把 producer（真正的 Canvas 渲染）也圈进去**：
     * 一旦整个方法加 @Synchronized，26 张缩略图会被强制串行逐张渲染，
     * 比并行慢好几倍，表现为"打开面板要等 1~2 秒"。之前踩过这个坑。
     */
    private fun thumbnailCacheKey(
        cacheKey: String,
        producer: () -> Bitmap?
    ): Bitmap? {
        synchronized(thumbnailCache) {
            thumbnailCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        }
        // 锁外渲染：允许所有缩略图在 Dispatchers.Default 上并行跑满 CPU
        val fresh = producer() ?: return null
        synchronized(thumbnailCache) {
            // 另一个线程可能已经渲染好了同一张：优先用先到的那份，避免重复渲染
            thumbnailCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
            thumbnailCache.put(cacheKey, fresh)
        }
        return fresh
    }


    /**
     * 只查缓存、不触发渲染的同步查询。供 UI 侧作为 produceState 的 initialValue：
     * 命中时该缩略图在首帧就有图（滚动回已渲染过的位置时完全无空白帧），
     * 未命中返回 null，由 produceState 内部异步渲染并写入缓存。
     */
    fun cachedThumbnail(
        widthDp: Int,
        heightDp: Int,
        style: WidgetStyle
    ): Bitmap? {
        val key = "${style.presetId ?: style.shape}_${widthDp}x$heightDp"
        synchronized(thumbnailCache) {
            thumbnailCache.get(key)?.let { if (!it.isRecycled) return it }
        }
        return null
    }

    /**
     * 风格缩略图统一入口：供 MainActivity / QuickAdjustActivity 的缩略图列表调用。
     *
     * 命中缓存时**同步返回**，因此 UI 侧 `produceState` 的首次赋值也在同一帧完成，
     * 不会出现"先空白一帧再补上"的闪动；未命中才回落到耗时渲染。
     */
    fun renderThumbnail(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap? {
        // key 用 presetId（缺失时退回形状+尺寸）而不是整个 style.toJson()：
        // 前者稳定且短，后者会随用户每次微调颜色而变，导致缓存永远不命中。
        val key = "${style.presetId ?: style.shape}_${widthDp}x$heightDp"
        return thumbnailCacheKey(key) {
            try {
                render(context, widthDp, heightDp, content, style)
            } catch (t: Throwable) {
                null
            }
        }
    }

    @Synchronized
    private fun getPresetImage(context: Context, resName: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val cacheKey = "${resName}_${targetWidth}x${targetHeight}"
        presetImageCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (resId == 0) return null
        val bmp = decodeResourceSampled(context.resources, resId, targetWidth, targetHeight)
        if (bmp == null) {
            // 矢量或非位图资源降级为 Drawable 渲染：必须按目标尺寸栅格化，
            // 否则按 intrinsic 尺寸（或兜底 300×120）生成的小图放大到组件尺寸会发虚
            val drawable = context.resources.getDrawable(resId, null) ?: return null
            val drawW = targetWidth.coerceAtLeast(MIN_BITMAP_SIZE)
            val drawH = targetHeight.coerceAtLeast(MIN_BITMAP_SIZE)
            val tmpBmp = Bitmap.createBitmap(drawW, drawH, Bitmap.Config.ARGB_8888)
            val tmpCanvas = Canvas(tmpBmp)
            drawable.setBounds(0, 0, drawW, drawH)
            drawable.draw(tmpCanvas)
            presetImageCache.put(cacheKey, tmpBmp)
            return tmpBmp
        }
        presetImageCache.put(cacheKey, bmp)
        return bmp
    }

    @Synchronized
    private fun decodeFileSampled(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun decodeResourceSampled(res: android.content.res.Resources, resId: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            // 透明 PNG（如信纸抠图）必须强制 ARGB_8888，否则采样后透明区会变成 RGB_565 导致的黑色
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(res, resId, opts)
    }

    private fun calcInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        var w = outWidth
        var h = outHeight
        while (w / 2 >= reqWidth && h / 2 >= reqHeight) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    // 随机生成撕纸效果 Path
    private fun generateTornPath(width: Float, height: Float, densityScale: Float): Path {
        val path = Path()
        val random = java.util.Random(TORN_SEED)
        val maxJitter = 2.0f * densityScale
        val step = 4.0f * densityScale

        path.moveTo(0f, 0f)
        // 1. 上边缘：(0, 0) -> (width, 0)
        var x = 0f
        while (x < width) {
            x += step
            if (x > width) x = width
            val jitterY = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(x, jitterY)
        }

        // 2. 右边缘：(width, 0) -> (width, height)
        var y = 0f
        while (y < height) {
            y += step
            if (y > height) y = height
            val jitterX = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(width + jitterX, y)
        }

        // 3. 下边缘：(width, height) -> (0, height)
        while (x > 0f) {
            x -= step
            if (x < 0f) x = 0f
            val jitterY = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(x, height + jitterY)
        }

        // 4. 左边缘：(0, height) -> (0, 0)
        while (y > 0f) {
            y -= step
            if (y < 0f) y = 0f
            val jitterX = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(jitterX, y)
        }

        path.close()
        return path
    }

    // 绘制拟物纸张颗粒/纤维纹理
    private fun drawPaperTexture(canvas: Canvas, rectF: RectF, textureType: String, densityScale: Float) {
        val random = java.util.Random(TEXTURE_SEED)
        val width = rectF.width()
        val height = rectF.height()
        val left = rectF.left
        val top = rectF.top

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (textureType == "PAPER") {
            // 短细纤维线条与少量点噪点
            val fiberCount = (width * height / 300f).toInt().coerceIn(100, 1000)
            paint.style = Paint.Style.STROKE
            for (i in 0 until fiberCount) {
                val startX = left + random.nextFloat() * width
                val startY = top + random.nextFloat() * height
                val length = (4f + random.nextFloat() * 11f) * densityScale
                val angle = random.nextFloat() * Math.PI * 2
                val endX = startX + (Math.cos(angle) * length).toFloat()
                val endY = startY + (Math.sin(angle) * length).toFloat()

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(8) + 2 else random.nextInt(12) + 4
                paint.strokeWidth = (0.5f + random.nextFloat() * 0.8f) * densityScale

                val ctrlX = (startX + endX) / 2f + (random.nextFloat() - 0.5f) * 3f * densityScale
                val ctrlY = (startY + endY) / 2f + (random.nextFloat() - 0.5f) * 3f * densityScale
                val path = Path().apply {
                    moveTo(startX, startY)
                    quadTo(ctrlX, ctrlY, endX, endY)
                }
                canvas.drawPath(path, paint)
            }

            paint.style = Paint.Style.FILL
            val noiseCount = (width * height / 200f).toInt().coerceIn(100, 800)
            for (i in 0 until noiseCount) {
                val px = left + random.nextFloat() * width
                val py = top + random.nextFloat() * height
                val r = (0.5f + random.nextFloat() * 1.0f) * densityScale

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(10) + 2 else random.nextInt(15) + 5
                canvas.drawCircle(px, py, r, paint)
            }
        } else if (textureType == "GRAIN") {
            // 高密度的细微噪点
            paint.style = Paint.Style.FILL
            val noiseCount = (width * height / 50f).toInt().coerceIn(500, 3000)
            for (i in 0 until noiseCount) {
                val px = left + random.nextFloat() * width
                val py = top + random.nextFloat() * height
                val r = (0.3f + random.nextFloat() * 0.7f) * densityScale

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(12) + 3 else random.nextInt(18) + 6
                canvas.drawCircle(px, py, r, paint)
            }
        }
    }
}