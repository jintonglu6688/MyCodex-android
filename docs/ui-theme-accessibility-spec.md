# MyCodex Android 统一 UI、深浅色与无障碍改造规格

| 项目 | 内容 |
| --- | --- |
| 状态 | 阶段 0–5 已实施；阶段 5 自动门禁与 Android/Windows 核心验收完成 |
| 版本 | 1.8 |
| 日期 | 2026-07-30 |
| 基线提交 | `55e6634` |
| 适用范围 | MyCodex Android 客户端 |

## 1. 文档目的

本规格用于指导 MyCodex Android 客户端的统一视觉改造。改造必须同时解决浅色/深色主题、Compose 与原生 Android View 的视觉一致性、不同页面的层级差异，以及屏幕阅读器、键盘、焦点恢复和大字体等无障碍要求。

各阶段必须能够独立构建、测试和真机验收。不得以视觉改造为由重写已经稳定的业务、导航、远程协议或原生无障碍交互。

## 2. 实施前基线与已知问题

### 2.1 深色模式链路不完整

当前代码已在 `ui/theme/Theme.kt` 中使用 `isSystemInDarkTheme()`，也定义了浅色和深色 Compose 配色，但原生启动主题仍固定为：

```xml
<style name="Theme.MyCodex" parent="android:Theme.Material.Light.NoActionBar" />
```

项目没有 `values-night/themes.xml`。因此启动窗口、原生 `Button`、`EditText`、Ripple、禁用态和部分系统栏外观仍来自浅色主题。

2026-07-29 在当前 Android 15 / HyperOS 真机上检查到：

- 用户预期设备处于深色模式。
- Android 框架向应用报告 `Night mode: no`。
- `mNightMode=1 (no)`。
- `mComputedNightMode=false`。
- MyCodex 实际以浅色窗口和浅色 Compose 配色运行。

应用不能依赖 OEM 私有设置判断主题。默认仍应跟随标准 Android `uiMode`，同时提供应用内显式浅色/深色覆盖，以处理系统信号与用户预期不一致的设备。

### 2.2 颜色角色不完整

当前 `Color.kt` 和 `Theme.kt` 只填写了部分 Material 3 角色。按钮、复选框、Dialog、选中容器等未填写的角色会回落到 Material 默认值，可能与现有蓝灰色板不一致。

必须补齐：

- `onPrimary`、`primaryContainer`、`onPrimaryContainer`
- Secondary 和 Tertiary 的前景及容器角色
- `error`、`errorContainer` 及对应前景
- 完整的 Surface Container 层级
- `outline`、`outlineVariant`
- inverse 系列和 `scrim`

### 2.3 页面视觉层级偏平

真机界面的主要问题：

- 顶栏较高，页面与顶栏主要依靠一条横线分隔。
- 三项底栏固定为 `92.dp`，与系统导航区叠加后占用过大。
- 工作区、会话、消息和设置主要使用“文字 + 全宽分隔线”。
- 工作区名、设备名等简单上下文信息占据较大的状态卡。
- 会话长标题占用顶栏空间过多。
- 聊天消息缺少角色化层级。
- Composer 永远把输入框、附件和发送按钮放在同一行。
- Profile 中存在看起来可点击但没有实际行为的入口。
- 加载、错误、空状态和普通说明的视觉及播报规则不一致。

### 2.4 无障碍实现是现有架构边界

`NativeAccessibleActionRow`、`NativeAccessibleTextInput` 和 `NativeAccessibleButton` 用于解决部分 Compose 控件无法可靠恢复屏幕阅读器焦点的问题。

这些原生控件及其以下能力必须保留：

- 一行一个无障碍焦点。
- 同一行上的自定义辅助功能操作。
- 长按菜单与自定义操作的一致性。
- 已接入路径使用的 `post → requestFocus() → ACTION_ACCESSIBILITY_FOCUS` 焦点恢复机制。
- 原生输入框的 IME 和辅助功能焦点处理。
- 禁用期间移除点击、长按和自定义操作。

工作区/会话删除、归档和部分 Dialog 关闭路径尚未统一接入相邻项或主操作的回退目标。阶段 3 必须在保留现有机制的基础上补齐这些调用方，不能把“目标节点重新出现”视为辅助功能焦点已经恢复。

## 3. 产品目标

改造完成后必须满足：

