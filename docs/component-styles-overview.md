# 组件风格总览与实现方案

> 这份文档回答两件事：
> 1. **现有 40 个组件风格各自是怎么实现的** —— 方便你对照着描述新风格。
> 2. **新增一个风格要动哪些地方** —— 一份可照着做的改动清单。
>
> 配套文档：`docs/component-style-guide.md`（图片处理脚本 `tools/process_component_image.py` 的用法与踩坑）。
>
> **与代码对齐核对：2026-10-08（第十一次）** —— `PRESETS` 40 条、`WidgetShape` 27 个取值。
> §4 / §5 的清单是**用脚本从 `WidgetStyle.kt` / `ShapeTraits.kt` 反查生成**的，不要手抄；
> 改动预设或形状后请重跑核对脚本（在 `.scratch/style-doc-drift/issues/01-resync-style-overview.md`），
> 并更新这一行的日期。

---

## 1. 先用「四要素」描述一个风格

跟人（或跟 AI）描述一个新风格时，把这四点说清楚就够了，剩下的都能推导出来：

| 要素 | 说明 | 取值 |
| --- | --- | --- |
| **① 路线** | 这个风格靠什么画出来 | A 纯代码卡片 / B 整卡贴图 / C 图文明信片（见 §3） |
| **② 形状** | 组件的外轮廓 | `WidgetShape` 的 27 个取值之一（见 §5） |
| **③ 素材** | 有没有图、图的透明处理方式 | 无素材 / 整幅抠图 / 实景照片不抠 |
| **④ 文字落点** | 正文压在哪个区域 | 留白区、避开装饰（见 §6） |

举例，用四要素描述「书香书架」：

> 路线 A（纯代码，无素材）；形状 `BOOKSHELF`；顶部彩色书脊立在横板上、底部是米色摘录面板，装饰按卡片矩形等比现画；文字落在底部面板的留白区内（见 §6）。

---

## 2. 一个风格由什么决定

代码上只有两个东西：

- **`WidgetShape`（枚举）** —— 外轮廓。决定裁剪路径、文字安全区、能否调圆角/背景色。
- **`WidgetStyle`（data class）** —— 所有可调参数。预设就是一堆预填好的 `WidgetStyle`。

### 2.1 WidgetStyle 字段速查

| 字段 | 作用 | 备注 |
| --- | --- | --- |
| `shape` | 外轮廓 | 见 §5 |
| `presetId` | **身份标识**（手写语义 ID，如 `p_pure_round`） | 付费判定的唯一依据；`copy()` 后仍保留 |
| `cornerRadiusDp` | 圆角 0~30dp | 多数形状是**外框**圆角；整幅插画类强制 0、滑条禁用（见 §7.2 / §7.3） |
| `backgroundColor` | 背景色 | 部分形状强制透明（见 §7） |
| `backgroundOpacity` | 背景不透明度 | 同时作用于背景图与文本框 |
| `backgroundImagePath` | 用户自定义背景图 | 优先于预设图 |
| `bgImageScaleMode` | 图片缩放模式 | STRETCH / CENTER_CROP / CENTER_FIT / CENTER_CROP_TOP / TILE |
| `presetImageResName` | 内置素材名（= drawable 名，不带扩展名） | 空则无素材 |
| `presetImageResNameSquare` | **方版素材**（4×4 用） | 组件比例 < 1.4 时优先用它；为空则一律用横版。见 style-guide §0.1 |
| `bestDisplaySize` | **最佳显示尺寸**（App 预览按它出图） | `WIDE_4X2`（250×110）/ `SQUARE_4X4`（250×250）；按素材原生画布定，见 style-guide §0.1.2。**不落 JSON**：读档时按 `presetId` 解析当前预设定义 |
| `font` / `fontSizeSp` / `fontBold` / `fontItalic` / `fontColor` / `textAlign` | 文字排版 | `WidgetFont` 有 8 种（系统默认 + 7 款中文字体） |
| `shadow` | 文字阴影 | 小霸王游戏机用它做**荧光晕**；巨剑用它做暗描边保可读性 |
| `gradientColors` / `gradientAngle` | 背景渐变 | 目前只有「蓝色画报」用 |
| `bgBlurRadius` / `bgScrimAlpha` | 背景模糊 / 压暗 | 自定义背景图时用 |
| `showCardShadow` | 卡片投影 | 只有 5 条预设开着（纯色圆角、拟物撕纸、复古手账、蓝色便签、天气盒子）；柔度定义见 `WidgetRenderPipeline.drawCardBackground` |
| `cardBorderWidthDp` / `cardBorderColor` | 卡片描边 | 裁到卡片路径、**只画在卡面内部**，不溢出到卡片外那圈透明内缩上。字段值 = **可见宽度**：内部按两倍线宽画（居中描边裁掉外侧一半），所以填 2.0 就得到 2dp 宽的白框（见 §10） |
| `textureType` | 纸张纹理 | `PAPER` / `GRAIN` / `NONE`；用 `PAPER` 的有 4 条：拟物撕纸、雪落宫墙、夏天的海、夏日荷花 |
| `lineSpacingMultiplier` / `letterSpacing` | 行距 / 字距 | |

### 2.2 免费与会员

- **免费预设只有 1 个**：`FREE_PRESET_IDS = {"p_pure_round"}`（纯色圆角）。
- 判定走 `isProPreset(style)`：**按 `presetId` 身份**，不按字段匹配（字段匹配能被"改一个字段"绕过）。
- 字体、字号、颜色、圆角、不透明度这些**细节调整全部免费**，不在会员范围内。
- ⚠️ `PRESETS` 列表**中间删条目会导致按下标取用的地方错位**；新增一律**追加到末尾**，并在分类列表里按 `presetId`/`shape`/`presetImageResName` 定位而不是按下标。

---

## 3. 三条实现路线

### 路线 A：纯代码绘制的卡片（无素材）

**特征**：没有 `presetImageResName`，整幅组件由 `WidgetCanvasRenderer` 用 Canvas 画出来。
**适用**：极简、纯色、拟物纸感、需要随尺寸自由伸缩的风格。
**代价**：换一个新造型就得写绘制代码。

| 风格 | 画法 |
| --- | --- |
| 纯色圆角 | 圆角矩形 + 浅阴影 + 细描边 |
| 拟物撕纸 | `generateTornPath()` 用固定种子 `TORN_SEED=42` 生成锯齿边 + `PAPER` 纹理 |
| 复古手账 | 内缩卡片 + `drawTape()` 在左上/右下角画两条半透明胶带 |
| 蓝色画报 | `SPLIT_CARD` + `LinearGradient` 渐变填充图片区 |
| 蓝色便签 | `drawBlueNoteChrome()`：顶部 NOTE 区 + 右上信息钮 + 底部米白手写签条 |
| 书香书架 | `drawBookshelfChrome()`：顶部彩色书脊立在横板上 + 底部米色摘录面板 |

