# MyLibrary 主题系统现状审计与实施路线

> 审计日期：2026-07-26  
> 审计基线：当前 `D:\Wenjian\Study\MyLibrary` 工作区，在其余并行任务全部结束后读取  
> 审计性质：只读代码审计与实施规划；除本报告外，未修改 Kotlin、Gradle、资源、数据库、业务逻辑或 UI  
> 验证：`compileDebugKotlin` 与 `lintDebug` 成功

## 1. 执行摘要

### 1.1 结论

当前主题化成熟度评定为：**部分可用**。

项目并非“没有主题基础”。相反，当前已经完成了几项最难补救的前置工作：

- 应用入口统一经过 `MyLibraryTheme`。
- 已有 `AppTheme`、`CompositionLocal`、`AppColors`、`AppTypography`、`LibraryShapes` 和全局尺寸常量。
- 普通 UI 几乎没有在页面里直接写十六进制颜色。
- 已有统一页面背景插入点 `AppScreenContainer`。
- 已有 4 个真实字体语义角色，以及同时输出 Compose `FontFamily` 和 Android `Typeface` 的 `AppFontResolver`。
- 底部导航目的地已通过 `NavigationIconSlot` 与具体 `ImageVector` 解耦，并已有普通/选中双状态。
- 当前 Canvas 海报已从 `AppFontResolver` 获取字体，并通过不可变 `CoverPosterPalette` 把颜色和字体传入后台渲染。
- 封面图片已经具备按尺寸降采样、LruCache、并发解码限制和相同请求合并，可为主题图片缓存设计提供成熟参考。

但当前基础仍是“固定默认主题的语义层”，不是完整运行时主题系统。主要差距是：

1. `AppColors` 有 24 个字段，而最终边界只有 3 个表面和 5 个普通颜色；现有别名过多且部分已失去用途。
2. 43 处 `Surface`、10 处 `ModalBottomSheet` 和多套对话框外观仍由页面分别指定颜色，不能统一接入表面图片。
3. 运行时没有 `ResolvedTheme`、主题仓库、主题生命周期、资源缓存、当前主题恢复和默认回退。
4. 字体基础较好，但仍有 10 处可见 `Text` 没有显式语义样式，Material Typography 映射也不完整。
5. 外部导航图片、`darkSystemBarIcons`、主题存储/备份、认证加密导入尚未实现；PDF 导出本身也尚不存在。

### 1.2 是否适合现在立即建设完整主题系统

**不适合现在直接进入主题包导入、TTF/图片动态加载或 AES-GCM。**

**适合现在立即完成 Phase 1 的默认主题内部骨架。** 项目仍处于 UI 重构期，此时应把页面背景、CARD、DIALOG、文字样式和 Material 默认值旁路统一收口。否则主要页面继续变化时，会继续生成新的表面和字体接入点，之后只能逐页返工。

推荐顺序：

1. 现在做默认主题语义收口和统一表面组件。
2. 主要页面稳定后冻结 Manifest v1、字体角色和图片显示规则。
3. 再实现运行时资源加载。
4. 最后实现加密主题包和备份恢复。

### 1.3 审计覆盖

扫描了 303 个非构建文件，其中：

| 类别 | 数量 |
| --- | ---: |
| 主代码 Kotlin | 226 |
| UI Kotlin | 116 |
| 非 UI 主代码 Kotlin | 110 |
| JVM/Android 测试 Kotlin | 41 |
| `res` 资源文件 | 8 |
| Room Schema 文件 | 11 |
| 其余 Gradle、Manifest、README、配置等 | 17 |

重点审计模块共 12 组：

1. 应用入口与 Theme
2. 通用组件
3. 首页/月历/时间轴
4. 资料库
5. 新增、编辑与详情
6. 统计与摘录
7. 设置与管理页面
8. 底部导航
9. Canvas 海报与预留导出目录
10. 图片存储与缓存
11. DataStore、App 私有存储
12. ZIP 备份、校验与恢复

### 1.4 关键量化结果

| 项目 | 结果 |
| --- | ---: |
| `AppColors` 字段 | 24 |
| `AppTypography` 视觉文字样式 | 16 |
| 字体家族语义角色 | 4 |
| 可见 `Text(...)` 调用 | 176 |
| 缺少显式 `style` 的 `Text(...)` | 10 |
| `Surface(...)` | 43，分布于 21 个文件 |
| `ModalBottomSheet(...)` | 10，分布于 8 个文件 |
| 原生 `Dialog(...)` | 7 |
| `AlertDialog(...)` | 1 |
| `Scaffold` / Material `Card` / `DropdownMenu` | 0 / 0 / 0 |
| 页面外直接十六进制普通 UI 色 | 0 |
| 主题/字体/图片静态资产 | 0 个 TTF、0 个主题 PNG/WebP |
| PDF 实现 | 0 |

## 2. 当前项目 Theme 架构

### 2.1 Theme 入口

控制路径为：

```text
MainActivity.onCreate
  → enableEdgeToEdge()
  → setContent
  → MyLibraryTheme
  → MyLibraryApp
  → AppNavHost
  → 各页面
```

真实文件：

- `app/src/main/java/com/example/mylibrary/MainActivity.kt`
- `app/src/main/java/com/example/mylibrary/ui/theme/Theme.kt`
- `app/src/main/java/com/example/mylibrary/ui/MyLibraryApp.kt`
- `app/src/main/java/com/example/mylibrary/ui/navigation/AppNavHost.kt`

`MainActivity` 只有一个 Compose Theme 入口，没有第二套 Compose Theme。

### 2.2 当前 Theme 组成

| 文件 | 当前职责 | 审计结论 |
| --- | --- | --- |
| `ui/theme/Color.kt` | `AppColors`、默认颜色、安全色 | 语义化基础很好，但字段数量超过最终边界 |
| `ui/theme/Type.kt` | 字体槽位、字体角色、字体解析器、16 套文字规格 | 已具备主题字体架构雏形 |
| `ui/theme/Shapes.kt` | Material Shapes | 与“形状由 App 固定”一致，应保留 |
| `ui/theme/Dimens.kt` | 页面、卡片、日历、导航尺寸 | 与“尺寸由 App 固定”一致，应保留 |
| `ui/theme/Theme.kt` | CompositionLocal、Material ColorScheme、系统栏 | 可容纳运行时值，但当前硬编码默认主题 |

`Theme.kt` 定义了 3 个 `CompositionLocal`：

- `LocalAppColors`
- `LocalAppTypography`
- `LocalFontResolver`

并通过 `AppTheme.colors`、`AppTheme.typography`、`AppTheme.fontResolver` 提供统一读取。

### 2.3 页面是否统一受控

大多数页面已统一读取 `AppTheme`。统计结果：

- 53 个文件读取 `AppTheme.colors`，共约 236 处。
- 47 个文件读取 `AppTheme.typography`，共约 174 处。
- 页面内未发现直接 `Color(0x...)` 的普通 UI 色。

但页面根背景仍有两种路径：

1. 推荐路径：`AppScreenContainer` / `MainPageLayout` / `ScreenContainer`
2. 直接路径：页面自己 `.background(AppTheme.colors.screenBackground).statusBarsPadding()`

直接路径至少存在于：

- `ui/item/ItemTagEditorScreen.kt`
- `ui/settings/FlatManagementList.kt`
- `ui/settings/TagManagementScreen.kt`
- `ui/settings/FieldManagementScreen.kt`

它们当前视觉一致，但未来 BACKGROUND 变成图片时，直接背景路径会绕开统一加载和缓存入口。

### 2.4 Material 默认值是否参与业务视觉

`AppColors.toMaterialScheme()` 只显式映射了部分 Material 3 token。已映射的主要字段包括：

- `primary`
- `onPrimary`
- `primaryContainer`
- `secondary`
- `background`
- `surface`
- `surfaceVariant`
- `outline`
- `outlineVariant`
- `error`

未完整显式映射的 token 包括：

- `secondaryContainer` / `onSecondaryContainer`
- `tertiary*`
- `errorContainer` / `onErrorContainer`
- `inverse*`
- 新版 `surfaceContainer*`
- `scrim`

当前多数自定义组件显式设置了颜色，因此没有普遍出现 Material 默认紫色。但至少有一个具体旁路：

- `ui/record/RecordStatusRow.kt` 使用默认 `FilterChip` 颜色，其选中容器可能读取未覆盖的 `secondaryContainer`。

另外：

- `Checkbox` 使用 Material 默认颜色，但其主要 token 当前可被 `primary`、`onSurfaceVariant` 间接控制。
- `AlertDialog`、输入标签等仍会使用部分 Material 默认排版或默认容器行为。

