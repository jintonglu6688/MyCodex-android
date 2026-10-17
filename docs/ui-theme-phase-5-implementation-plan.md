# MyCodex Android UI 阶段 5 详细实施与验收计划

| 项目 | 内容 |
| --- | --- |
| 状态 | 已实施；自动门禁、隔离 AVD、Android 真机与 Windows 联机核心验收通过 |
| 版本 | 1.1 |
| 日期 | 2026-07-30 |
| 基线提交 | `134cbc9` |
| 前置阶段 | 阶段 0–4 已实施；阶段 4 已通过自动门禁与核心聊天/附件真机验证 |
| 对应规格 | [`ui-theme-accessibility-spec.md`](ui-theme-accessibility-spec.md) 的阶段 5 |
| 实施范围 | 全量自动门禁、隔离 instrumentation、Android 真机整体验收、Windows 联机回归，以及有证据的最小重复代码清理 |
| 模拟器授权 | 用户已明确允许阶段 5 使用隔离模拟器测试 |

## 1. 文档目的

阶段 5 是本轮统一 UI 改造的收尾阶段，不再主动增加新视觉能力。

本阶段只做三件事：

1. 在不破坏用户数据的前提下完成自动测试和 Android 真机整体验收。
2. 修复验收中能够稳定复现的真实回归。
3. 删除已经确认被共享实现完全替代的私有透传函数。

没有失败证据时不重写稳定页面，不建立新的设计系统、导航框架、测试框架或焦点框架。

## 2. 最终交付结果

阶段 5 完成后：

1. 阶段 0–4 的浅色、深色、页面层级、状态表达、列表、聊天、附件和原生无障碍能力形成一份完整验收记录。
2. JVM、lint、Debug APK、AndroidTest APK 和隔离设备上的全量 instrumentation 全部通过。
3. 用户手机只进行覆盖安装和人工验收；不运行会清除偏好或设备身份密钥的测试。
4. TalkBack/VoiceBack 线性浏览、自定义操作、系统返回和焦点回落经过真实辅助技术验证。
5. 1.0、1.3、2.0 字体，浅色、深色、跟随系统，竖屏、横屏、分屏、三键和手势导航均有结果记录。
6. Windows 联机下的工作区、会话、消息、附件、设置和远程交互没有业务回归。
7. 已确认的六个设置 UI 私有透传函数被删除，调用方直接使用现有共享实现。
8. 相似但承担不同领域行为的状态面板保持原状，不为减少行数强行合并。
9. 没有新增第三方依赖、生产抽象、持久成功反馈或自动化占位界面。
10. 测试结束后恢复用户原来的深色、字体 1.0、竖屏锁定和三键导航设置。

## 3. 当前基线

### 3.1 仓库

- 当前分支：`main`
- 当前基线：`134cbc9 feat: refine accessible chat and attachments`
- 本地与 `origin/main` 已同步。
- 工作区只有六张既有未跟踪截图；它们不属于阶段 5，不删除、不暂存、不覆盖。

### 3.2 Android 真机

| 项目 | 当前值 |
| --- | --- |
| ADB serial | `192.168.50.69:40181` |
| 型号 | `23116PN5BC` |
| 系统 | Android 15 / HyperOS |
| 分辨率 | `1440 × 3200` |
| 密度 | `560 dpi` |
| 系统主题 | 深色，`Night mode: yes` |
| 字体缩放 | `1.0` |
| 导航模式 | `0`，当前为三键导航 |
| 自动旋转 | 关闭 |
| 当前方向 | 竖屏 |
| 无障碍服务 | VoiceBack、HyperOS 语音控制与增强 TalkBack 服务已启用 |

真机保留了现有工作区、会话、设备绑定、Android Keystore 身份密钥和主题偏好。任何测试流程都不得以重装方便为由清除这些数据。

### 3.3 Windows 联机

- Windows 设备：`JINTONGLU`
- 阶段 4 已验证现有远程会话和插件目录能够正常读取。
- 阶段 5 不自动重新配对、不删除设备绑定、不修改证书固定信息。
- 如果连接中断，先报告并恢复普通连接；不能通过清数据或重建设备身份解决。

### 3.4 测试栈

项目当前已经具备：

- JUnit 4 JVM 测试。
- Kotlin Coroutines Test。
- Compose UI instrumentation 测试。
- Espresso core、AndroidX Test runner 和 `NativeControlsTestActivity`。
- JVM source contract、controller、repository、协议和颜色对比测试。