> 路线 A 的完整名单见 §4 表里「路线 = A」的行（当前 5 条）。

>

### 路线 B：整卡贴图（图即主体）

**特征**：`presetImageResName` 指向一张**已经抠好底**的图，图铺满整卡，图外透明透出桌面壁纸。
**关键约束**：这类形状**不能设背景色**（设了会在主体外围露出一圈圆角卡片）。
**缩放模式**：多用 `STRETCH`（图本身就是按组件比例设计的），整幅实物模型类用 `CENTER_FIT`。

| 风格 | 形状 | 素材 | 特殊处理 |
| --- | --- | --- | --- |
| 羽毛信纸 | `FEATHER_LETTER` | `feather_letter` | 专用轮廓 `drawFeatherLetterPath()`，信纸居中、四周留边 |
| 复古像素 | `PIXEL_RETRO` | `pixel_retro` | 矩形轮廓，文字避开四周虚线框 |
| 撕边牛皮手账 | `NIUPI_SHOUZHANG` | `niupi_shouzhang` | |
| 教室黑板 | `CLASSROOM_BLACKBOARD` | `classroom_blackboard` | 粉笔白字写在绿板面上 |
| 萌宠猫咪趴 | `PET_CAT_NAP` | `cute_cat_lying` | 用 `CENTER_CROP_TOP`：等比铺满但**顶部锚定**，趴在顶部的猫不被裁 |
| 巨剑 | `GIANT_SWORD` | `giant_sword` | 文字只压右侧剑身，加暗描边保可读性 |
| 毛绒森林 | `PLUSH_FOREST` | `plush_forest` | |
| 小霸王游戏机 | `SUBOR_CONSOLE` | `subor_console` | 素材是**未通电**的深灰玻璃屏，代码叠绿色荧光底 + 扫描线 + 暗角 + 玻璃反光 |
| 萌宠乐园 | `PET_PARK` | `pet_park` | 粉色花边毛毡框 + 框顶六只毛毡小动物（猫狗兔鼠狐熊猫），框内奶油面板承载正文。素材原稿是**纯白底**（同样被压成无 alpha 的 RGB），按「低饱和 + 高亮 + 与边界连通」抠掉；白猫的绒毛与白底之间原图就没有硬边，门槛提到 240 才保住耳朵 |
| 竹林熊猫 | `PANDA_BAMBOO` | `panda_bamboo` | 竹框 + 框顶五只探头熊猫 + 四角竹叶，框内奶油面板承载正文。素材原稿四周的白灰棋盘格是**假透明**（PNG 被压成 RGB），按「低饱和 + 高亮 + 与边界连通」抠掉、保留内面板 |
| 春天与小狗 | `SPRING_DOG` | `spring_dog` | 绿框白卡 + 上沿草丛麦穗粉花 + 右上角探头柯基；四周抠成透明（卡外透壁纸）。与毛绒森林同一套设计语言，素材同为 1.79:1，同样走 `STRETCH` |
| 蜡笔彩虹框 **（4×2 原生）** | `CRAYON_FRAME` | `crayon_frame` | 蓝/绿/粉三色蜡笔波浪框 + 框内近白纸面，左上红蜡笔爱心、右下黄蜡笔气球；框外抠成透明。设计稿 2048×1152 是 16:9 画布、框只占中间一条（上下各留 21%~27% 透明），**入库前按内容框裁到 1826×627**，否则铺满后框会缩在中间。裁后 2.91:1，4×2 上纵向多铺约 28%。⚠️ **这款风格不出 4×4 版**（产品决定，2026-10-08）：4×4 上素材要纵向拉近 2.9 倍，爱心会拉成细条；**不要**替它补方版素材 |
| 天空之蓝 | `RECTANGLE` | `rectangle_1`（矢量 XML） | 代码额外画一个 "NOTE" 标签 |
| 纹理山水 | `RECTANGLE` | `texture_landscape` | 整卡贴图。素材是设计稿里那张水彩山水，已按设计稿的拉伸方式**预烘**成 222:90（源图切片直接裁会丢掉上半雾带与下半重山，构图对不上）。白框改用卡片描边而非烘进素材——圆角滑条一动，框跟着走圆角。⚠️ 4×2 原生：源切片仅 1152×679 真实像素，4×4 方形裁切要放大 2.3x，会软 |
| 信纸 | `RECTANGLE` | `ruled_paper`（**矢量 XML**） | 横线信纸。纸色由「背景颜色」给（默认白），只有横线在素材里；铺图用 `CENTER_CROP`——4×2 下等比裁中间一段，横线维持 18.3dp 原间距（`STRETCH` 会把 11 条压成 4 条）。矢量素材，栅格化按组件尺寸现算，拖多大都不糊 |
| 天气盒子 | `WEATHER_BOX` | `weather_box_cavity` | 白盒 + 内凹腔体；腔体几何由 `WeatherBoxRenderer` 按尺寸现算，只用 `CENTER_FIT` 摆腔底素材 |
| 雪落宫墙 / 深海鲸歌 / 夏天的海 / 夏日荷花 | `WINTER_PALACE` / `DEEP_SEA` / `SUMMER_SEA` / `SUMMER_LOTUS` | `winter_frame` / `deepsea_frame` / `summersea_frame` / `lotus_frame` | 「画框卡片」族：卡纸 + 相框 + 角落点缀，素材由 `FramedCardRenderer` 自己摆（见 §3 补充） |
| 可爱四小只 **（4×2 专属）** | `CUTE_FOUR_KIDS` | `cute_four_kids` | 2026-10-07 改版：四个头像横排在左下角，素材是透明底的「头像条」，压在背景色铺出的卡面上。设计稿就是 1824×912（2:1），**按产品决定不出 4×4 版**，4×4 上的拉伸是已知且接受的 |
| 青年雕塑 **（4×4 专属）** | `YOUTH_SCULPTURE` | `youth_sculpture` | 整卡贴图：浅灰卡面 + 上半身雕塑像（带白描边的抠图），下半白色面板放正文，面板中央有设计稿自带的 10% 星形纹样。设计稿只出 2048×2048 方版，**按产品决定不出 4×2 版**，4×2 上的横向拉伸是已知且接受的。卡面就是素材底色、四角同色，圆角交给管线裁，因此**不强制直角**、圆角滑条可用（12dp 起） |