结论：**当前 Material ColorScheme 不是业务色的唯一来源，但未覆盖 token 仍可能泄漏 Material 默认 tonal 色。**

### 2.5 是否存在多套互不一致的 Theme 或 UI 常量

不存在第二套完整 Theme，但存在同义字段和同义表面：

- `surface` 与 `cardSurface`
- `surfaceSecondary`
- `selectedBackground` 与 `accent`
- `selectedText` 与 `onAccent`
- `outline` 与 `divider`
- 一组实际未使用的 `navigation*` 颜色
- `capsule*` 专用颜色
- 实际未使用的 `calendarCellBackground`

这些字段在固定主题下没有明显错误，但不符合最终“3 表面 + 5 颜色”的外部边界。

### 2.6 当前架构能否容纳运行时主题

**结构上可以，生命周期上不可以。**

有利条件：

- CompositionLocal 已存在。
- `AppColors`、`AppTypography` 是不可变值。
- Font resolver 已抽象。
- MyLibraryTheme 是全局唯一入口。

缺失条件：

- `MyLibraryTheme` 内部固定使用 `DefaultAppColors`。
- 没有 `ResolvedTheme`。
- 没有 ThemeRepository 或 StateFlow。
- 没有启动恢复、故障回退和主题切换。
- 没有表面图片对象。
- 没有主题资源生命周期和缓存代际。

## 3. 硬编码颜色审计

### 3.1 搜索结果

全局扫描了：

```kotlin
Color.White / Color.BLACK / Color.Gray / Color.Transparent
Color(...)
Color(0x...)
copy(alpha = ...)
android.graphics.Color.*
XML #RRGGBB / #AARRGGBB
```

结果：

- 25 个 `Color(0x...)` 全部集中在 `ui/theme/Color.kt`：24 个 `AppColors` 默认字段和 1 个 `AppDanger`。
- 7 处 `copy(alpha = ...)`，分布于 5 个 UI 文件。
- 2 处完全限定的系统栏 `android.graphics.Color.TRANSPARENT`。
- 1 处封面缩略图处理 `Color.WHITE`。
- XML 十六进制颜色 3 处，均为启动图标资源。

### 3.2 分类

| 分类 | 文件/位置 | 当前用途 | 建议 | 是否固定 |
| --- | --- | --- | --- | --- |
| 普通 UI，应主题化 | `ui/theme/Color.kt` | 默认主题所有普通颜色 | 收缩到 3 表面 + 5 颜色 | 否 |
| 普通 UI，应主题化 | `ui/home/CalendarDayVisual.kt` | 日历卡片、边框、跨月弱化 | 卡片归 CARD；边框归 border；文字归 textSecondary；弱化规则由 App 固定但不新增主题字段 | 部分固定 |
| 普通 UI，应主题化 | `ui/settings/FieldManagementScreen.kt`、`TagManagementScreen.kt` | 选中胶囊的 `accent.copy(alpha=0.10f)` | 按最终规则使用 accent/onAccent，或使用 CARD + accent 边框；不要开放额外主题 alpha 参数 | 否 |
| 普通 UI，应主题化 | `ui/navigation/LibraryBottomBar.kt` | 未选中状态用透明 accent 背景 | 未选中直接无背景；选中使用 accent | 否 |
| 安全状态色 | `ui/theme/Color.kt` 的 `AppDanger` | 删除、危险操作 | 继续由 App 固定 | 是 |
| 安全状态色 | `LibraryTextField.kt`、`QuoteDraftSheet.kt` | `MaterialTheme.colorScheme.error` | 继续映射到 App 固定 error | 是 |
| 封面/图片处理色 | `data/image/CoverImageProcessor.kt` | 将透明缩略图铺白底后存 JPEG | 属于图片落盘策略，不应跟主题切换 | 是 |
| 封面/图片处理色 | `ui/components/CoverImage.kt` | 海报预览封面遮罩 | 从主题普通颜色模型移出，归 App 固定图片处理策略 | 是 |
| 导出专用色 | `ui/poster/CoverPosterRenderer.kt` | 背景、文字、边框、封面延展层 | 当前由 `CoverPosterPalette` 注入；延展层 Alpha 86 为导出固定规则 | 布局规则固定，颜色随快照 |
| 系统栏色 | `ui/theme/Theme.kt` | 透明状态栏/导航栏 | 保持透明概念，但迁移到可靠的 edge-to-edge 控制路径 | App 固定行为 |
| 启动/图标色 | `res/values/colors.xml`、`res/drawable/ic_launcher_foreground.xml` | Launcher 图标 | 不属于运行时主题 | 是 |

### 3.3 现有字段到最终边界的映射

| 当前字段 | 最终来源 |
| --- | --- |
| `screenBackground` | `BACKGROUND` |
| `surface`、`cardSurface`、`surfaceSecondary` | `CARD`，差异改为 App 固定结构/边框/状态，不再是主题字段 |
| `dialogSurface` | `DIALOG` |
| `navigationBackground` | `CARD` |
| `calendarCellBackground` | `CARD` |
| `capsuleBackground` | CARD 或透明 + border，按组件固定规则 |
| `textPrimary` | `textPrimary` |
| `textSecondary`、`textTertiary`、`navigationIcon`、`capsuleText` | `textSecondary` |
| `outline`、`divider` | `border` |
| `accent`、`selectedBackground`、`navigationIndicator`、`capsuleSelectedBackground` | `accent` |
| `onAccent`、`selectedText`、`navigationSelectedIcon`、`capsuleSelectedText` | `onAccent` |
| `imageScrim` | App 固定图片处理色，不进入主题普通颜色 |

当前未被实际 UI 读取的字段：

- `navigationBackground`
- `navigationIcon`
- `navigationSelectedIcon`
- `navigationIndicator`
- `calendarCellBackground`

这说明当前语义层曾为未来预留过多字段，Phase 1 应收缩而不是继续增加。

## 4. 三种表面审计

### 4.1 组件库存

| 组件 | 数量 | 结论 |
| --- | ---: | --- |
| `Scaffold` | 0 | 页面主要用 Box/Column，便于控制背景 |
| Material `Card` | 0 | 卡片均用 `Surface` 或普通布局 |
| `Surface` | 43 | 主要主题化工作量 |
| `ModalBottomSheet` | 10 | 当前容器色不统一 |
| 原生 `Dialog` | 7 | 多套自定义内容 |
| `AlertDialog` | 1 | `NameInputDialog` |
| `DropdownMenu` | 0 | 底部菜单主要用 Dialog/Sheet |
| `BasicTextField` | 5 | 已有自定义输入容器基础 |

43 处 `Surface` 当前容器来源大致为：

- `cardSurface`：18
- `surface`：18
- `dialogSurface`：4
- `surfaceSecondary`：3

10 处 `ModalBottomSheet` 当前容器来源为：

- `cardSurface`：5
- `dialogSurface`：2
- `surface`：3

按最终边界，10 处底部弹窗都应归入 DIALOG。

### 4.2 BACKGROUND

已有统一入口：

- `ui/components/AppScreenContainer.kt`

其 modifier 顺序先绘制背景，再应用 `statusBarsPadding()`，因此背景能够延伸到状态栏后方，内容则避开状态栏。这与目标一致。

通过统一入口的主要页面：

- 首页
- 资料库
- 统计与摘录
- 设置主页
- 全屏年历
- 详情页
- 新增/编辑页
- 摘录列表
- 布局设置

仍自行绘制根背景的页面：

- 作品标签编辑页
- 标签管理页
- 字段管理页
- 状态/作品类型的 `FlatManagementScreen`

问题不是当前颜色不一致，而是这些页面未来无法自动共享 BACKGROUND 图片加载、裁剪、缓存和故障回退。

### 4.3 CARD

应归 CARD 的当前对象包括：

- 时间轴卡片：`ui/home/HomeTimeline.kt`
- 空月历日期卡片：`ui/home/CalendarDayVisual.kt`
- 资料库列表卡片：`ui/library/LibraryViews.kt`
- 统计卡片、最近摘录卡片：`ui/statistics/StatisticsScreen.kt`
- 摘录列表卡片：`ui/quote/QuoteListScreen.kt`
- 设置项：`ui/components/SettingsEntry.kt`
- 搜索框：`ui/components/LibrarySearchField.kt`
- 输入区域：`ui/components/LibraryTextField.kt`
- 管理列表行：`ui/settings/FlatManagementList.kt` 等
- 底部导航：`ui/navigation/LibraryBottomBar.kt`
- 封面占位背景：`ui/components/CoverImage.kt`

