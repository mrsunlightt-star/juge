# 新增「图片背景」组件风格 —— 接入指南

> 适用：想让 App 的桌面小组件支持一种**用一张效果图做主体**的风格（如羽毛信纸、明信片、海报插画等）。
> 思路：图片处理成干净的组件主体图 → 作为背景图铺满组件 → 文字落在图的留白区。
>
> **本文负责列出「必须回答哪些问题」，不负责记住「改哪个文件」。**
> 渲染管线已按形状家族重构过两轮，函数与文件位置会漂移；而形状家族与文字安全区
> 都是**穷举 `when`、不写 `else`**，新增形状时编译器会直接在报错里点名还缺哪一处。
> 找不到落点时，**以编译器报错为准**——这比任何文档都可靠。

## 0. 出图规格（设计师照这个来，能省掉一整轮返工）

### 0.1 画布：只认两个比例

组件位图不是按"设计稿画布"渲染的，而是按**桌面上的真实尺寸**现算：

```
渲染像素 = 组件宽(dp) × 屏幕密度 × 1.5
```

实测（PJF110 / ColorOS，密度 2.55）：一个 4×2 组件上报 **304×158 dp** → 位图 **1163×604 px**。
密度 2.0 的机器约 912×474 px，密度 4.0 的约 1824×948 px；用户还能把组件拖大。
**长边一律按 2048 px 出**——这是仓库里绝大多数素材的档位，低于它高密度机器上会被放大到糊。

| 用途 | 画布 | 比例 | 卡面真实比例 | 出这个的代价 |
| --- | --- | --- | --- | --- |
| **4×2（横版）** | **2048 × 1024** | 2.000 | 296×150dp = 1.973 | 横向压 1.4%，肉眼无感 |
| **4×4（方版）** | **2048 × 2048** | 1.000 | 296×296dp = 1.000 | 零变形 |

### ★ 铁律：一款风格，一个尺寸，一份素材

**不要拿一张素材喂两个尺寸。** 4×2 卡面是 1.97:1、4×4 是 1:1，一张 1.75:1 的图两边用，
4×4 上要纵向拉 **76%**——熊猫会被拉成瘦高条、小猫会被压成扁饼。
仓库里现有几款整幅贴图（毛绒森林 1.75、春天与小狗 1.79、竹林熊猫 1.757、萌宠乐园 1.757、
蜡笔彩虹框 2.91）都是这么来的，**这正是它们在不同尺寸下观感不稳的根因**。

规则改成：

1. **每款风格只出一种画布尺寸的素材**——就是上面那张表里的横版或方版，二选一；
2. **这款风格要同时上 4×2 和 4×4 时，就出两份**：横版放 `presetImageResName`，
   方版放 `presetImageResNameSquare`，渲染时按组件比例自动挑（< 1.4 用方版）。
   方版是后加的字段：旧存档里没有它，读档时按 `presetId` 从当前预设补齐
   （`WidgetStyle.caughtUpWithPreset`），已落桌面的老组件不必重新套用；
3. **方版的正文安全区必须落在与横版相同的相对位置**——这样两张图切换时文字不用重排、
   也不用改代码。每款风格的比例见下方"现有整幅贴图风格待补方版"表。

> 只出一份素材也可以，但那款风格就只保证**它对应尺寸**上的效果，另一个尺寸会变形——
> 这是被接受的取舍，不是 bug。
>
> 有一类风格是**尺寸专属**的，只在声明的那一个尺寸上用，另一个尺寸上的变形是**已知且被接受**的：
>
> | 风格 | 专属尺寸 | 说明 |
> | --- | --- | --- |
> | 可爱四小只 | **4×2** | 设计稿就是 1824×912（2:1）。按产品决定**不出 4×4 版**，4×4 上纵向拉 94% 是接受的。**不要**给它补方版素材 |
> | 青年雕塑 | **4×4** | 设计稿只有 2048×2048 方版（素材来自设计给的 SVG；效果图 PNG 上那层青/洋红标注是**通用模板**叠出来的，渲染时已剔除）。按产品决定**不出 4×2 版**，4×2 上横向拉 ~97% 是接受的。**不要**给它补横版素材 |
> | 蜡笔彩虹框 | **4×2** | 素材原稿 2048×1152 是 16:9 画布（蜡笔框只占中间一条，上下各留 21%~27% 透明），入库前已按内容框裁到 1826×627 = 2.91:1。产品点名最佳显示尺寸 4:2，**这款风格没有 4×4 版**（2026-10-08 产品确认）：4×4 上纵向拉 ~2.9 倍（爱心会拉成细条）是已知且接受的。**不要**给它补方版素材 |
>
> 「专属尺寸」同时就是该风格 App 内预览的 `bestDisplaySize`（见 §0.1.2）。
>
> 遇到这类风格，**不要**当成"缺一份方版"来修。

