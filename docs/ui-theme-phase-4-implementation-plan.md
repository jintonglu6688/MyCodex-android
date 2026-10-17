# MyCodex Android UI 阶段 4 详细实施计划

| 项目 | 内容 |
| --- | --- |
| 状态 | 已实施并完成主线审阅、自动门禁与核心真机验证；完整跨场景回归纳入阶段 5 |
| 版本 | 1.1 |
| 日期 | 2026-07-30 |
| 基线提交 | `0d655c2` |
| 前置阶段 | 阶段 0–3 已实施并通过 Android 15 / HyperOS 真机验收 |
| 对应规格 | [`ui-theme-accessibility-spec.md`](ui-theme-accessibility-spec.md) 的阶段 4 |
| 实施范围 | 消息阅读层级、响应式 Composer、附件 Bottom Sheet、远程交互 Dialog 及其焦点恢复 |

## 1. 文档目的

本文把阶段 4 拆成可直接编码、测试、主线复审和真机验收的工作包。

阶段 4 只改聊天界面的视觉与交互外壳，不改消息、附件、审批、工具输入和计划执行的远程协议，也不改 repository/controller 的成功条件、业务顺序或错误码。阶段完成后，长消息、大字体、窄窗口、软键盘、TalkBack 和硬件键盘均应可以到达聊天中的主要内容与操作。

## 2. 最终交付结果

阶段 4 完成后：

1. 用户、助手、系统和工具消息使用全宽角色化表面，并继续显示可见角色标签。
2. 每条消息仍只有一个可聚焦业务节点；视觉 Surface 不可点击、不可聚焦。
3. 消息视觉折叠时，TalkBack 仍获得完整正文。
4. 短消息不再报告没有实际行为的普通点击；长消息可通过同一节点展开或收起。
5. 复制、分叉、派生和回滚继续作为同一消息节点的自定义操作，并进入现有业务流程。
6. 普通宽度下 Composer 保持单行；宽度小于 `360dp` 或字体缩放达到 `1.3` 时改为两行。
7. 两行 Composer 中输入框独占第一行，附件与发送/停止位于第二行。
8. 原生 `EditText`、IME 发送、草稿保留和 IME 关闭后的辅助焦点恢复链保持不变。
9. 附件入口具有明确次级视觉边界，发送/停止保持主操作层级。
10. 发送提交期间，输入、附件入口和附件移除使用同一可编辑状态；禁用节点不再执行动作。
11. 附件 Sheet 顶层始终提供“关闭”，子页同时提供“返回”。
12. Sheet 搜索有数据但过滤结果为空时，显示“没有匹配结果”。
13. 关闭 Sheet 或插入附件后，焦点回到 Composer 的附件入口；不会因为焦点变化自动加载、插入或发送。
14. 审批、计划执行和工具输入 Dialog 在 2.0 字体及 IME 打开时仍可滚动到正文与所有明确操作。
15. Dialog 状态文字可见并克制地 `polite` 公告；普通正文不使用 live region。
16. 成功由消息、附件或 Dialog 消失后的最终状态体现，不增加成功 Toast、Snackbar 或持久反馈行。

## 3. 范围边界

### 3.1 主要生产代码

- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`
- `app/src/main/java/com/mycodex/mobile/coding/CodingComposer.kt`
- `app/src/main/java/com/mycodex/mobile/coding/CodingAttachmentSheet.kt`
- `app/src/main/java/com/mycodex/mobile/coding/RemoteApprovalDialog.kt`
- `app/src/main/java/com/mycodex/mobile/coding/RemotePlanExecutionDialog.kt`
- `app/src/main/java/com/mycodex/mobile/coding/RemoteToolInputDialog.kt`

只有为角色化原生消息行传入正确文字颜色时，才修改：

- `app/src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt`

只有真机证明 Compose Sheet/Dialog 关闭后没有恢复到现有触发项时，才修改：

- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`

### 3.2 测试代码

优先扩展：

- `app/src/androidTest/java/com/mycodex/mobile/coding/CodingComposerTest.kt`
- `app/src/test/java/com/mycodex/mobile/coding/CodingMessagePresentationTest.kt`
- `app/src/test/java/com/mycodex/mobile/AccessibilityActionContractTest.kt`
- `app/src/test/java/com/mycodex/mobile/coding/RemoteApprovalDialogSourceTest.kt`
- `app/src/test/java/com/mycodex/mobile/coding/RemotePlanExecutionDialogSourceTest.kt`
- `app/src/test/java/com/mycodex/mobile/coding/RemoteToolInputDialogSourceTest.kt`

