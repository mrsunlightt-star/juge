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

    /** 贴纸夜景：整幅透明，只有文本框与抠图人物 */
    STICKER_SCENE,

    /** 城市剪影：剪影带 + 衔接层 + 文字栏 */
    CITY_CUTOUT,

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
    /** 内容整体内缩 4dp：只适用于内容完全按 rectF / outerRect 布局、内缩不会溢出的形状 */
    val usesInsetCard: Boolean,
    /** 强制直角：整幅插画被圆角裁切会切掉主体，忽略用户的圆角设置 */
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

/** 内容整体内缩 4dp 的形状 */
private val INSET_CARD_SHAPES = setOf(
    WidgetShape.RECTANGLE,
    WidgetShape.HANDBOOK_TAPE,
    WidgetShape.SPLIT_CARD,
    WidgetShape.SPLIT_CARD_HORIZONTAL,
    // 天气盒子：盒体与腔体都按 outerRect 布局，内缩安全；预设开了投影，
    // 不内缩的话阴影会被位图边界裁掉，盒体看起来是"贴平"的
    WidgetShape.WEATHER_BOX,
)

/** 整幅插画：预设套用时会继承上一个风格的圆角值，这里统一强制直角，避免旧数据套用后画面被裁 */
private val SQUARE_CORNER_SHAPES = setOf(
    WidgetShape.GIANT_SWORD,
    WidgetShape.PLUSH_FOREST,
    WidgetShape.SUBOR_CONSOLE,
    WidgetShape.CITY_CUTOUT,
    // 春天与小狗：圆角与绿框都在素材里，再裁一次会切掉框角
    WidgetShape.SPRING_DOG,
)

/** 整幅透明底：贴纸夜景的背景色只作用于文本框，城市剪影的只作用于文字栏 */
private val TRANSPARENT_CARD_SHAPES = setOf(
    WidgetShape.STICKER_SCENE,
    WidgetShape.CITY_CUTOUT,
)

/** 素材由家族自己摆放的形状：整卡铺图会与家族布局打架 */
private val OWN_BACKGROUND_SHAPES = setOf(
    WidgetShape.STICKER_SCENE,
    WidgetShape.CITY_CUTOUT,
    WidgetShape.WEATHER_BOX,
    WidgetShape.WINTER_PALACE,
    WidgetShape.DEEP_SEA,
    WidgetShape.SUMMER_SEA,
    WidgetShape.SUMMER_LOTUS,
)

private val TRAITS: Map<WidgetShape, ShapeTraits> = WidgetShape.entries.associateWith { shape ->
    ShapeTraits(
        family = shape.family(),
        usesInsetCard = shape in INSET_CARD_SHAPES,
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
    WidgetShape.ZHU_QING_SI_ZHI,
    WidgetShape.NIUPI_SHOUZHANG,
    WidgetShape.CLASSROOM_BLACKBOARD,
    WidgetShape.GIANT_SWORD,
    WidgetShape.PLUSH_FOREST,
    // 春天与小狗：素材自带绿框与卡面，装饰由素材提供，走普通圆角矩形（无内缩、不额外绘制）
    WidgetShape.SPRING_DOG -> ShapeFamily.ROUND_RECT

    WidgetShape.TORN_PAPER -> ShapeFamily.TORN_PAPER
    WidgetShape.FEATHER_LETTER -> ShapeFamily.FEATHER_LETTER
    WidgetShape.ELLIPSE -> ShapeFamily.ELLIPSE
    WidgetShape.HANDBOOK_TAPE -> ShapeFamily.HANDBOOK_TAPE
    WidgetShape.SPLIT_CARD,
    WidgetShape.SPLIT_CARD_HORIZONTAL -> ShapeFamily.SPLIT_CARD
    WidgetShape.BLUE_NOTE -> ShapeFamily.BLUE_NOTE
    WidgetShape.BOOKSHELF -> ShapeFamily.BOOKSHELF
    WidgetShape.SUBOR_CONSOLE -> ShapeFamily.SUBOR_CONSOLE
    WidgetShape.STICKER_SCENE -> ShapeFamily.STICKER_SCENE
    WidgetShape.CITY_CUTOUT -> ShapeFamily.CITY_CUTOUT
    WidgetShape.WEATHER_BOX -> ShapeFamily.WEATHER_BOX
    WidgetShape.WINTER_PALACE,
    WidgetShape.DEEP_SEA,
    WidgetShape.SUMMER_SEA,
    WidgetShape.SUMMER_LOTUS -> ShapeFamily.FRAMED_CARD
}
