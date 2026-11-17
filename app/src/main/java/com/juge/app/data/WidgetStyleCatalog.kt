package com.juge.app.data

import android.graphics.Color

/**
 * 内置风格目录：34 款预设，以及围绕它们的分类视图与付费判定。
 *
 * 原先是 [WidgetStyle] 的 companion 成员，模型、序列化、商品目录、付费判定挤在同一个
 * 1500 行文件里：改一款预设的配色要滚过序列化代码，改序列化又要滚过 900 行素材定义。
 * 拆出来后 [WidgetStyle] 只剩「数据结构 + JSON 读写」，目录与判定在这里。
 *
 * 对外入口仍是 `WidgetStyle.PRESETS` / `WidgetStyle.isProPreset(...)` 等（companion 里
 * 转发到这里），调用点一个都不用改。
 */
internal object WidgetStyleCatalog {

    // 内置风格预设
    // 每条都带一个手写的固定 presetId（如 p_pure_round）作为身份标识：套用后随 copy() 保留，
    // 付费判定依据身份而不是字段值（字段匹配可被"改一个字段"绕过），也不依赖列表位置。
    val PRESETS: List<WidgetStyle> = listOf(
        // 免费预设，同时是 App 的默认样式（[DEFAULT] 直接复用这一条）
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
            // 不加投影/描边：纯白卡面直接铺到组件边缘（投影还会带 4dp 内缩，
            // 描边会把白边压成灰边，看起来就不是纯白了）
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
            // 原先有一条 1.5dp 的米色描边（#DCD0BA）。描边改为「只画在卡面内」之后，
            // 它从"半条压在卡上、半条压在壁纸上"变成整条实心落在卡面上，可见宽度翻倍，
            // 在桌面上显成卡片外一圈浅色带。这层厚度感本来也不是必需的，直接去掉，卡片更干净。
            cardBorderWidthDp = 0f,
            authorSignature = "—— 手账心情"
        ), // 5. 复古手账风格 (PRO)
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
        // 新风格：经典左右分割（会员专属）
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