仅当现有测试文件无法清楚承载响应式布局或 Sheet 行为时，允许各新增一个小型测试文件。默认不新增生产代码文件。

### 3.3 明确不做

- 不迁移到 Navigation 3。
- 不增加 adaptive、Grid、FlexBox 或截图测试依赖。
- 不迁移原生 `EditText` 到 Compose `TextField`。
- 不改变发送、停止、附件插入、审批、计划执行或工具输入协议。
- 不改变附件目录的加载时机和过滤字段。
- 不增加 Markdown 渲染器、语法高亮器或富文本依赖。
- 不实现左右消息气泡、头像、时间线装饰或动画。
- 不建立通用消息组件框架、全局焦点栈或 Dialog 管理器。
- 不借本阶段重写 `CodingConversationController`、`CodingRemoteRepository` 或草稿 ViewModel。
- 不把远程请求的屏幕阅读器探索、选中变化或输入焦点变化当成提交动作。

## 4. 当前实现审计

### 4.1 消息列表

当前消息由 `NativeAccessibleActionRow` 承载，已经具备：

- 一条消息一个原生可访问节点。
- 完整 `message.accessibilityText()`。
- 长消息默认折叠和同节点展开/收起。
- 长按可见菜单。
- 复制、分叉、派生和回滚自定义操作。
- 稳定消息 ID、滚动锚点和转到底部逻辑。

当前缺口：

- 各角色没有独立消息表面。
- 连续消息仍以普通行和 divider 为主，阅读层级偏弱。
- 短消息也传入空行为 `onClick`，可能被报告为可点击。
- 消息操作繁忙时仍可能保留长按入口。
- 空消息文案仍是开发阶段占位表达。
- 发送或停止失败产生的 `errorCode` 在已加载状态下可能没有可见错误容器。

### 4.2 Composer

当前 Composer 已具备：

- 原生多行 `EditText`。
- IME Action 发送。
- IME 关闭后恢复输入框辅助焦点。
- 附件 Chip、附件入口、发送/停止按钮。
- 发送、生成和停止状态模型。
- `imePadding()`。

当前缺口：

- 输入、附件和发送始终挤在同一行。
- 大字体或窄窗口下输入框会被两个按钮压缩。
- 附件按钮与发送按钮视觉层级相近。
- 发送提交期间附件 Chip 仍可移除，与输入和附件入口的禁用状态不一致。

### 4.3 附件 Bottom Sheet

当前 Sheet 已具备：

- 插件、技能两类入口。
- 自适应网格。
- 子页返回。
- 搜索、加载、错误、空目录和重试。
- 附件行单节点语义和“已插入”状态。

当前缺口：

- 顶层没有显式“关闭”。
- 搜索过滤为零时显示空白列表。
- Spinner 可能形成重复朗读节点。
- 入口 tile 和列表行缺少清楚的表面边界。
- 关闭或插入后没有显式验证回到附件触发按钮。

### 4.4 远程交互 Dialog

三个 Dialog 已具备：

- 可滚动正文。
- 可见状态文字和 `polite` 公告。
- 提交期间禁用重复操作。
- 工具输入选项一行一个节点。
- 密文输入使用密码视觉变换。

当前缺口：

- 固定正文最大高度需要在 1.3/2.0 字体和 IME 下重新验证。
- 多按钮审批和长计划正文需要验证操作始终可到达。
- 工具输入的多个问题需要更清楚的结构顺序。
- Dialog 消失后的真实 TalkBack 焦点位置尚未形成阶段 4 验收记录。

## 5. 视觉实施规则

### 5.1 角色化消息表面

角色与 Material 颜色映射：

| 角色 | 容器 | 文字 |
| --- | --- | --- |
| 用户 | `primaryContainer` | `onPrimaryContainer` |
| 助手 | `surfaceContainerLow` | `onSurface` / `onSurfaceVariant` |
| 系统、工具 | `secondaryContainer` | `onSecondaryContainer` |
| 未知 | `surfaceContainer` | `onSurface` / `onSurfaceVariant` |

实施规则：