目前没有一个统一 `AppCardSurface`。相同视觉角色分别读取 `surface`、`cardSurface` 或 `surfaceSecondary`。

搜索框和输入框应归 CARD，不需要成为第 4 种表面。焦点、错误和禁用差异由 App 固定的 border/error/state 规则表达。

底部导航当前是一个带固定 4dp 阴影的圆角 `Surface`，容器色读取 `cardSurface`，最终应读取 CARD。阴影、圆角、高度继续由 App 固定。

### 4.4 DIALOG

当前已正确使用 `dialogSurface` 的代表：

- `AppConfirmDialog`
- `QuoteDraftSheet`
- `RecordDraftSheet`
- 设置页部分自定义 Dialog
- 数据导出部分 Dialog

当前错误归到 CARD/surface 的代表：

- `DatePickerBottomSheet`
- `TagSelectionSheet`
- `DynamicFieldEditorSheets`
- `LibraryTagFilterSheet`
- `ListFieldConfigSheet`
- `FieldManagementScreen` 内的底部弹窗
- `NameInputDialog`
- `TagManagementDialogs` 的 `ManagementDialogSurface` 和动作菜单

所有这些都属于最终 DIALOG。

对话框遮罩仍应由 App 固定，不纳入主题。Material `ModalBottomSheet` 和 `Dialog` 的窗口/遮罩行为可保留，只需把可见容器内容放入统一 DIALOG 表面。

### 4.5 特殊情况

不应机械套 CARD 图片的对象：

- 真实作品封面
- 月历中由封面组成的日期 Artwork
- Canvas 海报中的封面延展背景
- Launcher 图标
- 删除/错误/警告状态
- 透明的点击热区、拖动把手和纯布局 Box

### 4.6 Material 默认白色、紫色、tonal surface 和阴影

当前显式 Surface 大多设置：

```kotlin
shadowElevation = 0.dp
tonalElevation = 0.dp
```

因此没有大面积 Material tonal 叠色。

明确保留非零阴影的主要位置：

- 底部导航和新增按钮，使用 `FloatingNavigationShadowElevation = 4.dp`

潜在默认 tonal 泄漏主要来自：

- `FilterChip`
- 未完整映射的 Material 3 ColorScheme token
- `AlertDialog` 或以后新增但未显式 colors 的 Material 组件

### 4.7 表面图片的结构可行性

当前 `Surface(color = ...)` 只能绘制颜色，不能直接绘制图片。未来若 CARD/DIALOG 为图片，以下结构不能原样支持：

- 43 个直接 `Surface`
- 10 个仅设置 `containerColor` 的 `ModalBottomSheet`
- `NameInputDialog` 的 Material `AlertDialog`
- 直接 `.background(screenBackground)` 的页面根
- `CalendarDayVisual` 对 `cardSurface.copy(alpha=...)` 的颜色级透明处理

建议建立一个统一表面层：

```text
ThemeSurfaceRole = BACKGROUND | CARD | DIALOG
ResolvedSurface = ColorSurface | ImageSurface
AppThemeSurface(role, shape, border, content)
```

形状、尺寸、边框、阴影和点击行为继续由调用方/App 固定；表面层只负责在同一个裁剪范围内绘制已解析的颜色或图片。

### 4.8 图片显示规则建议

不建议一套 `ContentScale` 同时覆盖三个表面。应按表面固定：

| 表面 | 建议固定规则 | 原因 |
| --- | --- | --- |
| BACKGROUND | `Crop` + Center | 适配不同屏幕比例，背景必须铺满 |
| CARD | `Crop` + Center | 卡片比例非常多，不能拉伸；主题制作端需提供中心安全区 |
| DIALOG | `Crop` + Center | Dialog/Sheet 尺寸变化大，避免变形 |

CARD 图片会同时用于窄时间轴卡片、方形统计卡片、横向设置项和底部导航。主题 Schema 必须明确：CARD 图片应是可裁剪纹理/装饰背景，重要图案必须位于中心安全区。否则单张图片无法在所有比例上保持完整构图。

## 5. 字体与文字角色审计

### 5.1 当前基础

`ui/theme/Type.kt` 已经建立：

```kotlin
enum class FontSlot { A, B }

enum class FontRole {
    BRAND,
    HEADING,
    CONTENT,
    META
}
```

默认映射：

- BRAND → A
- HEADING → A
- CONTENT → B
- META → B

`AppFontResolver` 同时提供：

- Compose `FontFamily`
- Android `Typeface`

这正是 Compose、Canvas 和未来 PDF 共享主题字体配置所需的核心抽象。

### 5.2 字体角色候选表

当前有 16 套固定视觉 TextStyle，但字体家族角色已经合并为 4 个：

| 候选角色 | 当前用途 | 当前样式/组件 | 当前字号与字重 | 合并建议 |
| --- | --- | --- | --- | --- |
| BRAND | 首页 App 名称/品牌标题 | `appName` | 30sp SemiBold | 暂保留；Phase 2 再判断是否并入 HEADING |
| HEADING | 页面标题、分区标题、卡片标题、作品标题、月历标题、时间轴月份 | `pageTitle`、`sectionTitle`、`cardTitle`、`itemTitle`、`calendarMonth`、`timelineMonth` | 13–20sp Medium/SemiBold | 保留为一个字体角色，字号仍是 App 固定样式 |
| CONTENT | 正文、作者/导演、输入文字、按钮 | `creator`、`body`、`input`、`button` | 14–15sp Normal/Medium | 保留；输入显示和占位可共用字体角色 |
| META | 日期、说明、星期、日号、时间轴日期、胶囊与标签 | `metadata`、`calendarWeekday`、`calendarDay`、`timelineDay`、`capsule` | 11–13sp Normal/Medium | 保留；不按页面拆分 |

结论：

- 当前候选字体家族角色数量：4
- 当前视觉文字样式数量：16
- 推荐合并后的字体家族角色数量：4
- Phase 2 唯一可能继续合并的是 BRAND → HEADING；现在不应提前删除

### 5.3 文字样式使用情况

使用最广的样式：

- `body`：约 52 处
- `metadata`：约 41 处
- `button`：约 23 处
- `sectionTitle`：约 14 处
- `pageTitle`：约 11 处
- `input`：约 9 处

整体说明命名样式已经覆盖绝大多数真实文字，不需要重新发明大量字体角色。

### 5.4 仍未归入显式文字样式的 10 处

| 文件 | 用途 | 建议角色/样式 |
| --- | --- | --- |
| `ui/components/NameInputDialog.kt` | 输入标签 | CONTENT / `input` |
| `ui/item/DynamicFieldEditorSheets.kt` | 动态字段输入标签 | CONTENT / `input` |
| `ui/item/ItemForm.kt` | 标题输入标签 | CONTENT / `input` |
| `ui/item/ItemForm.kt` | 作者/导演输入标签 | CONTENT / `input` |
| `ui/item/ItemForm.kt` | 动态字段占位 | CONTENT / `input` |
| `ui/item/ItemTagEditorScreen.kt` | 错误提示 | META 或 BODY；颜色仍用固定 error 或 textSecondary，需按语义决定 |
| `ui/item/QuoteDraftSheet.kt` | 摘录内容占位 | CONTENT / `input` |
| `ui/item/QuoteDraftSheet.kt` | 页码占位 | CONTENT / `input` |
| `ui/item/RecordDraftSheet.kt` | 评价占位 | CONTENT / `input` |
| `ui/record/RecordStatusRow.kt` | FilterChip 标签 | META / `capsule` |

### 5.5 Material Typography 的旁路

`toMaterialTypography()` 目前只映射了 7 个 Material Typography slot：

- `displaySmall`
- `headlineSmall`
- `titleMedium`
- `bodyLarge`
- `bodyMedium`
- `bodySmall`
- `labelMedium`

Material 组件仍可能读取未映射的：

- `labelLarge`
- `labelSmall`
- `titleSmall`
- `headline*`
- 其他默认 slot

因此即使页面自己的 Text 都显式指定样式，Material 内部标签仍可能回到系统默认 FontFamily。

Phase 1 应：

1. 给全部会被当前 Material 组件使用的 Typography slot 显式绑定到 4 个字体角色。
2. 让 `LibraryTextField` 为 label/placeholder 提供 `LocalTextStyle`，而不仅是 `LocalContentColor`。
3. 给 10 处散落 Text 添加显式样式。
4. 对 `FilterChip` 使用自定义 colors 和文字样式，或替换为现有 `AppCapsule`。

### 5.6 字体回退现状

当前没有任何 TTF 文件，`SystemAppFontResolver` 始终返回：

- `FontFamily.Default`
- `Typeface.DEFAULT`

尚未实现：

