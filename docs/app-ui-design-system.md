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

App 界面统一使用**系统中文无衬线字体**。`assets/fonts/` 下的 9 款字体只服务于
桌面组件正文（用户可选的「金句字体」），不要拿来排应用界面。
其中 EB Garamond、Caveat 是纯拉丁字体（`WidgetFont.latinOnly`），没有汉字字形，
中文金句仍走系统字体回退，选项名因此统一带「英文」前缀。

| 令牌 | 字号 / 行高 | 字重 | 用途 |
| --- | --- | --- | --- |
| `AppType.brand` | 24 / 30 | Black，字距 -0.3 | 顶部品牌「句阁」 |
| `AppType.brandSub` | 12 / 16 | SemiBold，字距 0.8 | 「DeskQuotes」 |
| `AppType.hero` | 20 / 26 | Bold，字距 -0.2 | PRO 弹窗的强调标题 |
| `AppType.dialogTitle` | 18 / 24 | SemiBold | 普通弹窗标题 |
| `AppType.title` | 16 / 22 | SemiBold | 卡片级标题（预留） |
| `AppType.sectionTitle` | 14 / 20 | SemiBold，字距 0.1 | 定制页区块标题、预设行标题 |
| `AppType.label` | 12 / 16 | SemiBold，字距 0.1 | 控件标签、小按钮、页签角标 |
| `AppType.body` | 14 / 21 | Normal | 标准正文与说明 |
| `AppType.bodyStrong` | 14 / 21 | SemiBold | 需要强调的正文 |
| `AppType.bodyLarge` | 16 / 24 | Normal | 金句内容 |
| `AppType.caption` | 12 / 16 | Regular | 次要说明、空态、页脚 |
| `AppType.micro` | 11 / 16 | Regular | 备案号、支付说明等最小可读文字 |
| `AppType.badge` | 11 / 14 | SemiBold | 免费 / PRO / 分类角标 |
| `AppType.button` | 14 / 18 | SemiBold | 主/次按钮 |
| `AppType.chip` | 14 / 20 | Medium | 筛选胶囊、分段页签 |
| `AppType.link` | 14 / 20 | Medium | 文字按钮、协议链接 |

逐行操作弹窗的正文用 `AppTextRole.dialogBody`（16/24、Medium，比 `AppType.body` 大一档），
它不是独立字级，而是 `AppType.body` 的「设置页放大版」，只在 §6.1.4 允许的场景使用。

**暗色模式字重整体 -40%**：`AppType` 的所有字级都是 `@Composable get()`，字重经
`LocalAppFontWeightScale`（日间 1f、暗色 0.6f）缩放后吸附到最近的 100 档——
Black 900→500、Bold/SemiBold→400、Medium→300、Normal→200。深色底上高字重会「发胀」，
压一档既回正观感，也让暗色层次更安静。**手写字重的地方必须套 `appFontWeight(...)`**，
否则会漏掉这条规则（见 `AppTypographyWeightTest`）。

`appTypography()` 把这些字号映射进 `MaterialTheme.typography`，未显式写 `style` 的
Material 组件也会落在同一套阶梯上。

## 3. 颜色

`ui/theme/AppColors.kt` 分两层：

- **品牌色**：`accentBlue`（薄荷青，区块标题/链接）、`selectBlue`（明亮浅蓝填充，按用户偏好搭配白色文字/图标；
  协议链接与它同色相，历史上的 `accentLightBlue` 三蓝并存已合并至此）、`premiumGold` / `premiumGoldEnd`
  （PRO 金色渐变填充）、`premiumGoldDeep`（白底上的金色图标/文字）、`premiumGoldInk`（金色底上的深棕文字）。
- **语义色**：`textPrimary` / `textSecondary` / `textTertiary`、`borderBlue`、`surfaceSubtle`、
  `surfaceMuted`、`dangerRed`、`disabledGray`、`warning*`、`successGreen`、`brandInk*`。

规则：

1. 文字只用 `textPrimary` / `textSecondary` / `textTertiary` 三档，不再出现 #1F2937、#6B7280、
   #9CA3AF 等近似灰；