> 例外：`WEATHER_BOX`（盒面）与 `WINTER_PALACE` / `DEEP_SEA` / `SUMMER_SEA` / `SUMMER_LOTUS`（卡纸）
> 虽然也吃一整张素材，但素材只占画面的一部分，**背景色就是它们的盒面 / 卡纸底色**，因此可以设
> （见 §7.1 名单）——它们的素材由家族绘制自己摆放，不走整卡铺图。
>
> 另一类能设背景色的是「卡面即底色」的整卡贴图：纹理山水、信纸、**青年雕塑**。
> 它们填的是素材卡面自己的颜色，作用不是包边，而是**调低不透明度时整卡褪成同色**
> （不填就会露出上一个风格的底色）。

### 路线 C：图文明信片（图占一半，代码画另一半）

**特征**：图只占一部分，剩下的是**代码绘制**的文字区（底色 = `backgroundColor`，受 `backgroundOpacity` 控制）。
**两种朝向**：

| 形状 | 图占 | 文字区 | 预设（当前） |
| --- | --- | --- | --- |
| `SPLIT_CARD` | 上半 **48%** | 下半 | 蓝色画报、晨曦日出、治愈落日、星空森林、搏击俱乐部、绝命毒师、V字仇杀队、爱乐之城（8 条） |
| `SPLIT_CARD_HORIZONTAL` | 左半 **33.3%** | 右半 | 可爱猫咪、毛绒小狗、快乐小狗、毛绒猫咪、得意doro（5 条） |

**素材分两种**，直接决定 `bgImageScaleMode`：

- **实景照片（不抠底）** → `CENTER_CROP`，铺满图区、多出的部分裁掉。
- **透明抠图（宠物/角色）** → `CENTER_FIT`，等比完整显示，**避免裁掉头/脸**。

> ⚠️ 明信片类的 `backgroundColor` 是**文字区底色**，不是整卡底色。图区单独铺图，两处不能各写一遍矩形（共用 `splitImageRect()`）。

### 已退役的路线：D（贴纸场景）与 E（城市微缩）

> 2026-10-07 按产品决定**彻底删除**了这两条路线上的全部 4 个风格：
> 贴纸夜景（路线 D）、浙江剪影 / 北京微缩 / 上海微缩（路线 E）。
> 连同形状（`STICKER_SCENE` / `CITY_CUTOUT`）、家族、渲染器（`StickerRenderer` / `CityRenderer` / `SoilRenderer`）
> 与素材一起移除，`WidgetRenderPipeline.drawFamilyChrome` 里的两个分支、`textBoxFor` 的两个文字安全区分支
> 也一并删掉——所以本文不再有这两节。
>
> 旧存档若仍带着这些形状名，`WidgetStyle.fromJsonString` 的 `safeEnum` 会把 shape 降级为
> `RECTANGLE`（其余字段照常保留），桌面上的老组件会变成一张同色系的普通圆角卡，不会崩。
> 需要复活这条路线时，从 git 历史里取回这三个渲染器与对应素材即可。

## 4. 全部 40 个风格清单

`PRESETS` 的顺序**就是**下表下标（新增只能追加到末尾）。名称取自五个分类清单，
完整清单由脚本从 `WidgetStyle.kt` 反查生成，不要手抄。

| # | 名称 | presetId | 形状 | 路线 | 素材 | 会员 |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 纯色圆角 | `p_pure_round` | RECTANGLE | A | — | **免费** |
| 1 | 拟物撕纸 | `p_torn_paper` | TORN_PAPER | A | — | PRO |
| 2 | 复古手账 | `p_handbook_tape` | HANDBOOK_TAPE | A | — | PRO |
| 3 | 蓝色画报 | `p_postcard_note` | SPLIT_CARD | C | — | PRO |
| 4 | 晨曦日出 | `p_dawn_sunrise` | SPLIT_CARD | C | `bg_illustration_1` | PRO |
| 5 | 治愈落日 | `p_healing_sunset` | SPLIT_CARD | C | `bg_illustration_2` | PRO |
| 6 | 星空森林 | `p_starry_forest` | SPLIT_CARD | C | `bg_illustration_3` | PRO |
| 7 | 天空蓝 | `p_sky_blue` | RECTANGLE | B | `rectangle_1` | PRO |
| 8 | 搏击俱乐部 | `p_fight_club` | SPLIT_CARD | C | `boji_julebu` | PRO |
| 9 | 绝命毒师 | `p_breaking_bad` | SPLIT_CARD | C | `breaking_bad` | PRO |
| 10 | V字仇杀队 | `p_v_for_vendetta` | SPLIT_CARD | C | `v_for_vendetta` | PRO |
| 11 | 爱乐之城 | `p_la_la_land` | SPLIT_CARD | C | `aile_zhi_cheng` | PRO |
| 12 | 可爱猫咪 | `p_cute_cat` | SPLIT_CARD_HORIZONTAL | C | `cute_cat` | PRO |
| 13 | 毛绒小狗 | `p_fluffy_dog` | SPLIT_CARD_HORIZONTAL | C | `fluffy_dog` | PRO |
| 14 | 快乐小狗 | `p_happy_dog` | SPLIT_CARD_HORIZONTAL | C | `happy_dog` | PRO |
| 15 | 毛绒猫咪 | `p_fluffy_cat` | SPLIT_CARD_HORIZONTAL | C | `fluffy_cat` | PRO |
| 16 | 羽毛信纸 | `p_feather_letter` | FEATHER_LETTER | B | `feather_letter` | PRO |
| 17 | 复古像素 | `p_pixel_retro` | PIXEL_RETRO | B | `pixel_retro` | PRO |
| 18 | 萌宠猫咪趴 | `p_pet_cat_nap` | PET_CAT_NAP | B | `cute_cat_lying` | PRO |
| 19 | 可爱四小只 | `p_cute_four_kids` | CUTE_FOUR_KIDS | B | `cute_four_kids` | PRO |
| 20 | 蓝色便签 | `p_blue_note` | BLUE_NOTE | A | — | PRO |
| 21 | 撕边牛皮手账 | `p_niupi_shouzhang` | NIUPI_SHOUZHANG | B | `niupi_shouzhang` | PRO |
| 22 | 得意doro | `p_deyi_doro` | SPLIT_CARD_HORIZONTAL | C | `deyi_doro` | PRO |
| 23 | 教室黑板 | `p_classroom_blackboard` | CLASSROOM_BLACKBOARD | B | `classroom_blackboard` | PRO |
| 24 | 书香书架 | `p_bookshelf` | BOOKSHELF | A | — | PRO |
| 25 | 巨剑 | `p_giant_sword` | GIANT_SWORD | B | `giant_sword` | PRO |
| 26 | 毛绒森林 | `p_plush_forest` | PLUSH_FOREST | B | `plush_forest` | PRO |
| 27 | 小霸王游戏机 | `p_subor_console` | SUBOR_CONSOLE | B | `subor_console` | PRO |
| 28 | 天气盒子 | `p_weather_box` | WEATHER_BOX | B | `weather_box_cavity` | PRO |
| 29 | 雪落宫墙 | `p_winter_palace` | WINTER_PALACE | B | `winter_frame` | PRO |
| 30 | 深海鲸歌 | `p_deep_sea` | DEEP_SEA | B | `deepsea_frame` | PRO |
| 31 | 夏天的海 | `p_summer_sea` | SUMMER_SEA | B | `summersea_frame` | PRO |
| 32 | 夏日荷花 | `p_summer_lotus` | SUMMER_LOTUS | B | `lotus_frame` | PRO |
| 33 | 春天与小狗 | `p_spring_dog` | SPRING_DOG | B | `spring_dog` | PRO |
| 34 | 信纸 | `p_ruled_paper` | RECTANGLE | B | `ruled_paper` | PRO |
| 35 | 纹理山水 | `p_texture_landscape` | RECTANGLE | B | `texture_landscape` | PRO |
| 36 | 竹林熊猫 | `p_panda_bamboo` | PANDA_BAMBOO | B | `panda_bamboo` | PRO |
| 37 | 萌宠乐园 | `p_pet_park` | PET_PARK | B | `pet_park` | PRO |
| 38 | 青年雕塑 | `p_youth_sculpture` | YOUTH_SCULPTURE | B | `youth_sculpture` | **免费** |
| 39 | 蜡笔彩虹框 | `p_crayon_frame` | CRAYON_FRAME | B | `crayon_frame` | PRO |

