# 新增「图片背景」组件风格 —— 接入指南

> 适用：想让 App 的桌面小组件支持一种**用一张效果图做主体**的风格（如羽毛信纸、明信片、海报插画等）。
> 思路：图片处理成干净的组件主体图 → 作为背景图铺满组件 → 文字落在图的留白区。

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

## 3. 改代码（3 处）

### 3.1 `data/WidgetStyle.kt` —— 加形状 + 预设 + 推荐

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

### 3.2 `WidgetCanvasRenderer.kt` —— 渲染 3 处

- **① 形状裁剪分支** `when(style.shape)`：定义组件外轮廓。不规则撕纸轮廓用 `drawXXXPath` 构造（参考 `drawFeatherLetterPath`）。
- **② 背景图**：只要预设设了 `presetImageResName` + `backgroundColor` 透明，渲染器**自动**加载图并铺满、透明区透背景，无需额外代码。
- **③ 文字安全区** `else if (style.shape == FEATHER_LETTER)`：设置文字留白，避开装饰元素。参考例子：
  ```kotlin
  } else if (style.shape == WidgetShape.FEATHER_LETTER) {
      val verticalInset = targetHeight * 0.16f   // 顶部留白(避信封尖角/上边装饰)
      val leftLateral = targetWidth * 0.10f      // 左侧留白
      val rightLateral = targetWidth * 0.20f     // 右侧留白(避右侧装饰如羽毛笔,可不对称)
      paddingLeft = leftLateral
      paddingRight = targetWidth - rightLateral
      textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
      cardTop = verticalInset
      cardHeight = targetHeight - cardTop - targetHeight * 0.14f // 底部留白
  }
  ```

### 3.3 资源文件

把图放在 `app/src/main/res/drawable/`，名字匹配 `presetImageResName`。

## 4. 三个必避的 bug（都要检查）

1. **黑底**（信纸外一团黑的根因）：渲染器填底色时 `bgPaint.alpha` 被强制成 `backgroundOpacity*255`，透明背景也被画成黑色。
   **修复**（`WidgetCanvasRenderer.kt` 底色填充处）：透明背景不填充——
   ```kotlin
   if (Color.alpha(style.backgroundColor) > 0) {
       canvas.drawPath(...)   // 只有不透明才画底色
   }
   ```

2. **透明 PNG 解码后黑**：`decodeResourceSampled` 要显式 `inPreferredConfig = Bitmap.Config.ARGB_8888`，否则透明 PNG 采样后 alpha 丢失变黑。

3. **桌面深色卡片框**：widget 根布局别用系统 id `@android:id/background`（会被垫深色背景），改普通 id（如 `@+id/widget_root`）。
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

## 6. 一句话 checklist

info 检查 → crop/flood/inpaint 处理 → 合法名保存+删中文名 → 放 drawable → 改 WidgetStyle(枚举/预设/推荐) → 渲染器(形状分支/文字安全区) → 保证无黑底bug/ARGB_8888/widget id → 编译+adb 预览+桌面验证。
