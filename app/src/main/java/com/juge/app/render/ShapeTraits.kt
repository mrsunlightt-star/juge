package com.juge.app.render

import com.juge.app.data.WidgetShape

/**
 * 渲染管线里的「形状家族」。
 *
 * 管线只在家族粒度上分叉：新增一个风格时应该把它归入既有家族（或显式新增一个家族，
 * 并在这里、`WidgetRenderPipeline.drawFamilyChrome`、`CardTextRenderer.textBoxFor`
 * 三处各给出实现），而不是再往 render() 里插一个 `style.shape == X`。
 */
internal enum class ShapeFamily {
    /** 普通圆角矩形：形状完全由 rectF / outerRect 决定，装饰交给各自的绘制函数 */
    ROUND_RECT,

    /** 撕裂纸片：形状是从固定种子生成的撕边路径 */
    TORN_PAPER,

    /** 羽毛信纸：居中撕纸信纸，四周透出底色 */
    FEATHER_LETTER,

    /** 椭圆 */
    ELLIPSE,

    /** 手账胶带：正文画完后在左上 / 右下补两条胶带 */
    HANDBOOK_TAPE,

    /** 图文明信片（上下 / 左右分割）：一半铺图，另一半是代码绘制的文字区 */
    SPLIT_CARD,

    /** 蓝色便签：顶部 NOTE 区 + 底部米白签条 */
    BLUE_NOTE,

    /** 书香书架：顶部书脊横板 + 底部米色摘录面板 */
    BOOKSHELF,

    /** 小霸王游戏机：素材本体 + 屏幕绿荧光底 + 压在正文上的 CRT 叠加层 */
    SUBOR_CONSOLE,

    /** 天气盒子：白盒挖内凹腔体，腔下留白放正文 */
    WEATHER_BOX,

    /** 画框卡片：卡纸 + 相框 + 角落点缀（比例见 `FramedCardRenderer` 的 spec 表） */
    FRAMED_CARD,
}

/**
 * 一个形状在渲染管线里的全部差异。
 *
 * 这几项原先散落在 render() 的 47 处 `style.shape == X` 里，其中不少名单被抄了三四遍
 * （「不画整卡投影 / 不铺整卡底色」写了两处，「外框圆角跟随用户设置」写了两处，
 * 「素材由家族自己摆放」写了四五处）。漏改一处就是「A 风格正常、B 风格错位」的隐性 bug。
 * 集中到一处后名单只有一份，新增形状必须显式回答这里每一个问题。
 */
internal class ShapeTraits(
    val family: ShapeFamily,
    /**
     * 内容**能不能**整体内缩 4dp：只适用于内容完全按 rectF / outerRect 布局、内缩不会溢出的形状。
     *
     * 注意这是「能不能」，不是「要不要」——要不要由 `RenderScene.cardInset` 决定：
     * 只有轮廓外沿真有的东西要放（投影、材质边）时才内缩。
     */
    val insetCapable: Boolean,
    /**
     * 强制直角：忽略用户的圆角设置。整幅贴图类的角上压着的就是素材画的东西，
     * 裁圆角等于"作品被切角"（逐形状理由与实测数字见 `SQUARE_CORNER_SHAPES`）。
     */
    val forcesSquareCorners: Boolean,
    /** 整幅透明底：没有卡片外框，投影与底色只跟随各自的文本框 / 文字栏 */
    val transparentCard: Boolean,
    /** 不走「整卡铺背景图」：素材由家族自己的绘制逻辑摆放 */
    val drawsOwnBackground: Boolean,
) {
    /**
     * 外框圆角是否跟随用户的圆角设置。
     *
     * 撕纸 / 信纸 / 椭圆的「外框」只是给投影和底色用的一个方形兜底，不是可见的卡片边框，
     * 用户也看不到它的圆角，因此固定用默认圆角。
     */
    val followsUserCornerRadius: Boolean
        get() = family != ShapeFamily.TORN_PAPER &&
            family != ShapeFamily.FEATHER_LETTER &&
            family != ShapeFamily.ELLIPSE

    /** 填充与裁剪跟随撕边路径，而不是外框 */
    val fillsAlongTornPath: Boolean get() = family == ShapeFamily.TORN_PAPER

    /** 图文明信片：底色 / 背景图只铺「图片那一半」 */
    val isSplitCard: Boolean get() = family == ShapeFamily.SPLIT_CARD
}