- B 缺失回退 A
- A/B 均缺失回退系统
- 缺字回退系统字体
- 文件格式和字体真实性校验
- 同一主题内 Typeface/FontFamily 缓存

这些属于 Phase 3，不应在本轮或 Phase 1 演示实现。

## 6. 底部导航审计

### 6.1 当前结构

导航目的地：

- `ui/navigation/AppDestination.kt`

图标槽位和默认资源：

- `ui/navigation/NavigationIconResources.kt`

绘制：

- `ui/navigation/LibraryBottomBar.kt`

`AppDestination` 保存：

- route
- label
- `NavigationIconSlot`

而不是直接保存 `ImageVector`。因此：

**当前已经可以在不改 route 的情况下替换内置图标。**

### 6.2 双状态模型

当前 `NavigationIconResource` 已有：

```kotlin
normal: ImageVector
selected: ImageVector?
```

4 个默认导航项都提供 Outline + Filled 双状态。选中图标缺失时，代码也已经回退 `normal`。

这与最终规则高度一致。

### 6.3 当前耦合点

仍存在的耦合：

- `LibraryBottomBar` 直接调用 `DefaultNavigationIcons.resolve(...)`。
- `NavigationIconResource` 类型只能持有 `ImageVector`。
- 没有运行时 icon resolver。

未来最小抽象应是：

```text
NavigationIconSlot
NavigationIconPair(normal, selected?)
ResolvedNavigationIcon = BuiltInVector | InstalledBitmap
NavigationIconResolver.resolve(slot)
```

`AppDestination`、route、页面导航逻辑和 `MainPagerScreen` 不需要修改。

### 6.4 颜色、反馈与布局

当前规则：

- 导航背景：`cardSurface`
- 选中背景：`accent`
- 选中图标：`onAccent`
- 未选中图标：`textSecondary`
- 点击使用 `noRippleClickable`
- 选中项禁用重复点击
- 图标尺寸、高度、圆角和阴影全部由 App 固定

与目标边界一致。

需要决定但不应开放给主题：

- 自定义位图的缩放规则
- 是否对单色外部图片施加 tint

建议：

- 外部 PNG/WebP 默认按原色绘制，不强制 tint。
- 没有 selected 图片时，继续显示 normal 图片，并由 accent 选中背景表达状态。
- 内置 `ImageVector` 继续使用 tint。

## 7. 系统栏审计

### 7.1 当前控制路径

1. `MainActivity` 调用 `enableEdgeToEdge()`。
2. `MyLibraryTheme` 的 `SideEffect`：
   - 将状态栏设为透明
   - 将系统导航栏设为透明
   - 强制状态栏使用深色图标
   - 强制导航栏使用深色图标
3. `AppScreenContainer` 的背景先铺满，再对内容应用 `statusBarsPadding()`。
4. 底部导航和底部弹窗分别使用 `navigationBarsPadding()`。

### 7.2 当前可靠性

优点：

- edge-to-edge 已启用。
- 状态栏后方能显示页面背景。
- 主要页面顶部安全区路径统一。
- 底部悬浮导航避开系统导航栏。

问题：

- `darkSystemBarIcons` 被硬编码为 `true`。
- 深色主题背景会出现“深色背景 + 深色系统图标”。
- `themes.xml` 仍把 `android:navigationBarColor` 设为白色，Compose SideEffect 运行前可能出现启动阶段颜色不一致。
- Kotlin 编译对直接写 `window.statusBarColor` 和 `window.navigationBarColor` 报出 deprecated 警告。
- 4 个页面根仍自行处理状态栏 inset，未来容易与统一背景逻辑分叉。

Material Theme 本身没有在页面中反复修改系统栏，但 `enableEdgeToEdge()` 与 Theme SideEffect 的调用顺序仍应由单一入口管理。

### 7.3 支持 `darkSystemBarIcons` 的改造点

需要修改：

- `ui/theme/Theme.kt`
- 未来的 `ResolvedTheme`
- `res/values/themes.xml`
- 直接绘制根背景的 4 类页面

建议路径：

```text
ResolvedTheme.darkSystemBarIcons
  → MyLibraryTheme
  → 单一 SideEffect / edge-to-edge system bar controller
```

状态栏和系统导航栏图标明暗应一起使用该字段，除非后续明确需要拆分；Manifest v1 当前只应有一个字段。

## 8. 海报和 PDF 字体审计

### 8.1 当前实现

当前只有“资料库筛选结果封面网格海报”，没有 PDF 实现。

真实文件：

- `ui/library/LibraryScreen.kt`
- `ui/poster/CoverPosterExporter.kt`
- `ui/poster/CoverPosterRenderer.kt`
- `ui/poster/CoverPosterLayout.kt`
- `export/README.md`

`export/README.md` 只声明后续预留，当前没有文字报告或 PDF writer。

### 8.2 当前海报字体

`LibraryScreen` 从 `AppTheme.fontResolver` 获取：

- `FontRole.HEADING` → `headingTypeface`
- `FontRole.CONTENT` → `contentTypeface`

然后连同颜色一起构造不可变 `CoverPosterPalette`，传给 IO 协程和 Canvas renderer。

当前默认 resolver 最终是 `Typeface.DEFAULT`，但 renderer 本身没有直接写死 `Typeface.DEFAULT`。

结论：

- 当前没有多套互相独立的字体选择逻辑。
- `AppFontResolver` 已经是正确的统一入口。
- Canvas 海报已经接入该入口。
- 未来 PDF 应复用同一个 resolver/config，不应再增加 PDF 专属字体选择器。

### 8.3 当前快照行为

当前 palette 是不可变数据，点击导出时被协程捕获，因此同一次现有海报导出不会在渲染中途重新读取 Compose Theme。

仍需改进：

- `CoverPosterPalette` 在 `LibraryScreen` 重组时创建。
- 没有明确的 `ExportFontSnapshot` / `ExportThemeSnapshot` 类型。
- 没有跨 PNG/PDF 的统一 export request 入口。

Phase 1 应保留 `AppFontResolver` 名称，并把“从当前 Theme 生成不可变导出快照”的职责抽离出页面。Phase 3 再让快照持有动态字体配置。

### 8.4 Compose 与 Android 如何共享

共享的不是 TextStyle，而是：

```text
ThemeFontConfig
  ├── slot A file / missing
  ├── slot B file / missing
  └── role → A/B mapping

AppFontResolver
  ├── composeFontFamily(role)
  └── androidTypeface(role)
```

Compose 保留当前 16 套固定字号/字重/行高；Canvas/PDF 保留各自固定的字号和布局。两者只共享角色到字体家族的解析结果。

### 8.5 中文 TTF 风险

- 大型 CJK TTF 会增加安装主题后的磁盘占用、首次 Typeface 创建延迟和进程内字体缓存压力。
- Canvas 海报输出为 JPEG，字体不会直接增加 JPEG 文件体积，但会影响首次渲染耗时。
- PDF 文件大小取决于未来 PDF writer 是否嵌入整字体、子集化字体或转为路径；不能在选择 PDF 技术前假设文件一定很小。
- 每个导出任务不应重复读取 TTF 或构建 Typeface。
- 需要用包含中文、拉丁、数字、标点和缺字回退的真实字体做基准测试。

### 8.6 无需改动的导出代码

以下逻辑与主题字体无关，应保持：

- `CoverPosterLayout.kt` 的网格、画布尺寸和裁切计算
- `CoverPosterRenderer.kt` 的固定文字大小比例
- `ellipsize()` 和 `Paint.measureText()` 的布局职责
- 封面解码、中心适配、背景延展逻辑
- JPEG 质量、分享 URI 和 Intent

主题接入只改变 palette/snapshot 的来源。

## 9. 图片与字体性能风险

### 9.1 可复用的现有经验

`ui/components/CoverImage.kt` 已实现：

- `LruCache`
- 按请求边长降采样
- 相同请求合并
- 最多 2 个并发解码
- IO scope
- 不在 Composable 主线程解码

主题表面图片可以借鉴此生命周期，但不应直接塞入封面缓存，因为：

- 封面缓存 key 是作品路径和尺寸。
- 主题缓存 key 应是 themeId、theme generation、surface role 和 decode bucket。
- 主题切换需要独立失效。

### 9.2 建议的对象生命周期