- 使用非交互 `Surface` 包裹现有原生消息行。
- Surface 使用现有 Material shape，默认 `medium`；消息间距保持 8dp。
- 不在 Surface 上增加 `clickable`、`focusable`、`semantics` 或第二个 test tag。
- 原生行继续显示角色标签、正文和展开/收起 Footer。
- 若原生行需要适配容器文字颜色，只给 `NativeAccessibleActionRow` 增加最小的可选颜色参数；所有其他调用方保留当前默认值。
- 不新增图标、头像、时间戳或左右对齐。
- 不再在每个消息 Surface 后增加 divider；间距和不同表面已经足以分组。

### 5.2 消息行为

- `collapsible == false` 时，`onClick = null`。
- `collapsible == true` 时，普通点击、Enter 和 Space 切换展开状态。
- 长按和自定义操作只在 `actionsEnabled` 时存在。
- 操作禁用不能阻止用户阅读完整消息或展开长消息。
- 可见折叠不改变 `message.accessibilityText()`。
- 角色标签继续使用现有 `displayName()`，不因视觉改造改变 Windows 端一致性。
- “转到底部”保留简短名称和至少 48×48dp 命中区。

### 5.3 消息状态

- 加载：可见文字 + Spinner，Spinner 清空重复语义。
- 加载失败、发送失败或停止失败：`errorContainer` + `polite`。
- 仅在 `loadState == Loaded && messages.isEmpty()` 时显示“还没有消息”。
- `Unavailable` 使用普通说明，不冒充加载完成后的空态。
- 状态轮询不新增成功回执，不因重组重复播报相同普通说明。

### 5.4 响应式 Composer

使用现有 `BoxWithConstraints`、`LocalConfiguration.current.fontScale`、`Row` 和 `Column`，不引入 adaptive 依赖。

布局判断：

```text
stacked = maxWidth < 360dp || fontScale >= 1.3
```

普通布局：

```text
[              消息输入              ][附件][发送/停止]
```

紧凑/大字体布局：

```text
[                    消息输入                    ]
[                                  附件][发送/停止]
```

规则：

- 输入框第一优先获得可用宽度。
- 两行布局的第二行允许按钮按内容增高，不使用固定高度裁切文字。
- 外层使用 `surfaceContainer` 和圆角，不创建新的通用 Composer 容器组件。
- 附件使用现有 Material `FilledTonalButton` 或等价次级按钮。
- 发送/停止继续使用主色 `Button`。
- 按钮最小高度 48dp；不把文字替换为无标签图标。
- `editorEnabled = !state.sending` 统一传给输入、附件入口和附件移除。
- 生成期间允许继续编辑草稿，但 `canSend == false`；停止按钮仍是明确的主操作。
- 焦点变化只更新本地焦点状态，不发送、不加载附件、不联网。

布局判断若抽成内部纯函数，只用于清楚测试上述三个分支，不建立窗口分类框架。

### 5.5 附件 Chip

- Chip 保留“插件/技能 + 名称”的可见文字。
- 整个 Chip 继续作为一个“移除…”节点。
- `editorEnabled == false` 时移除点击和无障碍操作。
- 横向滚动保持，避免 2.0 字体下强行挤压或裁切多个附件。

### 5.6 附件 Sheet

顶层标题区：

- 显示“插入附件”。
- 始终显示“关闭”。
- 插件/技能子页同时显示“返回”。
- 返回只切回入口页；关闭才退出 Sheet。

入口 tile：

- 使用 Material tonal/outlined Surface。
- 每个 tile 只有一个点击和一个无障碍节点。
- 视觉文字仍为“插件”“技能”，不在名称中加入“按钮”。

目录与搜索：

- 原始目录为空：显示现有“没有可插入的插件/技能”。
- 原始目录有数据、查询后为空：显示“没有匹配结果”。
- 查询为空时不显示搜索无结果文案。
- 搜索焦点变化不重新加载目录。
- 已插入条目不可点击，也不暴露“插入”动作。

状态：

- Spinner 清空语义，由可见加载文字承担 `polite` 公告。
- 错误使用 `errorContainer` 和可见“重试”。
- 普通空态不使用 assertive。

### 5.7 Sheet 焦点

- 打开前的稳定来源是 `coding_composer_attachment`。
- “关闭”、系统返回、下滑关闭和成功插入后都应回到该来源。
- 先依赖 Compose ModalBottomSheet 的现有焦点恢复，并通过 instrumentation 与 TalkBack 真机验证。
- 只有真机证明焦点丢失时，才增加一个页面内布尔值/递增 generation，并复用现有焦点请求模式。
- 不保存全局焦点栈，不跨页面恢复已经消失的 Composer。

