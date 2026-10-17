# MyCodex Android UI 阶段 3 详细实施计划

| 项目 | 内容 |
| --- | --- |
| 状态 | 已实施、完成主线复审并通过真机验收 |
| 版本 | 1.1 |
| 日期 | 2026-07-30 |
| 基线提交 | `8861b6b` |
| 前置阶段 | 阶段 0–2 已实施并通过 Android 15 / HyperOS 真机验收 |
| 对应规格 | [`ui-theme-accessibility-spec.md`](ui-theme-accessibility-spec.md) 的阶段 3 |
| 实施范围 | 工作区、会话、设备、Coding 设置与 Windows 设置的列表、状态、焦点和表单视觉统一 |

## 1. 文档目的

本文把阶段 3 拆成可直接编码、测试、审阅和真机验收的工作包。

本阶段只处理数据列表、状态表达、设置分组、显式保存和与这些界面直接相关的焦点恢复。聊天消息、Composer、附件 Sheet 和远程交互 Dialog 留在阶段 4；不借本阶段重写导航、仓储层或业务协议。

阶段 3 必须先解决删除、归档及 Dialog 关闭后的稳定焦点回落，再做视觉统一。视觉完成但 TalkBack 焦点丢失，不视为阶段完成。

## 2. 最终交付结果

阶段 3 完成后：

1. 工作区、会话和设备列表使用同一套紧凑行距、缩进分隔线和主题颜色。
2. 每个业务行仍只有一个可访问焦点；点击、长按和自定义无障碍操作保持同一业务路径。
3. 工作区名称、会话标题和设备名称为主要信息，路径、更新时间、分支、子代理及设备状态为次要信息。
4. 信息、加载、空态和错误不再共用同一种状态表达。
5. 加载状态同时有可见文字和进度指示，但进度图形不制造重复朗读节点。
6. 普通信息不使用 live region；加载、动态空态和可恢复错误按规格使用克制的 polite 公告。
7. 删除或归档成功后不显示成功回执；焦点依次回落到下一项、上一项或页面内的稳定主目标。
8. Dialog 取消或非破坏性选择后回到触发项；确认删除后不再尝试聚焦已消失的节点。
9. Coding 设置和 Windows 设置使用一致的 heading、分组 Surface、选择行和原生保存按钮。
10. 选择控件只修改本地草稿；只有显式点击“保存”才触发远程写入。
11. 所有禁用行、按钮、开关和复选框都不可点击，并且不暴露长按或自定义操作。
12. 浅色、深色、跟随系统和 1.0/1.3/2.0 字体下均不裁切关键内容。

## 3. 范围边界

### 3.1 生产代码

主要修改：

- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`
- `app/src/main/java/com/mycodex/mobile/devices/DeviceScreens.kt`
- `app/src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt`

只有在完成焦点回落所必需时才修改：

- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`
- `app/src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt`

默认不新增生产源码文件。若编码后确认至少两个功能文件存在完全相同、无法通过现有私有函数消除的状态卡实现，才允许新增一个小型共享 UI 文件；文件中只放本阶段实际使用的组件。

### 3.2 自动测试

主要修改或新增：

- `app/src/test/java/com/mycodex/mobile/AccessibilityActionContractTest.kt`
- `app/src/test/java/com/mycodex/mobile/devices/DeviceScreensSourceTest.kt`
- `app/src/test/java/com/mycodex/mobile/settings/WindowsSettingsSourceContractTest.kt`
- `app/src/androidTest/java/com/mycodex/mobile/AccessibleNativeControlsThemeTest.kt`
- `app/src/androidTest/java/com/mycodex/mobile/MainScaffoldTest.kt`

允许新增：

- 一个只测试相邻项回落算法的 JVM 测试文件。
- 一个组合工作区、会话或设置内容的 instrumentation 测试文件；只有现有测试宿主无法承载时才新增。

现有 repository、controller、主题、导航和 transient state 测试必须继续通过，不为适配 UI 修改而降低断言。

### 3.3 文档

实施完成后更新：

- `README.md`
- `docs/ui-theme-accessibility-spec.md`
- 本文的状态、实际变更、自动测试和真机验收记录

### 3.4 明确不做

- 不修改远程命令名、请求载荷、仓储层成功条件或错误码映射。
- 不改变工作区、会话、设备和设置页面的导航层级。
- 不迁移到 Navigation Compose，不引入新的状态管理框架。
- 不把工作区、会话和设备强行抽象成通用泛型列表。
- 不新增设计 token 层、组件工厂、依赖注入框架或第三方 UI 依赖。
- 不增加成功 Toast、Snackbar、横幅或持久操作回执。
- 不用图标替换现有可见文字操作。
- 不在本阶段改造聊天消息、Composer、附件、审批、计划执行或工具输入界面。
- 不把错误默认设为 assertive；只有阻断且需立即处理的错误才允许使用。
- 不用“节点重新出现”或普通 Compose 输入焦点代替 TalkBack 辅助功能焦点验收。