1. 应用默认跟随标准 Android 浅色/深色模式。
2. 用户可以在“本机设置”中显式选择“跟随系统 / 浅色 / 深色”。
3. Activity、Compose、原生 View 和系统栏使用同一主题结果；跟随系统的启动窗口一致，显式覆盖接受第 6.1 节记录的首帧边界。
4. 主要页面使用统一的颜色、排版、圆角、间距和状态层级。
5. 选中、错误、禁用和连接状态不只依赖颜色表达。
6. 所有核心流程继续支持 TalkBack、键盘、系统返回和焦点恢复。
7. 2.0 倍字体下不裁切关键内容，所有操作仍可到达。
8. 保持此前的产品决定：不显示持久的成功操作反馈。
9. 每一阶段都可独立构建、安装和回归，不做一次性大改。

## 4. 非目标

本轮不包含：

- 重写应用导航或引入 Navigation Compose。
- 将原生无障碍控件迁移成纯 Compose 控件。
- 引入 AppCompat、Material Components View 或第三方主题库。
- 启用 Material You 动态色。
- 下载或打包自定义字体。
- 新增动画框架、渐变、玻璃效果或插画背景。
- 为未来需求搭建设计系统框架、组件工厂或多层 token 抽象。
- 新增聊天 Markdown、代码高亮或富文本渲染。
- 实现与本次 UI 改造无关的诊断功能。
- 恢复已经删除的自动化入口或占位标签。
- 恢复操作成功提示、Snackbar、Toast 或持久结果行。

## 5. 视觉方向

视觉语言命名为“Quiet Console / 安静的远程工作台”。

核心原则：

- 使用冷灰中性色建立页面层级。
- Azure 蓝只用于主操作、选中项和焦点。
- 青绿用于连接、可用和成功类状态。
- 红色只用于错误和破坏性操作。
- 通过 Surface 层级、圆角和留白组织内容，减少连续分隔线。
- 阴影只在 Dialog、Sheet 或确实需要浮层感的组件中使用。
- 不把每一项都做成厚重卡片。
- 长技术内容优先保证阅读宽度和自然换行。
- 可见文字、无障碍名称和业务状态保持一致。

## 6. 主题规格

### 6.1 主题模式

| 模式 | 行为 |
| --- | --- |
| 跟随系统 | 默认值；使用标准 Android `uiMode` 决定浅色或深色 |
| 浅色 | 忽略当前系统模式，固定使用浅色 Compose 与原生主题 |
| 深色 | 忽略当前系统模式，固定使用深色 Compose 与原生主题 |

主题偏好使用项目已经依赖的 `SharedPreferences` 保存，不新增 DataStore 或主题状态框架。

应用启动时必须先解析最终主题，再创建页面内容。Activity 原生主题和 Compose `MaterialTheme` 必须使用同一个结果。

启动窗口与 Activity 分开处理：

- 跟随系统模式：所有支持版本都使用 `values` / `values-night` 资源，使启动窗口与系统模式一致。
- 显式浅色/深色覆盖：所有支持版本都在 Activity 创建前选择固定 Light/Dark style，使 Activity、Compose、原生 View 和系统栏一致。
- 显式覆盖与系统模式相反时，starting window 允许短暂沿用系统模式。应用不调用包级 `setApplicationNightMode()`，避免目标 HyperOS 留下压住系统配置的应用夜间模式覆盖。

若未来需要在显式覆盖下严格保证零闪屏，再单独评估 AppCompat、SplashScreen 或启动架构；当前不为该边界增加依赖或双 Launcher。

### 6.2 原生主题资源

至少需要：

- 更新 `app/src/main/res/values/themes.xml`
- 新增 `app/src/main/res/values-night/themes.xml`
- 提供可被显式选择的浅色和深色原生主题
- 保持 Manifest 中的默认主题可按资源限定符跟随系统

两个原生主题都必须定义：

- 窗口背景
- `colorAccent`
- 状态栏和导航栏颜色/透明策略
- 状态栏和导航栏图标明暗
- 原生 Button、EditText、Ripple 和禁用态所需的基础属性

项目继续使用现有 edge-to-edge 方案。浅色和深色下都必须验证系统栏图标对比度。

### 6.3 浅色配色

