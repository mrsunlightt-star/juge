# 组件风格总览与实现方案

> 这份文档回答两件事：
> 1. **现有 40 个组件风格各自是怎么实现的** —— 方便你对照着描述新风格。
> 2. **新增一个风格要动哪些地方** —— 一份可照着做的改动清单。
>
> 配套文档：`docs/component-style-guide.md`（图片处理脚本 `tools/process_component_image.py` 的用法与踩坑）。
> 贴纸类素材的生成流程见 `tools/sticker/README.md`。

---

## 1. 先用「四要素」描述一个风格

跟人（或跟 AI）描述一个新风格时，把这四点说清楚就够了，剩下的都能推导出来：

| 要素 | 说明 | 取值 |
| --- | --- | --- |
| **① 路线** | 这个风格靠什么画出来 | A 纯代码卡片 / B 整卡贴图 / C 图文明信片 / D 贴纸场景（见 §3） |
| **② 形状** | 组件的外轮廓 | `WidgetShape` 的 19 个取值之一（见 §5） |
| **③ 素材** | 有没有图、图的透明处理方式 | 无素材 / 整幅抠图 / 实景照片不抠 |
| **④ 文字落点** | 正文压在哪个区域 | 留白区、避开装饰（见 §6） |

举例，用四要素描述「贴纸夜景」：

> 路线 D（贴纸场景）；形状 `STICKER_SCENE`；素材是抠好的「人物+路灯」透明贴纸，按 `CENTER_FIT` 等比完整显示；文字落在下方的**剪纸文本框**内（占高度 60%~90%），背景色/不透明度只作用于这个文本框。

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
| `cornerRadiusDp` | 圆角 0~30dp | 多数形状是**外框**圆角，部分形状强制 0；贴纸夜景是**文本框**圆角（见 §7） |
| `backgroundColor` | 背景色 | 部分形状强制透明（见 §7） |
| `backgroundOpacity` | 背景不透明度 | 同时作用于背景图与文本框 |
| `backgroundImagePath` | 用户自定义背景图 | 优先于预设图 |
| `bgImageScaleMode` | 图片缩放模式 | STRETCH / CENTER_CROP / CENTER_FIT / CENTER_CROP_TOP / TILE |
| `presetImageResName` | 内置素材名（= drawable 名，不带扩展名） | 空则无素材 |
| `font` / `fontSizeSp` / `fontBold` / `fontItalic` / `fontColor` / `textAlign` | 文字排版 | `WidgetFont` 有 8 种（系统默认 + 7 款中文字体） |
| `shadow` | 文字阴影 | 小霸王游戏机用它做**荧光晕**；巨剑用它做暗描边保可读性 |
| `gradientColors` / `gradientAngle` | 背景渐变 | 目前只有「蓝色画报」用 |
| `bgBlurRadius` / `bgScrimAlpha` | 背景模糊 / 压暗 | 自定义背景图时用 |
| `showCardShadow` | 卡片投影 | 贴纸夜景只给文本框加投影 |
| `cardBorderWidthDp` / `cardBorderColor` | 卡片描边 | |
| `showQuoteMark` | 艺术双引号 | 21 个预设开启 |
| `textureType` | 纸张纹理 | `PAPER` / `GRAIN` / `NONE`，目前只有「拟物撕纸」用 PAPER |
| `lineSpacingMultiplier` / `letterSpacing` | 行距 / 字距 | |

### 2.2 免费与会员

- **免费预设只有 1 个**：`FREE_PRESET_IDS = {"p_pure_round"}`（纯色圆角）。
- 判定走 `isProPreset(style)`：**按 `presetId` 身份**，不按字段匹配（字段匹配能被"改一个字段"绕过）。
- 字体、字号、颜色、圆角、不透明度这些**细节调整全部免费**，不在会员范围内。
- ⚠️ `PRESETS` 列表**中间删条目会导致按下标取用的地方错位**；新增一律**追加到末尾**，并在分类列表里按 `presetId`/`shape`/`presetImageResName` 定位而不是按下标。

---