模板图（可直接叠在设计稿上对位）：
- 横版 4×2 → `tools/asset-template-4x2.png`
- 方版 4×4 → `tools/asset-template-4x4.png`

### 0.1.2 App 内预览用的「最佳显示尺寸」（`WidgetStyle.bestDisplaySize`）

上面这条铁律决定了每款风格的原生画布（横版 / 方版），**App 内预览就按这个尺寸出图**：

- 每款预设显式声明 `bestDisplaySize`（`WIDE_4X2` = 250×110dp / `SQUARE_4X4` = 250×250dp），
  依据就是 §0.1 的原生画布：方版素材（2048×2048）→ **4×4**，横版素材（≈2:1）→ **4×2**；
  无素材的全代码风格跟随 App 默认落位 **4×2**。
  个别款由产品单独点名（如「小霸王游戏机」取 **4×4**：方形下模型更大、机身屏幕放得下两行正文），
  这类例外以 `WidgetStyle.kt` 里的声明为准，不受上面这条比例规则约束。
- 预览**不跟桌面组件的尺寸走**：同一个风格从哪个入口添加（4×4 / 4×2）、在桌面上被拉成多大，
  预览比例都固定（产品规则，2026-10-08）。预览区高度由 `PreviewMetrics.previewHeightForStyle`
  按同一尺寸推导，风格行标题不再挂尺寸后缀。
- 该字段**不落 JSON**：读档时按 `presetId` 从当前预设定义解析（`bestDisplaySizeForPreset`），
  所以改预设的尺寸，已保存、已落桌面的样式立即跟着变，不必重新套用预设。
- 新增风格时**必须一起想清楚**：这款是 4×2 还是 4×4？取值依据见上；写完跑
  `PresetDisplaySizeTest`（钉住有外部依据的那几条）。

### 0.1.1 现有整幅贴图风格：待补方版清单

这四款目前都只有一张 ~1.76:1 的横版素材，**在 4×4 上都会被纵向拉伸 76%**。
要修就得补一张 **2048×2048** 的方版。方版**不必和横版同一构图**（可以重新安排），
但**正文框必须落在下表给的坐标范围内**，否则文字会压到装饰上：

正文安全区**一律给像素坐标**（画布就是 2048×2048，坐标即画布上的像素位置，不用换算百分比）：

| 风格 | presetId | 横版素材（现状） | 方版画布 | **正文安全区（像素）** | 模板 |
| --- | --- | --- | --- | --- | --- |
| 毛绒森林 | `p_plush_forest` | `plush_forest` 1792×1024 | 2048×2048 | x **235~1812**　y **819~1781** | **✅ 已交付** `plush_forest_square` |
| 春天与小狗 | `p_spring_dog` | `spring_dog` 1812×1012 | 2048×2048 | x **92~1955**　y **819~1935** | `tools/asset-template-4x4-spring-dog.png` |
| 竹林熊猫 | `p_panda_bamboo` | `panda_bamboo` 2080×1184 | 2048×2048 | x **169~1878**　y **901~1699** | **✅ 已交付** `panda_bamboo_square` |
| 萌宠乐园 | `p_pet_park` | `pet_park` 2080×1184 | 2048×2048 | x **163~1884**　y **778~1781** | **✅ 已交付** `pet_park_square` |

