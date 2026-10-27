# App 界面设计规范（UI / 字体 / 动效）

> 适用范围：`app/src/main/java/com/juge/app/ui/`、`account/AccountDialog.kt`、`CropImageHelper.kt`
> 里的 Compose 界面。
>
> **不适用于**桌面小组件的渲染（`WidgetCanvasRenderer` / `render/*`）：组件正文字体、
> 字号、阴影是用户可调的产品能力，见 `docs/component-style-guide.md`。

## 1. 三层结构

```
ui/theme/AppColors.kt   颜色语义令牌
ui/theme/AppType.kt     字体阶梯（同时映射进 MaterialTheme.typography）
ui/theme/AppDimens.kt   圆角 / 间距令牌
ui/theme/AppMotion.kt   动效时长与曲线
ui/AppComponents.kt     共用组件（卡片、标题、按钮、胶囊、弹窗底板、输入框配色）
```

界面代码只引用令牌，不再写 `fontSize = 13.sp`、`Color(0xFF64748B)`、`RoundedCornerShape(8.dp)`、
`tween(durationMillis = 400)` 这类字面量。

## 2. 字体

App 界面统一使用**系统中文无衬线字体**。`assets/fonts/` 下的 7 款字体只服务于
桌面组件正文（用户可选的「金句字体」），不要拿来排应用界面。

| 令牌 | 字号 / 行高 | 字重 | 用途 |
| --- | --- | --- | --- |
| `AppType.brand` | 24 / 30 | Black | 顶部品牌「句阁」 |
| `AppType.brandSub` | 12 / 16 | SemiBold，字距 0.8 | 「DeskQuotes」 |
| `AppType.hero` | 20 / 26 | ExtraBold | PRO / 账号弹窗的强调标题 |
| `AppType.dialogTitle` | 18 / 24 | Bold | 普通弹窗标题 |
| `AppType.title` | 16 / 22 | Bold | 卡片级标题（预留） |
| `AppType.sectionTitle` | 14 / 20 | Bold | 定制页区块标题、预设行标题 |
| `AppType.label` | 12 / 16 | Bold | 控件标签、小按钮、页签角标 |
| `AppType.body` | 13 / 20 | Regular | 标准正文与说明 |
| `AppType.bodyStrong` | 13 / 20 | SemiBold | 需要强调的正文 |
| `AppType.bodyLarge` | 15 / 23 | Regular | 金句内容 |
| `AppType.caption` | 12 / 16 | Regular | 次要说明、空态、页脚 |
| `AppType.micro` | 11 / 16 | Regular | 备案号、支付说明等最小可读文字 |
| `AppType.badge` | 10 / 12 | Bold | 免费 / PRO / 分类角标 |
| `AppType.button` | 14 / 18 | Bold | 主/次按钮 |
| `AppType.chip` | 13 / 18 | Medium | 筛选胶囊、分段页签 |
| `AppType.link` | 13 / 18 | Medium | 文字按钮、协议链接 |

`AppTypography` 已把这些字号映射进 `MaterialTheme.typography`，未显式写 `style` 的
Material 组件也会落在同一套阶梯上。

## 3. 颜色

`ui/theme/AppColors.kt` 分两层：

- **品牌色**：`accentBlue`（薄荷青，区块标题/链接）、`selectBlue`（明亮浅蓝填充，按用户偏好搭配白色文字/图标）、
  `accentLightBlue`（协议链接）、`premiumGold` / `premiumGoldEnd`（PRO 金色渐变填充）、
  `premiumGoldDeep`（白底上的金色图标/文字）、`premiumGoldInk`（金色底上的深棕文字）。
- **语义色**：`textPrimary` / `textSecondary` / `textTertiary`、`borderBlue`、`surfaceSubtle`、
  `surfaceMuted`、`dangerRed`、`disabledGray`、`warning*`、`successGreen`、`brandInk*`。

规则：

1. 文字只用 `textPrimary` / `textSecondary` / `textTertiary` 三档，不再出现 #1F2937、#6B7280、
   #9CA3AF 等近似灰；
2. 默认卡片描边统一 `borderBlue`；选中预设和控件的状态描边使用对比度更高的 `accentBlue`；
3. 选中态与主按钮使用明亮浅蓝填充 `selectBlue`，其上的文字/图标按用户偏好使用白色，状态描边使用 `accentBlue`；危险操作统一 `dangerRed`；
4. 新增颜色先加进 `AppColors.kt` 并写清语义，不在界面里直接 `Color(0x…)`；
5. **对比度是硬门槛**：正文/标题/链接在其背景上 ≥ 4.5:1，图标、状态描边、开关等非文字元素 ≥ 3:1。
   常规文字/背景组合必须满足 AA 对比度要求。但用户明确偏好 `selectBlue`（`#42B8EC`）配白字，
   该组合约为 2.26:1，低于 4.5:1，是有意保留的产品例外；`AppColorContrastTest` 锁定色值和例外说明。
   请勿将此例外扩展到长正文或大段说明文字。