## 4. 当前实现审计

### 4.1 工作区与会话

| 区域 | 当前实现 | 已有基础 | 阶段 3 缺口 |
| --- | --- | --- | --- |
| 工作区列表 | `CodingWorkspaceListPage` 使用稳定 `workspace.id` key 和 `CodingWorkspaceRow` | 原生单节点行；点击打开；长按操作；归档/删除自定义操作 | 状态全部进入 `CodingStatusPanel`；分隔线未缩进；删除/归档成功后原目标消失时没有相邻项回落 |
| 会话列表 | `CodingSessionListPage` 使用稳定 `session.id` key 和 `CodingSessionRow` | 重命名、子代理、归档、删除均有自定义操作；操作期间移除 action | 行正文为空，标题把多种元数据拼成一段；状态未分类；删除/归档缺少相邻项回落 |
| 工作区操作 | `runWorkspaceOperation` 成功后清空状态并 reload | 已没有成功回执 | reload 前未计算删除后的稳定目标；失败和进行中共用普通状态正文 |
| 会话操作 | `runSessionOperation`、`runSessionRename`、子代理删除后 reload | 成功时不保留回执；失败可见 | 删除、归档和批量子代理操作的焦点目标未按结果更新 |
| Dialog 返回 | `interruptedFocusTarget` 记录源行 | 源节点仍存在时可由原生行恢复 | 仅以源 ID 恢复；确认删除后源节点消失，状态无法被消费 |
| 删除选项 | 删除 Dialog 内 Row + Checkbox | 整行可切换 | 需要固定为一个复选框语义节点，隐藏子 Checkbox 的重复语义，并验证 2.0 字体 |
| 状态卡 | `CodingStatusPanel` 的正文统一 polite | 有主题 Surface，文字对比度可继承 | 普通上下文、加载、空态、错误和操作失败全部被同样公告，可能重复朗读 |

### 4.2 设备

| 区域 | 当前实现 | 已有基础 | 阶段 3 缺口 |
| --- | --- | --- | --- |
| 设备列表 | `DeviceManagementPage` + `DeviceRow` | 稳定 ID；原生单节点；新增按钮也是原生按钮 | 分隔线未缩进；空态与详情摘要使用同一 `DeviceStatusPanel`；视觉未与 Coding 列表完全对齐 |
| 设备删除 | 详情页确认后由 `MyCodexApp` 删除并回到列表 | 已明确回退到“新增设备”按钮 | 未优先选择删除项相邻设备；需验证删除最后一项时原生新增按钮真实恢复 TalkBack 焦点 |
| 新增设备 | 等待状态不会在每次 poll 重建 | 已有可见短码、取消入口和 polite 状态 | 本阶段只统一状态颜色与间距，不重写配对流程 |
| 设备详情 | 测试连接使用可见文字和 polite 状态 | 操作必须显式点击，成功后不保留回执 | 加载和错误使用相同文字颜色；删除 Dialog 取消后未显式回到删除按钮 |

### 4.3 Coding 设置

| 区域 | 当前实现 | 已有基础 | 阶段 3 缺口 |
| --- | --- | --- | --- |
| 页面摘要 | `CodingStatusPanel` 显示会话标题和状态 | 信息完整 | 加载、错误、未返回和普通说明未分型 |
| 分节标题 | `SettingsSectionTitle` 已设置 heading | 语义方向正确 | 视觉需与 Windows 设置统一，避免两套相似 helper |
| 选择行 | `SettingsChoiceRow` 使用原生行，`enabled` 已透传 | 单节点和禁用态基础可靠 | 标题同时承载“跟随默认，当前值”等长状态；需要拆成短标题与正文/选中状态 |
| 保存 | 当前使用 Compose `Button` | 只有显式点击才保存 | 改用已验证的 `NativeAccessibleButton`；保留成功返回和失败留页行为 |

### 4.4 Windows 设置

| 区域 | 当前实现 | 已有基础 | 阶段 3 缺口 |
| --- | --- | --- | --- |
| 根页和子页 | `SettingsPage`、`SettingsHeading`、`SettingsNavigationRow`、`SettingsValueRow` | heading、原生行、原生保存按钮和稳定 tag 已存在 | 各页面容器仍是平铺；divider 规则不一致；helper 命名和 Coding 设置不统一 |
| 加载 | `QuietProgress` 只有隐藏语义的 spinner | 不会重复朗读 spinner | 没有可见“正在加载/正在保存”文字，不满足双通道状态表达 |
| 错误 | `SettingsError` 一律 assertive | 错误可见 | 可恢复的加载/保存错误不应抢占式公告；需使用 `errorContainer` 和 polite |
| 选择 Dialog | 选项为原生行，正文显示已选/未选 | 可滚动，取消不提交当前值 | 标题应只读选项名；选中状态放正文和无障碍描述；关闭后继续回触发项 |
| 开关/复选框 | 根 `toggleable`，子控件清空语义 | 已接近一行一焦点 | 必须复核 enabled 完整透传及 Dialog 返回焦点，不改为自制控件 |
| 命令前缀删除 | 取消回删除项；确认后回新增项 | 已有确定回退 | 删除中间项时应优先相邻项；不能依赖索引 tag 在列表变化后指向不同业务项而不做说明 |