### 5.8 远程交互 Dialog

- 保留现有业务动作和 `onDismissRequest` 策略。
- 审批和计划执行必须通过明确可见选项结束，不允许外部点击暗中作出业务决定。
- 工具输入继续提供明确“确定”和“取消”。
- 正文区域可滚动；操作按钮保持在滚动区之外。
- 问题文字、选项、输入框、状态和操作按任务顺序排列。
- 工具输入的每个选择行保持单节点，子 RadioButton 不重复暴露。
- 密文内容不得进入 content description、错误文字、日志或测试输出。
- 提交/取消期间所有会重复请求的操作同时禁用。
- Dialog 关闭后优先使用 Compose 的原触发焦点恢复；只有真机失败才接入 Composer 稳定回退。

## 6. 无障碍焦点与业务边界

### 6.1 单节点规则

- 消息：一个原生消息节点。
- 附件 Chip：一个移除节点。
- Sheet tile：一个入口节点。
- 附件目录行：一个插入节点或一个不可操作状态节点。
- 工具输入选项：一个选择节点。
- 按钮内部文字和图标不形成第二焦点。

### 6.2 显式操作

以下事件不得发送命令或改变远程状态：

- TalkBack 探索消息、附件或选项。
- 普通焦点进入输入框。
- Tab/方向键移动焦点。
- Sheet 搜索文本变化。
- Dialog 内容滚动。
- Composer 从单行重组为两行。
- 主题、字体、窗口宽度或 IME 可见性变化。

发送、停止、插入、重试、审批、执行、继续、提交和取消只由对应的点击、Enter、Space 或 IME Action 触发。

### 6.3 焦点顺序

聊天主流程：

```text
消息列表 → 转到底部（条件出现）→ 输入框 → 附件 → 发送/停止
```

附件 Sheet：

```text
标题 → 返回（子页）→ 关闭 → 搜索（子页）→ 状态/结果 → 重试（错误）
```

远程 Dialog：

```text
标题/问题 → 正文/问题组 → 输入或选项 → 状态 → 明确动作
```

视觉重排不得改变业务顺序；两行 Composer 中附件仍在发送/停止之前。

### 6.4 公告

- 普通消息和角色标签不使用 live region。
- 新消息继续通过列表最终状态呈现，不额外增加成功公告行。
- 加载、错误和远程提交状态默认 `polite`。
- Spinner、装饰 Surface 和重复子文字不公告。
- 相同状态不因轮询、字体变化或重组重复公告。

## 7. 工作包

### 4A：消息与状态

修改：

- `CodingScreens.kt`
- 必要时 `AccessibleNativeControls.kt`
- 消息展示与无障碍测试

步骤：

1. 为角色选择容器和内容颜色。
2. 用非交互 Surface 包裹原生消息行。
3. 移除消息间 divider。
4. 短消息使用 `onClick = null`。
5. 操作繁忙时移除长按和自定义操作，但保留阅读/展开。
6. 修正空态、不可用和操作错误表达。
7. 验证完整朗读、自定义操作和滚动锚点未回归。

完成条件：

- 角色由文字和表面共同表达。
- 每条消息仍只有一个业务焦点。
- 短消息不再报告无效点击。
- 长消息视觉折叠但完整朗读。

### 4B：Composer

修改：

- `CodingComposer.kt`
- `CodingComposerTest.kt`
- 必要的最小布局判断测试

步骤：

1. 增加标准/堆叠布局判断。
2. 复用同一输入框和按钮内容，不复制两套业务回调。
3. 附件按钮改为次级 tonal，发送/停止保持主按钮。
4. 统一输入、附件入口和 Chip 移除的 enabled。
5. 保留 IME Action、草稿和焦点恢复。
6. 验证重组不会调用 `onSend`、`onStop` 或 `onAttachmentClick`。

完成条件：

- 1.0 字体常规宽度为单行。
- 1.3/2.0 字体或窄宽度为两行。
- IME 打开时所有操作可滚动/聚焦到达。
- 关闭 IME 后输入辅助焦点恢复。

### 4C：附件 Sheet

修改：

- `CodingAttachmentSheet.kt`
- `CodingScreens.kt`
- `AccessibilityActionContractTest.kt`
- 必要的 Sheet 测试

步骤：

