# MyCodex Android UI 阶段 2 详细实施计划

| 项目 | 内容 |
| --- | --- |
| 状态 | 已完成：通过本地门禁与 Android 15 / HyperOS 真机验收并独立提交 |
| 版本 | 1.1 |
| 日期 | 2026-07-30 |
| 基线提交 | `155b53b` |
| 前置阶段 | 阶段 0–1 已实施并通过 Android 15 / HyperOS 真机验收 |
| 对应规格 | [`ui-theme-accessibility-spec.md`](ui-theme-accessibility-spec.md) 的阶段 2 |
| 实施范围 | 顶栏、底部导航、Profile、本机主题设置的页面级视觉统一 |

## 1. 文档目的

本文把阶段 2 拆成可直接编码、测试、审阅和真机验收的工作包。

本轮只改造应用外壳、Profile 和阶段 0 已建立的本机主题设置页，不提前重排工作区、会话、设备、Windows 设置、聊天、Composer 或附件 Sheet。阶段 2 必须独立通过构建、无障碍和真机门禁后，才能进入阶段 3。

## 2. 最终交付结果

阶段 2 完成后：

1. 顶栏使用明确的 Surface 层级，根页面标题保持居中，动态长标题可以完整换行。
2. “返回”“新建”“更多”等现有顶栏操作保持可见文本、原行为和至少 48×48dp 的操作区域。
3. 底部导航不再固定为 92dp，而是以约 72dp 为最小内容高度并随字体自然增高。
4. 底栏选中项同时使用胶囊、文字字重和标准 selected 语义，不只依赖颜色。
5. 底栏仍只有“编程 / 办公 / 我的”三个可操作无障碍节点。
6. Profile 当前设备信息使用紧凑的 `primaryContainer` 卡片。
7. “设备管理 / Windows 设置 / 本机设置”放入统一分组 Surface。
8. 尚未实现的“诊断”入口完全隐藏，不保留无行为点击节点。
9. 本机主题选项使用与 Profile 一致的分组层级，同时保留三态选择、保存失败和焦点恢复行为。
10. 浅色、深色、跟随系统、1.0/1.3/2.0 字体、TalkBack、键盘和两种系统导航方式通过验收。

## 3. 范围边界

### 3.1 生产代码

只修改：

- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`

不新增生产源码文件。

### 3.2 自动测试

主要修改：

- `app/src/androidTest/java/com/mycodex/mobile/MainScaffoldTest.kt`
- `app/src/test/java/com/mycodex/mobile/ui/theme/ThemeColorContrastTest.kt`

现有以下测试必须继续通过，不因阶段 2 重写：

- `MainActivityThemeFocusTest`
- `AccessibleNativeControlsThemeTest`
- `ThemePreferenceStoreTest`
- `NativeThemeTest`
- JVM 无障碍 source contract 与颜色对比度测试

### 3.3 文档

实施完成后更新：

- `README.md`
- `docs/ui-theme-accessibility-spec.md`
- 本文的状态和真机验收记录

### 3.4 明确不做

- 不修改 `MainActivity`、主题解析、主题持久化或原生 day/night 资源。
- 不修改 `MainTabDestination` 的三个 route、标签或图标资源。
- 不迁移到 Navigation Compose、`NavigationBar` 或新的导航框架。
- 不改工作区、会话、设备、Windows 设置和聊天页面内部布局。
- 不创建通用设计系统、spacing 对象、组件工厂或新共享 UI 文件。
- 不增加图标、动画、Badge、未读计数、动态色或第三方依赖。
- 不实现“诊断”；只隐藏无行为入口。
- 不增加成功 Toast、Snackbar 或持久成功状态。
- 不引入截图测试框架。

## 4. 当前实现审计

| 区域 | 当前实现 | 阶段 2 问题 | 处理 |
| --- | --- | --- | --- |
| Scaffold | 根 Modifier 使用 `WindowInsets.safeDrawing`，内容由同一个 Scaffold 分发 | Insets 链已可用 | 保持，不另建 edge-to-edge 层 |
| 顶栏 | 已位于根 safeDrawing 内的 `CenterAlignedTopAppBar`，标题固定一行并 Ellipsis | 默认 TopAppBar Insets 可能与根 padding 重复；动态标题丢失可见信息；固定高度不利于 2.0 字体 | 同文件内改成不再添加系统栏 Insets 的内容驱动顶栏 |
| 顶栏操作 | `TextButton` 承载返回、新建、更多 | 行为稳定，但没有统一测试 tag 和尺寸断言 | 保留文本与回调，增加稳定 tag 和 48dp 门禁 |
| 底栏 | 自定义 `Surface + Row`，Row 固定 `92.dp` | 占用过大；高字体不能自然增高 | 改为 `heightIn(min = 72.dp)` 和内容 padding |
| 底栏语义 | 根 Column 用 `clearAndSetSemantics`，手写“已选中/未选中”文案 | 没有标准 selected/Tab 语义；测试只证明可点击 | 改用 Compose 原生 `selectable` 的 `Role.Tab` 和 selected 语义 |
| 底栏视觉 | 图标和文字只改变颜色 | 选中状态依赖颜色 | 图标增加 `primaryContainer` 胶囊，标签增加半粗体 |
| Profile 设备卡 | `surfaceContainer` 状态卡，标题含设备名和前缀 | 上下文卡片偏高、层级不突出 | 改为紧凑 `primaryContainer` 卡 |
| Profile 入口 | 四个全宽行与分隔线平铺 | 缺少分组 Surface；“诊断”可点击但无行为 | 三个真实入口分组，删除诊断行 |
| 本机设置 | 三个原生行平铺 | 与阶段 2 的 Profile 分组风格不一致 | 放入同层级分组 Surface |
| 主题行为 | 三态偏好、失败 live region、重建焦点恢复已稳定 | 视觉包裹可能误加第二语义节点 | 外层只负责视觉，不可点击、不聚焦、不添加名称 |
| 返回与焦点 | `rememberSaveable` 目标 + 原生行恢复 | 已满足架构要求 | 不改变 route、tag、generation 或回调顺序 |

## 5. 已确定的实现决策

### 5.1 文件和组件边界

阶段 2 只在 `MyCodexApp.kt` 中增加必要的同文件私有函数。

允许：

- 一个可被 instrumentation 直接组合的 `internal` 顶栏函数。
- 现有 `MyCodexBottomNavigation` 和 `BottomNavigationButton` 的原位修改。
- Profile 与 LocalSettings 内部直接使用 Material `Surface`、`Column` 和 `HorizontalDivider`。

不为了两处相似 Surface 提前创建共享列表组件。若实际编码后出现完全相同且较长的视觉包裹，最多提取一个同文件私有函数；不得新建共享 UI 文件。

### 5.2 顶栏布局

用同文件 `MyCodexTopBar` 替换当前 Scaffold 内联的 `CenterAlignedTopAppBar`。

布局要求：

- 根使用 `Surface`，颜色为 `surfaceContainerLowest`。
- 保留轻量 `outlineVariant` 底部分隔线。
- 顶栏自身不再消费 status-bar Insets；根 Scaffold 的 `safeDrawing` 是唯一系统栏避让来源。
- 主行最小高度 64dp，不固定最大高度。
- 左操作区、标题区、右操作区使用对称空间分配，保证标题视觉居中且不与操作重叠。
- 左右操作区各自能容纳当前最多一个文本操作。
- 返回、新建和更多继续使用 Material `TextButton`。
- 每个操作显式 `heightIn(min = 48.dp)`，不依赖文字本身撑高命中区。
- 现有 enabled 条件和 DropdownMenu 行为原样保留。

标题策略：

- 根页面和静态短标题优先一行居中。
- `CodingChat` 和 `OfficeChat` 的会话标题按动态标题处理。
- 动态标题先按两行布局。
- 若两行仍发生视觉溢出，同一个标题节点自动展开为完整内容，不使用永久 Ellipsis，也不增加第二个重复标题节点。
- 标题区域内容驱动增高，不通过固定高度裁切。
- 标题始终只有一个 heading 节点。
- heading 的无障碍名称使用完整原始标题，不受视觉换行影响。
- 标题获得焦点或被 TalkBack 浏览时不改变页面、不展开菜单、不联网。

建议稳定 tag：

| 元素 | tag |
| --- | --- |
| 顶栏根 | `app_top_bar` |
| 标题 heading | `app_top_bar_title` |
| 返回 | `app_top_bar_back` |
| 新建 | `app_top_bar_create` |
| 更多 | `app_top_bar_more` |

### 5.3 底部导航布局

保留当前自定义三项底栏。

外层：

- 继续使用 `Surface` 和当前主题角色。
- 继续保留顶部轻量 Divider。
- Row 使用 `fillMaxWidth()`、垂直 padding 和 `heightIn(min = 72.dp)`。
- 不使用固定高度，不使用 `fillMaxSize()` 强迫 Tab 填充固定 92dp。

每个 Tab：

- 继续平均占据三分之一宽度。
- 整个 Tab 根是唯一点击、键盘和无障碍操作节点。
- 根命中高度至少 48dp，正常字体下底栏内容总高度约 72dp。
- 2.0 字体下允许底栏自然增高。
- 图标和标签之间保持 4dp 间距。
- 图标和文字本身不可点击、不可聚焦，图标 `contentDescription = null`。

选中视觉：

- 图标外层使用圆角胶囊。
- 选中胶囊颜色为 `primaryContainer`，图标为 `primary`。
- 未选中胶囊透明，图标和文字为 `onSurfaceVariant`。
- 选中标签使用 `labelLarge` 与 `SemiBold`。
- 未选中标签使用 `labelLarge` 的既有字重。
- 不新增动画。

选中语义：

- 根节点使用 `Modifier.selectable(selected = ..., role = Role.Tab, onClick = ...)`，不重复手写 selectable 已提供的 click 和 selected 语义。
- 无障碍名称直接来自可见标签“编程”“办公”“我的”，不把系统会播报的“标签页”写入名称。
- 选中状态由 selected 语义播报，不依赖把“已选中”硬编码进名称。
- `selectable` 必须合并图标与标签，使根节点保留一个显式 click action。
- 获得键盘焦点、TalkBack 焦点或普通重组不得切换 Tab。
- 点击、TalkBack 激活、Enter 或 Space 才调用 `onDestinationSelected`。

稳定 tag 保持不变：

- `tab_coding`
- `tab_office`
- `tab_profile`

底栏根新增：

- `bottom_navigation`

### 5.4 Profile 设备卡

设备存在时：

- 标题为“当前设备”。
- 正文显示完整 `displayName`。
- 不重复显示“当前设备：”前缀。
- “可在设备管理中切换或删除设备”不再占用常驻卡片空间，入口本身已经表达该操作。

无设备时：

- 标题为“尚未添加设备”。
- 正文为“请先进入设备管理添加 Windows 设备。”

视觉：

- `Surface` 颜色为 `primaryContainer`。
- 标题和正文使用 `onPrimaryContainer`。
- shape 使用现有 `medium`。
- 内边距内容驱动，不设置固定高度。
- 卡片仅为信息，不可点击、不聚焦、不声明 button/selected 语义。
- 长设备名允许自然换行。
- 增加测试 tag `profile_device_card`；tag 只用于测试，不增加 TalkBack 名称或操作。

### 5.5 Profile 入口分组

只保留三个真实入口：

1. 设备管理
2. Windows 设置
3. 本机设置

处理：

- 三行放入一个 `surfaceContainerLow` 的圆角 Surface。
- 行之间使用缩进的 `outlineVariant` Divider。
- Surface 和 Divider 不创建语义节点。
- `NativeAccessibleActionRow` 仍是每一行唯一可操作节点。
- 不在原生行外层添加 clickable、focusable、contentDescription 或自定义 action。
- 保持现有 test tag、点击回调、返回目标和 `onFocusRestored`。
- Windows 设置在无设备时继续显示当前说明和现有行为，本阶段不改变业务门控。
- 完全移除 `profile_diagnostics_row` 的组合；不改成 disabled 行，也不显示“即将推出”。
- 分组根增加测试 tag `profile_action_group`；只用于断言容器本身没有 click/focus action。

### 5.6 本机主题设置分组

三项主题仍为：

- 跟随系统
- 浅色
- 深色

行为保持：

- 标题只显示模式名称。
- 正文和无障碍名称表达“已选中/未选中”。
- 相同模式重复激活不写盘、不重建、不反馈。
- 保存失败保留旧选择，并使用现有 polite live region。
- 主题变化后的 generation 焦点恢复链不变。
- 不增加“应用”按钮。

视觉处理：

- 三项放入与 Profile 入口相同的 `surfaceContainerLow` 圆角分组 Surface。
- Divider 只作为装饰。
- 本阶段不为选中主题行增加另一层背景；“已选中/未选中”正文和无障碍名称继续作为明确状态，避免引入未验证的文字/容器组合。
- 错误文本位于分组下方，保持 `error` 色和 polite live region。
- 分组根增加测试 tag `local_theme_group`；不增加可访问名称或操作。

### 5.7 Insets、导航和生命周期

必须保留：

- Scaffold 根的 `WindowInsets.safeDrawing`。
- 当前 `BackHandler` 和 `goBack()` 路由。
- `selectedRoute`、`activeScreen` 与所有 `rememberSaveable` 状态。
- Profile 三个焦点 target 常量。
- LocalSettings 的主题 focus request、generation 和消费回调。
- 当前页面业务 repository、controller 和 ViewModel 所有权。

阶段 2 不新增状态 holder，不迁移路由，也不改变远程操作调用点。

视觉重组不得：

- 自动切换 Tab。
- 打开 DropdownMenu。
- 进入 Profile 子页。
- 保存主题。
- 发起网络请求。
- 重复恢复普通焦点或 TalkBack 焦点。

## 6. 工作包

### 6.1 工作包 2A：基线与规格门禁

1. 记录基线提交和工作树。
2. 运行 JVM、lint、Debug 和 androidTest 编译门禁。
3. 保留阶段 0–1 真机浅/深色基线。
4. 确认不在个人手机运行 connected/UTP 测试。
5. 确认总规格不再要求包级夜间模式 API。

完成门槛：

- 基线可构建。
- 当前失败若存在，已经分类为前置问题，不与阶段 2 混修。
- 本文与总规格没有主题链路冲突。

### 6.2 工作包 2B：内容驱动顶栏

1. 把 Scaffold 内联顶栏提取为同文件 `MyCodexTopBar`。
2. 接入现有完整标题字符串、返回回调和 actions slot。
3. 实现静态标题与动态标题的换行策略。
4. 为标题、返回、新建和更多增加稳定 tag。
5. 保留所有 action enabled 条件和菜单回调。
6. 移除不再使用的 Material TopAppBar import/opt-in，但不做其他清理。
7. 保留 heading 单节点与完整名称。
8. 增加长标题、48dp、显式激活和大字体测试。

完成门槛：

- 长会话标题无永久一行截断。
- 操作与标题不重叠。
- 标题只有一个 heading 节点。
- 返回、新建、更多的行为与 enabled 条件未改变。
- 2.0 字体下操作仍可到达。

### 6.3 工作包 2C：自适应底部导航

1. 删除固定 `92.dp` 高度。
2. 增加约 72dp 最小内容高度和垂直 padding。
3. 增加选中图标胶囊与标签字重。
4. 用原生 `selectable` 增加 `Role.Tab`、selected 和单节点语义。
5. 保留三个 route、图标、标签和 test tag。
6. 增加节点数量、选中切换、焦点不激活、48dp 和 2.0 字体测试。

完成门槛：

- 正常字体下底栏明显低于旧固定 92dp。
- 高字体下不裁切，而是自然增高。
- 恰好三个可操作 Tab 节点。
- 选中状态同时由形状、字重和 selected 语义表达。
- 键盘焦点和 TalkBack 浏览不自动切页。

### 6.4 工作包 2D：Profile 与本机设置

1. 将设备状态卡改为紧凑 `primaryContainer` 卡。
2. 将三个真实 Profile 入口放入分组 Surface。
3. 删除诊断入口组合。
4. 将主题选项放入相同层级的分组 Surface。
5. 保持所有 NativeAccessibleActionRow 原参数和稳定 tag。
6. 扩展 Profile、诊断隐藏、主题选择和返回焦点测试。

完成门槛：

- Profile 无无行为点击节点。
- 每个真实入口仍只有一个原生可操作节点。
- 主题三态和错误行为未改变。
- 返回 Profile 后焦点仍恢复到来源行一次。
- 长设备名和 2.0 字体无裁切。

### 6.5 工作包 2E：整合与真机验收

1. 运行完整本地门禁。
2. 构建最新 Debug APK。
3. 使用 `adb install -r` 覆盖安装，禁止卸载和 `pm clear`。
4. 在 Android 15 / HyperOS 真机执行阶段 2 矩阵。
5. 恢复测试前的系统主题、字体比例、导航方式和 TalkBack 状态。
6. 更新本文与总规格状态。

完成门槛：

- 自动测试与构建全部通过。
- 真机浅色、深色、系统跟随均通过。
- 真机 1.0、1.3、2.0 字体通过。
- TalkBack 和键盘显式激活通过。
- 手势导航与三键导航没有遮挡或不可达操作。
- 数据在覆盖安装后保留。

## 7. 自动测试计划

### 7.1 `MainScaffoldTest`

扩展或拆分为以下用例：

#### 底栏节点与语义

- `bottomNavigationHasExactlyThreeSingleActionTabs`
  - 底栏根存在。
  - 恰好三个 Tab 根。
  - 每个根只有一个 click action。
  - 图标无 content description。
  - 子文字不形成第二个可操作节点。

- `bottomNavigationExposesRoleAndSelectedState`
  - 初始 Coding 为 selected。
  - Office/Profile 为 not selected。
  - 点击 Office 后 selected 状态交换。
  - 名称只为“编程 / 办公 / 我的”，不重复控件类型。

- `bottomNavigationFocusDoesNotChangeDestination`
  - 请求 Office 普通焦点后 Coding 仍为 selected。
  - 只有显式 click/Enter/Space 后切换。

- `bottomNavigationTargetsMeetMinimumSize`
  - 三个 Tab 宽高均不低于 48dp。
  - 底栏根正常字体下不再固定为 92dp。

#### 顶栏

- `topBarKeepsFullDynamicTitleInSingleHeading`
  - 使用超出一行的会话标题直接组合顶栏。
  - 标题节点只有一个。
  - 节点具有 heading 语义。
  - 无障碍名称等于完整原始标题。
  - 视觉标题允许换行而不是永久一行 Ellipsis。

- `topBarActionsMeetMinimumSizeAndRequireActivation`
  - 返回和右侧文本操作均至少 48×48dp。
  - 请求焦点不调用回调。
  - 显式点击只调用一次。

#### Profile

- `profileShowsOnlyRealEntries`
  - 设备管理、Windows 设置、本机设置存在并可操作。
  - `profile_diagnostics_row` 不存在。
  - 三个原生行仍使用原稳定 tag。

- `profileAndLocalSettingsKeepSingleActionRows`
  - 分组 Surface 不可点击、不可聚焦。
  - 原生行仍是唯一 click action。
  - 主题三项内容描述和选择回调保持现有契约。

#### 返回与焦点

- 扩展现有返回测试：
  - LocalSettings 系统返回后，`profile_local_settings_row` 的原生 View 获得普通焦点。
  - 设备管理返回后，`profile_device_management_row` 恢复一次。
  - 焦点恢复不调用对应导航回调。

#### 大字体

- 对纯 Compose 顶栏和底栏使用测试 `LocalDensity` 的 `fontScale = 2f`：
  - 标题和标签存在。
  - 操作可见并满足最小尺寸。
  - 底栏内容驱动增高。
- 原生 Profile 行的真实字体缩放保留为真机门禁，不用修改全局设备设置的 instrumentation 测试冒充。

### 7.2 回归测试

必须继续通过：

- 主题选择成功、失败和重建焦点测试。
- 原生行 disabled、click、long-click、自定义 action 和焦点 generation 测试。
- 浅色/深色 ColorScheme 对比度测试。
- 所有现有业务 JVM 测试。

扩展 `ThemeColorContrastTest`，检查阶段 2 的真实组合：

- `onSurface / surfaceContainerLowest`：顶栏标题。
- `primary / surfaceContainerLowest`：顶栏文本操作。
- `onSurfaceVariant / surfaceContainer`：未选中底栏标签。
- `primary / surfaceContainer`：选中底栏标签。
- `primary / primaryContainer`：选中胶囊图标，门槛 3:1。
- `onPrimaryContainer / primaryContainer`：Profile 设备卡。
- `onSurface / surfaceContainerLow`：Profile/主题分组行标题。
- `onSurfaceVariant / surfaceContainerLow`：Profile/主题分组行正文。

普通文字组合使用 4.5:1；图标与必要非文字图形使用 3:1。

不增加：

- Robolectric。
- 截图测试依赖。
- 新 test runner。
- 为测试暴露业务 repository 或创建 DI 层。

## 8. 真机验收矩阵

### 8.1 主题

分别验证：

- 跟随系统 + 系统浅色
- 跟随系统 + 系统深色
- 显式浅色
- 显式深色

检查：

- 顶栏、底栏、设备卡、分组 Surface 和主题选中背景角色一致。
- 文本、图标、选中胶囊和 Divider 可读。
- 无默认紫色或错误 Force Dark。

### 8.2 字体

分别验证：

- 1.0
- 1.3
- 2.0

检查：

- 动态长标题完整显示且不与返回/更多重叠。
- 顶栏允许增高。
- 底栏允许增高，三个标签与图标不裁切。
- Profile 和主题行自然增高并可滚动。
- 所有操作仍可到达。

改变字体前记录原值，验收后恢复原值。不得用清应用数据作为字体测试步骤。

### 8.3 TalkBack

线性浏览顺序：

1. 顶栏返回或标题、右侧操作。
2. 页面内容。
3. 底栏三个 Tab。

检查：

- 标题作为 heading 播报一次。
- 底栏每项只播报一个节点和正确 selected 状态。
- 图标与标签不重复播报。
- TalkBack 移到 Office/Profile 不自动切页。
- 双击后才切页。
- Profile 只有三个真实入口，没有诊断。
- 进入并返回本机设置后焦点恢复到入口一次。
- 主题切换后焦点恢复到新选项一次。

### 8.4 键盘

检查：

- Tab/方向键只移动焦点。
- Enter/Space 才激活底栏或 Profile 行。
- 返回、新建、更多可由键盘激活。
- 焦点移动不触发网络请求或页面切换。

### 8.5 系统导航与窗口

验证：

- 手势导航
- 三键导航
- 竖屏
- 至少一次横屏或较窄窗口检查

检查：

- safeDrawing Insets 生效。
- 顶栏不重复消费状态栏 Insets。
- 底栏不被系统导航遮挡。
- 顶栏不进入状态栏不可读区域。
- 内容 padding 不重复，首尾内容可见。

## 9. 验证命令

本地门禁：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

个人手机禁止运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

原因：当前 connected/UTP 流程可能卸载测试目标或清理应用数据。若需要运行 instrumentation，只使用专用 emulator 或明确可清理的测试设备。

真机安装只使用：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

安装前后确认 package、版本和数据仍在；不得添加 `adb uninstall` 或 `pm clear`。

## 10. 实施与提交顺序

建议单个阶段提交：

| 顺序 | 内容 | 门槛 |
| --- | --- | --- |
| 1 | 2B 顶栏 | 长标题、heading、48dp 测试通过 |
| 2 | 2C 底栏 | 三节点、selected、显式激活和字体测试通过 |
| 3 | 2D Profile/本机设置 | 诊断隐藏、单节点、返回焦点测试通过 |
| 4 | 2E 整合 | 全部门禁和真机矩阵通过 |
| 5 | 提交 `feat(ui): refine accessible app shell and profile` | staged diff 仅含阶段 2 |

阶段 2 不与阶段 3 代码合并提交。真机验收失败时只修复阶段 2 范围，不顺手改造列表或设置页。

## 11. 审阅记录

### 11.1 架构审阅

结论：通过。

理由：

- 继续使用现有 Scaffold、手写路由和 Material 3。
- 生产改动限制在 `MyCodexApp.kt`。
- 不改变 repository、controller、ViewModel 或远程协议。
- 不增加依赖、导航框架或全局设计系统。
- 顶栏提取仅用于内容驱动布局和直接测试，不形成通用组件层。

阻断条件：

- 为阶段 2 新建导航状态框架。
- 修改 route 或页面业务回调。
- 为视觉分组新增全局组件库。

### 11.2 无障碍审阅

结论：有条件通过；条件已经写入自动测试和真机门禁。

必须保持：

- 标题单 heading。
- 顶栏操作和 Tab 至少 48×48dp。
- 底栏恰好三个可操作节点。
- selected 使用标准语义，并同时有非颜色视觉信号。
- 原生 Profile/主题行仍为唯一节点。
- 焦点、TalkBack 浏览和重组不执行工作。
- 返回与主题重建焦点只恢复一次。
- 2.0 字体无裁切和不可达操作。

阻断条件：

- 图标或文字成为第二个 Tab 焦点。
- 分组 Surface 成为可点击或可聚焦节点。
- 选中只通过颜色表达。
- 长标题的完整文本只存在于无障碍名称、视觉仍永久截断。
- TalkBack 浏览 Tab 自动切页。
- 隐藏诊断后留下空可点击区域。

### 11.3 生命周期与业务安全审阅

结论：通过。

理由：

- 阶段 2 不新增业务状态。
- route、焦点 target 和 ViewModel 不变。
- 顶栏 enabled 条件和菜单回调原样透传。
- Profile 与主题页只增加不可交互视觉容器。

必须验证：

- 重组不打开菜单。
- 字体/主题配置变化不切换 Tab。
- 主题保存失败仍保持旧选择。
- 返回后不重复触发页面入口。

### 11.4 视觉审阅

结论：通过。

角色映射：

| 元素 | 颜色/样式 |
| --- | --- |
| 顶栏 | `surfaceContainerLowest` / `onSurface` |
| 顶栏操作 | `primary` |
| 底栏 | `surfaceContainer` |
| 选中胶囊 | `primaryContainer` |
| 选中图标/标签 | `primary` / SemiBold |
| 未选中图标/标签 | `onSurfaceVariant` |
| 设备卡 | `primaryContainer` / `onPrimaryContainer` |
| Profile/主题分组 | `surfaceContainerLow` |
| Divider | `outlineVariant` |
| 错误 | `error` |

阶段 2 不增加新色值，但必须扩展现有测试，覆盖文字和图标实际落到新 Surface 后的组合。

### 11.5 测试可行性审阅

结论：通过。

- 顶栏作为同文件 internal Composable 可直接测试长标题和操作尺寸。
- 底栏现有稳定 tag 可扩展 selected 与单节点断言。
- Profile 和 LocalSettings 可通过现有 MyCodexApp 流程进入。
- 新 Surface 组合可直接扩展现有 JVM 对比度测试，无需截图框架。
- 真正的原生字体缩放和 TalkBack 焦点保留为真机验收，不用不等价的 Compose 节点测试替代。
- 不在个人手机运行 connected 测试。

### 11.6 最小化审阅

结论：通过。

明确不做：

- 不迁移标准 NavigationBar。
- 不新增设计系统组件文件。
- 不新增 spacing 常量对象。
- 不新增图标资源。
- 不新增动画。
- 不实现诊断。
- 不触碰阶段 3/4 页面布局。
- 不为两处 Surface 包裹创建抽象，除非实际重复代码明显且完全相同。

## 12. 风险与处置

| 风险 | 早期信号 | 处置 |
| --- | --- | --- |
| 动态标题与操作重叠 | 2.0 字体下返回/更多覆盖标题 | 对称操作区；标题内容驱动增高；真机长标题门禁 |
| 顶栏过度增高 | 极长标题占据过多屏幕 | 先两行布局，仅发生真实溢出时展开；不永久截断 |
| 底栏高字体裁切 | 标签下半截或系统导航遮挡 | 删除固定高度；保留 safeDrawing；验证两种系统导航 |
| Tab 双节点 | TalkBack 分别停在图标和文字 | 根清晰合并语义；子图标无名称；测试未合并树 |
| 选中只依赖颜色 | 无色环境无法判断当前 Tab | 胶囊 + 字重 + selected 语义 |
| 视觉 Surface 抢焦点 | TalkBack 先读容器再读行 | Surface 不添加 clickable/focusable/名称 |
| 返回焦点回归 | 从子页返回落到页首 | 保留稳定 tag 与 restore 参数；检查真实原生 View 焦点 |
| 主题切换重复执行 | 选中行重组再次写盘 | 不改阶段 0 选择处理器；回归保存计数 |
| 阶段范围膨胀 | 开始统一工作区/设置状态卡 | 停止；对应工作留给阶段 3 |

## 13. 阶段 2 完成定义

以下项目全部满足后，阶段 2 才算完成：

- 顶栏、底栏、Profile 和本机主题设置达到本文视觉规格。
- 动态长标题完整可见并只有一个 heading。
- 顶栏操作与三个 Tab 都至少 48×48dp。
- 底栏恰好三个单一可操作节点。
- Tab selected 同时由形状、字重和语义表达。
- Profile 不存在诊断或其他无行为点击入口。
- Profile 与主题选项分组不增加语义节点。
- 原生行的点击、键盘、自定义 action 和焦点恢复未回归。
- 浅色、深色、跟随系统和系统栏保持一致。
- 1.0、1.3、2.0 字体通过。
- TalkBack、键盘、手势导航和三键导航通过。
- JVM、lint、Debug 和 androidTest 编译通过。
- 最新 Debug APK 使用覆盖安装并通过真机验证。
- 应用数据未因安装或测试被删除。
- 无新依赖、无新生产文件、无阶段 3/4 范围混入。

## 14. 开发启动前检查表

- [x] 用户确认按本文执行阶段 2。
- [x] 当前分支与 `origin/main` 同步。
- [x] 工作树中真机截图等非代码文件不进入提交。
- [x] 记录阶段 2 基线测试。
- [x] 先实现顶栏并跑小测试。
- [x] 再实现底栏并跑小测试。
- [x] 再实现 Profile 与本机设置分组。
- [x] 运行完整本地门禁。
- [x] 构建并覆盖安装 Debug APK。
- [x] 完成主题、字体、TalkBack、键盘和系统导航真机矩阵。
- [x] 恢复手机测试前系统状态。
- [x] 更新文档状态。
- [x] 独立提交阶段 2，不混入阶段 3。

## 15. 实施与验收记录

实施日期：2026-07-30。

代码范围：

- 生产代码仅修改 `MyCodexApp.kt`。
- 自动测试仅修改 `MainScaffoldTest.kt` 和 `ThemeColorContrastTest.kt`。
- 未新增生产文件、依赖、图标、导航框架或阶段 3/4 代码。

本地门禁：

- `testDebugUnitTest`：219 个测试，0 failure、0 error、0 skipped。
- `lintDebug`：通过。
- `assembleDebug`：通过。
- `assembleDebugAndroidTest`：通过。
- `git diff --check`：通过。
- 按个人手机安全约束，未运行 `connectedDebugAndroidTest` 或 UTP。

真机验收：

- 设备：Xiaomi `23116PN5BC`，Android 15 / HyperOS。
- 使用 `adb install -r` 覆盖安装最新 Debug APK；未卸载、未清数据。
- 覆盖安装后 `firstInstallTime` 保持为 2026-07-29 21:20:23；既有设备、工作区和会话数据仍在。
- 浅色、深色、跟随系统均通过；最终恢复为跟随系统，系统深色值为 `2`。
- 字体 1.0、1.3、2.0 均通过；动态会话标题在 2.0 下完整扩展为三行，返回/更多无重叠。
- 底栏在 2.0 下自然增高，三个图标和标签完整；Profile 与主题分组自然换行并可滚动。
- VoiceBack、MIUI TalkBack 增强服务启用状态下完成焦点与显式激活检查。
- 三键导航和手势导航均无系统栏遮挡；最终恢复三键导航。
- 手机最终恢复为字体 1.0、跟随系统深色、三键导航。