### 4.5 原生可访问控件与测试

现有 `NativeAccessibleActionRow`、`NativeAccessibleTextInput` 和 `NativeAccessibleButton` 已具备：

- 原生 View 身份在主题切换时保持不变。
- `requestFocus()` 后发送 `ACTION_ACCESSIBILITY_FOCUS`。
- `restoreFocusGeneration` 可保证同一目标的不同恢复请求各执行一次。
- 禁用时移除 click、long-click 和自定义 accessibility action。
- 行内标题、正文和 footer 不单独进入无障碍树。
- 48dp 最小尺寸、主题颜色和字体缩放测试。

阶段 3 不复制这些机制。调用方只传递稳定目标、generation 和 enabled；确有必要扩展时，只增加状态卡或顶级主目标所需的最小参数。

## 5. 已确定的实现决策

### 5.1 保留业务结构，统一视觉层

工作区、会话、设备和设置继续保留各自的数据模型、回调和页面函数。统一的是以下可见规则，而不是业务抽象：

- 页面水平 padding：20dp。
- 连续数据列表：行之间使用 `outlineVariant` divider，左侧缩进与正文起点一致。
- 设置列表：section heading 后使用分组 Surface；同组内部才显示 divider。
- 行高度由内容和 48dp 最小触控要求共同决定，不固定最大高度。
- 标题使用 `onSurface`，正文使用 `onSurfaceVariant`，操作提示可使用 `primary`。
- 视觉 Surface、divider 和分组容器不添加点击、focus 或重复 content description。

### 5.2 状态类型

本阶段只实现实际出现的四种状态：

| 类型 | 容器与颜色 | 语义 | 用途 |
| --- | --- | --- | --- |
| 信息 | `surfaceContainer` / `onSurfaceVariant` | 无 live region | 当前设备、当前工作区、设置作用范围等稳定上下文 |
| 加载 | 中性容器、spinner、可见文字 | 文字 polite；spinner 清空语义 | 首次加载、显式刷新、保存中 |
| 空态 | 中性容器 | 初次进入不强制公告；从非空变为空时 polite | 没有工作区、会话或设备 |
| 错误 | `errorContainer` / `onErrorContainer` | 默认 polite | 加载失败、保存失败、删除/归档失败 |

不创建通用状态枚举或状态渲染框架。优先用少量私有 Composable 直接表达页面当前分支。

为避免轮询或重组重复公告：

- live region 文本只依赖稳定的用户可见状态，不包含 tick、时间戳或请求对象身份。
- 同一个加载周期不反复清空再写回相同错误。
- spinner 使用 `clearAndSetSemantics {}`，只让可见文字承担公告。
- 普通上下文卡不得设置 live region。

### 5.3 工作区与会话行

工作区行保持：

- 标题：`workspace.name`。
- 正文：路径；缺失时显示“路径未返回”。
- 可见行和 accessibility content description 使用同一信息顺序。
- 点击打开、长按菜单、归档和删除自定义操作不变。

会话行调整为：

- 标题：仅会话标题，可自然换行，不截断语义文本。
- 正文：更新时间、分支类型和子代理数量中实际存在的字段。
- 不为了对齐保留空正文。
- 自定义操作仍为重命名、子代理、归档、删除。
- 行 enabled 为 false 时，点击、长按和全部自定义操作同时消失。

元数据格式继续复用现有显示函数；若现有 `displayText()` 把标题和元数据耦合，只提取一个纯函数分别返回标题和正文，不创建 presentation model 层。

### 5.4 删除、归档和相邻项回落

对会让源节点消失的操作，使用以下确定顺序：

1. 在发起操作前保存源稳定 ID 和操作前的稳定 ID 顺序。
2. 操作成功并取得刷新后的列表后，先查找操作前源位置对应的“下一项”。
3. 下一项不存在时选择操作前的上一项。
4. 候选项也已被并发删除时，在刷新后列表中按最接近原索引的位置再次选择。
5. 刷新后列表为空时，回退到页面内可实际获得普通焦点和辅助功能焦点的主目标。
6. 先 `scrollToItem`，待目标 View 挂载后，再由原生控件执行 `requestFocus()` 和 `ACTION_ACCESSIBILITY_FOCUS`。
7. `onFocusRestored` 成功回调后消费目标；同一 generation 不重复恢复。