### 4.1 界面上怎么分组展示

五个入口列表都定义在 `WidgetStyle` 的 companion object 里，「个性定制」页统一取用（不要在各处再写一遍）：

| 列表 | 界面标题 | 内容（当前） |
| --- | --- | --- |
| `CLASSIC_PRESETS` | 经典风格 | 纯色圆角、拟物撕纸、复古手账、羽毛信纸、复古像素、蓝色便签、撕边牛皮手账、教室黑板、巨剑、小霸王游戏机、春天与小狗、纹理山水、蜡笔彩虹框（13 条） |
| `PET_PRESETS` | 萌宠风格 · 会员专属 | 得意doro、可爱猫咪、毛绒小狗、快乐小狗、毛绒猫咪、萌宠猫咪趴、可爱四小只、毛绒森林、竹林熊猫、萌宠乐园（10 条） |
| `POSTCARD_LEADING_PRESETS` | 明信片风格（**行首**的整幅贴图） | 青年雕塑（1 条） |
| `POSTCARD_CODE_PRESETS` | 明信片风格（代码绘制的大卡） | 蓝色画报、书香书架（2 条） |
| `POSTCARD_RENDERED_PRESETS` | 明信片风格（按真实渲染的场景卡） | 天气盒子、雪落宫墙、深海鲸歌、夏天的海、夏日荷花、信纸（6 条） |
| `ILLUSTRATION_PRESETS` | 明信片风格（插图素材） | 8 张插图的 `资源名 → 展示名` 映射 |

> 六份清单合计覆盖**全部 40 条**预设。

> **界面上的尺寸（2026-10-08 起）**：三行风格标题都**不带尺寸后缀**（原先「经典风格 · 4×2」的
> 后缀取的是选中组件的入口尺寸，与各款风格自己的设计尺寸并不相干）。每款风格的最佳显示尺寸
> 声明在 `WidgetStyle.bestDisplaySize` 上（见 §2.1、style-guide §0.1.2），App 内预览按它出图：
> 4×4 款始终方形、4×2 款始终长条，与组件是从哪个入口添加、在桌面上被拉成多大**都无关**。
> 一览当前取值（别手抄，以代码为准）：
> `grep -E 'presetId|bestDisplaySize' app/src/main/java/com/juge/app/data/WidgetStyle.kt`
>
> 2026-10-06 删掉了 3 条「没登记进任何分类行、界面上点不到」的预设：白底黑字（p_white_black）、
> 深夜模式（p_dark_night）、磨砂玻璃（p_frosted_glass）。这三条**已不在代码里**，所以在本段按普通文字书写、
> 不加反引号——§4 的核对脚本会把「反引号里的 presetId」一律当作必须存在的引用。
> 旧存档里若仍带着这三个 presetId，样式字段本身完整保存在 JSON 里，渲染与「是否 PRO」的判定
> 都不受影响（付费按 presetId 身份判定，不查预设列表）。

> 2026-10-07（第二次删除）又下架五款：**天天开心**（p_happy_daily，横版明信片）、**竹青撕纸**（p_zhu_qing_si_zhi，
> 连同形状 ZHU_QING_SI_ZHI 一起删）、**蓝天白云**（p_blue_sky_clouds，插图类）、以及当天上午刚上线的
> **状态色点缀**（p_status_accent）与**毛玻璃**（p_frosted_glass）——后两款连同 STATUS_ACCENT / FROSTED_GLASS
> 两个形状家族与各自的渲染器一并移除，`PRESETS` 39 → 34。这三条的 presetId 已不在代码里，故本段按普通文字书写。
>
> 萌宠/角色类素材刻意**不放进 `ILLUSTRATION_PRESETS`**，避免同一批图在明信片行重复出现。
>
> `POSTCARD_RENDERED_PRESETS` 里的场景卡（天气盒子 + 画框卡片族）都是「素材只占画面一部分、其余由代码画」的类型。缩略图**必须按组件真实渲染**（`WidgetCanvasRenderer.render`，行内统一 **150×80dp**），不能裁原图，否则形状/铺图方式的差异在缩略图上完全看不出来。按 `presetId` 定位，避免 `PRESETS` 新增条目时索引漂移。
> 行首的 `POSTCARD_LEADING_PRESETS` 同理（整幅铺贴 + 正文安全区的观感只有真实渲染才看得见）。

---

## 5. 形状清单（`WidgetShape`）

27 个取值，其中 **`ELLIPSE` 是历史遗留，没有任何预设使用**。
「可内缩 / 强制直角 / 整幅透明底 / 素材自己摆放」四列直接对应 `ShapeTraits.kt` 的四张名单。