| 对象 | 生命周期/缓存建议 |
| --- | --- |
| 默认主题 | 编译进 App 的永久单例，永不依赖外部文件 |
| ThemeManifest | 每个已安装主题解析一次；安装后保存规范化副本 |
| ResolvedTheme | 当前主题一份不可变实例，由 StateFlow 持有 |
| Font A/B Typeface | 每主题、每 slot 各一份缓存，不按角色重复构建 |
| Compose FontFamily | 每主题、每 slot 各一份缓存 |
| BACKGROUND bitmap | 当前主题一份或按屏幕尺寸 bucket 一份 |
| CARD bitmap | 当前主题共享一份或少量尺寸 bucket；所有卡片重复绘制同一实例 |
| DIALOG bitmap | 当前主题共享一份或少量尺寸 bucket |
| 导航图片 | 4–8 张小图，当前主题生命周期缓存 |
| ExportFontSnapshot | 每个导出任务创建一次，不随 Compose 重组变化 |

### 9.3 不能放在 Composable 中的工作

- 读取 `.mylibrarytheme`
- PBKDF2
- AES-GCM 解密
- ZIP 解包
- Manifest 校验
- TTF 文件校验和 Typeface 构建
- 大图 bounds 读取、降采样和解码
- 安装目录原子替换
- 备份/恢复主题文件

Composable 只能读取已经解析好的 `ResolvedTheme` 和已缓存资源句柄。

### 9.4 表面图片内存控制

建议：

1. 导入时限制像素尺寸、文件大小、格式和解码后内存上限。
2. 运行时先读 bounds，再按目标 bucket 降采样。
3. CARD 图片不允许每张卡片各自解码。
4. 同一个 ImageBitmap/Painter 可在多个裁剪容器重复绘制。
5. 主题缓存带 generation；切换主题后旧 generation 不再接受新请求。
6. 导出任务持有自己的不可变字体快照，避免主题切换影响进行中的文件。
7. 收到低内存信号时可清主题位图缓存，但保留 Manifest 和字体配置；下次异步重载。

### 9.5 启动与故障回退

安全启动顺序：

```text
立即提供编译内置 DefaultTheme
  → 读取 DataStore.currentThemeId
  → 验证安装目录和规范化 Manifest
  → 异步解析字体/图片
  → 全部必要资源成功后原子发布 ResolvedTheme
  → 任一步失败则继续使用 DefaultTheme
```

不得在启动时等待主题图片解码后才显示首屏。

如果当前主题文件被删除、损坏或 Schema 不兼容：

- 不崩溃。
- 不返回半套主题。
- 当前进程继续使用 DefaultTheme。
- 清除或修正失效的 currentThemeId。
- 保留失败原因供日志/用户提示，不自动破坏用户数据库。

## 10. 存储、备份与恢复审计

### 10.1 当前存储

用户设置：

- `data/preferences/PreferencesDataStore.kt`
- `data/repository/UserPreferencesRepository.kt`
- DataStore 名称：`my_library_preferences`

作品图片：

```text
filesDir/images/original/
filesDir/images/thumbnail/
```

数据库：

- Room Schema 10
- 图片仅存相对路径

### 10.2 当前主题 ID 应放在哪里

放在现有 Preferences DataStore：

```text
current_theme_id
```

不应进入 Room。主题选择是 App 外观偏好，不是资料库业务实体。

### 10.3 已安装主题资源目录

建议：

```text
filesDir/themes/<theme-id>/
├── installed-manifest.json
├── surfaces/
│   ├── background.png|webp
│   ├── card.png|webp
│   └── dialog.png|webp
├── fonts/
│   ├── font_a.ttf
│   └── font_b.ttf
├── navigation/
└── source.mylibrarytheme
```

资源不进入 Room，不转成 BLOB。

安装应先写：

```text
filesDir/themes/.staging/<uuid>/
```

全部验证后再原子移动/替换到正式目录。

### 10.4 当前自动备份

`res/xml/backup_rules.xml` 和 `data_extraction_rules.xml` 当前只包含：

- database
- `files/images/`
- `files/datastore/`

没有 `files/themes/`。

### 10.5 当前 App ZIP 备份

当前 Backup Schema v1 包含：

- `manifest.json`
- `data.json`
- `preferences.json`
- `covers/original/*`

不包含：

- 已安装主题
- 当前主题 ID
- 主题字体/图片
- 原始 `.mylibrarytheme`

当前备份已有较成熟的：

- SHA-256 文件摘要
- 路径标准化
- ZIP Slip 防护
- 条目数/单文件/总大小限制
- 文件真实性检查
- 临时目录
- 数据库、图片、Preferences 回滚

这些模式可复用于主题导入，但主题包应使用独立 validator 和独立限额，不能把数据备份 validator 直接扩成万能导入器。

### 10.6 完整备份策略

如果产品把现有 ZIP 称为“完整备份”，建议 Backup Schema v2 包含当前已安装主题，至少包含：

```text
theme/active.mylibrarytheme
preferences.currentThemeId
```

推荐保留原始加密主题文件，原因：

- 备份中继续保持主题内容不可直接解压查看。
- 恢复时可重新走同一套认证解密和校验。
- 避免备份一套解密后的散文件和另一套安装格式。
- 便于未来 Schema 迁移和重新安装。

安装目录仍需要解密后的私有资源供运行时使用；原始包和安装资源职责不同。

恢复规则：

1. 数据库和业务数据恢复不能依赖主题成功。
2. 主题恢复失败时，业务数据仍可恢复。
3. 主题验证成功后再安装并设置 currentThemeId。
4. 主题缺失、损坏或不兼容时回退 DefaultTheme。
5. 备份恢复不得把不存在的 themeId 写入 DataStore。

Backup Schema 升级不需要 Room Migration，但需要：

- `BackupPreferences` 扩展
- `BackupJsonCodec` 兼容旧字段缺失
- `BackupArchiveValidator` 允许并校验主题条目
- 导出/导入服务扩展主题快照和回滚
- 新增测试

## 11. 与目标主题系统的差距

| 目标能力 | 当前状态 | 成熟度 |
| --- | --- | --- |
| 全局 Theme 单入口 | 已有 | 接近可用 |
| 三表面颜色 | 已有近似字段 | 部分可用 |
| 三表面图片 | 无统一绘制/加载 | 无 |
| 五种普通颜色 | 已有语义色，但扩展到 24 字段 | 部分可用 |
| App 固定 shapes/dimens | 已有 | 可用 |
| 字体 A/B 模型 | 已有 `FontSlot` | 部分可用 |
| 真实字体角色 | 已有 4 个 | 接近可用，未冻结 |
| Compose/Canvas 统一字体入口 | 已有 `AppFontResolver` | 部分可用 |
| TTF 动态加载与缺字回退 | 无 | 无 |
| 海报字体快照 | 现有 palette 具备近似行为 | 部分可用 |
| PDF 主题字体 | PDF 尚未实现 | 无可审计实现 |
| 导航目的地/图标解耦 | 已有 icon slot | 接近可用 |
| 外部 PNG/WebP 导航图标 | 无 | 无 |
| `darkSystemBarIcons` | 强制 true | 无运行时支持 |
| ResolvedTheme / 切换 | 无 | 无 |
| 当前主题 DataStore | 无 | 无 |
| 主题图片/字体私有目录 | 无 | 无 |
| 认证加密主题包 | 无 | 无 |
| 原子安装/删除/默认回退 | 无 | 无 |
| 主题备份恢复 | 无 | 无 |

## 12. P0 / P1 / P2 / P3 分级

### P0：当前 UI 重构期必须解决

1. **收缩普通颜色语义**
   - 将 24 个 `AppColors` 字段收口到 5 个普通色和 3 个表面。
   - `AppDanger`、图片遮罩、错误色继续由 App 固定。
   - 清除未使用的 navigation/calendar 颜色字段。

2. **建立默认主题表面骨架**
   - BACKGROUND、CARD、DIALOG 三角色。
   - 先只使用内置默认颜色，不加载外部图片。
   - 建立一个可承载颜色或已解析图片的统一表面接口。

3. **统一页面根背景**
   - 所有页面经过 `AppScreenContainer` 或同一底层实现。
   - 消除 4 类页面的直接根 `.background(screenBackground)`。

4. **统一 CARD 与 DIALOG 调用点**
   - 43 个 `Surface` 按角色归类。
   - 10 个底部弹窗全部归 DIALOG。
   - `NameInputDialog`、Tag action menu、Management dialog 统一归 DIALOG。
   - 搜索、输入、设置项、底部导航统一归 CARD。

5. **封堵 Material 默认 token**
   - 完整映射当前会使用到的 Material ColorScheme/Typography token。
   - 修复 `RecordStatusRow` 默认 FilterChip。
   - 不允许新增 Material 组件依赖未审计默认颜色。

6. **补全字体样式**
   - 修复 10 个没有显式 style 的 Text。
   - 输入 label/placeholder 由 `LibraryTextField` 提供明确 LocalTextStyle。
   - 保留现有 4 个字体角色，不在 UI 未稳定时冻结 Manifest。