| Material 角色 | 色值 |
| --- | --- |
| `primary` | `#245EA8` |
| `onPrimary` | `#FFFFFF` |
| `primaryContainer` | `#D7E3FF` |
| `onPrimaryContainer` | `#001B3F` |
| `secondary` | `#38656F` |
| `onSecondary` | `#FFFFFF` |
| `secondaryContainer` | `#BCEBF5` |
| `onSecondaryContainer` | `#0A2025` |
| `tertiary` | `#376B4F` |
| `onTertiary` | `#FFFFFF` |
| `tertiaryContainer` | `#B9F1CD` |
| `onTertiaryContainer` | `#082114` |
| `background` | `#F7F8FA` |
| `onBackground` | `#171A1F` |
| `surface` | `#FFFFFF` |
| `onSurface` | `#171A1F` |
| `surfaceContainerLowest` | `#FFFFFF` |
| `surfaceContainerLow` | `#F5F6F8` |
| `surfaceContainer` | `#F0F2F5` |
| `surfaceContainerHigh` | `#E9ECF2` |
| `surfaceContainerHighest` | `#E1E5EC` |
| `surfaceVariant` | `#E9ECF2` |
| `onSurfaceVariant` | `#5E6573` |
| `outline` | `#767D8A` |
| `outlineVariant` | `#D8DCE4` |
| `error` | `#BA1A1A` |
| `onError` | `#FFFFFF` |
| `errorContainer` | `#FFDAD6` |
| `onErrorContainer` | `#410002` |
| `inverseSurface` | `#2C3036` |
| `inverseOnSurface` | `#F0F1F5` |
| `inversePrimary` | `#A8C8FF` |
| `scrim` | `#000000` |

### 6.4 深色配色

| Material 角色 | 色值 |
| --- | --- |
| `primary` | `#A8C8FF` |
| `onPrimary` | `#003061` |
| `primaryContainer` | `#0D477F` |
| `onPrimaryContainer` | `#D7E3FF` |
| `secondary` | `#A4CBD5` |
| `onSecondary` | `#06363E` |
| `secondaryContainer` | `#28505A` |
| `onSecondaryContainer` | `#C0E9F3` |
| `tertiary` | `#A5D0B6` |
| `onTertiary` | `#113825` |
| `tertiaryContainer` | `#27513A` |
| `onTertiaryContainer` | `#C0ECD0` |
| `background` | `#101114` |
| `onBackground` | `#E7E8EE` |
| `surface` | `#14161A` |
| `onSurface` | `#E7E8EE` |
| `surfaceContainerLowest` | `#0B0D10` |
| `surfaceContainerLow` | `#181A1F` |
| `surfaceContainer` | `#1C1E24` |
| `surfaceContainerHigh` | `#252832` |
| `surfaceContainerHighest` | `#30343F` |
| `surfaceVariant` | `#353945` |
| `onSurfaceVariant` | `#B8BECA` |
| `outline` | `#8D93A0` |
| `outlineVariant` | `#353945` |
| `error` | `#FFB4AB` |
| `onError` | `#690005` |
| `errorContainer` | `#93000A` |
| `onErrorContainer` | `#FFDAD6` |
| `inverseSurface` | `#E7E8EE` |
| `inverseOnSurface` | `#2D3036` |
| `inversePrimary` | `#245EA8` |
| `scrim` | `#000000` |

所有实际使用的文字/背景组合必须通过自动对比度测试，不能只根据表中颜色或目测判断。

### 6.5 排版

继续使用系统 Sans，不新增字体文件。

| 角色 | 字号 | 行高 | 字重 |
| --- | --- | --- | --- |
| `titleLarge` | 20sp | 28sp | SemiBold |
| `titleMedium` | 16sp | 24sp | SemiBold |
| `titleSmall` | 14sp | 20sp | SemiBold |
| `bodyLarge` | 16sp | 24sp | Normal |
| `bodyMedium` | 14sp | 20sp | Normal |
| `bodySmall` | 12sp | 16sp | Normal |
| `labelLarge` | 14sp | 20sp | Medium |
| `labelMedium` | 12sp | 16sp | Medium |

关键内容不得放入固定高度的文字容器。动态长标题、路径、消息和状态说明必须允许换行或滚动到达。

### 6.6 Shape 与间距

Shape：

- `extraSmall`: 8dp
- `small`: 12dp
- `medium`: 16dp
- `large`: 20dp
- `extraLarge`: 28dp

间距：

- 页面水平边距：20dp
- 页面垂直边距：12dp
- 嵌入式控件水平边距：12dp
- Bottom Sheet 内容边距：16dp
- 列表项间距：8dp
- 区块间距：20–24dp
- 基础间距：4 / 8 / 12 / 16 / 20 / 24dp

仅在散落数值已经造成实际不一致时抽取少量常量，不建立通用尺寸框架。

## 7. 组件规格

### 7.1 顶栏

- 根页面可继续使用居中标题。
- 会话、设备详情和设置等动态标题页面必须为长文本保留空间。
- 顶栏使用 `surface` 或 `surfaceContainerLowest`。
- 与内容区之间使用轻量 `outlineVariant` 边线；若表面层级已足够，可省略边线。
- 保留“返回”“新建”“更多”等可见文本操作。
- 每个操作命中区至少为 48×48dp。
- 页面标题保留 heading 语义。
- 动态长标题优先在两行内完整显示；若顶栏必须截断，页面内容区必须另有完整可见标题。
- 无障碍名称始终包含完整标题。