| 形状 | 外轮廓怎么来的 | 可内缩 | 备注 |
| --- | --- | --- | --- |
| `RECTANGLE` | 圆角矩形 | ✅ |  |
| `ELLIPSE` | 椭圆 | ❌ |  |
| `HANDBOOK_TAPE` | 圆角矩形 | ✅ |  |
| `TORN_PAPER` | `generateTornPath()` 固定种子锯齿 | ❌ |  |
| `SPLIT_CARD` | 圆角矩形 | ✅ |  |
| `SPLIT_CARD_HORIZONTAL` | 圆角矩形 | ✅ |  |
| `FEATHER_LETTER` | `drawFeatherLetterPath()` | ❌ |  |
| `PIXEL_RETRO` | 圆角矩形 | ❌ |  |
| `PET_CAT_NAP` | 圆角矩形 | ❌ |  |
| `BLUE_NOTE` | 圆角矩形 | ❌ |  |
| `NIUPI_SHOUZHANG` | 圆角矩形 | ❌ |  |
| `CLASSROOM_BLACKBOARD` | 圆角矩形 | ❌ |  |
| `BOOKSHELF` | 圆角矩形（**自身即组件**，无外框） | ❌ | 圆角滑条不生效 |
| `GIANT_SWORD` | 圆角矩形（**强制 0 圆角**） | ❌ |  |
| `PLUSH_FOREST` | 圆角矩形（**强制 0 圆角**） | ❌ |  |
| `SUBOR_CONSOLE` | 圆角矩形（**强制 0 圆角**） | ❌ |  |
| `WEATHER_BOX` | 圆角矩形（白盒 + 内凹腔体） | ✅ | 整幅透明底，背景色只作用于文本框/文字栏；素材由家族自己摆放 |
| `WINTER_PALACE` | 圆角矩形（卡纸 + 相框 + 角落点缀） | ❌ | 整幅透明底，背景色只作用于文本框/文字栏；素材由家族自己摆放 |
| `DEEP_SEA` | 圆角矩形（卡纸 + 相框 + 角落点缀） | ❌ | 整幅透明底，背景色只作用于文本框/文字栏；素材由家族自己摆放 |
| `SUMMER_SEA` | 圆角矩形（卡纸 + 相框 + 角落点缀） | ❌ | 整幅透明底，背景色只作用于文本框/文字栏；素材由家族自己摆放 |
| `SUMMER_LOTUS` | 圆角矩形（卡纸 + 相框 + 角落点缀） | ❌ | 整幅透明底，背景色只作用于文本框/文字栏；素材由家族自己摆放 |
| `SPRING_DOG` | 圆角矩形（**强制 0 圆角**） | ❌ | 绿框与圆角都在素材里 |
| `PANDA_BAMBOO` | 圆角矩形（**强制 0 圆角**） | ❌ | 整幅贴纸风插画：竹框 + 框顶熊猫 + 四角竹叶，框外已抠透明 |
| `PET_PARK` | 圆角矩形（**强制 0 圆角**） | ❌ | 整幅毛毡风插画：粉色花边毛毡框 + 框顶六只小动物，框外已抠透明 |
| `CUTE_FOUR_KIDS` | 圆角矩形（**强制 0 圆角**） | ❌ | 四个头像**横排**在左下角（2026-10-07 从「左侧 2×2 网格」改版），卡面由背景色铺；圆角会啃掉最左侧头像的下角 |
| `YOUTH_SCULPTURE` | 圆角矩形 | ✅ | 整卡贴图：浅灰卡面 + 雕塑像 + 白色正文面板都在 2048×2048 素材里。四角是卡面同色，圆角交给管线裁 → **不强制直角**（圆角滑条可用） |
| `CRAYON_FRAME` | 圆角矩形（**强制 0 圆角**） | ❌ | 整幅手绘素材：蜡笔波浪框 + 框内白纸面 + 两枚小装饰，框外已抠透明 |

**「可内缩」（`CARD_INSET_DP = 4dp`）**：名单在 `ShapeTraits.kt` 的 `INSET_CAPABLE_SHAPES`——`RECTANGLE` / `HANDBOOK_TAPE` / `SPLIT_CARD` / `SPLIT_CARD_HORIZONTAL` / `WEATHER_BOX` / `YOUTH_SCULPTURE`。
>
> ⚠️ 这一列说的是「内容按 `rectF`/`outerRect` 布局，内缩**不会溢出**」，即**能不能**内缩；**要不要**内缩由 `RenderScene.cardInset` 另判：**轮廓外沿真有东西要放才内缩**——① 样式开了投影（`showCardShadow`，阴影画在位图内部，满幅会被位图边界硬切、卡片看起来"贴平"），
>
> 两件事原先混在一份名单里，结果 15 款**没有投影**的风格（8 张明信片插图、6 款萌宠、天空之蓝）也白留了 4dp 透明边——那圈边除了透出壁纸，还给 launcher 重新挂载组件时垫的白色占位底留了个出风口。其余形状有各自按整幅位图绘制的装饰，保持满幅以免错位。

**未列入「跟随用户圆角」名单的形状**，外框圆角统一用 `DEFAULT_OUTER_CORNER_RADIUS_DP = 16dp`（即 `TORN_PAPER` / `FEATHER_LETTER` / `ELLIPSE`）。

---

## 6. 各形状的「文字安全区」

文字区不是写死的像素，而是**按比例算出来的矩形**（`cardTop` / `cardHeight` / `paddingLeft` / `paddingRight`），这样 4×2 和 4×4 都不会错位。
超出可容纳行数时用省略号（`TruncateAt.END`），不会被画布硬裁。