> **已补齐 3/4（2026-10-07）**：萌宠乐园、毛绒森林、竹林熊猫的方版都已交付并接上
> （`pet_park_square` / `plush_forest_square` / `panda_bamboo_square`）。
> **只剩「春天与小狗」**（`tools/asset-template-4x4-spring-dog.png`）。
> 这三款的 `bestDisplaySize` 已按「方版素材已交付」定为 **4×4**；
> **「春天与小狗」暂定 4×2**（横版在 4×4 上会纵向拉 76%），方版到货后把它那一行改成
> `SQUARE_4X4` 即可（`WidgetStyle.kt` 的 `p_spring_dog`）。
>
> 三张交付稿的共同点与两处小出入，供后续出图参考：
> ① 都是 **PNG + RGBA、四角 alpha 全 0** 的**真透明底**（前两版那种"棋盘格/纯白烘进像素"的坑没再出现）；
> ② 边长都是 **1536 < 规格的 2048**——本机（密度 2.55，4×4 渲染约 1163px）是缩小使用、清晰；
> 　　但密度 4.0 的手机渲染 1824px，会被放大 1.19× 略软，**理想值仍是 2048**；
> ③ 正文面板**横向都完整包住**规格；**下沿比规格短 89~161px**——文字是垂直居中画的，
> 　　1~3 行都落在面板内，只有长到填满整框时才会压到底部花边。
>
> 素材的透明区 RGB 是纯黑 `(0,0,0)`，入库前统一做了**颜色外扩**再转 WebP，否则缩放时黑会渗到边缘形成黑边。

**每款各有一张模板图**（带 alpha 的透明 PNG），直接拖进设计平台叠在 2048×2048 的画布上，
洋红虚线框就是正文安全区，照着画即可——不用自己量、不用算百分比。
新风格用通用的 `tools/asset-template-4x4.png`（正文区按默认 16dp = 111px 内缩）。

### 0.2 透明底：必须是真 alpha

**主体之外必须是真正的透明通道**（导出 PNG-32 或带 alpha 的 WebP）。

**AI 生成图最常见的坑**：图看着"透明"，其实是把**棋盘格或纯白**画进了像素里——
导出时被压成了无 alpha 的 RGB，那层底根本去不掉，只能事后抠（竹框那种有缝的还容易漏进内面板）。

用脚本检查（四角 alpha 必须是 0）：`python3 tools/process_component_image.py info <图>`

顺带两条同类的：
- **主体四周不要自带白边/相框**（会触发 `CENTER_CROP` 的自动裁边，宽横幅两侧露白）。
- **图上不要有文字水印/时间戳**——矢量稿能精确删，位图稿只能修补。

### 0.3 正文区：留一块干净的矩形，并告诉我它的范围

如果这个风格要在卡面上写正文（绝大多数都要），请：

1. 在素材里留出一块**干净、低对比**的矩形区域放正文（不要在这块里放花纹、亮片或强纹理）；
2. 出图时告诉我这块区域的**像素范围**（如"x 111~1974 / y 485~1021，素材 2080×1184"），
   或者让我从素材里量。

默认文字内边距是 **16dp**（换算到 2048×1024 的画布上约 **111 px**），压在这块区域内。
文字区太窄会被折成很多行、太靠边会压到装饰上。

### 0.4 其他

- 命名：只能小写 `a-z`、数字、下划线（中文/大写/空格会编译失败）。
- 体积：WebP 后**尽量压在 400 KB 以内**（仓库现有素材 20~330 KB，整幅插画的档位在 250~380 KB）。
- 一个风格 = 一张素材；同一套画风要出多款时，**共用同一套画布尺寸与正文区比例**，切换风格时文字才不会跳。

## 1. 图片处理（tools/process_component_image.py）

脚本五个子命令，按需组合：

```bash
# ① 检查（必做）：尺寸/四角alpha/非透明占比
python3 tools/process_component_image.py info 原图.png

# ② 裁边距：去掉四周纯白,让主体填满整图（图的主体是"接近白但≠纯白"时用）
python3 tools/process_component_image.py crop 原图.png 主体.png --pad 4

# ③ 去背景：从四边flood-fill,把连通近纯白置透明（不规则形状用这个,能保留撕纸边缘）
python3 tools/process_component_image.py flood 主体.png 组件主体.png --tol 8 --bg 255 255 255

# ④ 去水印/杂质：
#    a) 纯色/浅色背景 → inpaint(均色填充)
python3 tools/process_component_image.py inpaint 组件主体.png 干净图.png 1057 613 1532 719 --bg 251 249 239 --thresh 40
#    b) 带纹理/花纹背景(像素十字、纸纹等) → clone(从有效干净背景带覆盖,花纹连贯)
python3 tools/process_component_image.py clone 组件主体.png 干净图.png 535 320 1183 477 --from bottom --offset 5
```