2. **App 内部页面不再画 1dp 边框/分割线**（2026-10 改版，用户明确要求）：页面、卡片、控件之间的
   层次全部由微明度色阶表达，配合软阴影与留白。日间台阶是 页面 `darkBg`(#F2F5F9) → 卡片 `cardBg`(#FFF)
   → 控件面 `surfaceSubtle`(#EDF1F7)；夜间是 页面 `darkBg`(#1A1A1A) → 卡片 `cardBg`(#252525)
   → 控件面 `surfaceSubtle`(#303030)。输入框用 `fieldBg` / `fieldBgFocused` 表达静息与聚焦，
   不再用聚焦描边。`borderBlue` 只留给「必须勾边才成立」的功能性场景（如图片上的白色控件、
   裁剪框），不作为默认分层手段；**例外**：组件风格缩略图未选中态保留 0.5dp 发丝描边
   `borderBlue`（用户要求：分类缩略图需要一圈细边框勾出素材边界），选中态仍是 3dp 状态环；
3. 选中态与主按钮使用明亮浅蓝填充 `selectBlue`，其上的文字/图标按用户偏好使用白色；
   **状态环**（选中预设/色板的 3dp `accentBlue`、勾选框的 2dp）保留——这是状态而不是分层描边；
   危险操作统一 `dangerRed`；
4. **暗色模式是中性深灰，不再使用蓝紫夜空**：`AppPalette.Dark` 的所有底色/极光/抽屉玻璃令牌
   三通道近似相等；页面底 `#1A1A1A`、卡片 `#252525`、控件面 `#303030`、浮起控件 `#313131`
   （2026-10 二次反馈：不做接近纯黑的底，中性色阶整体上移一档；再往上会被
   `textTertiary` 在卡片上的 4.5:1 卡住）。不要往暗色调色板里加带蓝/紫偏色的底；
   夜间英雄图压暗也用页面底做 veil，而不是纯黑。`AppColorContrastTest` 锁定中性色与明度台阶；
5. 新增颜色先加进 `AppColors.kt` 并写清语义，不在界面里直接 `Color(0x…)`；
6. **对比度是硬门槛**：正文/标题/链接在其背景上 ≥ 4.5:1，图标、状态描边、开关等非文字元素 ≥ 3:1。
   常规文字/背景组合必须满足 AA 对比度要求。但用户明确偏好 `selectBlue`（日间 `#42B8EC`）配白字，
   该组合约为 2.26:1，低于 4.5:1，是有意保留的产品例外；**夜间这份亮蓝要压暗 20%**
   （`#3593BD`，白字约 3.5:1，夜间不再全屏最扎眼），日间保持原值。
   `AppColorContrastTest` 锁定两个色值和例外说明。请勿将此例外扩展到长正文或大段说明文字。

## 4. 形状与间距

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `AppShape.card` | 16dp | 所有内容卡片 |
| `AppShape.button` | 12dp | 主/次按钮、输入框、提示条 |
| `AppShape.chip` | 50%（全胶囊） | 筛选胶囊、勾选 chip |
| `AppShape.swatch` | 10dp | 方形缩略色块、直角变体 |
| `AppShape.thumbnail` | 10dp | 预设缩略图 |
| `AppShape.badge` | 6dp | 免费/分类角标 |
| `AppShape.dialog` | 20dp | 弹窗底板 |
| `AppShape.sheet` | 顶部 32dp | 主界面底部抽屉 |
| `AppShape.pill` | 50% | 浮动「录入新句」按钮、开发开关 |
| `AppShape.check` | 5dp | 勾选框 |
| `AppShape.handle` | 2dp | 抽屉把手 |

间距：`AppSpacing.screen`(20) / `cardPadding`(16) / `sectionGap`(16) / `itemGap`(12) /
`controlGap`(12) / `iconGap`(8) / `tight`(4)。

### 4.0 触控热区

- 所有可点击元素的**热区不小于 48×48dp**（Android / Material 标准）。
- 视觉可以更小，但必须把 `clickable` 放在 48dp 的外层容器上，或使用自带 48dp 热区的
  `IconButton` / `RadioButton` / `Slider`；**不要**用 `Modifier.size(28.dp)` 直接压小 `IconButton`。
- 相邻热区之间至少留 8dp；空间不够时收进溢出菜单（参考分类管理里的「更多」），不要并排塞四颗小图标。
- 纯文字链接（协议、备案号）用 `padding(vertical = 15.dp)` 把 18sp 行高撑到 48dp。
- **唯一例外：浮动菜单行 44dp**（`AppMenuItemRow`，见 §6）。iOS 菜单的行高惯例是 44dp，
  菜单又是居中的浮层、不贴边、不易误触，这里按 44dp 收；行内边距 16dp 仍是标准值。

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
- **顶栏三颗圆钮（刷新 / 日夜 / 我的）底色随主题**（`MainTopBar`）：
  日间是白色 38dp 圆 + `selectBlue` 浅蓝图标（与页签、筛选胶囊同一套选中色）；
  夜间改用 `surfaceRaised` 暗色圆 + 夜间 `selectBlue`——英雄区在夜间被压暗 52%~64%，
  再摆三块纯白圆就是全屏最亮的东西，与「真正的暗黑背景」冲突。图标/加载指示器的
  颜色与底色成对切换，不要只换底色。
- **顶栏会员入口按激活状态两态**（`MainTopBar`）：
  - 未激活：金色渐变「激活PRO ￥3.99」胶囊（`premiumGold`→`premiumGoldEnd` 底 + `premiumGoldInk` 深棕字），
    点击直接拉起付费墙——这是待激活用户在 App 顶端唯一的转化入口，样式不要改；
  - 已激活：胶囊换成「我的」图标（与刷新/日夜同款 38dp 圆钮 + 20dp `Person` 图标、
    `contentDescription = "我的"`），点开 `MinePage`（会员状态 / 会员账户昵称 / 退出登录 /
    注销账号 / 协议入口 / 版本号）。买断之后顶栏不再常驻 PRO 徽章——它没有可点的动作，
    会员身份改在「我的」页展示；
- 页面其余部分保留薄荷极光渐变，抽屉照旧压在最上层。方格网只画在抽屉上沿以上
  24dp / 12%（`gridLine`，基本被顶图盖住，仅首帧兜底），**暗色模式不画网格**（`gridLine` 带蓝调，
  会破坏中性纯黑底）；**上沿以下不再画网格**——
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
  不再“块滑到一半、字还是全灰”；胶囊轨道用 `panelTrack` 凹槽明度，选中滑块与筛选胶囊、
  主按钮统一使用 `selectBlue` 浅蓝填充并配白色字图标，不画 1dp 描边；
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
  金句卡片与区块卡片一样不画描边（层次靠页面/卡片明度台阶 + 软阴影），
  内边距上下 12dp——整卡高度比旧版收约 10%（用户定档）；
- **长按上下文菜单（iOS 式）**：金句卡片长按弹「编辑金句 / 删除金句」，分类 chip 长按弹「重命名 / 删除」
  （`ContextMenuOverlay`）。菜单是**同窗口非模态浮层**（不用 Dialog：弹出后那根手指继续拖动要能立刻
  取消菜单进入排序），页面背景保持原亮度 + 菜单卡（`AppShape.menu` 14dp 圆角 + `surfaceRaised` 明度分层 +
  `AppShadows.dialog` 弥散阴影，不画 1dp 描边），从指尖方向 scale 0.9→1 弹出；原地松手菜单保持打开，点空白处关闭。菜单项 44dp 行高、
  20dp 图标、破坏性项红色，无波纹按压高亮。检测器是
  `longPressMenuDragGestures` 单一状态机——**不能**用 detectTapGestures + detectDragGesturesAfterLongPress
  并行拼装：后者的 onDragStart 在长按超时那一刻就触发（不等移动），会把同时弹出的菜单瞬间关掉（踩过的坑）；
- **⋮ 溢出菜单**：分类管理弹窗每行右侧的「更多」按钮弹「重命名 / 删除」（`AppDropdownMenu`）。
  卡片与行与长按菜单**同一个出处**（`ui/AppMenu.kt`：`AppMenuCard` + `AppMenuItemRow`），
  区别只在浮出方式：它贴在按钮下方（`Popup`，右边缘对齐、下方放不下翻到上方、四周 8dp 钳制），
  **不压暗背景**——菜单卡自身用底色、圆角与阴影建立层级。
  与长按菜单同宽的 176dp 卡片；弹出时 0.9→1 缩放、从靠近按钮的那一角长出来（关闭即时，
  与长按菜单一致，不做退出动画）。
  **不要再用 Material3 `DropdownMenu`**：容器色取 `MenuDefaults.containerColor`（`surfaceContainer`），
  而本项目 `colorScheme` 没接这个槽位，会落回 M3 基线淡紫 #F3EDF7（夜间是另一个灰紫），
  再加 4dp 圆角、无 App 弥散阴影——在白色/深色弹窗上都是一块外来贴纸（2026-10 修复）；
- **长按拖拽排序**：菜单弹出后继续拖动越过 touch slop = 菜单取消、卡片「抬起」（scale 1.03 + 实影）进入排序。
  金句竖列与分类横排共用 `ReorderDragState`：拖拽项用 graphicsLayer 钉在指尖（不参与 `animateItem`，
  否则双重位移），相邻项靠 `animateItem` 让位；每交换一次按两槽实测尺寸差校正位移。拖拽期间只改本地
  顺序，松手先弹性归位再整表重写 sort_order 落库（金句 v8 起有 sort_order 列，升级时按旧显示顺序回填）；
  贴近列表边缘自动滚动。分类 chip 与「金句分类管理中心」弹窗内的分类行都支持长按拖拽排序，
  两处共用同一个状态机，弹窗里松手同样走事务化重排序落库。

- **文本输入（流式输出）**：所有文本框在键入时逐字「blur 晕开」——新字符先以约 8dp 高斯模糊 +
  35% 透明度出现，在约 320ms 内收拢为清晰墨色并落位，形成流式吐字的观感；静止文本不参与动画。
  实现在 `ui/AppTextField.kt`（BasicTextField 负责光标/选区/IME，文字由 Canvas 逐字绘制）。

不要新增超过 400ms 的动画，也不要给同一次交互叠加两套不同时长的动画。
需要新动效时，先在 `AppMotion.kt` 里加令牌，再在界面引用。

## 6. 共用组件

- `AppSectionCard`：白色圆角卡片 + 16dp 内边距 + 12dp 行距，默认高度变化自动动画；嵌套展开控件可关闭尺寸动画以避免叠加。跃然纸上的金句卡片同样走它
  （左右 16 / 上下 12 的瘦身内边距 + 整卡可点，卡面法律只有这一个出处）。**不画 1dp 描边**：
  卡片与页面的层次靠页面/卡片明度台阶 + 软阴影；
- `AppSectionHeader`：强调色区块标题（图标已按产品要求去掉）；
- `AppSectionLabel`：`label` 色 `textSecondary`；
- `AppPrimaryButton` / `AppSecondaryButton` / `AppDangerButton` / `AppTextButton`：
  统一 48dp 最小高度、12dp 圆角、14sp 按钮字、按压反馈；
- `AppFilterChip`：统一筛选胶囊；
- `CheckChip`：带勾选框的胶囊选项（加粗/倾斜/文字阴影/对齐方式）。金句字体选择复用
  `showCheckBox = false` 的单选变体——同尺寸、同配色，只去掉勾选框，保证「字体选项」与
  「加粗/对齐」两排选项视觉一致（用户反馈字体选项小一档）；
- 「文本内容与字形定制」的对齐控制分两组：**水平对齐**（左/中/右/两端）与**垂直位置**
  （垂直居中 / 顶部对齐），都复用 `CheckChip`；垂直位置对应 `WidgetStyle.verticalAlign`；
- `AppDialogSurface` / `AppDialogTitle`：统一弹窗底板与标题；
- `AppTextField`：全 App 唯一的输入框（原 `appTextFieldColors()` 已下线）。容器不画描边，
  用 `fieldBg`→`fieldBgFocused` 明度台阶表达静息/聚焦；输入时新键入字符**逐字 blur 晕开**
  （约 320ms 内从模糊 + 低透明度收拢清晰）。`minLines`/`maxLines` 由调用方决定，多行框可随
  内容长高、不在框内滚动藏文本（个性定制的金句框就是这种用法）；
- `AppNumberStepperRow`：数值行 + `−/+` 步进器（48dp 触控热区、长按连发、端点禁用、
  不可调整时用 `disabledHint` 替代按钮）。字号 1sp、行距 0.1x、字间距 0.05em、
  圆角 1dp、不透明度 5% 一档；
- `GradientTrackSlider`：取色弹窗专用，5dp 渐变胶囊轨道 + 18dp 白色圆钮。
  **圆钮必须带 2dp `accentBlue` 描边**——卡片底本身就是白色，只靠白底 + 阴影时圆钮会整个融进面板
  （用户反馈「拖动按钮和背景色融为一体，看不到按钮在哪」）。圆钮落在蓝色已填充段上时靠白芯区分，
  落在灰色未填充段上时靠描边勾出轮廓，两种位置都成立；
- `AppMenuCard` / `AppMenuItemRow` / `AppDropdownMenu`（`ui/AppMenu.kt`）：浮动菜单的唯一出处。
  卡片 = `surfaceRaised`（日间即 `cardBg` 纯白，夜间比 `cardBg` 亮一档，浮在弹窗上才看得出是另一层）
  + `AppShape.menu` 14dp + `AppShadows.dialog` 弥散阴影 + 上下 4dp 内边距，不画描边；
  行 = 44dp 高、20dp 图标、16dp 行内边距、无波纹按压高亮（日间 `surfaceSubtle`，夜间压到 `cardBg`）、
  点击带触感。长按菜单用它做同窗口浮层（`ContextMenuOverlay`），⋮ 菜单用它做贴按钮的 `Popup`
  （`AppDropdownMenu`，卡片四周留 20dp 阴影空间，否则 Popup 窗口会裁掉弥散阴影）。
  菜单项字号可由调用方给 `labelRole`：普通页 `AppTextRole.body`，16sp 弹窗传 `AppTextRole.dialogBody`；
- ~~`AppDivider`~~：已随「不画 1dp 分割线」改版下线，区块内分组改用 `Arrangement.spacedBy`
  留白与明度台阶表达。

### 6.1 子页面（弹窗）统一规范

二级 / 三级 / 四级页面（从页面逐层打开的弹窗）必须长得像「同一套系统的下一页」，
不能每一层各写一套壳子。规则：

1. **底板统一用 `AppDialogSurface`**：20dp 圆角、`cardBg` 明度底、`AppShadows.dialog`
   弥散阴影、宽度上限 420dp、`usePlatformDefaultWidth = false`、API 31+ 毛玻璃背景。
   不要在弹窗里自己 `clip + background(cardBg)` 拼一个无阴影、窄窗口的壳子
   （取色弹窗曾经这么写，已统一）。
2. **有自己内容的子页面用 `AppDialogHeader(title, onClose)`**：标题 + 右侧 X。
   取色、分类管理、协议全文、背景图裁剪都用它；不要再出现「< 关闭」文字按钮或
   各写一个 `IconButton(Close)`。简单确认框没有自己的内容，不需要 X，用底部取消。
3. **动作收口统一在右下角**：`AppTextButton("取消")` + `AppPrimaryButton(主操作)`；
   危险操作用 `AppDangerButton`。不再使用左右等分的两颗实心按钮。
   **例外——设置页形态**：设置类子页面（如金句分类管理中心）把动作放回内容流里，
   不硬塞到右下角——这类页面本来就靠「列表行 + 分组底」读层级，右下角悬浮按钮反而割裂。
4. **字号随层级一致**：标题一律 `AppDialogTitle`（18/24），正文 `AppType.body`；
   逐行操作的设置页（如金句分类管理中心）用 `AppTextRole.dialogBody`（16sp/Medium），
   但同一弹窗内的菜单、输入、按钮要一起放大（同一个 `style` 传下去），不能只放大某一行。
5. **特殊页面的例外要收窄到内容区**：首启隐私弹窗是合规专用的居中版式；
   图片裁剪只有「取景画面」是深色（图片编辑惯例），弹窗底板仍跟随主题，
   不允许整屏铺黑、白字标题、自带一套按钮。
6. 新增弹窗前先对照这一页：如果它不能直接复用 `AppDialogSurface` + `AppDialogHeader`
   + 右下角动作，先说明为什么，不要静默另起炉灶。

新增界面时优先复用这些组件；确实需要自绘时，颜色、字号、圆角、时长仍从令牌取。

### 6.2 整屏页面（推入式）

账户中心「我的」是**整屏页面**，不是弹窗：已激活用户的会员状态、账户昵称与账号操作都在这里。
它和主界面在同一个窗口里，作为上层页面从右侧滑入（弹簧位移 + 140ms 淡入，见 `AppMotion`），
返回键与左上角返回同一路径。

- 底板用 `darkBg` 页面底 + `AppSectionCard` 白卡 + `AppSectionLabel` 分组标题——
  与「个性定制」页同一套「页面 → 卡片 → 控件面」的明度台阶，不另造颜色；
- 头部：左上角返回（48dp 热区、`contentDescription = "返回"`）+ 居中标题（`AppDialogTitle` 同档 18sp），
  右侧留同宽占位让标题真正居中；不用 `AppDialogHeader`（那是弹窗的 X 收口）；
- **协议入口不在页面内重复**：各页底部 `LegalFooter` 是协议的唯一入口；
- 破坏性动作（注销账号）是**独立按钮**，点它弹独立确认弹窗（`AppDialogSurface` +
  `AppDialogTitle` + 右下角 `取消`/`AppDangerButton`），不在页面里内联展开确认区；
- 整屏页面必须用 `Modifier.pointerInput` 把空区域的手势接住（不能让点击穿透到下层主界面），
  并按需 `statusBarsPadding()` / `navigationBarsPadding()`。

## 7. 验证

- `./gradlew :app:testDebugUnitTest`：包含主界面与首启隐私弹窗的截图 + 语义基线；
- UI 改动确认无误后，删除 `app/src/test/resources/ui-baseline/main.tsv`、`main/`、
  `privacy.tsv`、`privacy/`，先跑一次生成、人工看 `app/build/ui-shots/*.png`，再跑一次通过；
- 小组件渲染基线（`widget-render-baseline.tsv`）不受本规范影响。