| 形状 | 上边 | 下边 / 可用高 | 左 | 右 | 说明 |
| --- | --- | --- | --- | --- | --- |
| RECTANGLE / HANDBOOK_TAPE / TORN_PAPER / ELLIPSE | 卡片内缩 | 卡片内缩 | 16dp | 16dp | 默认分支 |
| SPLIT_CARD | 48% + 12dp | 12dp | 16dp | 16dp | 落下半 |
| SPLIT_CARD_HORIZONTAL | 12dp | 12dp | 33.3% + 12dp | 12dp | 落右半 |
| FEATHER_LETTER | 16% | 14% | 10% | 20% | 右侧多留避羽毛笔 |
| PIXEL_RETRO | 12% | 12% | 10% | 10% | 避虚线框 |
| PET_CAT_NAP | 42% | 8% | 12% | 12% | 避顶部的猫 |
| BLUE_NOTE | 高度×76/363×0.85 | 高度×76/363 | 8% | 8% | 避 NOTE 与底部签条 |
| NIUPI_SHOUZHANG | 11% | 11% | 11% | 11% | 避撕边 |
| CLASSROOM_BLACKBOARD | 12% | 高 58%（止于 70%） | 9% | 9% | 避木框与粉笔槽 |
| BOOKSHELF | 面板内 + 6dp | 面板内 − 6dp | 面板内 + 14dp | 面板内 − 14dp | 与摘录面板同一矩形 |
| GIANT_SWORD | 39% | 高 33% | 33% | 93% | 只压右侧剑身 |
| PLUSH_FOREST | 40% | 高 47% | 11.5% | 88.5% | 避顶部毛绒小树 |
| SPRING_DOG | 40% | 高 54.5% | 4.5% | 95.5% | 避绿框（比例取自素材实测：框内白卡 x 46~1767 / y 388~964，素材 1812×1012） |
| PANDA_BAMBOO | 44% | 高 39% | 8.3% | 91.7% | 竹框内的奶油面板（素材 2080×1184：面板内沿 x 111~1974 / y 485~1021，再留余量不贴竹竿） |
| PET_PARK | 38% | 高 49% | 8% | 92% | 毛毡框内的奶油面板（素材 2080×1184：面板内沿 x 77~1973 / y 404~1062，再留余量不贴花边） |
| CUTE_FOUR_KIDS | 10% | 高 62% | 8% | 92% | 头像条**上方**的整幅留白区（头像条占设计稿 1824×912 的 y 689~840，再留 3.5% 间隔） |
| YOUTH_SCULPTURE | 59% | 高 34.5% | 11.5% | 84.5% | 下半那张白色面板（素材 2048×2048：面板 x 203~1760 / y 1174~1933，再留余量不贴面板边缘） |
| CRAYON_FRAME | 17.5% | 高 67% | 14.5% | 12.5% | 蜡笔框内的白纸面（素材 1826×627：纸面 x 0.052~0.952 / y 0.137~0.861），左避红爱心（右沿 x 0.133）、右避黄气球（左沿 x 0.890） |
| SUBOR_CONSOLE | 屏幕内 + 4dp | 屏幕内 − 4dp | 屏幕内 + 6dp | 屏幕内 − 6dp | 屏幕矩形相对 `CENTER_FIT` 模型算 |
| WEATHER_BOX | 腔体下沿 + 8dp | 盒底 − 8dp | 16dp | 16dp | 腔体下方的白留白区，组件变高只加文字不加腔体 |
| WINTER_PALACE / DEEP_SEA / SUMMER_SEA / SUMMER_LOTUS | 相框下沿 + 3dp | 底 − max(5dp, 高×2.5%) | max(14dp, 宽×5%) | max(12dp, 宽×3.5%) | 相框下方整幅留白带（与 `FramedCardRenderer` 同一份布局） |

> **素材内固定位置**（小霸王屏幕）用 `centerFitRect()` 先算出 `CENTER_FIT` 的实际落位，再按比例取屏幕矩形 —— 这样组件是 4×3 还是 4×4，文字都贴在屏幕上。

---

## 7. 可调项开关（哪些形状禁用哪些滑条）

这三份名单**必须同步维护**，它们各自独立：

### 7.1 不能设背景色的形状（`SHAPES_WITHOUT_BACKGROUND_COLOR`）

主体四周透明、插画不铺满整幅位图 —— 设了背景色会在主体外围露出一圈圆角卡片。

`FEATHER_LETTER`、`PET_CAT_NAP`、`NIUPI_SHOUZHANG`、`BOOKSHELF`、`GIANT_SWORD`、`PLUSH_FOREST`、`SUBOR_CONSOLE`、`SPRING_DOG`、`PANDA_BAMBOO`、`PET_PARK`、`CRAYON_FRAME`

> 判定入口是 `WidgetStyle.supportsBackgroundColor(shape)`；渲染时也会强制按透明处理，所以**旧组件不用重新保存**也不会露出包裹卡片。

### 7.2 圆角滑条禁用的形状

在 `ui/adjust/ShapeBackgroundCard.kt` 一处维护（桌面快捷面板已移除，不再有需要同步的第二处）：

`ELLIPSE`、`TORN_PAPER`、`BOOKSHELF`、`GIANT_SWORD`、`PLUSH_FOREST`、`SUBOR_CONSOLE`、`SPRING_DOG`、`CRAYON_FRAME`、`PANDA_BAMBOO`、`PET_PARK`、`CUTE_FOUR_KIDS`

滑条**永远渲染**（只是置灰 + 文案变「外框圆角（此形状无需调整）」），避免切换形状时控件消失导致列表高度突变、页面自动上滑。

> 注意这份名单与 7.1 **不是一回事**：`TORN_PAPER` 圆角不可调，但背景色就是它的纸面颜色，仍然可用。
>
> 这份名单**必须覆盖 §7.3 的全部形状**（渲染层强制直角 ⇒ 滑条置灰）。`ShapeCornerSliderTest`
> 逐形状核对这条蕴含关系——它历史上漏登记过三次（竹林熊猫、萌宠乐园、可爱四小只），
> 三次的表现都是「滑条看着能动、拖了画面没反应」，而当时的测试全绿。

### 7.3 渲染层强制外框圆角为 0 的形状

`GIANT_SWORD`、`PLUSH_FOREST`、`SUBOR_CONSOLE`、`SPRING_DOG`、`CRAYON_FRAME`、`PANDA_BAMBOO`、`PET_PARK`、`CUTE_FOUR_KIDS`

原因：整幅贴图类的角上压着的就是**素材自己画的东西**，裁圆角等于"作品被切角"；
而套用预设时圆角是**继承**上一个风格的（`StylePresetCard` 里 `preset.copy(cornerRadiusDp = …)`），
用户还能把滑条拖到 30dp，靠"预设自己写 0 圆角"兜不住，所以统一在渲染层强制。

下面这张表是**实测**的"用户真把圆角调大时会切掉多少"（口径：4×2 预览位图 1125px 宽 = 4.5px/dp，
数画面上已有的不透明像素落在圆角遮罩外的部分；括号里只数非卡面白的图案/笔画像素）：