### 7.2 底部导航

- 保留当前自定义三项底栏，不迁移到新的导航框架。
- 固定 `92.dp` 改为内容驱动，并保证内容区最小约 72dp。
- 选中项使用 `primaryContainer` 胶囊、`primary` 图标和半粗体标签。
- 未选中项使用 `onSurfaceVariant`。
- 选中状态必须同时由形状、文字样式和语义表达，不能只变颜色。
- 每个 Tab 只有一个可操作无障碍节点。
- 子图标和文字不得形成重复焦点。
- 焦点移动不得自动切页；只有显式激活才切换。

### 7.3 列表与行

列表分为两种：

1. 导航/设置列表：使用一个分组 Surface，内部使用轻量缩进分隔线。
2. 会话/消息等内容记录：使用有 8dp 间距的轻量内容块。

不得在 `NativeAccessibleActionRow` 外层增加第二个可点击或可聚焦节点。视觉容器只负责背景、圆角、边框和间距。

原生行：

- 标题 16sp。
- 正文 14sp。
- Footer 12sp。
- 内容驱动高度，最小不得低于可用的 48dp 触控目标；常规双行内容目标约为 64–76dp。
- 主标题使用 `onSurface`。
- 正文使用 `onSurfaceVariant`。
- Footer 或可操作提示使用 `primary`。
- 深浅色 Ripple 与当前表面保持可见但克制。

### 7.4 状态组件

状态不得全部使用同一中性卡片语义。

| 状态 | 视觉 | 无障碍 |
| --- | --- | --- |
| 普通说明 | `surfaceContainer` | 无强制 live region |
| 加载 | Spinner + 可见加载文字 | polite；Spinner 隐藏重复语义 |
| 空状态 | 中性表面或普通文字 | 可在内容变化时 polite |
| 错误 | `errorContainer` | 默认 polite；只有立即阻断流程且需用户立刻处理的错误使用 assertive |
| 禁用 | 降低表面和文字强调 | 同时移除不可用操作 |
| 成功 | 通过列表、值或页面最终状态体现 | 不增加持久反馈行 |

可以增加一个只负责视觉外壳的 `AppInfoCard` 和一个明确的 `AppErrorMessage`。不得创建承载所有状态和业务动作的万能组件。

### 7.5 表单与按钮

- 每个输入框保留可见标签。
- 错误信息靠近对应字段。
- 主保存按钮统一使用现有原生可访问按钮。
- 原生按钮关闭全大写，最小高度 48dp。
- 主按钮使用 `primary/onPrimary`。
- 禁用按钮使用 `surfaceContainerHighest/onSurfaceVariant`，并移除点击。
- 输入获得焦点不得触发保存、联网、配对或命令执行。
- 页面内容加载必须提供可见加载状态；业务提交期间只禁用重复操作并在触发节点提供必要的非持久 busy 状态，不新增“正在保存/删除”等结果行。
- 业务错误仍需可见；相同错误不得因轮询或重组重复播报。

### 7.6 Dialog 与破坏性操作

- Dialog 内容允许滚动。
- “取消”和明确的破坏性动作始终可到达。
- 删除等不可逆操作继续使用现有确认流程。
- “同时删除本地历史记录”等复选框整行为一个焦点，子 Checkbox/Text 不形成重复节点。
- TalkBack 自定义删除动作和可见菜单必须进入同一个确认流程。
- 关闭后恢复到原触发项；原项已删除时恢复到下一项、上一项或页面主操作。

## 8. 分页面规格

### 8.1 工作区列表

- 当前设备信息改为紧凑上下文信息，不占据高大的主卡片。
- 工作区标题为主信息，路径为次要信息。
- 保留打开、长按菜单、自定义操作、稳定 ID 和焦点恢复。
- 加载、错误、空状态分别呈现。
- 不为每个工作区增加没有业务含义的图标。

### 8.2 会话列表

- 工作区名使用紧凑页面上下文，不使用只有一行标题的高卡片。
- 会话标题为主信息。
- 更新时间、分支和子代理数量为次要元数据。
- 长标题自然换行；不能依赖固定行高。
- 归档、删除、重命名、子代理等继续作为同一行的自定义操作。
- 删除后不显示持久成功提示，焦点落到可预测的相邻项。

### 8.3 聊天