## 3. 四条实现路线

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
| 白底黑字 / 深夜模式 / 磨砂玻璃 | 纯参数差异：底色、不透明度、描边、阴影 |
| 蓝色便签 | `drawBlueNoteChrome()`：顶部 NOTE 区 + 右上信息钮 + 底部米白手写签条 |
| 书香书架 | `drawBookshelfChrome()`：顶部彩色书脊立在横板上 + 底部米色摘录面板 |
| 猫咪卡片 | `drawCatCardChrome()`：奶白卡 + 粉色内描边 + `drawCatHead()` 画猫头 |

### 路线 B：整卡贴图（图即主体）

**特征**：`presetImageResName` 指向一张**已经抠好底**的图，图铺满整卡，图外透明透出桌面壁纸。
**关键约束**：这类形状**不能设背景色**（设了会在主体外围露出一圈圆角卡片）。
**缩放模式**：多用 `STRETCH`（图本身就是按组件比例设计的），整幅实物模型类用 `CENTER_FIT`。

| 风格 | 形状 | 素材 | 特殊处理 |
| --- | --- | --- | --- |
| 羽毛信纸 | `FEATHER_LETTER` | `feather_letter` | 专用轮廓 `drawFeatherLetterPath()`，信纸居中、四周留边 |
| 复古像素 | `PIXEL_RETRO` | `pixel_retro` | 矩形轮廓，文字避开四周虚线框 |
| 竹青撕纸 | `ZHU_QING_SI_ZHI` | `zhu_qing_si_zhi` | |
| 撕边牛皮手账 | `NIUPI_SHOUZHANG` | `niupi_shouzhang` | |
| 教室黑板 | `CLASSROOM_BLACKBOARD` | `classroom_blackboard` | 粉笔白字写在绿板面上 |
| 萌宠猫咪趴 | `PET_CAT_NAP` | `cute_cat_lying` | 用 `CENTER_CROP_TOP`：等比铺满但**顶部锚定**，趴在顶部的猫不被裁 |
| 巨剑 | `GIANT_SWORD` | `giant_sword` | 文字只压右侧剑身，加暗描边保可读性 |
| 毛绒森林 | `PLUSH_FOREST` | `plush_forest` | |
| 小霸王游戏机 | `SUBOR_CONSOLE` | `subor_console` | 素材是**未通电**的深灰玻璃屏，代码叠绿色荧光底 + 扫描线 + 暗角 + 玻璃反光 |
| 天空之蓝 | `RECTANGLE` | `rectangle_1`（矢量 XML） | 代码额外画一个 "NOTE" 标签 |

### 路线 C：图文明信片（图占一半，代码画另一半）

**特征**：图只占一部分，剩下的是**代码绘制**的文字区（底色 = `backgroundColor`，受 `backgroundOpacity` 控制）。
**两种朝向**：

| 形状 | 图占 | 文字区 | 预设 |
| --- | --- | --- | --- |
| `SPLIT_CARD` | 上半 **48%** | 下半 | 蓝色画报、晨曦日出、治愈落日、星空森林、鲁迅画报、咕嘎与doro、53号机车、蓝天白云、天天向上、搏击俱乐部、绝命毒师、V字仇杀队、爱乐之城 |
| `SPLIT_CARD_HORIZONTAL` | 左半 **33.3%** | 右半 | 可爱猫咪、毛绒小狗、快乐小狗、毛绒猫咪、天天开心、可爱四小只、得意doro |

**素材分两种**，直接决定 `bgImageScaleMode`：

- **实景照片（不抠底）** → `CENTER_CROP`，铺满图区、多出的部分裁掉。
- **透明抠图（宠物/角色）** → `CENTER_FIT`，等比完整显示，**避免裁掉头/脸**。

> ⚠️ 明信片类的 `backgroundColor` 是**文字区底色**，不是整卡底色。图区单独铺图，两处不能各写一遍矩形（共用 `splitImageRect()`）。

### 路线 D：贴纸场景（自成一类）

目前只有「贴纸夜景」一个，但它和其它路线差异很大，单独归类：