回退算法保持为一个纯函数，例如输入“旧 ID 顺序、源 ID、新 ID 顺序”，输出目标 ID 或 null。只在工作区、会话和设备三处共同需要时共享；否则保留在对应包内。

具体页面策略：

- 删除/归档工作区：下一工作区 → 上一工作区 → 可聚焦的空态/页面主目标。
- 删除/归档会话：下一会话 → 上一会话 → 空态中的原生“新建会话”主按钮。
- 删除设备：下一设备 → 上一设备 → 已存在的原生“新增设备”按钮。
- 删除命令前缀：下一前缀的编辑行 → 上一前缀的编辑行 → “添加命令前缀”行。
- 重命名成功：目标仍存在，恢复到同一稳定 session ID。
- 操作失败：源节点仍存在，恢复到源节点并显示错误；不跳到相邻项。

会话空态中的“新建会话”按钮与顶栏“新建”调用同一个回调。它只在列表为空时出现，因此不是常驻重复操作；它提供符合原生恢复机制的最后回退目标。

工作区没有本地新建入口。最后一个工作区消失时，空态卡本身必须提供可恢复的只读焦点目标，但不得伪装成按钮或暴露点击动作。若现有原生控件不能表达只读可聚焦状态，只增加一个最小的原生信息节点实现；不得用 enabled 的空操作行冒充。

### 5.5 Dialog 焦点

统一规则：

- 打开 Dialog 前保存触发项稳定 ID/tag。
- 系统返回、点外部关闭、取消按钮和非破坏性选择完成后，恢复触发项。
- 确认删除后，关闭 Dialog，但延迟到数据刷新成功后选择相邻项。
- 确认失败后，恢复源项并展示错误。
- 从操作菜单进入删除确认时，最终仍以原业务行作为源，不以菜单按钮作为列表回退目标。
- Dialog 内容可滚动；取消和确认操作在 2.0 字体下可到达。
- 删除本地历史记录的“复选框 + 标签”使用一个根 `toggleable` 节点，子 Checkbox 使用 `clearAndSetSemantics {}`。
- 可见菜单和 accessibility custom action 必须进入同一个确认状态变量和同一个执行函数。

不新增全局 Dialog 管理器或焦点栈。

### 5.6 设置分组与选择行

Coding 和 Windows 设置使用一致规则：

- section title 使用 `titleMedium`、`primary` 和 `heading()`。
- 每节内容放入无交互语义的 `surfaceContainer` Surface。
- 组内 divider 缩进到正文起点；最后一行后不绘制 divider。
- 选择行标题只包含设置名或选项名。
- 当前值、说明和“已选中/未选中”放入正文及 accessibility description。
- 长说明允许自然换行。
- disabled 状态仍可被浏览和朗读，但不暴露 click、long-click 或 custom action。

选择 Dialog：

- 继续使用可滚动 `LazyColumn`。
- 每个选项一个原生行。
- 标题为选项名称，正文为选中状态。
- 选择只更新草稿并关闭 Dialog，不直接保存。
- 关闭后恢复触发行。

### 5.7 显式保存

Coding 设置的 Compose `Button` 改用现有 `NativeAccessibleButton`，与 Windows 设置保存按钮一致。

保存规则：

- 只有点击“保存”或等价键盘/辅助功能激活才调用 repository/controller。
- 输入焦点、浏览选项、切换 TalkBack 焦点、重组、主题切换和返回焦点不得保存。
- 加载、保存中或必要数据缺失时，保存按钮 disabled。
- 保存中显示可见文字“正在保存”，spinner 隐藏重复语义。
- 保存成功沿用现有返回路径，不增加成功回执。
- 保存失败留在当前页面，显示 errorContainer + polite 错误，并将焦点保持在可继续操作的区域。

### 5.8 最小复用策略

规格中允许的 `AppSectionHeading`、`AppInfoCard`、`AppErrorMessage`、`AppListDivider`、`AccessibleChoiceRow` 和 `AccessibleSettingsSaveButton` 都是候选名，不是必须全部创建。

实施时按以下顺序：

1. 先原位统一现有 `SettingsSectionTitle` / `SettingsHeading`、`CodingStatusPanel` / `DeviceStatusPanel`、`SettingsChoiceRow` / `SettingsValueRow`。
2. 只有两个以上文件仍存在相同实现时才提取。
3. 已能由 `NativeAccessibleActionRow` 或 `NativeAccessibleButton` 完成的功能不再包一层无行为组件。
4. 共享函数只接收实际需要的参数，不预留 icon、trailing、badge、animation 或未来 style variant。
5. 阶段结束前删除被替代的私有 helper 和未使用 import。

## 6. 分步实施顺序

### 6.1 工作包 3A：回归基线与焦点契约

目标：先让后续视觉修改有可验证的行为护栏。

步骤：