        // 可爱四小只 (PRO)：四个圆形萌宠头像(企鹅帽/蓝绿双马尾/黄脸/粉发双丸子)横排在卡片左下角，
        // 正文落在头像上方的整幅留白区（设计稿 1824×912）
        WidgetStyle(
            presetId = "p_cute_four_kids",
            bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 4×2 专属：设计稿就是 1824×912（2:1），不出 4×4 版
            // 2026-10-07 改版：四个头像从「左侧 2×2 网格」改成「左下角横排一排」（设计稿 1824×912）。
            // 刻意保留 presetId 不变——按身份判定的会员状态不受影响；
            // 已用这款的旧组件（存档里还是左右分割卡）由读档迁移跟着换成新造型，
            // 旧值记在 PRESET_LEGACY_LOOKS，改这一版的形状/铺图方式时同步更新那条。
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
            cardBorderWidthDp = 0f,
            presetImageResName = "weather_box_cavity",
            authorSignature = "—— 天气盒子"
        ), // 49. 天气盒子 (PRO)
        // 雪落宫墙 (PRO)：整卡米色卡纸，木框雪景宫墙照片按真实比例摆在上方，
        // 梅枝贴右下角。相框带羽化米边，梅枝是真透明抠图，卡纸底色由
        // 「背景颜色」绘制（默认米白），文字落在相框下方的留白带。
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
        // 相框带羽化卡纸边，鲸影是真透明抠图，文字落在相框下方整幅留白带。
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
        // 萌宠乐园 (PRO)：整幅毛毡风插画——粉色花边毛毡框 + 框顶六只毛毡小动物，框内奶油面板放正文。
        // 素材比例 1.757:1，与「毛绒森林」「春天与小狗」同一套语言，同样 STRETCH 铺满。
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
        // 青年雕塑 (免费, 2026-10-21 改版)：整幅明信片——纯白卡面 + 上方 52% 雕塑图，
        // 下方整片留白放正文。设计稿原生 250×250，已按 SVG 渲染成 2048×2048 方版；
        // 只出 4×4（见 WidgetShape.YOUTH_SCULPTURE 的说明），不填预设的方版字段。
        //
        // presetId 刻意保持不变：已保存/已落桌面的旧组件读档时直接跟到新造型，
        // 不必重新套用预设。
        //
        // 底色写成素材卡面自己的纯白：不透明度调低时整卡褪成同色，而不是露出上一个风格的底色。
        // 阴影 + 1dp 细描边与「纯色圆角」同款——免费风格的既有视觉定义，
        // 老存档读取时 upgradedForFreeStyle() 也会把这两项补齐，预设不写就会在重载时跳一下。
        WidgetStyle(
            presetId = "p_youth_sculpture",
            bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 4×4 专属：设计稿只有 250×250 方版，不出 4×2 版
            shape = WidgetShape.YOUTH_SCULPTURE,
            cornerRadiusDp = 12f, // 设计稿的「12dp 圆角参考」
            backgroundColor = Color.WHITE,
            backgroundOpacity = 1f,
            backgroundImagePath = null,
            bgImageScaleMode = ImageScaleMode.STRETCH,
            font = WidgetFont.SOURCE_HAN_SERIF, // 宋体：与纪念碑式的雕塑气质一致
            fontSizeSp = 18f,
            fontColor = Color.parseColor("#3F3B36"), // 暖石灰：压白色面板
            textAlign = "CENTER",
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
        // （2026-10-08：同「可爱四小只」，不要替它补方版素材）。
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
        // 可爱Doro (PRO)：萌宠——设计师新出的原生 2048×2048 方形版，
        // 粉发毛绒角色在上、奶油色留言板占下方约 42%~94%，四周透明；
        // 不再裁切/放大，留言板圆角完整保留，正文直接写在板子里。
        WidgetStyle(
            presetId = "p_cute_doro",
            bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 产品要求：默认 4×4
            shape = WidgetShape.CUTE_DORO,
            cornerRadiusDp = 12f,
            backgroundColor = android.graphics.Color.TRANSPARENT,
            backgroundOpacity = 1f,
            backgroundImagePath = null,
            bgImageScaleMode = ImageScaleMode.STRETCH,
            font = WidgetFont.LXGW_WENKAI,
            fontSizeSp = 18f,
            fontColor = Color.parseColor("#6B4A4A"), // 暖棕红：压奶油色留言板
            textAlign = "CENTER",
            showCardShadow = false,
            cardBorderWidthDp = 0f,
            presetImageResName = "cute_doro",
            authorSignature = "—— 可爱Doro"
        ), // 67. 可爱Doro (PRO)
        // 蓝色鲸鱼 (PRO)：4×2 萌宠卡——浅灰圆角卡 + #4998CD 描边 + 右下角小鲸鱼。
        // 设计稿 250×110，卡面/描边/鲸鱼都烘在 2048×1024 素材里；
        // 白框与描边本身是圆角，形状强制 0 圆角避免管线再把卡角切一刀。
        WidgetStyle(
            presetId = "p_blue_whale",
            bestDisplaySize = WidgetDisplaySize.WIDE_4X2, // 产品要求：默认 4:2
            shape = WidgetShape.BLUE_WHALE,
            cornerRadiusDp = 0f, // 设计稿的圆角在素材里；形状同时强制 0 圆角
            backgroundColor = android.graphics.Color.TRANSPARENT,
            backgroundOpacity = 1f,
            backgroundImagePath = null,
            bgImageScaleMode = ImageScaleMode.STRETCH,
            font = WidgetFont.LXGW_WENKAI,
            fontSizeSp = 18f,
            fontColor = Color.parseColor("#2F5D7A"), // 深蓝灰：与 #4998CD 描边同调
            textAlign = "CENTER",
            showCardShadow = false,
            cardBorderWidthDp = 0f,
            presetImageResName = "blue_whale",
            authorSignature = "—— 蓝色鲸鱼"
        ), // 68. 蓝色鲸鱼 (PRO)
        // 跨栏小子 (PRO)：明信片——上半是跨栏少年与栏杆的真透明抠图（不规则形状，
        // 人物与栏杆以外透出壁纸），下半是素材自带的白色记分牌正文框。
        // 素材原生 1536×1536，牌面 x 186~1356 / y 670~1405，正文落在牌内
        // （比例见 CardTextRenderer）；牌面与卡角圆角都在素材里，不铺背景色。
        // ⚠️ 按产品要求归类为明信片风格、默认 4×4，不出 4×2 版；
        // 4×2 上横向拉伸约 97% 是已知且接受的，**不要**给它补横版素材。
        WidgetStyle(
            presetId = "p_hurdle_boy",
            bestDisplaySize = WidgetDisplaySize.SQUARE_4X4, // 产品要求：默认 4×4
            shape = WidgetShape.HURDLE_BOY,
            cornerRadiusDp = 12f,
            backgroundColor = android.graphics.Color.TRANSPARENT,
            backgroundOpacity = 1f,
            backgroundImagePath = null,
            bgImageScaleMode = ImageScaleMode.STRETCH,
            font = WidgetFont.SOURCE_HAN_SANS, // 无衬线：与运动卡片/记分牌的现代感一致
            fontSizeSp = 18f,
            fontColor = Color.parseColor("#243244"), // 深蓝灰：压白色记分牌
            textAlign = "CENTER",
            showCardShadow = false,
            cardBorderWidthDp = 0f,
            presetImageResName = "hurdle_boy",
            authorSignature = "—— 跨栏小子"
        ), // 69. 跨栏小子 (PRO)
    )