- **整幅透明底**：背景色 / 不透明度**只作用于文本框**，不铺整卡底色，卡片阴影也只跟文本框走。
- **绘制顺序**：光柱 → 文本框（剪纸剪角 + 纸纹 + 白边 + 投影）→ 接触阴影 → 贴纸 → 灯罩光晕 → 玫瑰花束。
- **贴纸**按 `CENTER_FIT` 等比完整显示，脚底压在文本框上沿（`STICKER_GROUND_RATIO = 0.60`）。
- **光效**：径向渐变光柱瞄准人物（`STICKER_BEAM_FAR_LEFT_RATIO = -0.86`），人物之上再补一层受光（用贴纸 alpha 当遮罩）；光柱和受光一起随不透明度淡出，不会出现"人没光还在"。
- **文本框**：`paperCutBoxPath()` 四角剪掉一小块（`STICKER_TEXT_SNIP_DP = 7dp`），直角边 + 白色描边 + 平铺纸纹（`paperGrainBitmap()` 细噪点 + 短纤维）。圆角滑条（`cornerRadiusDp`）就作用在这四角上：0 = 斜切直角，调大后改圆弧。
- **玫瑰花**：`drawStickerRoses()` 画在文本框左下角，半径受文本框高度限制（`min(10.5dp, 文本框高 × 0.14)`），防止 4×2 短文本框里溢出画面。

---

## 4. 全部 40 个风格清单

`PRESETS` 的顺序**就是**下表下标（新增只能追加到末尾）。

| # | 名称 | presetId | 形状 | 路线 | 素材 | 会员 |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 纯色圆角 | `p_pure_round` | RECTANGLE | A | — | **免费** |
| 1 | 拟物撕纸 | `p_torn_paper` | TORN_PAPER | A | — | PRO |
| 2 | 复古手账 | `p_handbook_tape` | HANDBOOK_TAPE | A | — | PRO |
| 3 | 蓝色画报 | `p_postcard_note` | SPLIT_CARD | C | 渐变代码绘制 | PRO |
| 4 | 晨曦日出 | `p_dawn_sunrise` | SPLIT_CARD | C | `bg_illustration_1` | PRO |
| 5 | 治愈落日 | `p_healing_sunset` | SPLIT_CARD | C | `bg_illustration_2` | PRO |
| 6 | 星空森林 | `p_starry_forest` | SPLIT_CARD | C | `bg_illustration_3` | PRO |
| 7 | 天空之蓝 | `p_sky_blue` | RECTANGLE | B | `rectangle_1`（矢量） | PRO |
| 8 | 鲁迅画报 | `p_luxun_poster` | SPLIT_CARD | C | `bg_illustration_luxun` | PRO |
| 9 | 咕嘎与doro | `p_guga_doro` | SPLIT_CARD | C | `guga_doro` | PRO |
| 10 | 53号机车 | `p_motorcycle_53` | SPLIT_CARD | C | `motorcycle_53` | PRO |
| 11 | 蓝天白云 | `p_blue_sky_clouds` | SPLIT_CARD | C | `blue_sky_clouds` | PRO |
| 12 | 天天向上 | `p_tiantian_xiangshang` | SPLIT_CARD | C | `tiantian_xiangshang` | PRO |
| 13 | 搏击俱乐部 | `p_fight_club` | SPLIT_CARD | C | `boji_julebu` | PRO |
| 14 | 绝命毒师 | `p_breaking_bad` | SPLIT_CARD | C | `breaking_bad` | PRO |
| 15 | V字仇杀队 | `p_v_for_vendetta` | SPLIT_CARD | C | `v_for_vendetta` | PRO |
| 16 | 爱乐之城 | `p_la_la_land` | SPLIT_CARD | C | `aile_zhi_cheng` | PRO |
| 17 | 可爱猫咪 | `p_cute_cat` | SPLIT_CARD_HORIZONTAL | C | `cute_cat`（`CENTER_FIT`） | PRO |
| 18 | 毛绒小狗 | `p_fluffy_dog` | SPLIT_CARD_HORIZONTAL | C | `fluffy_dog` | PRO |
| 19 | 快乐小狗 | `p_happy_dog` | SPLIT_CARD_HORIZONTAL | C | `happy_dog`（`CENTER_FIT`） | PRO |
| 20 | 毛绒猫咪 | `p_fluffy_cat` | SPLIT_CARD_HORIZONTAL | C | `fluffy_cat` | PRO |
| 21 | 天天开心 | `p_happy_daily` | SPLIT_CARD_HORIZONTAL | C | `happy_daily` | PRO |
| 22 | 白底黑字 | `p_white_black` | RECTANGLE | A | — | **免费** |
| 23 | 深夜模式 | `p_dark_night` | RECTANGLE | A | — | **免费** |
| 24 | 磨砂玻璃 | `p_frosted_glass` | RECTANGLE | A | — | PRO |
| 25 | 羽毛信纸 | `p_feather_letter` | FEATHER_LETTER | B | `feather_letter` | PRO |
| 26 | 复古像素 | `p_pixel_retro` | PIXEL_RETRO | B | `pixel_retro` | PRO |
| 27 | 萌宠猫咪趴 | `p_pet_cat_nap` | PET_CAT_NAP | B | `cute_cat_lying`（`CENTER_CROP_TOP`） | PRO |
| 28 | 可爱四小只 | `p_cute_four_kids` | SPLIT_CARD_HORIZONTAL | C | `cute_four_kids`（`CENTER_FIT`） | PRO |
| 29 | 蓝色便签 | `p_blue_note` | BLUE_NOTE | A | — | PRO |
| 30 | 竹青撕纸 | `p_zhu_qing_si_zhi` | ZHU_QING_SI_ZHI | B | `zhu_qing_si_zhi` | PRO |
| 31 | 撕边牛皮手账 | `p_niupi_shouzhang` | NIUPI_SHOUZHANG | B | `niupi_shouzhang` | PRO |
| 32 | 得意doro | `p_deyi_doro` | SPLIT_CARD_HORIZONTAL | C | `deyi_doro`（实景 `CENTER_CROP`） | PRO |
| 33 | 教室黑板 | `p_classroom_blackboard` | CLASSROOM_BLACKBOARD | B | `classroom_blackboard` | PRO |
| 34 | 书香书架 | `p_bookshelf` | BOOKSHELF | A | — | PRO |
| 35 | 猫咪卡片 | `p_cat_card` | CAT_CARD | A | — | PRO |
| 36 | 巨剑 | `p_giant_sword` | GIANT_SWORD | B | `giant_sword` | PRO |
| 37 | 毛绒森林 | `p_plush_forest` | PLUSH_FOREST | B | `plush_forest` | PRO |
| 38 | 小霸王游戏机 | `p_subor_console` | SUBOR_CONSOLE | B | `subor_console` | PRO |
| 39 | 贴纸夜景 | `p_sticker_lalaland` | STICKER_SCENE | D | `sticker_lalaland` | PRO |