1. 为相邻项回落算法写 JVM 测试：
   - 删除中间项选择下一项。
   - 删除最后一项选择上一项。
   - 新列表发生并发变化时选择最接近原索引的有效项。
   - 新列表为空返回 null。
2. 扩展原生控件 instrumentation：
   - 同一 target + 同一 generation 只恢复一次。
   - 新 generation 可再次恢复。
   - disabled 行无 click、long-click 和 custom action。
3. 为删除 Dialog 的复选框单节点语义增加测试。
4. 为系统返回和 Dialog 取消后源节点重新获得焦点增加可自动验证的普通焦点断言。
5. 明确 TalkBack 辅助功能焦点仍必须在真机人工验收，自动测试不作虚假替代。

完成门槛：

- 焦点算法为纯函数，无 Compose 或 repository 依赖。
- 现有业务成功/失败分支没有改动。
- 后续视觉修改前所有新增测试先能表达真实缺口。

### 6.2 工作包 3B：工作区、会话与设备焦点回落

目标：补齐规格中已知的高风险缺口。

步骤：

1. 在工作区、会话和设备删除/归档前捕获源稳定 ID 与旧顺序。
2. 操作成功 reload 后计算下一/上一目标。
3. 先滚动到目标，再触发原生行 restore。
4. 为每次新恢复请求递增 generation，避免相同 target 被误判为已处理。
5. 操作失败恢复源项；失败文本进入错误状态。
6. Dialog 取消、系统返回、重命名完成和设置选择关闭恢复源项。
7. 为会话空态增加原生“新建会话”主按钮并接入现有创建回调。
8. 为工作区空态提供不带点击动作的可恢复只读目标。
9. 删除设备优先相邻设备，只有列表为空时回“新增设备”。
10. 删除命令前缀使用相邻稳定业务值或明确 tag，不让变化后的旧索引静默指向错误内容。

完成门槛：

- 删除/归档后焦点不会落到屏幕根、状态栏或已消失节点。
- 取消/失败回源，成功回相邻项，空列表回稳定主目标。
- 没有新增成功提示。

### 6.3 工作包 3C：列表和状态视觉统一

目标：统一 Coding 与设备页面的数据密度和状态层级。

步骤：

1. 工作区、会话、设备列表使用一致的水平 padding 和缩进 divider。
2. 会话标题与元数据分离；长标题自然换行。
3. 将上下文信息、加载、空态和错误拆为对应视觉分支。
4. loading 同时显示 spinner 和文字，spinner 清空语义。
5. 普通信息移除 live region。
6. 错误使用 `errorContainer` / `onErrorContainer` 和 polite。
7. Add Device 与 Device Detail 只做同规则的状态颜色、间距和 live region 修正，不改配对、连接和删除业务。
8. 检查 Surface/divider 不进入无障碍树，不增加第二点击节点。

完成门槛：

- 三类列表在浅色和深色下属于同一视觉系统。
- TalkBack 每行只停一次。
- 状态文本不会因轮询或重组反复公告。

### 6.4 工作包 3D：Coding 与 Windows 设置统一

目标：统一设置结构并强化显式保存边界。

步骤：

1. 统一 section heading 样式和 heading 语义。
2. 以无交互 Surface 分组设置行，规范组内 divider。
3. 调整选择行的标题、正文和选中状态信息。
4. Coding 设置保存改为 `NativeAccessibleButton`。
5. Windows `QuietProgress` 增加可见加载/保存文字。
6. Windows `SettingsError` 改为 errorContainer + polite。
7. 复核所有 `enabled`：
   - 导航行。
   - 值选择行。
   - 快速模式 switch。
   - 权限 checkbox。
   - 命令前缀添加、编辑、删除。
   - 三个保存按钮。
8. 复核所有选择 Dialog 和确认 Dialog 的关闭焦点。
9. 保持 controller 的草稿、确认和保存 API 不变。

完成门槛：

- Coding 与 Windows 设置的节标题、分组、选择行和保存入口一致。
- 浏览、聚焦、切换主题或重组不会产生远程写入。
- disabled 控件没有残留激活动作。

### 6.5 工作包 3E：清理、自动门禁与真机验收

步骤：

1. 删除重复 helper、死代码和未使用 import。
2. 更新 source contract，使其断言行为契约，不绑定无关实现细节。
3. 扩展实际新增颜色组合的对比度测试。
4. 依次执行 JVM test、lint、debug APK 和 debug androidTest APK 构建。
5. 只用覆盖安装把 debug APK 装到已连接手机。
6. 按第 9 节矩阵完成浅色、深色、字体、TalkBack、键盘和系统导航验收。
7. 更新本文与总规格的完成状态。

## 7. 文件级改动清单

### 7.1 `CodingScreens.kt`