/**
 * **可以**内缩的形状：这些形状的内容完全按 rectF / outerRect 布局，内缩不会溢出。
 *
 * 名单只管「**能不能**内缩」；「**要不要**内缩」由 `RenderScene.cardInset` 判：看轮廓外沿有没有
 * 东西要放（样式的投影 / 形状的外侧装饰）。两件事原先混在一份名单里，结果没有投影的样式
 * 也白留了 4dp 透明边——那圈边除了透出壁纸，还给 launcher 的白色占位底留了个出风口。
 */
private val INSET_CAPABLE_SHAPES = setOf(
    WidgetShape.RECTANGLE,
    WidgetShape.HANDBOOK_TAPE,
    WidgetShape.SPLIT_CARD,
    WidgetShape.SPLIT_CARD_HORIZONTAL,
    // 天气盒子：盒体与腔体都按 outerRect 布局，内缩安全
    WidgetShape.WEATHER_BOX,
    // 青年雕塑：卡面就是整幅素材，内容全按 outerRect 布局，内缩（给投影让位）安全
    WidgetShape.YOUTH_SCULPTURE,
)

/**
 * 强制 0 圆角的形状：渲染时**忽略**用户的圆角设置（见 [ShapeTraits.forcesSquareCorners]）。
 *
 * **① 为什么必须由渲染层决定**：靠"预设自己把圆角写成 0"兜不住——套用预设时圆角是**继承**来的
 * （`ui/adjust/StylePresetCard.kt` 里 `preset.copy(cornerRadiusDp = selectedStyle.cornerRadiusDp)`），
 * 用户还能把滑条拖到 30dp。整幅贴图类的角上往往就是素材画的东西，裁一刀就是"作品缺了个角"。
 *
 * **② 圆角会切掉什么**（口径：预览位图 1125px 宽 = 4.5px/dp，数"画面上已有的不透明像素"落在圆角
 * 遮罩外的部分；斜杠后只数非卡面白的图案/笔画像素。4×4 只有 SPRING_DOG 另算——见它那一行，
 * CUTE_FOUR_KIDS 与 4×2 相同，其余 4×4 均为 0）：
 *  · SPRING_DOG     绿框四个角——**12dp 就会切到**：4×2 653/280px，4×4 273/34px；30dp 6473/4154px
 *  · PANDA_BAMBOO   竹框下沿两角：12dp 0，30dp 1018/956px
 *  · PET_PARK       毛毡框下沿两角：12dp 0，30dp 1053/684px
 *  · CRAYON_FRAME   最外侧的蜡笔波浪尖：12dp 0，30dp 693/433px
 *  · PLUSH_FOREST   底部毛绒草地：12dp 0，30dp 780/775px
 *  · GIANT_SWORD    左下衣摆：12dp、20dp 都是 0，30dp 才 102/92px
 *  · CUTE_FOUR_KIDS 白卡四角——切到的是**卡面**，不是头像（12dp 头像 0px、30dp 仅 10px）。
 *                   直角是这款的设计选择（整卡留白到边），别写成"保护头像条"
 *  · SUBOR_CONSOLE  标准尺寸下**切不到**：`CENTER_FIT` 把机身缩在中间，四角本来就是透明的
 *
 * **③ 别照 ② 的数字删条目**：上表是标准 4×2 / 4×4 预览下测的。用户在桌面上把组件**拖小**（占的格数
 * 变少）时，同一个圆角在卡片上占的比例更大、切得更多；`SUBOR_CONSOLE` 还多一层——它的素材是
 * 1.604:1，组件长宽比一接近它，`CENTER_FIT` 就把机身撑满四角；再加上"从上一个风格继承来一个大
 * 圆角"这条路径，切到的东西都跟着变。删一条**不会让任何测试变红**（`ShapeCornerSliderTest`
 * 只核对"强制直角 ⇒ 圆角滑条置灰"），而桌面上的老组件会当场多一刀。
 */
private val SQUARE_CORNER_SHAPES = setOf(
    WidgetShape.GIANT_SWORD,
    WidgetShape.PLUSH_FOREST,
    WidgetShape.SUBOR_CONSOLE,
    WidgetShape.SPRING_DOG,
    WidgetShape.PANDA_BAMBOO,
    WidgetShape.PET_PARK,
    WidgetShape.CUTE_FOUR_KIDS,
    WidgetShape.CRAYON_FRAME,
)

/**
 * 整幅透明底：背景色只作用于各自的文本框 / 文字栏。
 *
 * 当前**没有形状在用**（原本占位的贴纸夜景 / 城市剪影 / 毛玻璃都已下线）。名单与
 * [ShapeTraits.transparentCard] 一并保留，是留给这一类形状的扩展位——新形状归入时加一行即可，
 * 管线里「不铺整卡底色」「投影跟着文本框走」两条分支不用重写。
 */