7. **导航资源读取解耦**
   - `LibraryBottomBar` 不再直接访问 `DefaultNavigationIcons`。
   - 先注入只返回内置 ImageVector 的 resolver。
   - 外部位图仍不实现。

8. **正式化导出字体快照入口**
   - 保留 `AppFontResolver`。
   - 将 palette/snapshot 构造从 `LibraryScreen` 抽到主题/导出桥接层。
   - 不修改海报布局。

### P1：主要页面稳定后、冻结 Schema 前解决

1. 最终确认 BRAND 是否独立，冻结 3 或 4 个字体角色。
2. 冻结三表面图片的 ContentScale、对齐、中心安全区和 Alpha 表达。
3. 冻结五种颜色的 Alpha 编码和回退规则。
4. 冻结 `darkSystemBarIcons`。
5. 冻结导航图片 normal/selected 缺失规则、像素尺寸、格式和 tint 规则。
6. 定义 `ResolvedTheme`、ThemeManifest v1 和版本兼容规则。
7. 冻结 TTF 限制、字体映射和缺字回退验收。
8. 冻结主题包明文 header、KDF 参数字段、nonce/salt 格式。
9. 设计 Backup Schema v2 的主题条目。

### P2：实现主题导入系统时解决

1. TTF 文件校验、FontFamily/Typeface 创建和缓存。
2. BACKGROUND/CARD/DIALOG 图片解码、降采样和缓存。
3. PNG/WebP 导航图片解析。
4. ThemeRepository、StateFlow、DataStore currentThemeId。
5. 启动恢复、故障回退、删除和恢复默认。
6. PBKDF2-HMAC-SHA-256。
7. AES-256-GCM 认证解密。
8. 安全解包、文件真实性校验、限额和路径校验。
9. staging 目录和原子安装。
10. 备份/恢复与主题安装事务协调。

需要在 Phase 4 前明确：PBKDF2 的“密码/秘密”来自哪里。若没有用户输入密码，则网页生成器和 App 必须共享协议秘密；它只能满足“不能直接改 zip 查看”和一般完整性要求，不能提供 DRM 级安全。该限制符合当前产品边界，但必须写入协议说明。

### P3：主题制作网页阶段解决

1. 三表面图片或颜色编辑。
2. 五种普通颜色编辑与 Alpha。
3. 字体 A/B 上传、格式/大小校验。
4. 字体角色 A/B 映射。
5. 4 个导航槽位的 normal/selected 图片。
6. Manifest v1 生成。
7. PBKDF2 + AES-GCM 导出。
8. 编辑已有主题。

不建设预览商店、社区、账号、推荐、同步、插件或页面模板。

## 13. 分阶段实施路线

### Phase 0：当前项目审计结论

- **目标**：确认真实基线、差距和实施顺序。
- **涉及模块**：全项目 303 个非构建文件，重点 12 个模块组。
- **修改文件类别**：仅本审计报告。
- **前置依赖**：其余共享工作区任务全部结束。
- **验收标准**：文件级结论、P0–P3、Phase 1–5、构建/lint 结果齐全。
- **主要风险**：UI 继续变化会使计数和少量文件地图过时。
- **数据库迁移**：否。
- **用户数据影响**：否。

### Phase 1：默认主题内部骨架

- **目标**：
  - 建立 3 表面、5 普通颜色、4 个暂定字体角色的单一内部模型。
  - 所有现有 UI 在默认主题下保持视觉和交互不变。
  - 为未来图片表面和运行时值预留唯一入口，但不读取外部文件。
- **涉及模块**：
  - `ui/theme`
  - `ui/components`
  - 所有 UI 页面中的 Surface/Dialog/Sheet
  - `ui/navigation`
  - `ui/poster` 与 `LibraryScreen`
- **预计修改文件类别**：
  - Theme 数据类
  - 通用表面组件
  - 页面容器
  - 卡片/输入/弹窗调用点
  - 导航 resolver
  - 导出 snapshot 工厂
  - Compose 测试与静态规则测试
- **前置依赖**：
  - 当前主要 UI 结构可继续重构，但每新增组件必须使用新骨架。
- **验收标准**：
  - 页面外没有普通 UI 颜色字面量。
  - `AppColors` 外部可配置边界只有 5 色。
  - 所有普通根页面从 BACKGROUND 读取。
  - 所有普通内容容器从 CARD 读取。
  - 所有 Dialog/Sheet 从 DIALOG 读取。
  - 176 个 Text 调用全部具有明确字体角色或由已审计组件提供 LocalTextStyle。
  - `FilterChip` 等不再泄漏 Material 默认 tonal 色。
  - 导航 route 不变、图标行为不变。
  - 海报布局和输出尺寸不变。
  - compile、unit test、androidTest 编译、lint 通过。
- **主要风险**：
  - 大范围视觉回归。
  - 将图片封面/安全色误归普通主题表面。
  - 为了追求统一而改变现有间距、圆角或交互。
- **数据库迁移**：否。
- **用户数据影响**：否。

### Phase 2：Schema 冻结

- **目标**：
  - 在页面稳定后冻结 ThemeManifest v1 和资源约束。
- **涉及模块**：
  - `ui/theme` 模型
  - 导航资源模型
  - 导出字体快照契约
  - 主题协议文档和 validator 测试样例
- **预计修改文件类别**：
  - Manifest 数据模型
  - Schema 文档
  - 兼容矩阵
  - 资源限制常量
  - 纯 JVM 验证测试
- **前置依赖**：
  - Phase 1 完成。
  - 首页、资料库、统计、设置、详情、编辑和主要弹窗视觉稳定。
- **验收标准**：
  - 3 表面和 5 色字段不再变化。
  - 字体角色数量冻结。
  - PNG/WebP/TTF 的数量、尺寸、大小和回退明确。
  - `darkSystemBarIcons`、导航缺失规则、默认主题回退明确。
  - 未知字段、旧版本、新版本的处理规则明确。
- **主要风险**：
  - Schema 过早冻结导致以后兼容负担。
  - 单张 CARD 图片无法满足所有卡片比例。
  - KDF 秘密来源未决定。
- **数据库迁移**：否。
- **用户数据影响**：否。

### Phase 3：运行时主题

- **目标**：
  - 从已安装且已验证的私有目录生成 `ResolvedTheme`。
  - 支持字体、表面图片、导航图片、切换和默认回退。
  - 让 Compose 与导出共享同一字体配置。
- **涉及模块**：
  - 新的 theme data/repository/cache 层
  - `MyLibraryApplication` / `AppContainer`
  - `MyLibraryTheme`
  - DataStore
  - UI 表面组件
  - 导出 snapshot
- **预计修改文件类别**：
  - ThemeRepository
  - ResolvedTheme
  - Font/Bitmap resolver 与 cache
  - DataStore key
  - 应用启动状态
  - 生命周期和故障测试
- **前置依赖**：
  - Manifest v1 已冻结。
  - 安装目录结构已定义。
- **验收标准**：
  - 默认主题同步可用。
  - 安装主题加载失败不会阻塞启动。
  - 同一表面资源不会按卡片重复解码。
  - 主题切换会原子更新 Compose。
  - 进行中的导出不受切换影响。
  - 字体 B/A/system 和缺字回退通过测试。
- **主要风险**：
  - CJK 字体内存和首次加载时延。
  - 位图缓存生命周期错误。
  - 旧主题资源在切换时被过早释放。
- **数据库迁移**：否；只新增 DataStore 偏好。
- **用户数据影响**：低；故障只应影响外观偏好，不能影响 Room。

### Phase 4：主题文件导入

- **目标**：
  - 安全读取 `.mylibrarytheme`，认证解密、校验、原子安装并直接启用。
  - 支持删除主题、恢复默认和备份恢复。
- **涉及模块**：
  - theme package/crypto/validation/install
  - 文件选择入口
  - AppContainer
  - DataStore
  - Backup Schema v2
  - 自动备份规则
- **预计修改文件类别**：
  - 二进制容器 reader
  - PBKDF2/AES-GCM
  - 安全 ZIP validator
  - 资源 magic 校验
  - installer/staging
  - 备份 codec、validator、import/export
  - UI 的最小导入/删除/默认入口
- **前置依赖**：
  - Phase 2 Schema 和容器协议冻结。
  - Phase 3 runtime loader 稳定。
- **验收标准**：
  - 任意比特修改导致 GCM 认证失败。
  - 损坏、超限、路径穿越、重复条目、伪扩展名全部拒绝。
  - 改名 `.zip` 无法直接浏览内容。
  - 安装失败不留下半安装目录。
  - 删除当前主题时先切回默认。
  - 恢复备份时主题失败不阻止业务数据恢复。