### 4.1 界面上怎么分组展示

四个入口列表都定义在 `WidgetStyle` 的 companion object 里，**主界面与快捷面板共用同一份**（不要在两处各写一遍）：

| 列表 | 界面标题 | 内容 |
| --- | --- | --- |
| `CLASSIC_PRESETS` | 经典风格 | 纯色圆角、拟物撕纸、复古手账、天天开心、羽毛信纸、复古像素、蓝色便签、竹青撕纸、撕边牛皮手账、教室黑板、猫咪卡片、巨剑、小霸王游戏机、贴纸夜景 |
| `PET_PRESETS` | 萌宠风格 · 会员专属 | 得意doro、咕嘎与doro、可爱猫咪、毛绒小狗、快乐小狗、毛绒猫咪、萌宠猫咪趴、可爱四小只、毛绒森林 |
| `POSTCARD_CODE_PRESETS` | 明信片风格（代码绘制的大卡） | 蓝色画报、书香书架 |
| `ILLUSTRATION_PRESETS` | 明信片风格（插图素材） | 12 张插图的 `资源名 → 展示名` 映射 |

> 萌宠/角色类素材刻意**不放进 `ILLUSTRATION_PRESETS`**，避免同一批图在明信片行重复出现。

---

## 5. 形状清单（`WidgetShape`）

19 个取值，其中 **`ELLIPSE` 是历史遗留，没有任何预设使用**。