- 使用全宽消息卡，不使用狭窄左右气泡。
- 用户消息使用 `primaryContainer`。
- 助手消息使用 `surfaceContainerLow`。
- 系统/工具消息使用 `secondaryContainer` 或中性高层表面。
- 错误消息使用 `errorContainer`。
- 每条消息保留可见角色标签；颜色不是唯一角色信号。
- 消息的复制、折叠和其他现有操作继续挂在同一消息节点。
- 消息视觉折叠或截断时，TalkBack 仍必须获得 `message.accessibilityText()` 的完整内容。
- 跳到底部操作至少 48×48dp，并提供简短无障碍名称。

### 8.4 Composer

- 外层使用圆角 `surfaceContainer` 输入面板。
- 正常宽度可保持输入框、附件和发送同行。
- 紧凑宽度、分屏或大字体时，输入框独占第一行，附件和发送位于第二行。
- 附件使用次级 tonal 样式。
- 发送使用主色样式。
- 保留原生 `EditText`、IME 发送、输入焦点和辅助功能焦点恢复链。
- 不在本阶段迁移到 Compose `TextField`。

### 8.5 附件 Bottom Sheet

- 图片、文件、插件和技能入口使用有明确边界的 tonal tile。
- 每个 tile 只有一个点击和无障碍节点。
- 顶层提供显式关闭操作。
- 子页保留“返回”。
- 搜索过滤结果为空时显示“没有匹配结果”。
- 加载、错误、空状态遵循统一状态规格。

### 8.6 Profile、设备与 Windows 设置

- 当前设备信息使用紧凑的 `primaryContainer` 卡片。
- 真实入口放入统一分组 Surface。
- “本机设置”用于主题模式和真实的本机偏好。
- 尚未实现的“诊断”先隐藏，不保留无行为的可点击占位行。
- Coding 与 Windows 设置使用同一 Section Heading 外观和 heading 语义。
- 两套设置页的主保存动作统一为原生可访问按钮。
- 选择项标题只显示选项名称，选中状态放入正文和无障碍名称。
- 所有 `enabled` 状态必须透传到原生行，禁用时节点不得继续报告可点击。

## 9. 无障碍硬性门槛

任何阶段若违反以下条目，不得进入下一阶段。

### 9.1 名称与结构

- 每个可聚焦或可操作控件有一个简短、明确的名称。
- 不在名称中重复“按钮”“标签页”等系统已播报的控件类型。
- 父节点已经描述整项时，子图标和文字不重复暴露。
- 页面和设置区标题使用 heading 语义。
- 状态不能只通过颜色、位置、图标或动画表达。

### 9.2 显式操作

- 焦点、选中、悬停和屏幕阅读器浏览不得触发联网、保存、命令、页面切换或删除。
- 真实工作只能由点击、双击、Enter、Space、IME Action 或明确菜单命令触发。
- 删除等破坏性动作必须进入确认流程。

### 9.3 焦点与返回

- 系统返回与可见返回执行同一层级导航。
- 返回前保存来源项的稳定 ID 和列表位置。
- 返回时先滚动到目标，再恢复普通焦点和辅助功能焦点。
- 焦点只恢复一次，不触发任何业务操作。
- 原项不存在时使用稳定的相邻项或页面主操作作为回退。
- Dialog 和 Bottom Sheet 不得形成无法退出的焦点陷阱。

### 9.4 尺寸、字体和对比度

- 所有独立操作目标至少 48×48dp。
- 普通正文对比度至少 4.5:1。
- 大号文字、图标、焦点轮廓和必要控件边界至少 3:1。
- 1.0、1.3 和 2.0 倍字体下无文字重叠或不可达操作。
- 高字体缩放时允许布局换行和页面滚动，不以裁切维持固定高度。

### 9.5 动态状态

- 加载和错误可见且可被辅助技术感知。
- 错误默认使用 polite；只有立即阻断流程且需用户立刻处理的错误使用 assertive。
- 相同状态不得因轮询或重组重复播报。
- 成功不创建持久反馈行；可见内容本身应反映最终结果。
- 业务操作期间禁用触发控件并防止重复请求；必要的 busy 语义附着在触发节点，完成后立即移除。
- 成功后不得遗留空 live-region 节点。

## 10. 分阶段实施计划

阶段 0 和阶段 1 的文件级顺序、API 分支、测试门禁与审阅结论见 [`ui-theme-phase-0-1-implementation-plan.md`](ui-theme-phase-0-1-implementation-plan.md)。