**关键规则（都是踩过的坑）**：
- 处理后的图→**合法资源名**：只能小写 a-z、数字、下划线（如 `feather_letter.png`）。中文/大写/空格会编译失败。
- **删掉原中文名文件**，否则两个文件一起编译报错。
- crop 后再 flood：先裁掉大块白边距，再用 flood-fill 把不规则边缘外的背景置透明。
- **去水印的包围盒一定要包含所有杂质**（如文字+旁边的红心），否则残留。先 scan 出完整包围盒。
- **纹理背景用 clone 不用 inpaint**：inpaint 均色填充会破坏花纹/纹理的一致(残留亮块)；clone 从水印下方/旁边的干净背景带平移覆盖,花纹连贯。

## 2. 放入资源

把处理后的图放到 `app/src/main/res/drawable/`，文件名与 `presetImageResName` 一致（**不带扩展名**）。

## 3. 代码侧要回答的问题

### 3.1 注册形状、预设与推荐位

全部在 `data/WidgetStyle.kt`：

```kotlin
// ① WidgetShape 枚举加一行
FEATHER_LETTER("羽毛信纸")

// ② PRESETS 里加预设（背景图风格的关键字段）
WidgetStyle(
    shape = WidgetShape.FEATHER_LETTER,
    backgroundColor = android.graphics.Color.TRANSPARENT, // 有背景图时常设透明(见下面黑底坑)
    bgImageScaleMode = ImageScaleMode.STRETCH,            // 拉伸铺满;要保比例改 CENTER_CROP
    fontColor = Color.parseColor("#5C4A3A"),              // 文字色(对应留白区)
    font = WidgetFont.SOURCE_HAN_SERIF,
    fontSizeSp = 20f,
    textAlign = "CENTER",
    presetImageResName = "feather_letter",                // = drawable 名,不带扩展名
    authorSignature = "—— 羽毛信纸"
)

// ③ CLASSIC_PRESETS / PET_PRESETS 登记(编辑页推荐用)
"羽毛信纸" to (PRESETS.firstOrNull { it.shape == WidgetShape.FEATHER_LETTER } ?: PRESETS[0])
```

**明信片类（图文分割）预设**：文字显示区（下半/右半）的底色就是 `backgroundColor`，
在预设里显式声明（现有明信片预设都是白色，只有素材本身设计成别的颜色时才另给，如天空蓝）。
插图素材的预设行由 `WidgetStyle.illustrationStyle()` 换算，**整份样式取自预设**——
不要写成从当前样式 `copy()`，否则用户在颜色面板选过深色后，文字区会跟着变黑。

### 3.2 渲染侧：回答 4 个问题

渲染逻辑已按「形状家族」拆开，不再集中在一个文件。**按问题找落点，不要按文件找**：

| # | 要回答的问题 | 单一事实来源 | 漏了会怎样 |
| --- | --- | --- | --- |
| ① | 它属于哪个形状家族？ | `render/ShapeTraits.kt` 的 `WidgetShape.family()` | **编译不过** |
| ② | 正文落在哪块安全区？ | `render/CardTextRenderer.kt` 的 `textBoxFor()` | **编译不过** |
| ③ | 它在管线里的差异是什么？ | `render/ShapeTraits.kt`：`ShapeTraits` 的 4 个属性 + 对应的 4 张名字清单 | 静默套用错误默认值（如该透明却填了底色） |
| ④ | 家族装饰怎么画？ | `render/WidgetRenderPipeline.kt` 的 `drawFamilyChrome()` | 装饰不出现或错位 |

其中的 ①② 是**穷举 `when`**：加完枚举直接编译，编译器会告诉你还缺哪一处，跟着报错补即可。

**只有当 11 个既有家族都不合适时才需要新增家族**（2026-10-07 核对：家族数随风格下架从 13 减到 11），此时要在 ① 的家族分派和 ④ 的家族装饰两处
各加一个分支，并把 ③ 的差异在 `ShapeTraits` 里回答完整。