- **主要风险**：
  - 密钥管理误解。
  - 压缩炸弹、路径穿越和大文件 DoS。
  - 文件安装与偏好写入非原子。
  - 备份恢复回滚不完整。
- **数据库迁移**：否；Backup Schema 需要升级，但 Room 不需要。
- **用户数据影响**：中；只在备份/恢复事务处理错误时可能间接影响数据，必须以业务数据安全优先。

### Phase 5：本地主题制作网页

- **目标**：
  - 在浏览器本地生成 Manifest v1 和加密 `.mylibrarytheme`。
- **涉及模块**：
  - 独立 Web 项目，不修改 Android 业务层。
- **预计修改文件类别**：
  - Web UI
  - 资源检查器
  - Manifest builder
  - WebCrypto PBKDF2/AES-GCM
  - 导入已有主题的编辑器
- **前置依赖**：
  - Android 端 Manifest/容器测试向量冻结。
- **验收标准**：
  - Web 生成文件可被 Android 导入。
  - Android 拒绝的文件，Web 在导出前给出同等限制。
  - 使用共享的固定测试向量验证 KDF、nonce、AAD 和 GCM tag。
- **主要风险**：
  - Web 与 Android 协议实现漂移。
  - 浏览器内大字体/图片内存压力。
- **数据库迁移**：否。
- **用户数据影响**：否。

## 14. 文件级改造地图

| 当前文件或目录 | 当前职责 | 发现的问题 | 后续动作 | 阶段 |
| --- | --- | --- | --- | --- |
| `app/src/main/java/com/example/mylibrary/MainActivity.kt` | Compose/edge-to-edge 入口 | Theme 无运行时状态来源 | 保留单入口；未来注入 ThemeRepository 状态 | Phase 3 |
| `app/src/main/java/com/example/mylibrary/MyLibraryApplication.kt` | Application 与容器创建 | 没有主题生命周期对象 | 默认主题继续立即可用；未来创建 ThemeRepository | Phase 3 |
| `app/src/main/java/com/example/mylibrary/ui/theme/Color.kt` | 24 个颜色字段 | 超过最终 5 色 + 3 表面，存在未使用字段 | 收缩、建立固定安全/图片色分区 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/theme/Theme.kt` | CompositionLocal、Material scheme、系统栏 | 默认主题写死，Material token 不完整，系统栏图标强制深色 | 默认骨架、完整 token 映射；后续读取 ResolvedTheme | Phase 1/3 |
| `app/src/main/java/com/example/mylibrary/ui/theme/Type.kt` | 4 字体角色、16 TextStyle、Font resolver | 基础正确；缺动态加载/缺字回退；Material slot 映射不全 | 保留 resolver；补全 Material Typography；Phase 2 冻结角色 | Phase 1/2/3 |
| `app/src/main/java/com/example/mylibrary/ui/theme/Shapes.kt` | 固定形状 | 无主题化问题 | 保留，不开放给主题 | 保留 |
| `app/src/main/java/com/example/mylibrary/ui/theme/Dimens.kt` | 固定尺寸 | 无主题化问题 | 保留，不开放给主题 | 保留 |
| `app/src/main/java/com/example/mylibrary/ui/components/AppScreenContainer.kt` | 全局背景与状态栏 inset | 目前只支持颜色 | 作为 BACKGROUND 图片唯一入口扩展 | Phase 1/3 |
| `app/src/main/java/com/example/mylibrary/ui/components/MainPageLayout.kt` | 主页面结构 | 依赖 AppScreenContainer，结构良好 | 保留 | 保留 |
| `app/src/main/java/com/example/mylibrary/ui/components/ScreenContainer.kt` | 滚动二级页面 | 依赖 AppScreenContainer，结构良好 | 保留 | 保留 |
| `app/src/main/java/com/example/mylibrary/ui/components/LibrarySearchField.kt` | 搜索输入容器 | 使用 `surface`，未来无图片入口 | 归 CARD 统一表面 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/components/LibraryTextField.kt` | 自定义输入 | label 只有 LocalContentColor，没有 LocalTextStyle | 归 CARD；统一输入/占位字体角色 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/components/AppConfirmDialog.kt` | 确认弹窗 | 已用 dialogSurface，基础最好 | 作为 DIALOG 标准参考，接统一表面 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/components/NameInputDialog.kt` | 输入 AlertDialog | 容器用 surface；label 无 style | 归 DIALOG；补字体样式 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/components/DatePickerBottomSheet.kt` | 日期选择 BottomSheet | 用 cardSurface，不符合最终 DIALOG | 接统一 DIALOG 表面 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/components/TagSelectionSheet.kt` | 标签选择 BottomSheet | 用 surface | 接统一 DIALOG 表面 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/components/SettingsEntry.kt` | 设置卡片 | 用 surface | 归 CARD | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/item/ItemTagEditorScreen.kt` | 标签编辑页 | 自行绘制根背景；Checkbox 默认色；错误 Text 无 style | 统一 BACKGROUND；审计 Checkbox；补样式 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/item/DynamicFieldEditorSheets.kt` | 动态字段 BottomSheet | 用 cardSurface；label 无 style | 归 DIALOG；补输入样式 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/item/QuoteDraftSheet.kt` | 摘录编辑 Sheet | 已用 dialogSurface；2 处占位无 style | 保留 DIALOG；补样式 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/item/RecordDraftSheet.kt` | 记录编辑 Sheet | 已用 dialogSurface；1 处占位无 style | 保留 DIALOG；补样式 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/item/ItemForm.kt` | 编辑表单 | 3 处输入 label/placeholder 无 style；职责较多 | 不因行数机械拆分；先只接统一样式/表面 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/home/CalendarDayVisual.kt` | 月历日期卡片 | 对 color 做状态 alpha，图片表面不能直接复用 | CARD 表面与状态透明规则分离 | Phase 1/2 |
| `app/src/main/java/com/example/mylibrary/ui/home/HomeTimeline.kt` | 时间轴卡片 | 已用 cardSurface；局部 FontWeight override | 接统一 CARD；保留固定字重规则 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/library/LibraryViews.kt` | 资料库多布局 | 列表卡片直接 Surface；纯封面模式是特殊情况 | 列表归 CARD；纯封面模式保持特殊 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/library/LibraryTagFilterSheet.kt` | 标签筛选 Sheet | 用 surface | 归 DIALOG | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/library/ListFieldConfigSheet.kt` | 列表字段 Sheet | 用 surface | 归 DIALOG | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/settings/FlatManagementList.kt` | 状态/类型管理通用列表 | 自行绘制根背景，行用 cardSurface | 统一 BACKGROUND/CARD | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/settings/TagManagementScreen.kt` | 标签管理 | 巨型 UI 文件；直接背景；多种表面 | 先替换统一表面；仅按职责复用需要再拆 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/settings/TagManagementDialogs.kt` | 标签管理对话框 | Dialog 容器多用 surface | 全部归 DIALOG；复用标准弹窗表面 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/settings/FieldManagementScreen.kt` | 字段管理、选项编辑、BottomSheet、拖拽 | 1261 行且混合多类表面；BottomSheet 用 cardSurface | 先统一角色；后续只按可复用交互职责拆分 | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/navigation/AppDestination.kt` | route、label、icon slot | 结构正确 | 保留，不绑定外部资源 | 保留 |
| `app/src/main/java/com/example/mylibrary/ui/navigation/NavigationIconResources.kt` | 内置双状态 ImageVector | 只支持 ImageVector | 保留默认图标；未来扩展 resolved icon union | Phase 1/3 |
| `app/src/main/java/com/example/mylibrary/ui/navigation/LibraryBottomBar.kt` | 底栏绘制 | 直接访问 DefaultNavigationIcons | 注入 resolver；容器归 CARD | Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/library/LibraryScreen.kt` | 页面与海报任务发起 | 在 Composable 内构造 poster palette | 抽出导出快照工厂 | Phase 1/3 |
| `app/src/main/java/com/example/mylibrary/ui/poster/CoverPosterRenderer.kt` | Canvas 渲染 | 已接 palette/typeface，结构正确 | 保留布局；只替换快照来源 | 保留/Phase 1 |
| `app/src/main/java/com/example/mylibrary/ui/poster/CoverPosterLayout.kt` | 固定画布与网格计算 | 无主题耦合 | 不改 | 保留 |
| `app/src/main/java/com/example/mylibrary/export/README.md` | 后续导出预留 | PDF/文字报告尚无实现 | 不提前创建空实现；实际导出建设时扩展 | 后续导出 |
| `app/src/main/java/com/example/mylibrary/ui/components/CoverImage.kt` | 封面显示与内存缓存 | 缓存成熟，但不应承载主题表面缓存 | 保留；主题资源使用独立 cache | 保留/Phase 3 |
| `app/src/main/java/com/example/mylibrary/data/image/CoverImageProcessor.kt` | 封面采样与缩略图 | 白色 JPEG 底属于图片策略 | 保留固定，不跟主题 | 保留 |
| `app/src/main/java/com/example/mylibrary/data/preferences/PreferencesDataStore.kt` | Preferences DataStore | 可容纳 currentThemeId | 保留；Phase 3 增加 key | Phase 3 |
| `app/src/main/java/com/example/mylibrary/data/repository/UserPreferencesRepository.kt` | 布局偏好与备份快照 | 无主题选择 | Phase 3 加 themeId；Phase 4 扩展备份 | Phase 3/4 |
| `app/src/main/java/com/example/mylibrary/di/AppContainer.kt` | 依赖装配 | 无 ThemeRepository | Phase 3 增加，不耦合 Room | Phase 3 |
| `app/src/main/java/com/example/mylibrary/backup/model/BackupModels.kt` | Backup Schema v1 | 不含主题 | Phase 4 升级 v2，Room 版本不变 | Phase 4 |
| `app/src/main/java/com/example/mylibrary/backup/DataExportService.kt` | ZIP 备份 | 只含数据、偏好、封面 | 可选加入原始加密 active theme | Phase 4 |
| `app/src/main/java/com/example/mylibrary/backup/DataImportService.kt` | 备份恢复与回滚 | 无主题安装/回退 | 业务数据优先；主题失败回退默认 | Phase 4 |
| `app/src/main/java/com/example/mylibrary/backup/validation/BackupArchiveValidator.kt` | ZIP 安全校验 | 结构成熟但规则只允许备份文件 | 扩展备份主题条目；主题导入另建 validator | Phase 4 |
| `app/src/main/res/values/themes.xml` | 启动窗口/系统栏默认 | 导航栏白色，LightStatusBar 固定 | 与 edge-to-edge 默认回退一致化 | Phase 1/2 |
| `app/src/main/res/xml/backup_rules.xml` | OS 备份 | 不含 themes | 主题存储落地后决定是否包含 | Phase 4 |
| `app/src/main/res/xml/data_extraction_rules.xml` | 云备份/设备迁移 | 不含 themes | 同上 | Phase 4 |