| 阶段 | 目标 | 主要交付 |
| --- | --- | --- |
| 0 | 修通主题链路 | 跟随系统、显式浅色/深色、原生 night 主题 |
| 1 | 建立视觉基础 | 完整颜色角色、排版、Shape、原生控件同步 |
| 2 | 改造应用外壳 | 顶栏、底栏、Profile、本机主题设置 |
| 3 | 统一内容页面 | 工作区、会话、设备、状态和设置 |
| 4 | 改造聊天体验 | 角色化消息、响应式 Composer、附件 Sheet |
| 5 | 完成整体验收 | 自动测试、真机主题、TalkBack 和大字体回归 |

阶段按顺序实施；每一阶段通过构建和本阶段验收后再进入下一阶段。

### 阶段 0：主题链路

目标：让应用真正具备一致、可覆盖的浅色和深色运行模式。

主要文件：

- `app/src/main/java/com/mycodex/mobile/MainActivity.kt`
- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`
- `app/src/main/java/com/mycodex/mobile/ui/theme/Theme.kt`
- `app/src/main/res/values/themes.xml`
- 新增 `app/src/main/res/values-night/themes.xml`

工作内容：

1. 增加“跟随系统 / 浅色 / 深色”主题模式。
2. 使用现有 `SharedPreferences` 保存主题偏好。
3. 在页面创建前统一解析原生主题和 Compose 主题。
4. 增加 night 原生主题资源。
5. 让现有“本机设置”入口打开一个最小主题选择界面；本阶段不扩展其他本机设置。
6. 所有 API 都在 Activity 创建前选择固定 Light/Dark style；不调用包级夜间模式 API。
7. 配置启动窗口和 edge-to-edge 系统栏。
8. 不读取 OEM 私有深色设置。

完成标准：

- 冷启动进入 Activity 后、热启动和进程恢复使用正确主题。
- 系统浅色/深色和两个显式覆盖模式都可验证。
- 当前 Android 15 真机能够显式进入完整深色模式。
- 跟随系统模式在所有支持版本无主题闪烁。
- 所有 API 的显式覆盖允许 starting window 短暂沿用系统主题，进入 Activity 后必须一致。
- 本阶段不改业务页面布局。

### 阶段 1：全局 token 与原生控件

目标：建立 Compose 和原生 View 共用的最小视觉基础。

主要文件：

- `app/src/main/java/com/mycodex/mobile/ui/theme/Color.kt`
- `app/src/main/java/com/mycodex/mobile/ui/theme/Theme.kt`
- `app/src/main/java/com/mycodex/mobile/ui/theme/Type.kt`
- `app/src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt`
- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`（仅一处已知 `enabled` 透传）
- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`（仅两处已知 `enabled` 透传）
- `app/src/test/java/com/mycodex/mobile/ui/theme/ThemeColorContrastTest.kt`

工作内容：

1. 补齐浅色和深色 Material 3 颜色角色。
2. 明确 Typography 和 Shapes。
3. 同步原生行、输入框、按钮、Ripple 和禁用态。
4. 为原生行补充必要的最小触控高度，但不固定裁切内容。
5. 扩展颜色对比度测试。
6. 提前修复三处已经定位的选择行 `enabled` 未透传；阶段 3 继续复核其余调用点。

完成标准：

- 未出现 Material 默认紫色角色混入。
- Compose 与原生 Button/EditText 在浅深色下无明显风格断层。
- 原生控件的焦点、点击、自定义操作和恢复逻辑未改变。
- 所有实际文字/容器组合通过对比度测试。

### 阶段 2：应用外壳与 Profile

目标：先改造用户每次都会看到的顶栏、底栏和 Profile。

主要文件：

- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`
- `app/src/androidTest/java/com/mycodex/mobile/MainScaffoldTest.kt`
- `app/src/test/java/com/mycodex/mobile/ui/theme/ThemeColorContrastTest.kt`（只补阶段 2 实际 Surface 组合）

工作内容：

1. 调整顶栏表面、标题和动态长标题处理。
2. 将底栏从固定 92dp 改为自适应高度。
3. 增加非颜色唯一的选中胶囊。
4. 统一 Profile 当前设备卡和入口分组。
5. 将阶段 0 的最小主题选择入口调整为统一视觉样式。
6. 隐藏尚未实现的诊断入口。

完成标准：

- 底栏仍只有“编程 / 办公 / 我的”三个可操作语义节点。
- 切换后新旧选中状态正确更新。
- 顶栏和底栏所有操作至少 48×48dp。
- Profile 不存在可点击但无行为的入口。
- 浅色、深色和 2.0 倍字体下均可正常布局。

### 阶段 3：列表、状态与设置

目标：统一工作区、会话、设备、Coding 设置和 Windows 设置。

详细的文件边界、焦点回落算法、工作包、自动门禁、真机矩阵与主线审阅结论见 [`ui-theme-phase-3-implementation-plan.md`](ui-theme-phase-3-implementation-plan.md)。