| 形状 | 圆角会切掉什么 | 12dp | 30dp |
| --- | --- | --- | --- |
| `SPRING_DOG` | 绿框四个角 | **653px**（笔画 280px） | 6473px（笔画 4154px） |
| `PANDA_BAMBOO` | 竹框下沿两角 | 0 | 1018px（笔画 956px） |
| `PET_PARK` | 毛毡框下沿两角 | 0 | 1053px（笔画 684px） |
| `CRAYON_FRAME` | 最外侧的蜡笔波浪尖 | 0 | 693px（笔画 433px） |
| `PLUSH_FOREST` | 底部毛绒草地 | 0 | 780px（笔画 775px） |
| `GIANT_SWORD` | 左下衣摆 | 0（20dp 也是 0） | 102px（笔画 92px） |
| `CUTE_FOUR_KIDS` | **白卡四角**——切到的是卡面，不是头像（12dp 头像 0px、30dp 仅 10px）。直角是这款的设计选择 | 2568px（笔画 0px） | 15792px（笔画 10px） |
| `SUBOR_CONSOLE` | 标准尺寸下**切不到**：`CENTER_FIT` 把机身缩在中间、四角本来就透明 | 0 | 0 |

> 4×4 下除 `SPRING_DOG`（12dp 273px/34px、30dp 5647px/3661px）与 `CUTE_FOUR_KIDS`（同 4×2）外，其余均与上表一致或为 0。
>
> ⚠️ **这张表不是删条目的依据**：它是标准 4×2 / 4×4 预览下的数字。用户在桌面上把组件**拖小**（占的
> 格数变少）时，同一个圆角在卡片上占的比例更大、切得更多；`SUBOR_CONSOLE` 还多一层——它的素材是
> 1.604:1，组件长宽比一接近它，`CENTER_FIT` 就把机身撑满四角。再加上"从上一个风格继承来一个大圆角"
> 这条路径，切到的东西都跟着变。
> 删一条**不会让任何测试变红**（`ShapeCornerSliderTest` 只核对"强制直角 ⇒ 滑条置灰"），而桌面上的老组件会当场多一刀。

### 7.4 图片缩放模式怎么选

| 模式 | 什么时候用 |
| --- | --- |
| `STRETCH` | 图就是按组件比例设计的（整卡贴图类），直接铺满 |
| `CENTER_CROP` | 实景照片，铺满、多余裁掉；会先自动裁掉素材四周的近白相框（`detectLightBorder()`，阈值 228，相框占比 >30% 时回退原图） |
| `CENTER_FIT` | **透明抠图**（宠物头像），等比完整显示，绝不裁主体 |
| `CENTER_CROP_TOP` | 主体在**顶部**、不能被裁（萌宠猫咪趴），多余高度从底部裁 |
| `TILE` | 平铺 |

---

## 8. 新增一个风格：改动清单

### 8.1 先走一遍决策树

```
新风格有素材吗？
├─ 没有 → 路线 A：写绘制代码（参考 drawBookshelfChrome / drawBlueNoteChrome）
└─ 有 → 图铺满整卡吗？
         ├─ 铺满整卡（图即主体）→ 路线 B
         │    └─ 主体是透明抠图？ → CENTER_FIT；是实景？ → CENTER_CROP
         └─ 只占一半（另一半放文字）→ 路线 C
              └─ 实景照片 → CENTER_CROP；透明抠图 → CENTER_FIT
```

特殊到套不进 A/B/C 的（整幅透明底、多元素分层、有独立文本框）→ 在
`WidgetRenderPipeline.drawFamilyChrome` 里新增一个家族分支；若这类形状还要共享一张「形状 → 参数」表，照 `FramedCardRenderer.specFor` 的写法办。

### 8.2 改动点（4 个文件）

**① `app/src/main/res/drawable/`** —— 放素材
- 文件名 = `presetImageResName`，**只能小写 a-z / 数字 / 下划线**（中文/大写/空格会编译失败）。
- 统一 WebP：照片类有损 q95，带透明通道的做颜色外扩消彩边。
- 图片处理脚本见 `docs/component-style-guide.md`。

**② `data/WidgetStyle.kt`** —— 5 处
```kotlin
// a. WidgetShape 枚举加一行（如果现有形状都不合适）
NEW_SHAPE("新形状名")

// b. PRESETS 末尾追加（不要插在中间！）
WidgetStyle(
    presetId = "p_new_style",            // 手写语义 ID，是付费判定的身份
    // 最佳显示尺寸：App 预览按它出图。4×4 款（方版素材/方形设计）填 SQUARE_4X4，
    // 横版素材（≈2:1）或无素材的代码风格填 WIDE_4X2，见 style-guide §0.1.2
    bestDisplaySize = WidgetDisplaySize.WIDE_4X2,
    shape = WidgetShape.NEW_SHAPE,
    backgroundColor = ...,
    bgImageScaleMode = ImageScaleMode.CENTER_FIT,
    presetImageResName = "new_style",     // = drawable 名
    ...
)

// c. 分类列表登记（按 presetId/shape/资源名定位，别按下标）
"新风格" to (PRESETS.firstOrNull { it.presetId == "p_new_style" } ?: PRESETS[0])

// d. 若主体四周透明 → 加进 SHAPES_WITHOUT_BACKGROUND_COLOR

// e. 若这款风格有外部依据的尺寸（文档写明 / 方版素材已交付），补进
//    PresetDisplaySizeTest 的表，把决策钉住
```

**③ `render/ShapeTraits.kt` + `render/CardTextRenderer.kt`** —— 最多 3 处
```kotlin
// a. WidgetShape.family()：穷举 when，给新形状认领一个家族
//    （不写 else，编译器会强制你为每个新形状选家族）
// b. ShapeTraits 的名单：INSET_CAPABLE_SHAPES（内容可内缩 4dp；要不要内缩看投影/外侧装饰）/
//    SQUARE_CORNER_SHAPES（整幅插画强制直角）/ TRANSPARENT_CARD_SHAPES（整幅透明底）/
//    OWN_BACKGROUND_SHAPES（素材由家族自己摆，不走整卡铺图）
// c. CardTextRenderer.textBoxFor()：加一个文字安全区分支   ← 见 §6
// 有专属装饰（NOTE 区、书脊、状态色块与波浪…）才需要在 render/ 下新写 drawXxx，
// 并在 WidgetRenderPipeline.drawFamilyChrome 的 when 里挂上
```

**④ `ui/adjust/ShapeBackgroundCard.kt`** —— 只有需要禁用圆角滑条时才改（见 7.2）

### 8.3 改完自检