| 形状 | 外轮廓怎么来的 | 内缩 4dp | 备注 |
| --- | --- | --- | --- |
| `RECTANGLE` | 圆角矩形 | ✅ | |
| `ELLIPSE` | 椭圆 | ✅ | 无预设使用 |
| `HANDBOOK_TAPE` | 圆角矩形 | ✅ | 角上叠胶带 |
| `TORN_PAPER` | `generateTornPath()` 固定种子锯齿 | ❌ | 圆角不可调 |
| `SPLIT_CARD` | 圆角矩形 | ✅ | 图占上 48% |
| `SPLIT_CARD_HORIZONTAL` | 圆角矩形 | ✅ | 图占左 33.3% |
| `FEATHER_LETTER` | `drawFeatherLetterPath()` | ❌ | 信纸居中、四周留边 |
| `PIXEL_RETRO` | 圆角矩形 | ❌ | |
| `PET_CAT_NAP` | 圆角矩形 | ❌ | |
| `BLUE_NOTE` | 圆角矩形 | ❌ | |
| `ZHU_QING_SI_ZHI` | 圆角矩形 | ❌ | |
| `NIUPI_SHOUZHANG` | 圆角矩形 | ❌ | |
| `CLASSROOM_BLACKBOARD` | 圆角矩形 | ❌ | |
| `BOOKSHELF` | 圆角矩形 | ❌ | 自身就是组件，无外框 |
| `CAT_CARD` | 圆角矩形 | ❌ | |
| `GIANT_SWORD` | 圆角矩形（**强制 0 圆角**） | ❌ | |
| `PLUSH_FOREST` | 圆角矩形（**强制 0 圆角**） | ❌ | |
| `SUBOR_CONSOLE` | 圆角矩形（**强制 0 圆角**） | ❌ | |
| `STICKER_SCENE` | 整幅透明（不画外框） | ❌ | 圆角滑条作用于**文本框**四角（见 §7.3） |

**「内缩 4dp」（`CARD_INSET_DP`）**：只对 `RECTANGLE` / `HANDBOOK_TAPE` / `SPLIT_CARD` / `SPLIT_CARD_HORIZONTAL` 生效，让卡片四周留一点透明边。其余形状有各自按整幅位图绘制的装饰，保持满幅以免错位。

**未列入「跟随用户圆角」名单的形状**，外框圆角统一用 `DEFAULT_OUTER_CORNER_RADIUS_DP = 16dp`（即 `TORN_PAPER` / `FEATHER_LETTER` / `ELLIPSE`）。

---

## 6. 各形状的「文字安全区」

文字区不是写死的像素，而是**按比例算出来的矩形**（`cardTop` / `cardHeight` / `paddingLeft` / `paddingRight`），这样 4×2 和 4×4 都不会错位。
超出可容纳行数时用省略号（`TruncateAt.END`），不会被画布硬裁。

| 形状 | 上边 | 下边 | 左 | 右 | 说明 |
| --- | --- | --- | --- | --- | --- |
| RECTANGLE / HANDBOOK_TAPE / TORN_PAPER | 卡片内缩 | 卡片内缩 | 16dp | 16dp | 默认分支 |
| SPLIT_CARD | 48% + 12dp | 12dp | 16dp | 16dp | 落下半 |
| SPLIT_CARD_HORIZONTAL | 12dp | 12dp | 33.3% + 12dp | 12dp | 落右半 |
| FEATHER_LETTER | 16% | 14% | 10% | 20% | 右侧多留避羽毛笔 |
| PIXEL_RETRO | 12% | 12% | 10% | 10% | 避虚线框 |
| PET_CAT_NAP | 42% | 8% | 12% | 12% | 避顶部的猫 |
| BLUE_NOTE | 高度×76/363×0.85 | 高度×76/363 | 8% | 8% | 避 NOTE 与底部签条 |
| ZHU_QING_SI_ZHI / NIUPI_SHOUZHANG | 11% | 11% | 11% | 11% | 避撕边 |
| CLASSROOM_BLACKBOARD | 12% | 高 70% | 9% | 9% | 避木框与粉笔槽 |
| BOOKSHELF | 面板内 + 6dp | 面板内 − 6dp | 面板内 + 14dp | 面板内 − 14dp | 与摘录面板同一矩形 |
| CAT_CARD | 50% | 10% | 11% | 11% | 避猫头 |
| GIANT_SWORD | 39% | 高 33% | 33% | 93% | 只压右侧剑身 |
| PLUSH_FOREST | 40% | 高 47% | 11.5% | 88.5% | 避顶部毛绒小树 |
| SUBOR_CONSOLE | 屏幕内 + 4dp | 屏幕内 − 4dp | 屏幕内 + 6dp | 屏幕内 − 6dp | 屏幕矩形相对 `CENTER_FIT` 模型算 |
| STICKER_SCENE | 60% + 8dp | 90% − 8dp | 5% + 12dp | 95% − 12dp | 文本框内 |