主要文件：

- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`
- `app/src/main/java/com/mycodex/mobile/devices/DeviceScreens.kt`
- `app/src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt`
- 必要时新增一个小型共享 UI 文件

允许新增的最小复用：

- `AppSectionHeading`
- `AppInfoCard`
- `AppErrorMessage`
- `AppListDivider`
- `AccessibleChoiceRow`
- `AccessibleSettingsSaveButton`

若现有私有函数可以直接统一，则不新增对应组件。

工作内容：

1. 统一设置区标题、heading 语义和分组方式。
2. 区分普通说明、加载、空状态和错误。
3. 统一连续列表分隔规则。
4. 统一设置页主保存动作。
5. 复核选择行 `enabled` 透传，确认阶段 1 已修复的三处没有同类遗漏。
6. 保留所有稳定 key、自定义操作和焦点返回参数。

完成标准：

- 加载有可读文字，错误使用正确 live region。
- 禁用行不再报告可点击或暴露自定义操作。
- 工作区、会话、设备和设置列表在浅深色下属于同一视觉体系。
- 删除、归档、重命名、设置保存和逐级返回行为未改变。

### 阶段 4：聊天与附件

目标：改善消息阅读层级和输入区自适应能力。

详细的文件边界、响应式判断、焦点恢复、远程交互 Dialog、工作包、自动门禁、真机矩阵与主线审阅结论见 [`ui-theme-phase-4-implementation-plan.md`](ui-theme-phase-4-implementation-plan.md)。

实施状态：已完成。角色化消息、响应式 Composer、附件 Sheet 和远程工具输入问题层级已经落地，四项自动门禁通过；Android 15 / HyperOS 真机已覆盖浅色/深色、1.0/1.3/2.0 字体、IME、Sheet 两级导航、搜索无结果以及 Windows 联机目录加载。远程审批/计划/工具输入 Dialog 的无副作用实机触发、横屏/分屏、实体键盘和全应用 TalkBack 穷举回归并入阶段 5。

主要文件：

- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`
- `app/src/main/java/com/mycodex/mobile/coding/CodingComposer.kt`
- `app/src/main/java/com/mycodex/mobile/coding/CodingAttachmentSheet.kt`
- `app/src/main/java/com/mycodex/mobile/coding/RemoteApprovalDialog.kt`
- `app/src/main/java/com/mycodex/mobile/coding/RemotePlanExecutionDialog.kt`
- `app/src/main/java/com/mycodex/mobile/coding/RemoteToolInputDialog.kt`

工作内容：

1. 增加全宽角色化消息表面和可见角色标签。
2. 保留消息节点的自定义操作和单焦点结构。
3. Composer 在紧凑宽度或大字体时换成两行。
4. 统一附件、发送和禁用状态。
5. 为附件入口增加明确视觉边界。
6. 增加 Sheet 显式关闭和搜索无结果状态。
7. 统一远程交互 Dialog 的滚动可达性、状态表达和关闭焦点。

完成标准：

- 长消息和长会话标题不被固定高度裁切。
- 2.0 倍字体和 IME 打开时，输入、附件、发送、停止和关闭操作仍可到达。
- 输入框焦点变化不提交、保存或联网。
- 关闭 Sheet 或返回页面后焦点恢复正确。
- 审批、计划执行和工具输入 Dialog 在 2.0 倍字体及 IME 打开时仍可滚动到正文和全部明确操作，关闭后焦点不丢失。

### 阶段 5：整体验收与清理

目标：完成真实设备回归，移除迁移后留下的重复私有组件。

详细的数据隔离、测试分层、清理边界、真机矩阵、Windows 联机回归、系统恢复和主线审阅结论见 [`ui-theme-phase-5-implementation-plan.md`](ui-theme-phase-5-implementation-plan.md)。

实施状态：已完成。全量 29 项 instrumentation 已在隔离 API 36 AVD 通过；Android 15 真机只使用 `adb install -r` 覆盖安装，设备身份、主题偏好和 `JINTONGLU` Windows 配对均保留。真机已覆盖系统深色、跟随系统、1.0/1.3/2.0 字体、VoiceBack 语义、三键导航、IME 和 Windows 设置只读加载；模拟器补充覆盖横屏、深色、2.0 字体和手势导航。详细记录、APK 哈希和唯一的 HyperOS OEM 分屏人工边界见阶段 5 文档第 17 节。

工作内容：