- 拆分 `CodingStatusPanel` 的信息、加载、空态和错误职责。
- 调整 workspace/session list divider。
- 将会话标题与元数据分开。
- 为 workspace/session 操作接入稳定 ID 回落和 generation。
- 补齐 Dialog 取消、失败、重命名与确认删除的焦点路径。
- 修正删除本地历史复选行的单节点语义。
- 统一 Coding 设置 heading、分组、选择行、状态和原生保存按钮。
- 保持消息列表及聊天 Dialog 不变。

### 7.2 `DeviceScreens.kt`

- 统一设备列表和状态视觉。
- 删除设备后优先相邻项，再回新增按钮。
- 删除 Dialog 取消后回删除按钮。
- 将连接测试 loading/error 分型。
- 保留 PairingController 的单次 Waiting 状态策略。

### 7.3 `WindowsSettingsScreens.kt`

- 统一 settings group、heading、divider 和状态。
- 让 loading/saving 有可见文字。
- 将可恢复错误改为 polite errorContainer。
- 复核选择行、switch、checkbox、命令前缀和保存按钮 enabled。
- 补齐选择/确认 Dialog 的触发项恢复。
- 删除与 Coding 设置重复且已被共享实现替代的 helper。

### 7.4 `MyCodexApp.kt`

仅在子页面需要调用现有顶栏主操作或设备删除需要传递相邻项目标时修改：

- 向会话空态传递现有新建会话回调。
- 设备删除前计算或接收回落目标。
- 保留所有 route、标题、返回逻辑和阶段 2 外壳布局。
- 不重写 `MyCodexTopBar` 或底部导航。

### 7.5 `AccessibleNativeControls.kt`

默认不改。只有只读信息目标无法用现有原生机制正确恢复时，增加一个最小的只读焦点组件或参数：

- 无 click、long-click、custom action。
- 有明确简短名称。
- 支持普通焦点和 `ACTION_ACCESSIBILITY_FOCUS`。
- 支持 generation 去重。
- 主题切换保持 View 身份。

不得把 `NativeAccessibleActionRow` 复制成第二套近似实现。

## 8. 自动测试计划

### 8.1 JVM 单元测试

必须覆盖：

- 相邻项回落算法的中间、末尾、并发变化和空列表。
- 现有 repository 删除、归档、重命名和保存命令保持不变。
- Windows controller 仍只在显式 save 方法中 update。
- source contract 确认 disabled 参数传入原生行/按钮。
- source contract 确认 spinner 清空语义、heading 存在、错误不再一律 assertive。
- source contract 确认可见菜单与 custom action 共享删除确认入口。

source contract 只用于难以在本地 JVM 直接组合的架构边界；能用纯函数或 instrumentation 验证的行为不新增字符串脆弱测试。

### 8.2 Instrumentation

必须覆盖：

- 数据行只有一个可访问/可点击根节点。
- disabled 原生行没有 click、long-click 和 custom action。
- 删除选项复选框与标签合并为一个节点。
- 选择 Dialog 一项一个节点，选中状态可读。
- Dialog 取消后普通焦点回到触发项。
- 删除中间项后普通焦点回下一项；删除末项后回上一项。
- 空会话列表回原生新建按钮；空设备列表回原生新增按钮。
- loading 有可见文字但 spinner 无独立语义。
- 浅色/深色切换不重建原生 View，不丢失选择或焦点。
- 1.0/1.3/2.0 字体下标题、正文、按钮和 Dialog 操作可到达。

instrumentation 不在用户日常数据设备上执行会真实删除远端业务对象的 connected 测试。页面测试使用 fake repository/controller 或纯 UI fixture。

### 8.3 构建门禁

按顺序执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

任一失败必须先修复再进入真机验收。

## 9. 真机验收矩阵

### 9.1 安装与数据保护

- 使用 `adb install -r app\build\outputs\apk\debug\app-debug.apk` 覆盖安装。
- 不执行 `adb uninstall`。
- 不执行 `pm clear`。
- 不主动删除用户真实工作区、会话或设备。
- 破坏性路径使用明确的临时测试对象；若没有临时对象，只验取消和失败路径，不扩大授权。
- 只有用户再次明确允许清数据或重装时，才可偏离覆盖安装。

### 9.2 主题与字体

组合：

- 系统浅色 + 跟随系统。
- 系统深色 + 跟随系统。
- 应用显式浅色。
- 应用显式深色。
- 字体 1.0、1.3、2.0。

检查：

- 列表标题、路径和元数据不相互遮挡。
- 长会话标题可读。
- 状态卡和 errorContainer 对比度清晰。
- section heading 不被裁切。
- 保存、取消和删除操作在高字体下可滚动到达。
- divider、边界和焦点指示在浅/深色下可辨识。

### 9.3 TalkBack

逐项验证：