> **素材内固定位置**（小霸王屏幕）用 `centerFitRect()` 先算出 `CENTER_FIT` 的实际落位，再按比例取屏幕矩形 —— 这样组件是 4×3 还是 4×4，文字都贴在屏幕上。

---

## 7. 可调项开关（哪些形状禁用哪些滑条）

这三份名单**必须同步维护**，它们各自独立：

### 7.1 不能设背景色的形状（`SHAPES_WITHOUT_BACKGROUND_COLOR`）

主体四周透明、插画不铺满整幅位图 —— 设了背景色会在主体外围露出一圈圆角卡片。

`FEATHER_LETTER`、`PET_CAT_NAP`、`ZHU_QING_SI_ZHI`、`NIUPI_SHOUZHANG`、`BOOKSHELF`、`GIANT_SWORD`、`PLUSH_FOREST`、`SUBOR_CONSOLE`

> 判定入口是 `WidgetStyle.supportsBackgroundColor(shape)`；渲染时也会强制按透明处理，所以**旧组件不用重新保存**也不会露出包裹卡片。

### 7.2 圆角滑条禁用的形状

在 `MainActivity.kt` 与 `QuickAdjustActivity.kt` **两处**各自维护（内容必须一致）：

`ELLIPSE`、`TORN_PAPER`、`BOOKSHELF`、`GIANT_SWORD`、`PLUSH_FOREST`、`SUBOR_CONSOLE`

滑条**永远渲染**（只是置灰 + 文案变「外框圆角（此形状无需调整）」），避免切换形状时控件消失导致列表高度突变、页面自动上滑。

> 注意这份名单与 7.1 **不是一回事**：`TORN_PAPER` 圆角不可调，但背景色就是它的纸面颜色，仍然可用。

### 7.3 渲染层强制外框圆角为 0 的形状

`GIANT_SWORD`、`PLUSH_FOREST`、`SUBOR_CONSOLE`

原因：整幅插画被圆角裁剪会切掉剑身/毛绒小树/实物模型。预设套用时会继承上一个风格的圆角值，所以这里统一强制，防止旧数据或跨风格套用后画面被裁。

> **`STICKER_SCENE` 不在这份名单里**：它整幅透明、不画外框，圆角滑条转而作用于**文本框**——`paperCutBoxPath(rect, snip, cornerRadiusDp)` 里，滑条为 0 时四角保持「斜切一刀」的剪纸直角，大于 0 时四角改走圆弧，半径 = 剪纸口幅度 + 滑条值（上限为文本框短边的一半）。所以贴纸夜景的圆角滑条是**生效的**，也不需要加进 7.2 的禁用名单。
>
> 小尺寸注意：4×2 的文本框只有约 33dp 高，半径在滑条约 10 之后就被「短边一半」夹住，即已经圆到极限。

### 7.4 图片缩放模式怎么选

| 模式 | 什么时候用 |
| --- | --- |
| `STRETCH` | 图就是按组件比例设计的（整卡贴图类），直接铺满 |
| `CENTER_CROP` | 实景照片，铺满、多余裁掉；会先自动裁掉素材四周的近白相框 |
| `CENTER_FIT` | **透明抠图**（宠物头像、贴纸），等比完整显示，绝不裁主体 |
| `CENTER_CROP_TOP` | 主体在**顶部**、不能被裁（萌宠猫咪趴），多余高度从底部裁 |
| `TILE` | 平铺 |

---

## 8. 新增一个风格：改动清单

### 8.1 先走一遍决策树

```
新风格有素材吗？
├─ 没有 → 路线 A：写绘制代码（参考 drawBookshelfChrome / drawCatCardChrome）
└─ 有 → 图铺满整卡吗？
         ├─ 铺满整卡（图即主体）→ 路线 B
         │    └─ 主体是透明抠图？ → CENTER_FIT；是实景？ → CENTER_CROP
         └─ 只占一半（另一半放文字）→ 路线 C
              └─ 实景照片 → CENTER_CROP；透明抠图 → CENTER_FIT
```