1. 执行 JVM 和 instrumentation 测试。
2. 在当前 Android 15 真机安装 Debug 版。
3. 分别检查浅色、深色和跟随系统模式。
4. 使用 TalkBack 线性浏览、操作菜单、双击激活和系统返回。
5. 检查 1.0、1.3、2.0 倍字体。
6. 检查手势导航、三键导航、横屏、IME 和长文本。
7. 删除已经完全被替代的重复状态卡或标题函数。

完成标准：

- 所有自动测试通过。
- 所有硬性无障碍门槛通过。
- 无重复 UI helper 或死代码。
- 无新增第三方依赖。
- README 与实际完成状态一致。

## 11. 测试矩阵

| 维度 | 必测流程 | 通过标准 |
| --- | --- | --- |
| 主题 | 跟随系统、浅色覆盖、深色覆盖、冷启动、恢复进程 | Activity/Compose/原生 View 一致；跟随系统的启动窗口一致；显式覆盖接受第 6.1 节记录的首帧边界 |
| TalkBack | 底栏、工作区、会话、消息、设备、设置、Dialog、Sheet | 每项一个短名称；无重复或匿名节点；自定义操作完整 |
| 字体 | 1.0 / 1.3 / 2.0 | 无重叠、裁切或不可达按钮；内容可滚动 |
| 触控 | 顶栏、底栏、原生行、附件、移除、保存、Dialog | 每个独立操作至少 48×48dp |
| 对比度 | 正文、辅助文字、按钮、选中、错误、禁用、焦点 | 正文至少 4.5:1；图标和必要边界至少 3:1 |
| 焦点恢复 | 逐级返回、关闭 Dialog/Sheet、删除或归档项目 | 回到原项或稳定回退项；只移动一次；不触发业务 |
| 输入 | 获得焦点、IME Send、附件、发送、停止 | 浏览和聚焦不执行工作；只有显式操作执行 |
| 状态 | 加载、空、错误、成功 | 加载/错误可感知；成功不产生持久反馈行 |

自动化测试至少包括：

- 扩展 `ThemeColorContrastTest` 覆盖新增颜色角色。
- 扩展 `MainScaffoldTest` 验证三项底栏的单节点和选中状态。
- 验证禁用原生行不暴露点击、自定义操作或长按。
- 验证删除确认框和复选框行的单焦点结构。
- 验证系统返回逐级导航和目标节点重新出现。
- 验证消息视觉折叠时仍暴露完整 `message.accessibilityText()`。
- 验证轮询和重组不会重复产生相同 live-region 播报。

真实 TalkBack 焦点是否正确恢复不能只由 Compose 节点存在性测试替代，必须保留真机人工验收。

## 12. 风险与控制

| 风险 | 控制措施 |
| --- | --- |
| Compose 与原生 View 深色不一致 | 阶段 0 先修原生主题，阶段 1 再统一控件色 |
| OEM 深色信号与用户预期不一致 | 默认跟随标准 `uiMode`，提供显式浅色/深色覆盖 |
| 显式覆盖的 starting window 无法在进程代码运行前读取应用偏好 | 所有 API 接受启动窗口短暂沿用系统主题；Activity 创建前立即应用固定主题 |
| 视觉容器产生双焦点 | 点击和语义始终只保留在现有原生行根节点 |
| 布局优化破坏焦点恢复 | 不重写原生控件；完整透传 restore 参数和稳定 ID |
| 大字体导致按钮被挤出 | 使用内容驱动高度、滚动和 Composer 响应式换行 |
| 一次改动过大难以回归 | 按阶段独立构建、测试、安装和真机确认 |
| 复用组件演变为设计系统框架 | 只抽取已经重复且本阶段确实使用的最小组件 |

## 13. 完成定义

统一 UI 改造只有在以下条件全部满足后才算完成：

- 浅色、深色和跟随系统模式均可用。
- 当前真机可通过显式深色模式获得完整深色界面。
- Compose、原生 View 和系统栏视觉一致；启动窗口满足第 6.1 节记录的行为边界。
- 工作区、会话、聊天、附件、Profile、设备和设置页面完成统一。
- 所有操作目标、对比度、大字体和 TalkBack 验收通过。
- 删除、归档、重命名、设置、配对和聊天行为没有回归。
- 不存在可点击但无实际行为的占位入口。
- 不恢复自动化界面或持久操作成功反馈。
- 自动测试通过，Debug APK 已在真机验证。
- 没有为本次改造新增不必要依赖或架构层。

## 14. 延后决策

以下内容只有在出现明确产品需求时再评估：

- Material You 动态色。
- 自定义字体。
- Badge 或未读计数。
- 动画和复杂过渡。
- Markdown/代码高亮。
- 截图测试框架。
- 大屏、折叠屏专用双栏布局。
- 诊断功能。