1. 增加始终可见的“关闭”。
2. 保留子页“返回”并区分两种动作。
3. 为入口 tile 增加明确边界。
4. 增加过滤无结果状态。
5. 清除 Spinner 重复语义并统一错误容器。
6. 验证关闭、返回和插入后的附件按钮回焦。

完成条件：

- 顶层、子页和搜索结果均有明确退出路径。
- 搜索无结果不再显示空白区域。
- Tile 和条目均保持一个节点。
- 关闭后焦点回到附件入口。

### 4D：远程交互 Dialog

修改：

- `RemoteApprovalDialog.kt`
- `RemotePlanExecutionDialog.kt`
- `RemoteToolInputDialog.kt`
- 对应 source/instrumentation 测试
- 只有焦点证据要求时才修改 `MyCodexApp.kt`

步骤：

1. 统一正文滚动和按钮可达规则。
2. 复核多按钮、长计划、多问题和密文输入。
3. 保留显式业务动作和 disabled 约束。
4. 验证关闭后的普通焦点与 TalkBack 焦点。
5. 若平台恢复失败，只添加页面内稳定回退，不建全局机制。

完成条件：

- 2.0 字体和 IME 下所有明确操作可到达。
- 选择/焦点变化不提交。
- 关闭后焦点不丢失，不触发附加业务。

### 4E：回归、部署与文档

1. 执行完整自动门禁。
2. 使用 `adb install -r` 覆盖安装 Debug APK。
3. 完成 Android 真机与 Windows 联机验收。
4. 恢复测试后的系统主题和字体缩放。
5. 清理明确的临时会话/附件，不删除本地历史。
6. 更新本文、总规格和 README 的实际状态。
7. 用户确认后再提交和推送。

## 8. 自动测试计划

### 8.1 JVM

扩展或新增最小测试覆盖：

- 长消息完整无障碍文本保持不变。
- 长/短消息折叠条件保持不变。
- 标准宽度 1.0 字体不堆叠。
- 小于 360dp 时堆叠。
- 1.3/2.0 字体时堆叠。
- 空消息只在 Loaded 状态出现。
- 搜索目录有数据但过滤为空时使用“没有匹配结果”。
- 远程 Dialog 不增加控制类型词或隐式提交路径。

### 8.2 Android instrumentation

扩展 `CodingComposerTest`：

- 空草稿发送禁用。
- 文本和仅附件草稿的发送状态正确。
- 发送提交期间输入、附件入口和 Chip 移除均禁用。
- 普通与堆叠布局中的焦点顺序均为输入、附件、发送/停止。
- 仅布局变化不触发回调。
- IME 关闭后输入焦点恢复。

对消息和 Sheet 只增加能够稳定验证行为的测试，不以大量 source string 断言替代真实 UI 行为。

### 8.3 颜色对比

复用现有 `ThemeColorContrastTest` 中已经覆盖的组合：

- `onPrimaryContainer / primaryContainer`
- `onSurface / surfaceContainerLow`
- `onSecondaryContainer / secondaryContainer`
- `onErrorContainer / errorContainer`

只有实现引入新颜色组合时才扩展测试，不复制已有断言。

### 8.4 完整门禁

按顺序执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

任何失败必须先修复，再进入真机安装。

## 9. 真机与 Windows 联机验收

### 9.1 安装与数据保护

- 先确认包已安装。
- 只使用 `adb install -r app\build\outputs\apk\debug\app-debug.apk`。
- 不执行 `adb uninstall`。
- 不执行 `pm clear`。
- 不删除用户现有会话、工作区、设备或本地历史。
- 需要发送、附件或删除验证时，只使用明确命名的临时会话，并在结束后清理。

### 9.2 主题、字体与宽度

至少覆盖：

| 组合 | 重点 |
| --- | --- |
| 系统浅色 + 跟随系统 + 1.0 | 消息表面、按钮边界、Sheet |
| 系统深色 + 跟随系统 + 1.0 | 消息表面、焦点框、Dialog |
| 深色 + 1.3 | Composer 切为两行 |
| 深色 + 2.0 | 长消息、Sheet、Dialog 滚动可达 |
| 窄窗口/分屏等价约束 | Composer 两行与按钮不裁切 |

验收结束后恢复用户原来的深色模式和 `font_scale = 1.0`。

### 9.3 TalkBack

逐项观察真实辅助焦点：