- 工作区、会话和设备每行只停一次。
- 标题、正文、选中状态和 disabled 状态按合理顺序朗读。
- 自定义操作名称正确，且与可见菜单进入同一确认流程。
- 打开并取消 Dialog 后回到触发行。
- 删除/归档临时对象后依次回下一项或上一项。
- 删除列表最后一个临时对象后回到页面主目标。
- loading/error 只在状态变化时公告一次。
- 普通设备/工作区上下文不会反复公告。
- 系统返回后回到上一级原触发项。

必须实际观察 TalkBack 焦点框或朗读位置。仅看节点存在、普通键盘焦点或自动测试通过，不算完成。

### 9.4 键盘与系统导航

- Tab/方向键可到达所有行、保存、取消和确认操作。
- Enter/Space 只在 enabled 时激活。
- 焦点进入输入框不保存、不联网。
- 系统返回、顶栏返回和手势返回行为一致。
- 三键导航与手势导航下内容都不被系统栏遮挡。

### 9.5 Windows 联机回归

在已连接 Windows 设备可用时检查：

- 工作区和会话正常加载。
- 进入会话和返回列表不丢焦点。
- 重命名、归档、删除只对临时测试对象执行。
- Coding 设置和 Windows 设置只有点“保存”才发送更新。
- 保存成功按现有路径返回；保存失败留页并显示错误。
- 配对和测试连接流程未因状态组件调整而改变。

## 10. 主线审阅结论

### 10.1 范围审阅

通过，条件如下：

- 三个主要屏幕文件是阶段 3 的直接范围。
- `MyCodexApp.kt` 和原生控件文件只用于焦点回落闭环，不得顺带重构外壳。
- 聊天和远程交互 Dialog 保持阶段 4 范围。
- 默认不新增共享文件，避免提前建设设计系统。

### 10.2 无障碍审阅

通过，计划已把以下事项设为硬门槛：

- 一行一焦点。
- enabled 同时控制点击、长按和 custom action。
- 删除确认与可见/无障碍操作同路。
- Dialog 关闭和删除后都有稳定焦点目标。
- 最后一项删除时使用真实可聚焦目标，不使用空操作伪按钮。
- spinner 不重复朗读。
- 普通信息无 live region，可恢复错误默认 polite。
- TalkBack 真机观察不可由 source contract 或普通焦点替代。

### 10.3 生命周期与业务审阅

通过，计划不改变 repository/controller 成功条件，并明确：

- 只在操作成功且刷新完成后切换到相邻目标。
- 失败时保留源项和错误。
- 目标以稳定 ID 为主，不以易漂移列表索引作为业务身份。
- 主题切换和重组不重发保存、删除或焦点动作。
- generation 只表达新的恢复请求。

### 10.4 视觉与深浅色审阅

通过，计划只使用阶段 1 已验证的 Material 颜色角色：

- 普通信息：`surfaceContainer`。
- 主要正文：`onSurface`。
- 次要正文：`onSurfaceVariant`。
- 错误：`errorContainer` / `onErrorContainer`。
- 分隔线：`outlineVariant`。
- 保存按钮沿用原生按钮已验证的 primary/disabled 组合。

新增实际颜色组合后必须扩展对比度测试，不靠目测签字。

### 10.5 可测试性审阅

通过：

- 相邻项选择下沉为纯函数。
- 原生控件行为由 instrumentation 验证。
- repository/controller 行为由现有 JVM 测试保护。
- source contract 只保护关键接线。
- 真机测试专门覆盖自动化不能证明的 TalkBack 辅助功能焦点。

### 10.6 过度设计审阅

通过：

- 不建通用列表框架。
- 不建全局焦点管理器或 Dialog 栈。
- 不强制创建规格列出的全部候选组件。
- 不引入依赖。
- 空会话 CTA 只解决最后一项删除后的真实主目标需求。
- 只读空态目标只在现有原生机制确实不能满足时增加。

## 11. 风险与回滚

| 风险 | 预防 | 回滚点 |
| --- | --- | --- |
| 删除后目标 ID 已不存在 | 旧/新稳定 ID 顺序计算相邻项 | 回滚对应页面的 target 计算，不动 repository |
| reload 重组导致重复焦点 | target + generation，成功后消费 | 回滚 generation 接线 |
| 状态 live region 重复朗读 | 稳定文字、spinner 清语义、不用 tick | 回滚单个状态分支 |
| 共享 helper 造成大范围回归 | 默认保留文件内私有实现 | 删除新共享 helper，恢复原位代码 |
| 设置浏览触发保存 | 保存 API 只绑定原生保存按钮 | 回滚 UI 包裹，不改 controller |
| 高字体 Dialog 操作不可达 | 内容限高并可滚动，真机 2.0 验收 | 回滚单个 Dialog 布局 |
| 真机安装丢数据 | 仅 `adb install -r` | 停止安装并报告，不自动卸载 |

代码实施应按工作包分别形成可审阅差异。若阶段 3 尚未整体完成，不把文档状态改为“已完成”。