## 4. 形状与间距

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `AppShape.card` | 16dp | 所有内容卡片 |
| `AppShape.button` | 12dp | 主/次按钮、输入框、提示条 |
| `AppShape.chip` | 10dp | 筛选胶囊、色板、列表行 |
| `AppShape.thumbnail` | 10dp | 预设缩略图 |
| `AppShape.badge` | 6dp | 免费/分类角标 |
| `AppShape.dialog` | 20dp | 弹窗底板 |
| `AppShape.sheet` | 顶部 32dp | 主界面底部抽屉 |
| `AppShape.pill` | 50% | 浮动「录入新句」按钮、开发开关 |
| `AppShape.check` | 5dp | 勾选框 |
| `AppShape.handle` | 2dp | 抽屉把手 |

间距：`AppSpacing.screen`(16) / `cardPadding`(16) / `sectionGap`(16) / `itemGap`(10) /
`controlGap`(12) / `iconGap`(6) / `tight`(4)。

### 4.0 触控热区

- 所有可点击元素的**热区不小于 48×48dp**（Android / Material 标准）。
- 视觉可以更小，但必须把 `clickable` 放在 48dp 的外层容器上，或使用自带 48dp 热区的
  `IconButton` / `RadioButton` / `Slider`；**不要**用 `Modifier.size(28.dp)` 直接压小 `IconButton`。
- 相邻热区之间至少留 8dp；空间不够时收进溢出菜单（参考分类管理里的「更多」），不要并排塞四颗小图标。
- 纯文字链接（协议、备案号）用 `padding(vertical = 15.dp)` 把 18sp 行高撑到 48dp。

## 4.1 顶部主视觉

主界面顶部（状态栏 → 顶栏 → 组件预览 → 预览提示）铺一张本地阳光海滩图：

- 素材：`app/src/main/res/drawable-nodpi/sunny_beach.webp`（1153×1152，WebP q85，约 150KB）。
  已按运行时可见区域**右对齐预裁**（只保留右侧遮阳伞与沙滩，左侧海浪不再加载）；
  原始 2048×1152 版本见 git 历史。WebP 只减包体，解码内存仍由 1153×1152 决定（约 5.07MiB）；
- 布局：`MainScreenBackground(heroHeight = 实测抽屉上沿)`，图片从屏幕最顶端开始，
  运行时 `ContentScale.Crop` + `TopEnd` 只做极小的等比裁切；绘制盒比可见 hero 多一个
  32dp 抽屉圆角，保证圆角缺口露出的是图而不是页面底色；图片下缘在抽屉上沿以下渐隐一个圆角高度，
  避免硬边透过毛玻璃显出直线截断；
- 可读性：图片顶部是亮蓝天空，顶栏品牌字使用白色 + 文字阴影，图片顶部再加
  38%→20%→透明 的黑色渐变暗压；预览提示使用白色文字 + 30% 黑色胶囊；
- 页面其余部分保留薄荷极光渐变，抽屉照旧压在最上层。方格网只画在抽屉上沿以上
  24dp / 12%（`gridLine`，基本被顶图盖住，仅首帧兜底）；**上沿以下不再画网格**——
  面板是毛玻璃，网格会从卡片缝隙里透成方格纹理。抽屉背后垫**平坦的页面底色**：
  斜向渐变在面板后残留的浅尾巴会被 8-bit 抖动成一层细颗粒（透过毛玻璃显脏），
  平坦底色没有渐变可抖；面板的「透」由顶边模糊的海滩图带提供。
- 抽屉毛玻璃：`hazeSource` 挂在背景层，必须与抽屉**平级**——source 作为抽屉的祖先时，
  Haze 会过滤掉它自己的区域，`hazeEffect` 会静默失效（不模糊、也不上 tint）。
  材质是自定义 `HazeStyle`：白 45% tint（比 `HazeMaterials.thin` 的 60% 更透一档）、
  24dp 模糊、`noiseFactor = 0`。模糊输入用 `HazeInputScale.Fixed(0.5f)`：采样像素量
  降到 1/4，Haze 会按同一比例换算模糊半径，观感与 1× 基本一致。
- 抽屉面板**不画外描边、顶部高光、内阴影，也不给 elevation 落影**（Card 默认的 1dp
  也要显式归零）：面板与背景的边界只靠毛玻璃的白色 tint 区分，上沿保持干净的圆角轮廓。