正文安全区参考写法（数值按实际留白调整，左右可以不对称）：

```kotlin
WidgetShape.FEATHER_LETTER -> {
    // 羽毛信纸：文字落在信纸留白区（右侧多留避羽毛笔，顶部避开尖角）
    val verticalInset = targetHeight * 0.16f   // 顶部留白
    val leftLateral = targetWidth * 0.10f      // 左侧留白
    val rightLateral = targetWidth * 0.20f     // 右侧留白
    val paddingLeft = leftLateral
    val paddingRight = targetWidth - rightLateral
    val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
    val cardTop = verticalInset
    TextBox(paddingLeft, textWidth, cardTop, targetHeight - cardTop - targetHeight * 0.14f)
}
```

### 3.3 资源文件

把图放在 `app/src/main/res/drawable/`，名字匹配 `presetImageResName`。

## 4. 四个必避的 bug（都要检查）

1. **黑底**（信纸外一团黑的根因）：渲染器填底色时 `bgPaint.alpha` 被强制成 `backgroundOpacity*255`，
   透明背景也会被画成黑色。
   **落点**：`render/WidgetRenderPipeline.kt` 的 `drawCardBackground()`——只有
   `Color.alpha(style.backgroundColor) > 0` 才画底色。

2. **透明 PNG 解码后黑**：`decodeResourceSampled` 要显式 `inPreferredConfig = Bitmap.Config.ARGB_8888`，
   否则透明 PNG 采样后 alpha 丢失变黑。
   **落点**：`render/WidgetRenderKernel.kt`。

3. **桌面深色卡片框**：widget 根布局别用系统 id `@android:id/background`（会被垫深色背景），
   改普通 id（如 `@+id/widget_root`）。
   **落点**：`res/layout/widget_layout.xml`。
   **注意**：改布局后，**桌面已放置的组件要删除、重新添加**才生效。

4. **预览里明信片文字区变黑**：这是样式换算问题，不是渲染问题——插图预设若从当前样式
   `copy()`，用户选过的深色底（如「深墨」）会被带进文字显示区。
   **落点**：`data/WidgetStyle.kt` 的 `illustrationStyle()`（预设行统一走它，见 §3.1）。

## 5. 验证流程

```bash
# 编译 + 打包
./gradlew :app:assembleDebug --offline

# 确认图进 APK、且 alpha 正确
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep <资源名>
python3 tools/process_component_image.py info <裁剪出的APK内图>   # 四角应透明

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **App 预览**：色块、文字区、无遮挡、无黑框（预览卡背景若非白色，信纸外会透预览背景色，正常）。
  预览按**风格自己的最佳显示尺寸**出图（`WidgetStyle.bestDisplaySize`：4×4 款 250×250、
  4×2 款 250×110），与组件从哪个入口添加、桌面上的实时拉伸都无关（见 §0.1.2）；
  4×2 / 4×4 两种规格都要看（不连真机时跑 `WidgetPreviewRenderTest`，两个尺寸各出一份 PNG——
  预览只出最佳尺寸那一张，另一个尺寸看的是它被挂上去时的兜底表现）。
- **桌面真实验证**：删除旧组件 → 重新添加，看信纸外是否透出壁纸、不规则形状是否呈现。

改动**绘制逻辑**后还要跑一次渲染基线，确认既有风格没有被连带改到：

```bash
./gradlew :app:testDebugUnitTest --tests "com.juge.app.WidgetRenderBaselineTest" --offline
# 确实要改绘制结果时，删除 app/src/test/resources/widget-render-baseline.tsv 后重跑，
# 人工确认 build/widget-previews/ 下的 PNG 正常后再提交新基线。
```

## 6. 一句话 checklist

**按 §0 的画布尺寸出图（4×2 用 2048×1024、4×4 用 2048×2048，带真 alpha）** → info 检查 → crop/flood/inpaint 处理 → 合法名保存+删中文名 → 放 drawable →
WidgetStyle(枚举/预设/推荐) → **编译，跟着 `when` 穷举报错补家族归属与文字安全区** →
保证无黑底bug/ARGB_8888/widget id → 编译 + 渲染基线 + adb 预览 + 桌面验证。