## 12. 完成定义

阶段 3 只有在以下条件全部满足后才算完成：

- 工作区、会话、设备、Coding 设置和 Windows 设置视觉统一。
- 信息、加载、空态和错误均按类型表达。
- 连续列表和设置分组的 divider 规则一致。
- 一行一焦点，无重复可访问节点。
- disabled 控件无点击、长按或 custom action。
- 删除/归档成功回下一项、上一项或稳定主目标。
- Dialog 取消、失败和系统返回回到合理触发项。
- 没有新增成功 Toast、Snackbar 或持久回执。
- 设置只通过显式保存发送远程写入。
- 浅色、深色、跟随系统和 1.0/1.3/2.0 字体通过。
- JVM、lint、debug APK 和 debug androidTest APK 全部通过。
- Android 15 / HyperOS 真机 TalkBack、键盘、手势和三键导航验收通过。
- 使用覆盖安装，用户数据未因开发流程丢失。
- 无重复 helper、死代码或新增第三方依赖。
- README、总规格和本文状态与实际结果一致。

## 13. 实施启动条件

本文已经完成主线范围、无障碍、生命周期、视觉、测试和过度设计审阅。

用户确认开始开发后，按既定协作方式只新开一条开发子代理。子代理按 3A → 3E 顺序完成生产代码和测试；主线不并行写同一批代码，只负责：

1. 审核子代理差异。
2. 修正阻断问题。
3. 执行完整自动门禁。
4. 使用覆盖安装部署 debug APK。
5. 完成真机与 Windows 联机验收。
6. 汇总结果并在用户确认后提交、推送。

## 14. 实施与验收记录

### 14.1 实施结果

阶段 3 已按 3A → 3E 完成，业务仓储、远程协议和导航结构保持不变，未引入第三方依赖。

- 工作区、会话和设备列表统一了标题/元数据层级、紧凑行距与缩进分隔线。
- 普通说明、加载、空状态和错误使用不同表达；进度图形不再制造重复朗读节点，错误使用克制的 `polite` 公告。
- Coding 设置与 Windows 设置使用共享的 heading、分组 Surface、分隔线和原生保存按钮。
- 会话标题与分支等元数据分离，删除本地历史记录复选框保持单一无障碍节点。
- 原生操作行的 `enabled` 状态同时约束点击、键盘激活和无障碍操作；禁用节点不再暴露可执行动作。
- 删除或归档成功后以稳定 ID 计算下一项、上一项或页面主目标；取消、失败和系统返回恢复到原触发项。
- 空会话列表提供真实可聚焦的“新建会话”主目标，避免最后一项删除后焦点落入空节点。

实际涉及的新增小型共享文件只有 `SettingsUi.kt`；它只承载两个设置页面已经共同使用的标题、分组和分隔线，没有扩展为通用设计系统。

### 14.2 主线复审修正

主线在子代理实现后完成逐项差异审阅，并补充了两处阻断修正：

1. 会话空态只在 `Loaded` 且结果为空时出现，未连接设备和不可用状态不会被误报为“没有会话”。
2. Windows 设置加载状态的 `liveRegion` 放到真实可见文字节点，避免空父节点承担公告语义。

同时增加 source contract，锁定“加载完成后才能显示空态”的条件。

### 14.3 自动门禁

以下命令均在 2026-07-30 通过：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

生成并安装的 debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`。

### 14.4 安装与数据保护

真机部署使用带 `-r` 的覆盖安装，返回 `Success`。本轮没有执行 `adb uninstall`、`pm clear` 或应用数据清理，原有用户数据未因安装流程丢失。

### 14.5 真机与联机验收

验收设备为 Android 15 / HyperOS 真机，屏幕阅读器保持启用，Windows 设备 `JINTONGLU` 保持连接。

- 跟随系统的浅色、深色界面均完成可见性检查；状态卡、标题、元数据、分隔线和焦点框对比清晰。
- 在深色模式下检查字体缩放 1.0、1.3 和 2.0；列表行可增高并可滚动到达，未发现关键内容裁切。
- 从会话列表系统返回后，TalkBack 焦点恢复到原工作区行。
- 从 Windows 设置系统返回后，TalkBack 焦点恢复到“Windows 设置”触发行。
- 在空会话列表创建一个临时会话后，焦点落到新会话；删除该唯一临时会话后，列表恢复为空，焦点落到真实的“新建会话”按钮。
- 临时会话已经清理；确认删除时“同时删除本地历史记录”保持未勾选，未触碰其他会话、工作区或设备。

验收结束后设备已恢复为系统深色模式，字体缩放恢复为 1.0。

### 14.6 当前交付状态

阶段 3 的代码、测试、文档、构建、覆盖安装和真机验收均已完成。改动保持在工作区，等待用户审阅后再提交和推送。