- 每条消息只停一次。
- 角色和完整正文按合理顺序朗读。
- 短消息不报告无意义的点击。
- 长消息可展开/收起，复制和线程操作仍在同一节点。
- 输入、附件、发送/停止顺序稳定。
- Chip 只报告一个“移除…”节点。
- Sheet tile 和附件行各一个节点。
- 关闭 Sheet 后回到附件入口。
- 远程 Dialog 不形成无法退出的焦点陷阱。
- Dialog 完成或取消后回到原触发项或 Composer 稳定回退。

仅有普通键盘焦点或测试节点存在，不算 TalkBack 验收通过。

### 9.4 IME、键盘与返回

- 输入焦点打开 IME 不发送。
- IME Action 只在 `canSend` 时发送一次。
- 关闭 IME 后焦点回到输入框。
- Tab/方向键可到达输入、附件、发送/停止、Sheet 和 Dialog 操作。
- Enter/Space 只在 enabled 时激活。
- Sheet 子页系统返回先回入口页；顶层系统返回关闭 Sheet。
- 手势返回和三键返回行为一致。

### 9.5 联机业务回归

在 Windows 设备可用时检查：

- 现有消息正常加载，滚动锚点保持。
- 文本发送、仅附件发送和停止生成行为不变。
- 附件目录只在进入对应子页后加载。
- 插入附件后草稿保留，已插入条目禁用。
- 发送接受后只清理已接受的文本和附件。
- 审批、计划执行和工具输入只在明确动作后发出一次响应。
- 错误可见；成功不留下持久反馈行。

## 10. 主线审阅结论

### 10.1 范围审阅

通过，边界如下：

- 消息、Composer 和附件 Sheet 是阶段 4 的主要范围。
- 阶段 3 明确延期的三个远程交互 Dialog 同属本阶段，但只改 UI、可达性和焦点。
- `AccessibleNativeControls.kt` 只允许增加消息容器所需的可选颜色能力。
- `MyCodexApp.kt` 只允许在真机证明默认焦点恢复失败后增加最小回退接线。
- 不改 repository/controller、协议、导航和数据模型。

### 10.2 无障碍审阅

通过，硬门槛为：

- 一个业务项一个节点。
- 视觉容器不增加交互语义。
- 短消息没有空点击。
- disabled 同时移除点击、键盘和无障碍动作。
- 输入和选择焦点变化不执行真实工作。
- Sheet 有显式关闭并恢复来源焦点。
- Dialog 在高字体和 IME 下可退出、可提交、无焦点陷阱。
- TalkBack 真机焦点不能由 source contract 或普通焦点代替。

### 10.3 自适应审阅

通过，采用现有平台能力：

- `BoxWithConstraints`
- `LocalConfiguration.fontScale`
- `Row` / `Column`
- `WindowInsets.ime` / `imePadding`
- Material 3 自带按钮、Surface、Sheet 和 Dialog

不采用 Navigation 3、多窗格、实验 Grid/FlexBox 或新截图测试基础设施。本阶段只解决当前聊天界面的窄宽度、大字体和 IME 适配；更广泛的平板/折叠屏导航属于新的产品范围。

### 10.4 生命周期与业务审阅

通过：

- 布局重组不调用发送、停止、附件加载或远程响应。
- 发送、停止和远程响应仍由现有 controller 防重复。
- 草稿和附件继续由现有 ViewModel 保留。
- Sheet 页面状态以会话为边界。
- 主题、字体和窗口变化不清空草稿、不重新发送。
- 成功不创建新回执。

### 10.5 过度设计审阅

通过：

- 不建消息组件层级或设计系统。
- 不建通用响应式框架。
- 不建全局焦点管理器。
- 不新增依赖。
- 不增加装饰性头像、动画或无业务图标。
- 响应式判断只保留一个最小规则和一个测试。
- 默认依赖平台 Sheet/Dialog 焦点恢复，证据失败后才加回退。

## 11. 风险与回滚点