private val TRANSPARENT_CARD_SHAPES = emptySet<WidgetShape>()

/** 素材由家族自己摆放的形状：整卡铺图会与家族布局打架 */
private val OWN_BACKGROUND_SHAPES = setOf(
    WidgetShape.WEATHER_BOX,
    WidgetShape.WINTER_PALACE,
    WidgetShape.DEEP_SEA,
    WidgetShape.SUMMER_SEA,
    WidgetShape.SUMMER_LOTUS,
)

private val TRAITS: Map<WidgetShape, ShapeTraits> = WidgetShape.entries.associateWith { shape ->
    ShapeTraits(
        family = shape.family(),
        insetCapable = shape in INSET_CAPABLE_SHAPES,
        forcesSquareCorners = shape in SQUARE_CORNER_SHAPES,
        transparentCard = shape in TRANSPARENT_CARD_SHAPES,
        drawsOwnBackground = shape in OWN_BACKGROUND_SHAPES,
    )
}

/** 形状在管线里的全部差异，见 [ShapeTraits]。 */
internal fun WidgetShape.traits(): ShapeTraits = TRAITS.getValue(this)

/**
 * 形状 → 家族。这里刻意穷举而不写 `else`：新增形状时编译器会强制在这里要一个归属，
 * 不会悄悄落进默认分支、被当成普通圆角矩形渲染。
 */
internal fun WidgetShape.family(): ShapeFamily = when (this) {
    WidgetShape.RECTANGLE,
    WidgetShape.PIXEL_RETRO,
    WidgetShape.PET_CAT_NAP,
    WidgetShape.NIUPI_SHOUZHANG,
    WidgetShape.CLASSROOM_BLACKBOARD,
    WidgetShape.GIANT_SWORD,
    WidgetShape.PLUSH_FOREST,
    // 春天与小狗：素材自带绿框与卡面，装饰由素材提供，走普通圆角矩形（无内缩、不额外绘制）
    WidgetShape.SPRING_DOG -> ShapeFamily.ROUND_RECT

    // 竹林熊猫：竹框、熊猫、竹叶全在素材里，装饰由素材提供，走普通圆角矩形（无内缩、不额外绘制）
    WidgetShape.PANDA_BAMBOO,
    // 萌宠乐园：毛毡框与六只小动物全在素材里，同上
    WidgetShape.PET_PARK,
    // 可爱四小只：头像条压在卡面左下角，走普通圆角矩形（卡面由背景色铺、头像条由背景图铺）
    WidgetShape.CUTE_FOUR_KIDS -> ShapeFamily.ROUND_RECT

    // 青年雕塑：浅灰卡面 + 雕塑 + 白色正文面板全在素材里，装饰由素材提供，
    // 走普通圆角矩形（不额外绘制）。圆角不强制直角——卡面本身就是一个 12dp 圆角矩形，
    // 素材四角是同色卡面，圆角交给管线裁（见 SQUARE_CORNER_SHAPES 的说明）
    WidgetShape.YOUTH_SCULPTURE -> ShapeFamily.ROUND_RECT

    // 蜡笔彩虹框：蜡笔框、爱心、气球全在素材里，装饰由素材提供，走普通圆角矩形（无内缩、不额外绘制）
    WidgetShape.CRAYON_FRAME -> ShapeFamily.ROUND_RECT

    WidgetShape.TORN_PAPER -> ShapeFamily.TORN_PAPER
    WidgetShape.FEATHER_LETTER -> ShapeFamily.FEATHER_LETTER
    WidgetShape.ELLIPSE -> ShapeFamily.ELLIPSE
    WidgetShape.HANDBOOK_TAPE -> ShapeFamily.HANDBOOK_TAPE
    WidgetShape.SPLIT_CARD,
    WidgetShape.SPLIT_CARD_HORIZONTAL -> ShapeFamily.SPLIT_CARD
    WidgetShape.BLUE_NOTE -> ShapeFamily.BLUE_NOTE
    WidgetShape.BOOKSHELF -> ShapeFamily.BOOKSHELF
    WidgetShape.SUBOR_CONSOLE -> ShapeFamily.SUBOR_CONSOLE
    WidgetShape.WEATHER_BOX -> ShapeFamily.WEATHER_BOX
    WidgetShape.WINTER_PALACE,
    WidgetShape.DEEP_SEA,
    WidgetShape.SUMMER_SEA,
    WidgetShape.SUMMER_LOTUS -> ShapeFamily.FRAMED_CARD

}