- 换图时保持「上方是天空、下方是海面/地面」的构图，中间不要放主体细节——组件预览卡会盖住画面中部。
  换主体在左侧的素材时，把 `heroAlignment` 改成 `TopStart` 或 `TopCenter`。

## 5. 动效

统一入口 `AppMotion`：

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `FastMs` | 140ms | 退出/淡出 |
| `StandardMs` | 220ms | 颜色、边框、按压缩放、预览 Crossfade |
| `EmphasizedMs` | 320ms | 展开/收起 |
| `EntranceMs` | 360ms | 列表进场 |
| `easing` | FastOutSlowIn | 大多数状态切换 |
| `decelerate` | LinearOutSlowIn | 进场 |
| `PressedScale` | 0.97 | 按压反馈 |

已统一的交互：

- **分段页签**：指示器跟手位移；文字/图标颜色按 pager 实时位移在灰与白之间插值，
  不再“块滑到一半、字还是全灰”；白色胶囊轨道带 1dp `borderBlue` 描边——面板是
  近白毛玻璃，描边缺失时轨道会融进面板（与区块卡片同一条规则）；
- **筛选胶囊 / 勾选胶囊**：底色、文字色、描边色平滑过渡；
- **预设缩略图 / 色板**：选中描边宽度与颜色平滑过渡；
- **风格预设行**：缩略图统一 180×96dp 卡片盒（`PresetThumbnailBox`，旧版 150×80 的 1.2 倍），内容按
  `WidgetStyle.bestDisplaySize` 等比渲染（4×2 → 180×79、4×4 → 96×96），方形风格不再被压扁。
  行内可双向滚动；右滑返回「跃然纸上」不再由行自己判断（旧版 `pageBackAtRowStart` 已删除），
  而是统一交给页面级观察者 `swipeBackToPage`（`SwipeBackGesture.kt`）：
  - **只观察、不消费**指针事件：pager 的跟手、行回卷、文本选择全部保持原样，
    观察者只在松手瞬间决定要不要把“本来会弹回去”的拖动补成一次完整翻页；
  - 判定：横向向右、累计位移里横向占优后越过 touch slop，再走 6dp，且满足下面任一：
    - 这次拖动**真的把 pager 往右推了至少 6dp**（普通区域、滚到最左端的横向行）；
    - 或者按在了横向滚动行上、但行自己没有动（已到最左端 / 本来就不需要滚）——
      这次右滑对行无事可做，就交给翻页。
    “行有没有动”由 `SwipeBackControlUsage` 打点：挂了 `horizontalControlUsage(state)`
    的横向行自动上报；行滚动了就让位，否则接管。用绝对位移而不是比例，让
    “行滚到一半后继续右滑”的同手势交接也能触发；
    方向判定不再“一越 slop 就锁死”——真机起手常带纵向抖动或斜向弧线，
    旧逻辑会在第一帧就把整段手势判死；
  - 「个性定制」页的连续值不再用横向滑条：字号、行距、字间距、外框圆角、背景不透明度
    全部改为 `AppNumberStepperRow`（数值行 + `−/+`，长按连发）。步进器只响应点击，
    不参与横向拖动，页面里任何位置右滑都不需要再给滑条让路；取色弹窗里的
    `GradientTrackSlider` 不在 pager 内，保留滑动手感；
  - 抽屉顶栏（拖拽把手 + 分段页签）不在 pager 内，单独挂同一个观察者、不要求 pager
    位移；它只包住顶栏，不包 pager，避免与内容层重复触发；
  - 顶部区域（顶栏、预览轮播、组件标签）同样纳入：观察者以**预览 pager** 为参照，
    预览轮播还能往右翻时让轮播自己翻；单个组件或已在第一个预览时右滑返回。
  - 松手后的翻页动画以 `MutatePriority.UserInput` 进入（`animateToPageOnUserInput`）：
    pager 自身的回弹事务还握着滚动锁，标准 `animateScrollToPage`（Default）会被它
    打断；页签点击共用同一入口，所以翻页动画途中点另一个页签仍立即生效；
  - 兜底不变量：pager 只要「位移停止 120ms、无程序化滚动、手指已离开」，就自动吸到
    最近页（`MainAppScreen` 的 settle 看门狗）——任何路径造成的半页状态都不会永久卡死；
- **主/次按钮**：按压缩放到 0.97，松开回弹；
- **教程卡片**：箭头旋转 180°，内容展开/收起带高度与淡入淡出；
- **预览区**：仅预设/形状/背景素材身份切换时 Crossfade 220ms；正文、颜色和数值调整直接更新；
- **列表**：仅前四条首次出现或新增到前四条的金句卡片进场 360ms（滑入 + 淡入）；普通滚动不重复播放。
  金句卡片带 1dp `borderBlue` 描边（与区块卡片同规则），内边距上下 12dp——整卡高度比旧版收约 10%（用户定档）；