| 风险 | 预防 | 最小回滚点 |
| --- | --- | --- |
| 角色容器文字对比不足 | 使用现有已测试 Material 成对颜色 | 回滚消息颜色参数和 Surface 映射 |
| Surface 产生第二焦点 | Surface 不加交互与语义，真机观察节点 | 回滚外层 Surface |
| 短消息失去长按/自定义操作 | 只移除普通空点击 | 恢复对应 `onClick` 分支 |
| Composer 阈值在设备上过早/过晚 | 360dp + 1.3 基线，真机 1.0/1.3/2.0 验证 | 调整单个判断常量 |
| 重组触发发送或附件加载 | 回调只绑定显式控件并留测试 | 回滚布局外壳，不改 controller |
| Sheet 关闭后焦点丢失 | 先验平台恢复，再加页面内 generation | 回滚焦点接线 |
| Dialog 高字体下按钮不可达 | 正文滚动，按钮留在滚动区外 | 回滚单个 Dialog 布局 |
| 远程请求被返回键隐式取消 | 保留现有 `onDismissRequest` 与明确动作 | 回滚 dismiss 相关改动 |
| 真机安装丢数据 | 只覆盖安装，不卸载、不清数据 | 停止安装并报告 |

## 12. 完成定义

阶段 4 只有在以下条件全部满足后才算完成：

- 四类消息使用规定表面和正确文字颜色。
- 每条消息只有一个业务焦点。
- 短消息无空点击；长消息可展开并完整朗读。
- 消息自定义操作、菜单和滚动锚点未回归。
- 消息加载、空态、不可用和错误表达正确。
- Composer 在普通宽度 1.0 字体为单行，在窄宽度或 1.3/2.0 字体为两行。
- 输入、附件和发送/停止在 IME 打开时可到达。
- 发送期间输入、附件入口和 Chip 移除一致禁用。
- 附件入口、发送和停止具有清楚且非纯颜色的状态。
- Sheet 有关闭、返回、搜索无结果、加载、错误和空目录状态。
- 关闭 Sheet 后焦点回到附件入口。
- 三个远程 Dialog 在 2.0 字体和 IME 下可滚动、可完成且无焦点陷阱。
- 焦点、选择、字体、主题、窗口和 IME 变化不触发远程工作。
- 没有新增成功 Toast、Snackbar 或持久回执。
- JVM、lint、Debug APK 和 Debug AndroidTest APK 全部通过。
- Android 15 / HyperOS 真机 TalkBack、键盘、IME、手势和三键导航验收通过。
- 使用覆盖安装，用户数据未因开发流程丢失。
- 无新增第三方依赖、通用框架、重复 helper 或死代码。
- README、总规格和本文状态与实际结果一致。

## 13. 实施启动条件

本文已经完成主线范围、视觉、无障碍、自适应、生命周期、测试和过度设计审阅。

用户确认开始开发后，继续沿用既定协作方式：

1. 只新开一条阶段 4 开发子代理。
2. 子代理按 4A → 4D 完成生产代码和测试。
3. 主线不与子代理并行修改同一批代码。
4. 主线审核完整差异并修正阻断问题。
5. 主线执行四项自动门禁。
6. 主线使用覆盖安装部署最新 Debug APK。
7. 主线完成真机、TalkBack、IME 与 Windows 联机验收。
8. 主线更新实施记录。
9. 用户确认后再提交和推送。

## 14. 实施与验证记录

### 14.1 实施结果

阶段 4 已按 4A → 4D 完成，且没有修改远程协议、repository/controller、导航或数据模型。

实际生产代码变更：

- `AccessibleNativeControls.kt`
  - 为 `NativeAccessibleActionRow` 增加可选的标题、正文和 Footer 文字颜色参数。
  - 默认参数保持原有调用方外观和行为不变。
- `CodingScreens.kt`
  - 用户、助手、系统/工具和未知角色分别使用既定的 Material 容器与成对文字颜色。
  - 视觉 Surface 不承担点击或第二份语义；消息仍由原生行提供单一业务节点。
  - 短消息不再暴露空普通点击，长消息仍在同一节点展开/收起。
  - 操作繁忙时移除长按和自定义操作，但保留正文阅读及长消息展开。
  - 区分加载失败、操作失败、已加载空消息和服务不可用状态。
- `CodingComposer.kt`
  - 使用 `BoxWithConstraints` 和系统 `fontScale` 实现单行/两行切换。
  - `maxWidth < 360dp` 或 `fontScale >= 1.3` 时，输入框位于第一行，附件与发送/停止位于第二行。
  - 附件使用 tonal 次级按钮，发送/停止保持主按钮。
  - 发送提交期间，输入、附件入口和附件 Chip 移除使用同一禁用状态。
  - 原生输入、IME Action、草稿及既有 IME 焦点恢复链保持不变。
- `CodingAttachmentSheet.kt`
  - 顶层始终显示“关闭”，子页同时显示“返回”。
  - 入口 tile 和目录行具有明确但克制的表面边界。
  - 原目录有数据但过滤后为空时显示“没有匹配结果”。
  - Spinner 不再形成重复朗读节点；加载、错误和普通空态使用不同表达。
