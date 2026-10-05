# 新增「图片背景」组件风格 —— 接入指南

> 适用：想让 App 的桌面小组件支持一种**用一张效果图做主体**的风格（如羽毛信纸、明信片、海报插画等）。
> 思路：图片处理成干净的组件主体图 → 作为背景图铺满组件 → 文字落在图的留白区。
>
> **本文负责列出「必须回答哪些问题」，不负责记住「改哪个文件」。**
> 渲染管线已按形状家族重构过两轮，函数与文件位置会漂移；而形状家族与文字安全区
> 都是**穷举 `when`、不写 `else`**，新增形状时编译器会直接在报错里点名还缺哪一处。
> 找不到落点时，**以编译器报错为准**——这比任何文档都可靠。

## 0. 前置：先确认图的质量（省一半处理）

开工前先看效果图，最好要：
- **主体占满画面**，四周不要有边框/背景色。
- 主体外**最好是透明**（透明抠图），否则"组件即主体、透出壁纸"做不了。
- 图上**不要有文字水印**（如"见字如面"），否则要额外去。

用脚本检查：`python3 tools/process_component_image.py info <图>`，看四角 alpha。

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

### 3.2 渲染侧：回答 4 个问题

渲染逻辑已按「形状家族」拆开，不再集中在一个文件。**按问题找落点，不要按文件找**：

| # | 要回答的问题 | 单一事实来源 | 漏了会怎样 |
| --- | --- | --- | --- |
| ① | 它属于哪个形状家族？ | `render/ShapeTraits.kt` 的 `WidgetShape.family()` | **编译不过** |
| ② | 正文落在哪块安全区？ | `render/CardTextRenderer.kt` 的 `textBoxFor()` | **编译不过** |
| ③ | 它在管线里的差异是什么？ | `render/ShapeTraits.kt`：`ShapeTraits` 的 4 个属性 + 对应的 4 张名字清单 | 静默套用错误默认值（如该透明却填了底色） |
| ④ | 家族装饰怎么画？ | `render/WidgetRenderPipeline.kt` 的 `drawFamilyChrome()` | 装饰不出现或错位 |

其中的 ①② 是**穷举 `when`**：加完枚举直接编译，编译器会告诉你还缺哪一处，跟着报错补即可。

**只有当 13 个既有家族都不合适时才需要新增家族**，此时要在 ① 的家族分派和 ④ 的家族装饰两处
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

## 4. 三个必避的 bug（都要检查）

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
- **桌面真实验证**：删除旧组件 → 重新添加，看信纸外是否透出壁纸、不规则形状是否呈现。

改动**绘制逻辑**后还要跑一次渲染基线，确认既有风格没有被连带改到：

```bash
./gradlew :app:testDebugUnitTest --tests "com.juge.app.WidgetRenderBaselineTest" --offline
# 确实要改绘制结果时，删除 app/src/test/resources/widget-render-baseline.tsv 后重跑，
# 人工确认 build/widget-previews/ 下的 PNG 正常后再提交新基线。
```

## 6. 一句话 checklist

info 检查 → crop/flood/inpaint 处理 → 合法名保存+删中文名 → 放 drawable →
WidgetStyle(枚举/预设/推荐) → **编译，跟着 `when` 穷举报错补家族归属与文字安全区** →
保证无黑底bug/ARGB_8888/widget id → 编译 + 渲染基线 + adb 预览 + 桌面验证。