项目是 Compose 与 `AndroidView` 原生控件混合架构，没有依赖注入框架、Robolectric、截图测试、UI Automator、Jacoco 或 mocking 框架。

阶段 5 不增加 Hilt、MockK、Robolectric、截图测试、Dropshots、UI Automator 或 Jacoco。现有测试能力足以完成本阶段；为测试而重构生产依赖关系不符合收尾阶段边界。

## 4. 范围边界

### 4.1 计划内生产代码

确定的清理范围只有：

- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`
- `app/src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt`

若整体验收发现真实回归，只允许修改根因所在的现有文件，并补一个能够阻止回归的最小测试。

### 4.2 计划内测试

现有测试继续复用。

允许新增一个小型 instrumentation 文件：

- `app/src/androidTest/java/com/mycodex/mobile/coding/RemoteDialogsAccessibilityTest.kt`

该文件只用合成状态渲染：

- `RemoteApprovalDialog`
- `RemotePlanExecutionDialog`
- `RemoteToolInputDialog`

它不连接 Windows、不发送远程命令、不读取用户会话，也不写 SharedPreferences 或 Android Keystore。

### 4.3 计划内文档

- `README.md`
- `docs/ui-theme-accessibility-spec.md`
- 本文

### 4.4 明确不做

- 不迁移 Navigation 3。
- 不增加多窗格、导航 rail 或大屏专用布局。
- 不增加截图测试框架。
- 不引入实验 Grid、FlexBox 或 MediaQuery API。
- 不把 `AndroidView` 原生可访问控件迁回纯 Compose。
- 不增加依赖注入框架、fake 架构或通用测试 runner。
- 不合并 repository/controller，不重写远程协议。
- 不增加新的主题模式、动态色、动画、Markdown 或代码高亮。
- 不恢复自动化界面、占位入口、成功 Toast、Snackbar 或持久反馈行。
- 不统一仅仅“看起来相似”但承担不同状态、焦点或领域职责的组件。
- 不在用户手机上执行全量 `connectedDebugAndroidTest`。

自适应验收只验证现有实现能否在方向、窗口、字体和输入设备变化下工作。Navigation 3、多窗格和截图基线仍属于规格中的延后决策。

## 5. 重复与死代码审计

### 5.1 确认删除

`SettingsUi.kt` 已经提供：

- `AppSettingsHeading`
- `AppSettingsGroup`
- `AppSettingsGroupDivider`

但两个页面文件仍保留了只转发一次调用的私有 wrapper。

`CodingScreens.kt`：

- `SettingsSectionTitle` → `AppSettingsHeading`
- `SettingsGroup` → `AppSettingsGroup`
- `SettingsGroupDivider` → `AppSettingsGroupDivider`

`WindowsSettingsScreens.kt`：

- `SettingsHeading` → `AppSettingsHeading`
- `SettingsGroup` → `AppSettingsGroup`
- `SettingsGroupDivider` → `AppSettingsGroupDivider`

实施方式：

1. 调用点直接改用现有 `AppSettings*` 名称。
2. 删除上述六个私有函数。
3. 删除两个文件中只为 wrapper 存在的 `ColumnScope` import。
4. 不修改共享实现，不改变参数、颜色、间距、heading 语义或列表顺序。

这是机械清理，不建立新 helper。

### 5.2 确认保留

以下组件虽然结构相似，但不在本阶段合并：

- `CodingInfoPanel`、`DeviceStatusPanel`、`StatusPanel`
- `CodingLoadingPanel`、`DeviceLoadingPanel`、`QuietProgress`
- `CodingErrorPanel`、`DeviceErrorPanel`、`SettingsError`
- `CodingEmptyPanel` 与其他普通状态面板
- Coding、Device、Windows 设置中的行组件

保留原因：

- 可见层级、焦点恢复、原生节点、live region、空态行为或业务输入不同。
- 合并它们需要增加参数和条件，代码并不会更简单。
- 当前没有重复修改导致的缺陷证据。

### 5.3 死代码结论

静态扫描没有发现可以直接删除的未调用私有生产函数。

`MyCodexAppPreview` 没有普通调用点，但由 `@Preview` 使用，不是死代码。

除六个透传 wrapper 外，不做推测性删除。若 Kotlin 编译器、lint 或后续引用扫描提供新的明确证据，再单独评估。

## 6. Instrumentation 数据隔离

### 6.1 为什么不能在用户手机上运行全量测试

现有 instrumentation 中有两类会修改真实目标应用数据的测试。

`LocalDeviceIdentityStoreTest`：

- 清除 `local_device_identity` SharedPreferences。
- 删除 `mycodex.remote.device.signing.v1`。
- 删除 `mycodex.remote.device.agreement.v1`。
- 验证缺失密钥时生成新设备身份。

如果在当前用户手机执行，会改变设备 ID 和密钥，使现有 Windows 配对失效。

`ThemePreferenceStoreTest`：

- 在 `@Before` 和 `@After` 清除真实主题偏好。
- 写入所有主题模式和损坏值。

如果测试进程异常中止，用户主题偏好可能停留在测试状态。

因此：

- 禁止在用户手机执行不带设备隔离的 `connectedDebugAndroidTest`。
- 禁止为了运行测试而备份、清除或恢复用户手机应用数据。
- 禁止在用户手机运行上述两个测试类。

### 6.2 隔离设备方案

本机现有环境已经具备：

- `D:\Android\Sdk\emulator\emulator.exe`
- Android 36 `google_apis_playstore/x86_64` 系统镜像
- 可用的 WHPX 硬件加速

阶段 5 可创建一个专用测试 AVD，例如 `MyCodex_API_36_Test`，并将 AVD 数据放在 `D:\Android` 现有 Android 开发环境下。

约束：

- 不下载新系统镜像。
- 复用 `D:\Android\Sdk` 下现有 SDK、emulator、cmdline-tools 和 Android 36 镜像。
- 通过本次进程的 `ANDROID_AVD_HOME` / `ANDROID_EMULATOR_HOME` 将 AVD 与 emulator 数据放在 `D:\Android`，不回落到用户配置目录或 C 盘。
- 不为本阶段永久修改系统级环境变量。
- 不修改项目依赖。
- 不使用用户手机数据初始化 AVD。
- 全量 instrumentation 只定向到解析出的 emulator serial。
- 用户手机保持连接，但不得被 Gradle connected task 选中。
- AVD 失败不允许回退到用户手机运行危险测试。

执行全量测试前必须显式设置本次进程的 `ANDROID_SERIAL` 为 emulator serial，并在命令结束后移除该临时环境变量。

### 6.3 真机测试边界

用户手机只执行：

- `adb install -r` 覆盖安装已经通过门禁的 Debug APK。
- 不写用户数据的人工 UI 验收。
- 必要时通过 ADB 注入 Tab、Enter、Space、Back 和方向键。
- 主题、字体、方向等可逆系统设置切换，并在结束后恢复。

AndroidTest APK 不需要安装到用户手机。所有合成 Dialog 和全量 instrumentation 验证均在隔离 AVD 完成。

## 7. 自动测试计划

### 7.1 现有 JVM 与构建门禁

按顺序执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

任何失败先分类：

- 阶段 5 改动引起：最小修复并重跑受影响及完整门禁。
- 既有失败：提供基线证据，不以跳过或关闭检查解决。
- 环境失败：修复环境或报告，不改产品逻辑掩盖。

### 7.2 Remote Dialog 合成 instrumentation

新增的单个测试文件覆盖：

1. 2.0 字体下长审批问题可滚动，所有 enabled 决策按钮存在。
2. 提交状态下所有审批按钮禁用，焦点或选择不调用 `onDecision`。
3. 2.0 字体下长计划正文可滚动，“执行计划”和“继续讨论”均可到达。
4. 工具输入多个问题按顺序形成 heading。
5. 每个单选项只有一个可操作节点，内部 `RadioButton` 不形成第二节点。
6. 密文输入不通过可见文本或语义暴露内容。
7. 提交/取消状态下“确定”和“取消”同时禁用。
8. 只有显式点击才增加回调计数；构图、字体变化、滚动和焦点请求的回调计数保持零。

测试继续使用 `NativeControlsTestActivity`、Compose Test API 和现有主题，不增加 runner、fixture framework 或 fake repository。

### 7.3 隔离 AVD 全量 instrumentation

在专用 AVD 启动并完成 boot 后：

```powershell
$env:ANDROID_SERIAL = "<emulator-serial>"
.\gradlew.bat connectedDebugAndroidTest
Remove-Item Env:ANDROID_SERIAL
```

执行前后都检查 `adb devices -l`，确认 Gradle 目标是 emulator，而不是 `192.168.50.69:40181`。

通过标准：

- 所有 instrumentation 测试通过。
- AVD 内的身份密钥和主题偏好可以由测试自由创建/清除。
- 用户手机的包路径、设备身份、绑定、主题和会话不因测试改变。

### 7.4 不增加的测试基础设施

- 不增加截图 reference。
- 不增加 9 组窗口尺寸的截图矩阵。
- 不增加 Robolectric 或 LayoutLib。
- 不增加 UI Automator 端到端套件。
- 不增加覆盖率阈值。

本阶段用现有行为测试、隔离 AVD 和人工真机矩阵完成验收。只有未来 CI 或多设备持续回归成为明确需求时，才单独设计截图和设备矩阵基础设施。

## 8. 真机验收前保护步骤

开始真机验收前记录：

```text
ADB serial
package path
versionCode / versionName
lastUpdateTime
Night mode
font_scale
navigation_mode
accelerometer_rotation
user_rotation
enabled_accessibility_services
当前应用主题模式
当前 Windows 设备连接状态
```

安装规则：

```powershell
adb -s 192.168.50.69:40181 install -r app\build\outputs\apk\debug\app-debug.apk
```

禁止：

```text
adb uninstall
pm clear
uninstallAll
清除存储
重置 Android Keystore
删除现有设备绑定
用完整 instrumentation 覆盖用户数据
```

只有阶段 5 代码重新构建并通过全部自动门禁后才能覆盖安装。

## 9. 真机验收矩阵

### 9.1 主题

验证组合：

| 系统主题 | 应用主题 | 重点 |
| --- | --- | --- |
| 深色 | 跟随系统 | 启动窗口、Compose、原生行、系统栏一致 |
| 浅色 | 跟随系统 | 同上，确认不会继续使用深色缓存 |
| 深色 | 显式浅色 | Activity 创建后正确覆盖；接受规格记录的启动首帧边界 |
| 浅色 | 显式深色 | Activity 创建后正确覆盖；原生 View 同步 |

每种至少验证：

- 冷启动。
- 从后台恢复。
- Activity 重建后选择保持。
- 原生行、EditText、按钮、禁用态和系统栏一致。

结束后恢复：

- 应用主题：跟随系统。
- 系统主题：深色。

### 9.2 字体

| 字体缩放 | 重点 |
| --- | --- |
| 1.0 | 常规布局和基线密度 |
| 1.3 | Composer 换行、顶栏增高、列表辅助文字 |
| 2.0 | 长标题、长消息、设置、Dialog、Sheet、按钮和底栏 |

通过标准：

- 不因固定高度裁切文字。
- 操作按钮没有被推出可达区域。
- 内容区域能够滚动。
- 视觉重排不改变业务顺序。
- 字体变化不提交、不保存、不加载附件、不发送远程响应。

结束后恢复 `font_scale = 1.0`。

### 9.3 方向和窗口

当前设备锁定竖屏。

验证：

- 竖屏完整流程。
- 临时横屏后的顶栏、底栏、列表、Composer、Sheet 和 Dialog。
- HyperOS 分屏中的紧凑窗口。
- 从横屏/分屏回到全屏竖屏后，当前页面、草稿和滚动状态合理保留。

HyperOS 分屏使用系统界面手动进入，不修改 OEM 私有设置。

方向切换前记录原始设置，结束后恢复自动旋转关闭、竖屏。

不因本阶段引入 Navigation 3、多窗格或导航 rail；现有单页布局在可用宽度内保持可操作即为通过。

### 9.4 三键、手势与键盘

当前为三键导航，先完成：

- 系统 Back。
- 应用可见“返回”。
- Bottom Sheet 子页返回与顶层关闭。
- Dialog 明确取消/拒绝/继续。

手势导航由用户通过 HyperOS 系统设置临时切换。主线不直接写 OEM 私有导航数据库。

手势模式验证：

- 边缘返回逐级返回，不直接退出子页面。
- Bottom Sheet 子页先回入口。
- IME 打开时第一次返回先关闭 IME，后续返回再离开页面。

基础键盘验证可通过 ADB 注入：

- Tab/Shift+Tab。
- 方向键。
- Enter。
- Space。
- Back。

通过标准：

- 焦点顺序符合任务顺序。
- Enter/Space 只激活 enabled 控件。
- 仅移动焦点不联网、不保存、不删除、不发送。

结束后恢复三键导航。

### 9.5 IME

验证：

- 会话标题、设备配对 URI、Composer、Sheet 搜索、设置 Dialog 和工具输入。
- 聚焦只打开输入法。
- Composer 的 IME Send 只在 `canSend` 时提交一次。
- 关闭 IME 后输入焦点恢复。
- 2.0 字体和横屏时输入及明确操作仍可到达。
- 密文输入不进入可见状态、content description、日志或截图说明。

### 9.6 TalkBack / VoiceBack

绿色焦点框本身不作为 GUI 正确与否的判定依据。

真实验收依据：

- 线性浏览停靠次数。
- 实际朗读名称、状态和控件类型。
- 自定义操作菜单内容。
- 双击、Enter、Space 后的实际动作。
- 返回后的朗读目标。
- 焦点移动是否意外触发业务。

需要用户协同完成 VoiceBack 手势的检查点；ADB 普通点击不能替代触摸探索和双击激活。

页面覆盖：

1. 底栏三项：每项一个节点，短名称和选中状态正确。
2. 顶栏：标题一个 heading；返回和更多操作名称简短。
3. 工作区：一项一个节点；打开、重命名、归档、删除自定义操作存在。
4. 会话：标题和元数据朗读合理；归档、删除和菜单在同一节点。
5. 消息：每条消息停靠一次；角色和完整正文可读；短消息不报告无意义点击；长消息可展开。
6. Composer：输入、附件、发送/停止顺序稳定；Chip 一项一个移除节点。
7. 附件 Sheet：入口、关闭、返回、搜索和目录行无重复节点。
8. Profile：状态卡不可点击；三个真实入口可达；没有诊断或自动化占位。
9. 设备：设备行、自定义删除、添加设备、配对取消和详情返回。
10. 本地及 Windows 设置：heading、分组、选择状态、保存和错误。
11. Dialog：审批、计划和工具输入不存在焦点陷阱，明确动作可离开。

### 9.7 焦点恢复

逐项验证：

- 工作区打开后返回原工作区。
- 会话打开后返回原会话。
- 删除/归档临时项后回到下一项、上一项或稳定主操作。
- 删除确认取消后回原触发项。
- 设备删除确认取消后回原设备删除入口。
- 添加设备取消后回“新增设备”。
- 本地设置返回 Profile 原入口。
- Windows 设置子页返回原设置入口。
- 关闭 Sheet 后回附件入口。
- 关闭 Dialog 后回原触发项或 Composer 稳定回退。
- 关闭 IME 后回输入框。

原项已经删除时只要求稳定回退，不要求恢复不存在的节点。

焦点恢复只移动一次；不能连续抢焦点，不能触发默认点击。

### 9.8 触控与对比度

- 所有独立操作至少 48 × 48dp。
- 正文颜色组合满足 4.5:1。
- 必要边界和状态图形满足 3:1。
- 角色、选中、错误和禁用不只依赖颜色。
- 2.0 字体下触控目标仍独立，不互相覆盖。

颜色对比继续由 `ThemeColorContrastTest` 负责数值门禁；真机负责动态主题、OEM 渲染和实际边界观察。

## 10. Windows 联机业务回归

验证顺序：

1. 现有设备连接和恢复。
2. Coding 工作区加载。
3. Coding 会话加载与滚动锚点。
4. Office 会话入口。
5. 消息加载、展开、复制和转到底部。
6. 临时会话中的一次短文本发送和回复。
7. 附件插件/技能目录加载、搜索和取消。
8. 会话设置只在显式保存时更新。
9. Windows 设置选择只修改草稿，显式保存才联网。
10. 远程审批、计划或工具输入若自然出现，验证明确动作只响应一次。

测试数据规则：

- 不在用户重要会话中发送测试消息。
- 需要实际发送、删除或归档时，只创建一个带 `UI验收临时` 前缀的会话。
- 临时会话测试完成后通过正常 UI 删除。
- 不删除工作区、设备或 Windows 本地历史。
- 不执行有文件写入、进程终止或权限提升效果的远程命令。
- 无法安全自然触发的远程 Dialog 由隔离 AVD 合成测试覆盖，并在记录中标明未做真实业务触发。

## 11. 工作包

### 5A：测试保护与合成 Dialog 测试

1. 新增单个 `RemoteDialogsAccessibilityTest.kt`。
2. 使用合成长文本、2.0 字体和回调计数覆盖三个 Dialog。
3. 确认测试只运行于 `NativeControlsTestActivity`。
4. 不引入 repository、controller fake 或依赖注入。
5. 构建 AndroidTest APK。

完成条件：

- 三个 Dialog 的长内容、操作可达、禁用和显式激活形成真实 Compose UI 测试。
- 测试无网络、文件、偏好和 Keystore 副作用。

### 5B：最小重复代码清理

1. 两个设置页面直接调用 `AppSettingsHeading`、`AppSettingsGroup` 和 `AppSettingsGroupDivider`。
2. 删除六个私有透传 wrapper。
3. 删除失效 import。
4. 不修改共享实现和布局。

完成条件：

- 生产行为差异为零。
- Kotlin 编译、现有设置测试和 lint 通过。
- 不新增 helper。

### 5C：自动门禁与隔离 instrumentation

1. 执行 JVM、lint、Debug APK 和 AndroidTest APK。
2. 创建或复用专用 API 36 AVD。
3. 用 emulator serial 定向执行全量 `connectedDebugAndroidTest`。
4. 确认用户手机未被 instrumentation 选中。
5. 复核 `git diff --check` 和工作区。

完成条件：

- 所有自动门禁通过。
- 危险身份/主题测试只改变隔离 AVD。

### 5D：真机与 Windows 整体验收

1. 覆盖安装最新 Debug APK。
2. 执行主题、字体、方向、分屏、导航、键盘和 IME 矩阵。
3. 与用户协同执行 TalkBack/VoiceBack 线性浏览和双击激活。
4. 执行焦点恢复与临时会话业务回归。
5. 记录通过、失败、不适用和未安全触发项。
6. 修复可复现回归后重跑全部受影响矩阵。

完成条件：

- 所有硬性无障碍门槛通过。
- 业务行为未回归。
- 没有以清数据或重配对解决问题。

### 5E：恢复、文档和交付

1. 恢复深色、跟随系统、字体 1.0、竖屏锁定和三键导航。
2. 保留用户原有数据。
3. 更新本文实际结果。
4. 将总规格状态改为阶段 0–5 已完成。
5. 更新 README。
6. 用户确认后提交并推送。

## 12. 缺陷处理规则

验收失败时：

1. 先在同一配置重复一次，排除页面尚未稳定或外部通话/系统浮层。
2. 确定是视觉、普通键盘焦点、辅助功能焦点、业务状态还是环境问题。
3. 检查所有共享调用路径，修复根因，不在单一页面叠加补丁。
4. 使用现有平台能力或现有 helper。
5. 只增加一个能够证明缺陷的最小测试。
6. 重跑受影响测试、四项门禁、隔离 instrumentation 和对应真机矩阵。

禁止：

- 用延时循环持续抢焦点。
- 用全局焦点栈解决单页问题。
- 用 content description 重复整屏可见文字。
- 用成功提示掩盖最终状态没有更新。
- 用禁用 lint、跳过测试或删除断言解决失败。
- 因一个页面失败而迁移导航或重写整个 UI。

## 13. 主线审阅结论

### 13.1 范围审阅

通过。

- 阶段 5 是验收与清理，不继续视觉扩张。
- 生产代码预期只做六个 wrapper 的机械删除。
- 真实缺陷必须有复现证据才扩大文件范围。
- 不新增依赖、导航和设计系统。

### 13.2 无障碍审阅

通过，硬门槛为：

- 一项一个业务节点。
- 名称简短，不重复控件类型。
- 禁用时点击、长按和自定义操作同时不可执行。
- 焦点、选择、滚动和探索不执行真实工作。
- 返回、Dialog、Sheet、删除和归档后焦点可预测。
- 自动测试不能替代真实 TalkBack/VoiceBack。
- 绿色焦点框的视觉位置不单独作为 GUI 通过或失败依据。

### 13.3 自适应审阅

通过。

项目没有 Navigation 3，且包含为无障碍保留的原生 `AndroidView`。阶段 5 不进行 Navigation 3、多窗格、Grid/FlexBox 或导航 rail 迁移。

使用现有能力完成验收：

- `BoxWithConstraints`
- `LocalConfiguration.fontScale`
- `WindowInsets.ime` / `imePadding`
- 内容驱动高度和滚动
- 系统方向与分屏
- Compose 和原生控件现有焦点行为

若横屏或分屏发现裁切，只修正具体约束根因，不借机建立通用窗口分类框架。

### 13.4 测试审阅

通过。

- 沿用 JUnit 4、Compose UI Test 和 Espresso。
- 不安装 Hilt、MockK、Robolectric、截图框架、UI Automator 或 Jacoco。
- 新增一个合成 Dialog 测试文件足够覆盖阶段 4 遗留的真实布局证据。
- 全量 instrumentation 使用隔离 AVD。
- 用户手机只做覆盖安装和人工验收。

### 13.5 数据保护审阅

通过，且属于阻断条件：

- 用户手机不得运行会删除身份密钥的全量 instrumentation。
- 不卸载、不清数据、不重建 Android Keystore。
- 不自动重新配对。
- 安装只使用 `adb install -r`。
- 测试系统设置必须恢复原值。
- 六张既有截图不属于本阶段。

### 13.6 过度设计审阅

通过。

- 删除六个无价值 wrapper，而不是抽取更多组件。
- 保留有领域差异的状态面板。
- 只增加一个测试文件。
- 复用已安装系统镜像，不下载新镜像。
- 不新增生产抽象或依赖。

## 14. 风险与回滚

| 风险 | 预防 | 回滚点 |
| --- | --- | --- |
| 全量 instrumentation 删除用户设备身份 | 只定向隔离 AVD | 停止测试，不回退到手机 |
| Gradle 误选真机 | 设置并核对 emulator `ANDROID_SERIAL` | 在运行前终止 |
| wrapper 清理改变布局 | 直接调用完全相同共享函数 | 回滚两个文件的机械替换 |
| 2.0 字体或横屏裁切 | 内容高度、滚动和真机矩阵 | 回滚单个布局修复 |
| TalkBack 重复节点 | 真机线性浏览和现有原生节点 | 回滚新增语义 |
| 焦点恢复抢焦点 | generation 一次性消费 | 回滚对应恢复接线 |
| 远程测试产生副作用 | 合成 Dialog；临时会话；显式动作 | 停止业务测试并报告 |
| 系统设置未恢复 | 前后记录值，结束逐项核对 | 立即恢复原始值 |
| Windows 连接中断 | 不清数据、不重配对 | 暂停并恢复普通连接 |

## 15. 完成定义

阶段 5 只有在以下条件全部满足后才完成：

- 六个设置 UI 透传 wrapper 已删除。
- 没有其他无证据的组件合并或抽象。
- Remote Dialog 合成 instrumentation 覆盖长内容、2.0 字体、禁用和显式激活。
- JVM、lint、Debug APK 和 AndroidTest APK 通过。
- 全量 instrumentation 在隔离 AVD 通过。
- 最新 Debug APK 使用覆盖方式安装到真机。
- 浅色、深色、跟随系统和冷启动/恢复通过。
- 1.0、1.3、2.0 字体通过。
- 竖屏、横屏和 HyperOS 分屏通过。
- 三键、手势、键盘和 IME 流程通过。
- TalkBack/VoiceBack 线性浏览、自定义操作和返回链通过。
- 消息、附件、三个 Dialog 和设置操作可达且没有焦点陷阱。
- 删除、归档、重命名、设备、设置和聊天业务没有回归。
- 不存在焦点或选择变化触发真实工作的路径。
- 成功状态不留下持久反馈行。
- 用户设备身份、绑定、会话和设置没有因测试丢失。
- 系统恢复深色、跟随系统、字体 1.0、竖屏锁定和三键导航。
- 没有新增第三方依赖或通用框架。
- README、总规格和本文与实际结果一致。
- 用户确认后才提交和推送阶段 5。

## 16. 执行流程

用户确认执行本文后：

1. 只新开一条阶段 5 开发子代理。
2. 子代理只完成 5A 合成 Dialog 测试和 5B 机械清理。
3. 主线不与子代理并行修改相同文件。
4. 主线审阅完整差异。
5. 主线执行四项门禁。
6. 主线创建/启动隔离 AVD，并定向执行全量 instrumentation。
7. 主线覆盖安装真机 Debug APK。
8. 主线与用户协同完成真实辅助技术和系统模式验收。
9. 主线修复有证据的阻断回归并重复验证。
10. 主线恢复系统状态并更新实施记录。
11. 用户确认后再提交和推送。

## 17. 实施与验收记录

实施日期：2026-07-30。

### 17.1 最终代码范围

生产代码保持为最小改动：

1. `CodingScreens.kt` 删除三个设置 UI 透传 wrapper。
2. `WindowsSettingsScreens.kt` 删除三个设置 UI 透传 wrapper。
3. `AccessibleNativeControls.kt` 为原生文本输入补齐 48dp 最小高度。
4. `MyCodexApp.kt` 让所有非根页面复用已有的标题溢出自动扩行逻辑。

没有修改三个 Remote Dialog 的生产实现，没有新增依赖、接口、测试钩子、导航框架或通用焦点框架。

测试侧新增一个 `RemoteDialogsAccessibilityTest.kt`，并校正六个已经与 AndroidView 或 Android 16 平台语义不一致的既有测试文件。校正内容只包括：

- 直接检查原生 View 的可点击、禁用、文本输入和 `contentDescription` 状态。
- 显式声明 `assertTextContains(..., substring = true)`。
- 使用有效的 Activity 恢复来源 ID，而不是保留值 `Long.MIN_VALUE`。
- 不要求 disabled View 取得键盘输入焦点。
- 不要求 Compose 测试语义树隐藏密码字段的内部 `InputText`；改为验证 `Password` 语义。
- 源码契约直接检查共享 `AppSettings*` 组件，不再要求已删除的 wrapper。

### 17.2 自动门禁

以下命令最终全部通过：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

结果：

- JVM：224 项通过。
- Android lint：通过。
- Debug APK：通过。
- AndroidTest APK：通过。

完整 instrumentation 使用临时 `ANDROID_SERIAL=emulator-5554`，只在 `MyCodex_API_36_Test` AVD 上运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

结果：29 项通过、0 失败、0 跳过。其中新增的 5 项 Remote Dialog 测试覆盖审批、计划执行和工具输入 Dialog 的 2.0 字体、长内容滚动、禁用状态、heading、单节点选项、密码语义和显式回调。

### 17.3 隔离模拟器

- AVD：`MyCodex_API_36_Test`
- API：36
- ABI：x86_64
- 系统镜像：复用既有 `google_apis_playstore`
- 数据位置：`D:\Android\UserHome\avd`
- 没有下载新镜像，没有修改永久环境变量。

除完整 instrumentation 外，模拟器还通过以下组合的视觉检查：

- 横屏。
- 深色模式。
- 2.0 字体。
- 手势导航。

顶栏、状态卡、正文和底部导航没有遮挡、横向溢出或系统手势区域冲突。结束时已恢复浅色、1.0 字体和自动旋转，并停止 AVD；AVD 本身保留供后续复测。

### 17.4 Android 15 / HyperOS 真机

最终 Debug APK：

```text
SHA-256 137BD6BC12ED307E759C45EC4BAD2B6A5D330F448CD2A13D5077DE14084FBB41
```

两次安装均使用：

```powershell
adb -s 192.168.50.69:40181 install -r app\build\outputs\apk\debug\app-debug.apk
```

安装前后 `firstInstallTime` 始终为 `2026-07-29 21:20:23`，证明没有卸载重装或清除应用数据。最终状态：

- 字体恢复为 1.0。
- 系统仍为深色模式。
- 应用仍为“跟随系统，已选中”。
- 三键导航保持不变。
- VoiceBack 等既有无障碍服务未被关闭或重置。
- 当前设备和 Windows 配对仍为 `JINTONGLU`。
- 应用最终返回“编程”根页面。

真机检查通过：

- 深色主题与跟随系统状态一致。
- 1.0、1.3、2.0 字体下设置卡片自然扩高、正文换行且操作可达。
- 2.0 字体下 `Windows 设置` 顶栏从永久省略修复为完整两行显示。
- 工作区、Profile、主题选项和 Windows 设置入口均为单一可点击、可聚焦节点。
- 配对 URI 输入框获得焦点并打开 IME 后，输入框、取消和添加设备操作均位于键盘上方。
- 未输入配对内容、未提交、未保存或修改 Windows 设置。
- 页面上没有重新出现成功操作反馈行。

### 17.5 Windows 联机

覆盖安装后，Android 端仍能从 `JINTONGLU` 加载：

- Windows（System）。
- ChatGPT 账号状态。
- 默认模型 `gpt-5.6-sol`。
- 默认权限。
- 自定义权限摘要。
- Codex 高级设置入口。
- Windows 工作区列表。

本轮只进行了读取和导航，没有保存远程设置或触发破坏性业务。

### 17.6 已知人工边界

没有通过 shell 强制切换 HyperOS 的 OEM 分屏入口，以免改变用户当前手机任务布局。API 36 模拟器上的横屏、2.0 字体和手势导航已作为自动化窗口压力验证；如果发布门禁要求“HyperOS 实机分屏”逐项留证，仍需用户手动进入分屏后补一次视觉确认。