- `RemoteToolInputDialog.kt`
  - 问题文字使用标题层级并具备 heading 语义。
- `RemoteApprovalDialog.kt`、`RemotePlanExecutionDialog.kt`
  - 主线复核后确认现有滚动正文、外置操作、禁用和状态公告已满足本阶段规则，因此没有为追求形式一致而改写生产代码。

没有新增生产文件、第三方依赖、通用响应式框架、全局焦点管理器、成功 Toast、Snackbar 或持久回执。

### 14.2 测试与主线审阅

测试变更覆盖：

- Composer 发送期间输入、附件入口和附件移除同时禁用。
- `360dp` 宽度边界以及 1.3/2.0 字体的两行布局判断。
- Sheet 显式关闭、角色颜色映射和消息状态分支的 source contract。
- 审批、计划执行和工具输入 Dialog 的滚动、禁用与标题结构。

主线完成完整差异审阅，确认：

- 外层 Surface 没有形成第二点击或第二语义节点。
- 现有消息自定义操作、草稿、附件和远程命令入口没有改写。
- 布局、主题、字体和输入焦点变化不会发送、停止、插入或提交远程响应。
- 没有出现无关文件覆盖；用户原有六张未跟踪截图保持不变。

以下自动门禁全部通过：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

`git diff --check` 同样通过。

### 14.3 真机与 Windows 联机验证

验证设备为 Android 15 / HyperOS 真机，Windows 设备 `JINTONGLU` 保持联机。现有远程会话和插件目录均从 Windows 正常加载，证明本阶段没有破坏既有联机读取链路。

已验证：

- 系统深色、字体 1.0：
  - 四类消息表面层级清楚，角色标签继续可见。
  - 长消息保留“展开全文”，短消息没有额外视觉操作。
  - Composer 保持单行，附件为次级动作，发送为主动作。
- 系统深色、字体 1.3：
  - Composer 自动切为两行，输入框优先获得整行宽度。
  - 附件和发送按钮完整可见。
- 系统深色、字体 2.0：
  - 长消息、顶栏、Composer 和附件 Sheet 均可显示、滚动，按钮未被固定高度裁切。
  - 系统导航区仍保持可见。
- 系统浅色、字体 1.0：
  - 用户/助手消息、正文、辅助文字、附件按钮、禁用发送按钮和 Sheet 边界均保持可读。
- 附件 Sheet：
  - 顶层“插件/技能”和“关闭”可见。
  - 插件子页同时提供“返回”和“关闭”。
  - 插件目录只在进入子页后加载。
  - 搜索框能正常拉起 IME；无匹配查询显示“没有匹配结果”。
  - 返回先回到入口页，关闭退出 Sheet。
- IME：
  - 聚焦输入框只打开键盘，不发送。
  - 输入、附件和发送区域整体位于键盘上方，没有被遮挡。

验证结束后已恢复：

- `Night mode: yes`
- `font_scale = 1.0`

### 14.4 安装与数据保护

最新 Debug APK 已使用以下方式覆盖安装到真机：

```powershell
adb -s 192.168.50.69:40181 install -r app\build\outputs\apk\debug\app-debug.apk
```

安装结果为 `Success`。本阶段没有执行卸载、`pm clear`、清除应用数据或删除现有会话；覆盖安装后原有工作区、会话和消息仍在。

### 14.5 阶段 5 保留项

阶段 4 的代码实现、自动门禁和核心聊天/附件真机矩阵已经完成。以下跨场景穷举验证保留到阶段 5，不阻塞本阶段代码交付：

- 在服务端自然产生审批、长计划或工具输入请求时，对三个远程 Dialog 做完整 2.0 字体、IME 和关闭后焦点实机回归；本阶段不为制造测试条件而发送有业务副作用的远程命令。
- 横屏、系统分屏/窄窗口、外接实体键盘的完整遍历。
- 全应用 TalkBack 线性浏览、所有自定义操作和每条返回链的最终统一验收。
- 清理已经完全被替代的重复私有 UI helper，并再次执行全量回归。

真机上的绿色焦点框由用户操作控制，不作为本阶段 GUI 视觉层是否正确的判定依据；焦点语义与禁用行为由现有原生控件约束、自动测试和阶段 5 的完整辅助技术回归共同覆盖。