- **长按上下文菜单（iOS 式）**：金句卡片长按弹「编辑金句 / 删除金句」，分类 chip 长按弹「重命名 / 删除」
  （`ContextMenuOverlay`）。菜单是**同窗口非模态浮层**（不用 Dialog：弹出后那根手指继续拖动要能立刻
  取消菜单进入排序），整页压暗 16% + 白卡菜单（`AppShape.menu` 14dp 圆角 + 1dp 描边 + `AppShadows.dialog`
  弥散阴影），从指尖方向 scale 0.9→1 弹出；原地松手菜单保持打开，点空白处关闭。菜单项 44dp 行高、
  20dp 图标、破坏性项红色，按压高亮走 surfaceSubtle 无波纹方案。检测器是
  `longPressMenuDragGestures` 单一状态机——**不能**用 detectTapGestures + detectDragGesturesAfterLongPress
  并行拼装：后者的 onDragStart 在长按超时那一刻就触发（不等移动），会把同时弹出的菜单瞬间关掉（踩过的坑）；
- **长按拖拽排序**：菜单弹出后继续拖动越过 touch slop = 菜单取消、卡片「抬起」（scale 1.03 + 实影）进入排序。
  金句竖列与分类横排共用 `ReorderDragState`：拖拽项用 graphicsLayer 钉在指尖（不参与 `animateItem`，
  否则双重位移），相邻项靠 `animateItem` 让位；每交换一次按两槽实测尺寸差校正位移。拖拽期间只改本地
  顺序，松手先弹性归位再整表重写 sort_order 落库（金句 v8 起有 sort_order 列，升级时按旧显示顺序回填）；
  贴近列表边缘自动滚动。排序不再进分类管理中心按箭头（那里只留重命名/删除/新建）。

不要新增超过 400ms 的动画，也不要给同一次交互叠加两套不同时长的动画。
需要新动效时，先在 `AppMotion.kt` 里加令牌，再在界面引用。

## 6. 共用组件

- `AppSectionCard`：白色圆角卡片 + 1dp 描边 + 16dp 内边距 + 12dp 行距，默认高度变化自动动画；嵌套展开控件可关闭尺寸动画以避免叠加。跃然纸上的金句卡片同样走它
  （左右 16 / 上下 12 的瘦身内边距 + 整卡可点，卡面法律只有这一个出处）。面板是近白毛玻璃，
  **描边不能省**——只靠阴影白卡会融进面板（用户反馈「轮廓要清晰、有层次感」）；
- `AppSectionHeader`：强调色区块标题（图标已按产品要求去掉）；
- `AppSectionLabel`：`label` 色 `textSecondary`；
- `AppPrimaryButton` / `AppSecondaryButton` / `AppDangerButton` / `AppTextButton`：
  统一 48dp 最小高度、12dp 圆角、14sp 按钮字、按压反馈；
- `AppFilterChip`：统一筛选胶囊；
- `AppDialogSurface` / `AppDialogTitle`：统一弹窗底板与标题；
- `appTextFieldColors()`：统一输入框配色——描边式（透明底 + 未聚焦 `borderBlue` / 聚焦 `accentBlue` 描边）；
- `AppNumberStepperRow`：数值行 + `−/+` 步进器（48dp 触控热区、长按连发、端点禁用、
  不可调整时用 `disabledHint` 替代按钮）。字号 1sp、行距 0.1x、字间距 0.05em、
  圆角 1dp、不透明度 5% 一档；
- `GradientTrackSlider`：取色弹窗专用，5dp 渐变胶囊轨道 + 18dp 白色圆钮。
  **圆钮必须带 2dp `accentBlue` 描边**——卡片底本身就是白色，只靠白底 + 阴影时圆钮会整个融进面板
  （用户反馈「拖动按钮和背景色融为一体，看不到按钮在哪」）。圆钮落在蓝色已填充段上时靠白芯区分，
  落在灰色未填充段上时靠描边勾出轮廓，两种位置都成立；
- `AppDivider`：统一 1dp 浅色分隔线。

新增界面时优先复用这些组件；确实需要自绘时，颜色、字号、圆角、时长仍从令牌取。

## 7. 验证

- `./gradlew :app:testDebugUnitTest`：包含主界面与首启隐私弹窗的截图 + 语义基线；
- UI 改动确认无误后，删除 `app/src/test/resources/ui-baseline/main.tsv`、`main/`、
  `privacy.tsv`、`privacy/`，先跑一次生成、人工看 `app/build/ui-shots/*.png`，再跑一次通过；
- 小组件渲染基线（`widget-render-baseline.tsv`）不受本规范影响。