    /**
     * App 的默认样式：就是免费预设「纯色圆角」。
     *
     * 首次安装的默认卡片、Provider 自动创建的配置、隐私/空库兜底与空 JSON 都取这一份，
     * 所以"没做任何选择"和"选中纯色圆角"是同一个样式（含 presetId，预设行会显示选中）。
     *
     * 不要再用 `WidgetStyle()` 当默认样式：构造函数的默认值只是"空白画布"
     * （底色 #F5F5F5、无描边、无投影），与这条预设不是一回事。
     */
    val DEFAULT: WidgetStyle = PRESETS.first { it.presetId == "p_pure_round" }

    // 免费预设：按身份 id 判定，未激活用户可直接套用。
    // 当前规则：「纯色圆角」与「青年雕塑」免费，其余风格一律会员专属。
    // 字体、字号、颜色、圆角、不透明度等细节调整不在此列，全部免费。
    private val FREE_PRESET_IDS = setOf(
        "p_pure_round",      // 纯色圆角
        "p_youth_sculpture"  // 青年雕塑
    )

    // 主体四周透明的形状：这些形状的插画/装饰并不铺满整个组件位图，背景色只会在主体外围
    // 露出一圈圆角卡片（羽毛信纸/撕纸/牛皮/书架），因此不支持设置背景色。
    // 与「外框圆角」的禁用名单不同（撕裂纸片圆角不可调，但背景色就是它的纸面颜色，仍可用）。
    private val SHAPES_WITHOUT_BACKGROUND_COLOR = setOf(
        WidgetShape.FEATHER_LETTER,
        WidgetShape.NIUPI_SHOUZHANG,
        WidgetShape.BOOKSHELF,
        WidgetShape.PLUSH_FOREST,
        WidgetShape.SUBOR_CONSOLE,
        // 春天与小狗：绿框白卡是素材本身的一部分，卡外透明处透出壁纸；
        // 再铺一层背景色会在卡外露出一圈色块
        WidgetShape.SPRING_DOG,
        // 萌宠乐园：同理——毛毡框内面板是素材自带的，框外（小动物周围）透明处透壁纸
        WidgetShape.PET_PARK,
        // 蜡笔彩虹框：同理——蜡笔框外一侧是透明的（透出壁纸），
        // 再铺一层背景色会在蜡笔描边外露出一圈方角色块
        WidgetShape.CRAYON_FRAME,
        // 跨栏小子：少年/栏杆抠图 + 白色记分牌都在素材里，周边透明；
        // 铺底色会把上半的透明区和记分牌外缘一起填掉
        WidgetShape.HURDLE_BOY,
        // 可爱Doro：角色与留言板是整张抠图，上方透明；铺底色会把透明区域填掉
        WidgetShape.CUTE_DORO,
        // 蓝色鲸鱼：浅灰卡面与蓝色描边都烘在素材里，铺底色会填掉卡外四角
        WidgetShape.BLUE_WHALE
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
    // 主界面各分类行共用同一份列表，避免多处硬编码各自维护导致入口间素材不一致。
    // 注意：萌宠/角色类素材(guga_doro、cute_cat、fluffy_dog)归入
    // 下方的 PET_PRESETS(萌宠风格)，这里刻意排除，避免分类不干净、同一批图重复出现在明信片行。
    val ILLUSTRATION_PRESETS: List<Pair<String, String>> = listOf(
        "bg_illustration_1" to "晨曦日出",
        "bg_illustration_2" to "治愈落日",
        "bg_illustration_3" to "星空森林",
        "boji_julebu" to "搏击俱乐部",
        "breaking_bad" to "绝命毒师"
    )

    /**
     * 插图素材 → 套用后的风格：形状、配色、字体一律取**素材自己的预设定义**，
     * 背景图改由素材资源名提供（自定义背景图路径清空）。
     *
     * 刻意不从当前样式 copy：上一个风格的底色会被带过来，明信片下半的
     * 文字显示区就会跟着变成深墨/黑色，与素材缩略图（预设原样渲染）完全对不上。
     * 明信片类预设的底色都声明为白色，只有素材本身设计成别的颜色时才不是。
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

    // 首页推荐预设（按风格分类）。
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
        "小霸王游戏机" to (PRESETS.firstOrNull { it.shape == WidgetShape.SUBOR_CONSOLE } ?: PRESETS[0]),
        "春天与小狗" to (PRESETS.firstOrNull { it.presetId == "p_spring_dog" } ?: PRESETS[0]),
        "蜡笔彩虹框" to (PRESETS.firstOrNull { it.presetId == "p_crayon_frame" } ?: PRESETS[0]),
    )

    // 明信片行**首位**的整幅贴图预设：按产品要求排在明信片风格第一个。
    // 与 POSTCARD_RENDERED_PRESETS 同样按组件真实渲染缩略图——整幅铺贴 + 正文面板的观感，
    // 裁原图是看不出来的。与代码绘制组分开列，避免"这组都是代码画的"的说法被说破。
    val POSTCARD_LEADING_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
        "青年雕塑" to (PRESETS.firstOrNull { it.presetId == "p_youth_sculpture" } ?: PRESETS[0])
    )

    // 明信片风格行里的代码绘制预设：没有插图素材，由 WidgetCanvasRenderer 直接绘制整幅组件。
    // 与插图素材项同排展示，排在插图之前。缩略图统一 180×96 外框，
    // 内容按各自 bestDisplaySize 等比（见 presetThumbnailSize），4×4 款不再被压扁。
    val POSTCARD_CODE_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
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
        "跨栏小子" to (PRESETS.firstOrNull { it.presetId == "p_hurdle_boy" } ?: PRESETS[0])
    )

    // 萌宠风格：动物/角色类卡通插画
    val PET_PRESETS: List<Pair<String, WidgetStyle>> = listOf(
        "得意doro" to (PRESETS.firstOrNull { it.presetImageResName == "deyi_doro" } ?: PRESETS[0]),
        "毛绒小狗" to (PRESETS.firstOrNull { it.presetImageResName == "fluffy_dog" } ?: PRESETS[0]),
        "可爱四小只" to (PRESETS.firstOrNull { it.presetImageResName == "cute_four_kids" } ?: PRESETS[0]),
        "毛绒森林" to (PRESETS.firstOrNull { it.shape == WidgetShape.PLUSH_FOREST } ?: PRESETS[0]),
        "萌宠乐园" to (PRESETS.firstOrNull { it.presetId == "p_pet_park" } ?: PRESETS[0]),
        "可爱Doro" to (PRESETS.firstOrNull { it.presetId == "p_cute_doro" } ?: PRESETS[0]),
        "蓝色鲸鱼" to (PRESETS.firstOrNull { it.presetId == "p_blue_whale" } ?: PRESETS[0])
    )
}