- [ ] 免费/会员是否符合预期？（默认新风格都是 PRO，除非加进 `FREE_PRESET_IDS`）
- [ ] 4×2 和 4×4 下文字都没被裁、没压住主体？
- [ ] 背景色/不透明度是否符合预期（整卡贴图类应不可设背景色）？
- [ ] 「个性定制」页的分类行里出现了（经典 / 萌宠 / 明信片，见 §8.2 ②）？
- [ ] `bestDisplaySize` 定了吗？App 预览区显示的正是这款风格自己的比例（4×4 款方形、4×2 款长条）？
- [ ] 素材四角透明是否正确（`process_component_image.py info`）？

---

## 9. 验证方式

### 9.1 不连真机：JVM 上批量渲染预览图

```bash
./gradlew :app:testDebugUnitTest --tests "com.juge.app.WidgetPreviewRenderTest"
# 产物：app/build/widget-previews/{4x2,4x4}/<风格>.png
```

这个测试用 Robolectric 的 **NATIVE 图形模式**（走真实 Skia），字体、圆角、阴影、贴图与真机一致，density 用 xxhdpi。它会遍历**所有 `PRESETS` + 所有插图**，是加新风格后最快的肉眼检查手段。

### 9.2 真机

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

> ⚠️ debug 包与 release 包**无法共存**（相同 applicationId、不同签名），装 release 前要先卸载 debug。
> ⚠️ 支付宝 APP 支付**只有 release 包能跑通**（开放平台登记的是正式包名 + 正式签名）。
> ⚠️ 换签名 = 同包名无法覆盖安装 + 备案信息不符，必须始终用同一个 keystore。

---

## 10. 踩过的坑（改风格时最容易复发的）

| 现象 | 根因 | 解法 |
| --- | --- | --- |
| 信纸外一圈黑底 | 底色填充时 `alpha` 被强制成 `backgroundOpacity*255`，透明背景也被画黑 | 透明背景不填充（`Color.alpha(effectiveBgColor) > 0` 才画） |
| 透明 PNG 解码后变黑 | `BitmapFactory` 采样后透明区变成 RGB_565 | `inPreferredConfig = ARGB_8888` |
| 桌面组件外有深色卡片框 | widget 根布局用了系统 id `@android:id/background` | 换普通 id；改完布局要**删除并重新添加**组件 |
| 圆角裁剪切掉主体 | 整幅插画形状继承了上一个风格的圆角值 | 加进 `render/ShapeTraits.kt` 的 `SQUARE_CORNER_SHAPES` |
| 删掉的风格在旧组件上崩掉 | 存档里 shape 名已不存在 | `fromJsonString` 的 `safeEnum` 降级为 `RECTANGLE`（字段全保留），老组件变普通圆角卡 |
| 背景色在主体外围露一圈 | 透明抠图形状被设了背景色 | 加进 `SHAPES_WITHOUT_BACKGROUND_COLOR` |
| 宽横幅两侧露白边 | 素材自带近白相框 | `CENTER_CROP` 会自动 `detectLightBorder()` 裁掉（阈值 228，相框 >30% 时回退原图） |
| 明信片底色叠两遍、不透明度失真 | 图区和文字区各铺了一次底色 | 图区只铺图，文字区单独画（共用 `splitImageRect()`） |
| 新增/删除预设后免费风格错乱 | 按下标取用 `PRESETS` | 一律追加到末尾 + 按 `presetId`/`shape`/资源名定位 |
| 半透明卡面被画成白卡 | 管线为投影先补一层**不透明**底色，玻璃被垫实 | 卡面交家族自己画（归入 `TRANSPARENT_CARD_SHAPES`，当前无成员），投影只画卡外（裁掉卡内） |
| 卡片四周多出一圈透壁纸的透明带，launcher 重挂载组件时还会在那圈里闪白 | 内缩原先按**形状**一刀切，没有投影的样式也留了 4dp——那圈边除了透出壁纸别无用处，正好给 launcher 的白色占位底当出风口 | 内缩条件改绑「轮廓外沿有东西要放」：`traits.insetCapable && style.showCardShadow`（`RenderScene.cardInset`；材质边框族下架后，另一条判据 `decoratesOutsideContour` 已随之删除） |
| 卡片外多出一圈白框（深色壁纸上尤其明显） | 描边是 STROKE，**居中**画在路径上：一半落在卡片外那圈透明内缩上、压在壁纸里。设计稿里的边框（如纹理山水那圈白）本来在图片内部，这条边是凭空多出来的 | 描边前 `clipPath(scene.path)`，整条描边落在卡面内（`WidgetRenderPipeline.drawBorderInsideCard`） |
| 描边比设定值细一半（如 `cardBorderWidthDp = 1` 只画出 0.5dp） | 上一条的代价：裁掉外侧后只剩一半线宽可见，而字段名说的是"描边宽度" | 卡片描边处标称线宽按 `cardBorderWidthDp * 2` 给，让字段等于**可见宽度**；撕纸那条白边是内部固定宽度，按原样给 |
| 组件画面被拉扁/拉长，描边沿拉伸方向变粗 | 位图按 `OPTION_APPWIDGET_MIN/MAX_WIDTH/HEIGHT` 渲染，ImageView 是 `fitXY` 会把它拉满组件视图——两者比例不一致时就是非等比拉伸。实测（PJF110 / ColorOS）：launcher 自报的 `OPTION_APPWIDGET_SIZES` 与 MIN/MAX 都是 304×158dp，却按 ~370×192dp 布局，**它自己的两个数就对不上**，App 侧无从校正 | 当前无解，只能靠 `RENDER_DENSITY_SCALE = 1.5` 的过采样吸收**均匀**缩放（实测两轴 0.2% 差，肉眼无碍）。比例真出问题时看 `JuGeWidget` 那条日志（渲染尺寸 / 比例 / launcher 声明的候选尺寸） |
| App 内预览的比例与风格的设计尺寸对不上（长条风格显示成方形、或反之） | 预览原按组件的入口 / 实时尺寸出图，而每款风格只在**一个**尺寸上设计 | 每款预设显式声明 `bestDisplaySize`，预览按它出图（见 §4.1 与 style-guide §0.1.2）；新增风格时一起定这个值 |
| 切形状时页面自动上滑 | 控件被移除导致列表高度突变 | 控件常驻渲染，只置灰 |

---

## 11. 一句话总结

> 一个风格 = **形状（外轮廓）** + **一组参数** + **可能一张素材**；
> 三条路线决定"图从哪来、文字落哪"；
> 新增风格只动 `WidgetStyle.kt`（枚举 + PRESETS + 分类 + 名单）、`render/ShapeTraits.kt`（家族 + 名单）
> 和 `render/CardTextRenderer.kt`（文字安全区），
> 有专属装饰才需要新写绘制函数；改完用 `WidgetPreviewRenderTest` 出图肉眼验一遍。