### 14.1 应继续保留的现有文件/职责

- `AppFontResolver` 名称和双输出接口
- `FontSlot`、4 个暂定 `FontRole`
- `LibraryShapes`
- `Dimens.kt`
- `AppScreenContainer`
- `NavigationIconSlot`
- `NavigationIconResource` 的 normal/selected 概念
- `CoverPosterLayout`
- `CoverPosterRenderer` 的固定排版
- 封面图片的存储、采样和缓存系统
- Backup validator 的安全校验模式

### 14.2 应从现有文件抽出的职责

- 从 `Theme.kt` 抽出 ResolvedTheme/系统栏配置，但只在对应 Phase 创建。
- 从 `LibraryScreen.kt` 抽出导出快照构造。
- 从页面级 `Surface` 抽出统一 BACKGROUND/CARD/DIALOG 绘制。
- 从 `LibraryBottomBar.kt` 抽出导航资源解析。
- 从 Backup 业务中保持“主题包 validator”和“数据备份 validator”独立。

### 14.3 未来可能必要的新文件

建议名称仅用于职责说明，不要求机械照搬：

Phase 1：

- `ui/theme/SurfaceRole.kt`
- `ui/components/AppThemeSurface.kt`
- `ui/theme/DefaultTheme.kt`
- `ui/poster/ExportThemeSnapshotFactory.kt`

Phase 2：

- `theme/model/ThemeManifest.kt`
- `theme/model/ThemeSchemaLimits.kt`

Phase 3：

- `theme/runtime/ResolvedTheme.kt`
- `theme/runtime/ThemeRepository.kt`
- `theme/runtime/ThemeFontResolver.kt`
- `theme/runtime/ThemeImageCache.kt`
- `theme/runtime/NavigationIconResolver.kt`

Phase 4：

- `theme/package/ThemePackageReader.kt`
- `theme/package/ThemeCrypto.kt`
- `theme/package/ThemeArchiveValidator.kt`
- `theme/package/ThemeInstaller.kt`

### 14.4 暂时不应创建的新文件

- 主题设置页
- 主题预览页
- 主题商店/在线仓库
- AES-GCM 样例实现
- 动态 TTF 样例 loader
- PDF 空壳 renderer
- Room ThemeEntity/ThemeDao
- 插件或页面模板接口
- Web 主题制作器

### 14.5 巨型文件和职责混杂

较大的 UI 文件包括：

- `FieldManagementScreen.kt`：约 1261 行
- `TagManagementScreen.kt`：约 718 行
- `SettingsExportDialogs.kt`：约 563 行
- `ItemForm.kt`：约 532 行
- `HomeCalendar.kt`：约 486 行

不建议以 500 行作为机械拆分标准。

真正值得拆分的判断标准：

- 同一文件同时拥有页面根、BottomSheet、Dialog、输入组件和独立拖拽状态机。
- 某一组件需要被多个页面复用。
- 某一职责需要独立测试或独立生命周期。

主题化本身只需要替换表面/文字入口，不应借机重写业务交互。`FieldManagementScreen.kt` 和 `TagManagementScreen.kt` 可以在 Phase 1 先完成语义接入，之后再按复用价值决定是否拆分。

## 15. 推荐的下一轮实际任务

下一轮建议执行：

### “Phase 1A：默认主题语义收口与统一表面骨架”

范围：

1. 将 Theme 外部边界收缩为：
   - BACKGROUND
   - CARD
   - DIALOG
   - textPrimary
   - textSecondary
   - border
   - accent
   - onAccent
2. 保留 App 固定的 error/danger、scrim 和图片处理色。
3. 新建默认主题的统一表面组件，但只渲染颜色。
4. 迁移页面根、卡片、搜索、输入、设置项、底部导航、Dialog 和 BottomSheet。
5. 补齐 10 个无显式 style 的 Text。
6. 补齐 Material ColorScheme/Typography token，修复默认 FilterChip。
7. 给 `LibraryBottomBar` 注入只支持内置 ImageVector 的 resolver。
8. 把海报 palette/font snapshot 构造从 `LibraryScreen` 抽出。

明确不做：

- 不读取外部主题文件。
- 不加载 TTF。
- 不加载主题图片。
- 不实现 AES-GCM。
- 不修改数据库。
- 不增加主题设置页。
- 不修改海报/PDF 布局。
- 不建设 Web。

验收重点：

- 默认视觉不变。
- 交互不变。
- 所有表面具有唯一语义角色。
- 新增页面不再能绕过统一入口。
- compile、unit test、androidTest 编译和 lint 通过。

Phase 1A 完成后，再执行 Phase 1B 的逐页视觉回归和遗漏审计；不要直接跳到主题导入。

## 16. 当前不应实施的内容

本阶段不应实施：

- `.mylibrarytheme` 导入
- PBKDF2/AES-GCM
- 动态字体加载
- 主题图片解码
- 外部导航图标加载
- currentThemeId 持久化
- 安装目录
- 主题删除/恢复默认
- 主题备份恢复
- Manifest v1 最终冻结
- PDF 主题化
- 主题设置/预览
- 主题制作网页
- Theme Room 表
- 在线主题库、商店、社区、账号、同步、推荐
- 插件系统、页面模板系统

原因：

当前真正会造成未来逐页返工的是 UI 内部语义和表面入口，而不是加密或文件格式。应先把默认主题变成完整、严格、唯一的内部实现，再让外部主题只替换已冻结的视觉资源。

---

## 附：构建与静态检查

执行命令：

```powershell
.\gradlew.bat compileDebugKotlin lintDebug --rerun-tasks
```

结果：

- `compileDebugKotlin`：成功
- `lintDebug`：成功
- Lint：16 个 Warning，0 Error
  - `UseKtx`：5
  - `ModifierParameter`：5
  - `GradleDependency`：4
  - `NewerVersionAvailable`：1
  - `UseOfNonLambdaOffsetOverload`：1
- 与主题直接相关的编译警告：
  - `Theme.kt` 直接设置 `statusBarColor` 已 deprecated
  - `Theme.kt` 直接设置 `navigationBarColor` 已 deprecated

本轮没有修改任何业务或 UI 文件，也没有修改数据库、资源、Gradle 或依赖版本。