特殊到套不进 A/B/C 的（整幅透明底、多元素分层、有独立文本框）→ 路线 D，像贴纸夜景那样单独写一段渲染分支。

### 8.2 改动点（4 个文件）

**① `app/src/main/res/drawable/`** —— 放素材
- 文件名 = `presetImageResName`，**只能小写 a-z / 数字 / 下划线**（中文/大写/空格会编译失败）。
- 统一 WebP：照片类有损 q95，带透明通道的做颜色外扩消彩边。
- 图片处理脚本见 `docs/component-style-guide.md`。

**② `data/WidgetStyle.kt`** —— 4 处
```kotlin
// a. WidgetShape 枚举加一行（如果现有形状都不合适）
NEW_SHAPE("新形状名")

// b. PRESETS 末尾追加（不要插在中间！）
WidgetStyle(
    presetId = "p_new_style",            // 手写语义 ID，是付费判定的身份
    shape = WidgetShape.NEW_SHAPE,
    backgroundColor = ...,
    bgImageScaleMode = ImageScaleMode.CENTER_FIT,
    presetImageResName = "new_style",     // = drawable 名
    ...
)

// c. 分类列表登记（按 presetId/shape/资源名定位，别按下标）
"新风格" to (PRESETS.firstOrNull { it.presetId == "p_new_style" } ?: PRESETS[0])

// d. 若主体四周透明 → 加进 SHAPES_WITHOUT_BACKGROUND_COLOR
```

**③ `WidgetCanvasRenderer.kt`** —— 最多 3 处
```kotlin
// a. 形状裁剪分支 when(style.shape)：定义外轮廓（简单形状加进现有分支即可）
// b. 强制 0 圆角 / 背景色强制透明的名单（如果整幅是插画）
// c. 文字安全区 else if (style.shape == NEW_SHAPE) { ... }   ← 见 §6
// 有专属装饰（NOTE 区、书脊、猫头、光柱…）才需要写新的 drawXxxChrome()
```

**④ `MainActivity.kt` + `QuickAdjustActivity.kt`** —— 只有需要禁用圆角滑条时才改（见 7.2）

### 8.3 改完自检

- [ ] 免费/会员是否符合预期？（默认新风格都是 PRO，除非加进 `FREE_PRESET_IDS`）
- [ ] 4×2 和 4×4 下文字都没被裁、没压住主体？
- [ ] 背景色/不透明度是否符合预期（整卡贴图类应不可设背景色）？
- [ ] 主界面与快捷面板两处入口都出现了？
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
| 圆角裁剪切掉主体 | 整幅插画形状继承了上一个风格的圆角值 | 加进「强制 0 圆角」名单 |
| 背景色在主体外围露一圈 | 透明抠图形状被设了背景色 | 加进 `SHAPES_WITHOUT_BACKGROUND_COLOR` |
| 宽横幅两侧露白边 | 素材自带近白相框 | `CENTER_CROP` 会自动 `detectLightBorder()` 裁掉（阈值 228，相框 >30% 时回退原图） |
| 明信片底色叠两遍、不透明度失真 | 图区和文字区各铺了一次底色 | 图区只铺图，文字区单独画（共用 `splitImageRect()`） |
| 新增/删除预设后免费风格错乱 | 按下标取用 `PRESETS` | 一律追加到末尾 + 按 `presetId`/`shape`/资源名定位 |
| 4×2 里玫瑰花溢出画面 | 花朵尺寸写死 | 半径 `min(固定 dp, 文本框高 × 0.14)` |
| 切形状时页面自动上滑 | 控件被移除导致列表高度突变 | 控件常驻渲染，只置灰 |

---

## 11. 一句话总结

> 一个风格 = **形状（外轮廓）** + **一组参数** + **可能一张素材**；
> 四条路线决定"图从哪来、文字落哪"；
> 新增风格只动 `WidgetStyle.kt`（枚举 + PRESETS + 分类 + 名单）和 `WidgetCanvasRenderer.kt`（裁剪分支 + 文字安全区），
> 有专属装饰才需要新写绘制函数；改完用 `WidgetPreviewRenderTest` 出图肉眼验一遍。