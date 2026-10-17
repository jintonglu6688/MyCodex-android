# MyCodex Android UI 阶段 0–1 详细实施计划

| 项目 | 内容 |
| --- | --- |
| 状态 | 已实施并通过 Android 15 / HyperOS 真机验收 |
| 版本 | 1.1 |
| 日期 | 2026-07-30 |
| 基线提交 | `55e6634` |
| 对应规格 | [`ui-theme-accessibility-spec.md`](ui-theme-accessibility-spec.md) 的阶段 0 与阶段 1 |
| 实施范围 | 主题链路、最小主题选择页、全局颜色/排版/Shape、原生无障碍控件样式 |

## 1. 文档目的

本文把总规格中的阶段 0 和阶段 1 拆成可直接编码、逐项检查、独立提交和真机验收的工作包。

本文不是新的产品规格。若本文与总规格冲突，以总规格中的产品目标、非目标和无障碍硬性门槛为准；本文补充总规格未展开的 API 分支、状态恢复策略、文件边界、测试方法和阶段门禁。

本文最初用于制定和审阅实施顺序；阶段 0 与阶段 1 现已按该顺序完成开发和真机验收。

## 2. 最终交付结果

完成前两个阶段后，应用应具备以下能力：

1. 默认跟随标准 Android `uiMode`。
2. 用户可以在“我的 → 本机设置”中显式选择“跟随系统 / 浅色 / 深色”。
3. Activity 原生主题、Compose、原生 Android View 和系统栏使用同一个最终主题结果。
4. 所有 API 都由 Activity 在创建前选择固定 Light/Dark style，不写入包级或全局夜间模式。
5. 启动窗口继续跟随 Manifest 的 day/night 资源；显式 Light/Dark 与系统相反时，接受总规格已经声明的 starting window 边界。
6. Material 3 颜色角色不再回退到默认紫色。
7. 排版和 Shape 使用规格中确定的最小 token。
8. 原生行、输入框和按钮在浅色与深色下保持一致，同时不破坏现有 TalkBack 焦点、自定义操作、IME 和禁用态契约。
9. 主题导致的配置变化不丢失关键用户输入，不自动重发业务命令，也不会留下永久禁用的控件。

## 3. 范围边界

### 3.1 本轮包含

- 三态主题偏好和稳定持久化值。
- 所有受支持 API 的 Activity 创建前固定原生主题选择。
- `values` / `values-night` 原生主题资源。
- edge-to-edge 系统栏明暗同步。
- Profile 中现有“本机设置”入口的最小可用主题页。
- 主题切换后的页面、选择状态和无障碍焦点恢复。
- 完整 Material 3 浅色/深色角色。
- 明确的 Typography 和 Shapes。
- 三个现有原生无障碍控件的主题同步。
- 修复三处已知的原生选择行 `enabled` 未透传，保证阶段 1 的 disabled 契约能在真实调用点生效。
- JVM、instrumentation 和当前 Android 15 / HyperOS 真机验收。
- 与配置变化直接相关的最小状态连续性修复。

### 3.2 本轮不包含

- 顶栏、底栏、Profile 分组和主题页的最终视觉改造；这些属于阶段 2。
- 工作区、会话、设备和 Windows 设置页面重排；这些属于阶段 3。
- 聊天消息、Composer 和附件 Sheet 的布局重做；这些属于阶段 4。
- Navigation Compose、AppCompat、Material Components View、DataStore 或第三方主题库。
- Material You 动态色、自定义字体、动画框架或通用设计系统框架。
- OEM 私有深色设置读取。
- 恢复成功 Snackbar、Toast、持久成功状态行或其他操作成功反馈。
- 与主题配置变化无关的业务架构重写。

## 4. 当前代码基线与审计结论

| 区域 | 当前状态 | 对实施的影响 |
| --- | --- | --- |
| Manifest | `<application>` 使用 `@style/Theme.MyCodex` | Manifest 可保持不变，通过资源限定符和创建前 `setTheme()` 完成适配 |
| 原生主题 | 只有固定浅色 `android:Theme.Material.Light.NoActionBar` | 启动窗口、Button、EditText、Ripple 和部分系统栏仍是浅色 |
| Compose 主题 | `MyCodexTheme` 已支持显式 `darkTheme`，动态色默认关闭 | 无需新增主题状态框架，只需把统一解析结果传入 |
| 页面状态 | 顶层路由和大部分焦点目标已使用 `rememberSaveable` | 可以沿用现有手写导航与焦点恢复机制 |
| 本机设置 | Profile 中存在无行为入口 | 阶段 0 直接接入最小主题页，不增加其他占位功能 |
| 偏好存储 | 项目已有小型 `SharedPreferences` Store，并使用同步 `commit()` | 主题偏好沿用同一模式，无需 DataStore |
| 原生行 | 单一可访问根节点，子 TextView 隐藏，自定义 action 与焦点恢复已稳定 | 只做原位样式更新，不改语义树和动作 ID |
| 原生输入框 | 原生 `EditText`，有 TextWatcher、IME Send 和焦点恢复 | 必须保持更新顺序，主题更新不得触发文本回调或移动光标 |
| 原生按钮 | 原生 `Button`，但禁用时仍安装点击 listener | 阶段 1 同步修正严格禁用契约 |
| ColorScheme | 仅填写部分角色，深色 `surfaceVariant` 映射也不符合规格 | 阶段 1 必须显式填完规格角色和当前 Material 3 的额外角色 |
| Typography | 完全使用 Material 默认值 | 阶段 1 显式冻结规格角色及项目实际使用的 `headlineMedium` |
| 配置变化 | 部分草稿使用普通 `remember`，部分协程 busy 状态错误地使用 `rememberSaveable` | 阶段 0 开始前必须先过状态连续性门禁 |

## 5. 已确定的技术决策

### 5.1 单一主题结果

持久化的 `ThemeMode` 是产品层唯一来源；应用不创建平台夜间模式镜像，也不写入包级或全局夜间模式。

```text
SharedPreferences 中的 ThemeMode
            │
            ├── System ── 标准 Android uiMode
            ├── Light  ── false
            └── Dark   ── true
                         │
                         ├── Activity 原生主题
                         ├── Compose MyCodexTheme
                         ├── AndroidView 创建上下文
                         └── edge-to-edge 系统栏
```

不允许 Activity、Compose 和各原生控件分别推断主题。

### 5.2 主题模式模型

新增：

`app/src/main/java/com/mycodex/mobile/ui/theme/ThemeMode.kt`

最小 API：

```kotlin
enum class ThemeMode(val persistedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}
```

同一文件内放置小型 `ThemePreferenceStore` 和必要的纯映射函数，不拆分 repository、use case 或状态层。

规则：

- preferences 文件名：`ui_preferences`
- key：`theme_mode`
- 默认值：`System`
- 缺失、未知或损坏值：回退 `System`
- 持久化稳定小写字符串，不持久化 `enum.name`
- 保存使用同步 `commit()` 并处理 `false`
- 相同模式重复选择：不写盘、不切换、不重建、不显示反馈

### 5.3 API 分支

| 产品模式 | 所有 API 的 Activity style | Compose |
| --- | --- | --- |
| 跟随系统 | 按全局标准 `uiMode` 选择固定 Light/Dark style | 全局标准 `uiMode` |
| 浅色 | `Theme.MyCodex.Light` | `darkTheme = false` |
| 深色 | `Theme.MyCodex.Dark` | `darkTheme = true` |

`MODE_NIGHT_AUTO` 的公开语义是按位置和传感器自动切换，并不等同于跟随用户的系统主题；目标 HyperOS 还会让包级 `setApplicationNightMode(NO/YES)` 留下固定应用覆盖。因此应用不再调用该 API：Activity 用公开全局 `Resources` 选择固定原生主题，System 模式收到标准配置广播后只重建自身。历史测试版已经写入的包级覆盖通过一次明确的应用数据清理移除；不得读取或写入 OEM 私有设置。

### 5.4 Activity 重建策略

- 不声明 `android:configChanges="uiMode"`。
- 所有 API 只有在最终浅/深结果发生变化时调用一次 `recreate()`。
- 模式名称改变但当前最终浅/深结果相同，例如“系统浅色 → 固定浅色”，只更新持久选择和页面选中状态，不做无意义重建。
- 原生 Android View 必须由正确的 Activity theme context 创建；不能只切 Compose 颜色。

### 5.5 配置变化状态策略

状态按所有权处理，不使用“一律保存”或“一律清空”：

| 状态类型 | 示例 | 策略 |
| --- | --- | --- |
| 路由和返回目标 | 当前 Tab、当前子页、会话 ID、焦点 tag | `rememberSaveable`，重建后恢复 |
| 列表位置 | 工作区、会话、设备列表 | 沿用可保存的 LazyListState；返回前先滚动再恢复焦点 |
| 非敏感 UI 状态 | 选中的普通选项、Dialog 类型、非敏感焦点 tag | 必要时使用 `rememberSaveable`，重建后恢复 |
| 敏感或用户创作中的输入 | Composer 文本/附件、配对 URI/claim token、命令前缀、Windows 设置草稿 | 只在配置保留的内存 holder/ViewModel 中保留；禁止写入 Bundle、SavedStateHandle、SharedPreferences 或磁盘 |
| 瞬时 Dialog/Sheet | 删除确认、选择 Dialog、附件 Sheet | 能安全恢复时保存；不能安全恢复时明确关闭，并把焦点恢复到稳定触发项 |
| 服务端快照 | 工作区、会话、消息、Windows 设置快照 | 不放进 Bundle；重建后从现有 repository/连接重新加载 |
| in-flight 标记 | create/save/delete/toggle busy | 不把协程生命周期状态盲目保存为 `true` |
| 远程操作 | 删除、归档、保存、配对、命令 | 重建不得自动重发；能安全跨配置保留的 controller 使用内存 holder，否则取消旧 UI scope 后重新加载最终状态，只允许用户再次显式操作 |
| 成功/普通状态文本 | 已完成说明 | 不恢复持久成功反馈 |
| 错误 | 偏好保存失败 | 保留旧选择，显示可见且可感知的错误，不伪装成功 |

README 已明确 pairing claim token 和 conversation contents 不持久化。因此，阶段 0 不得用 `rememberSaveable` 或 Saver 保存 Composer、附件、配对 URI/claim token 等内容。它们使用项目已经依赖的 lifecycle ViewModel 作为仅配置变化期间存在的内存 holder，并在发送成功、取消、设备切换或会话切换时清理；进程死亡后按安全策略丢弃。Windows 设置 controller/draft 采用相同的配置保留方式，不使用 SavedStateHandle。

这些 holder 必须按页面或 controller 边界保持小型化，不建立全局 AppState、DI 框架或新的依赖。

配置保留对象不得持有 Activity、View、旧 Composition 回调或非 application Context。需要跨重建继续的 controller 只保留应用级依赖并暴露状态/带 ID 的一次性结果；新 UI 显式消费结果。无法解除旧 UI callback 的操作不跨配置继续，而是取消旧 scope、重新加载最终服务端状态。

## 6. 阶段 0：主题链路详细实施

### 6.1 工作包 0A：建立基线和配置变化门禁

目的：在主动引入主题重建前，先防止现有状态模型出现草稿丢失、永久 busy 或重复请求。

涉及文件按实际失败范围限制为：

- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`
- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`
- `app/src/main/java/com/mycodex/mobile/devices/DeviceScreens.kt`
- `app/src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt`
- Composer、Pairing 和 `WindowsSettingsController` 的小型配置保留 holder/ViewModel
- 对应现有测试文件

实施步骤：

1. 记录阶段开始前的 JVM、lint、Debug 构建和 androidTest 构建结果。
2. 对所有 `remember` / `rememberSaveable` 状态按第 5.5 节分类。
3. 将用户未提交输入按设备、会话或设置页稳定 ID 放入内存 holder；不进入 saved-state 或磁盘。
4. 验证 Activity recreation 后 holder 实例及草稿仍存在，进程重启后敏感草稿不会恢复。
5. 将与已取消 Compose scope 绑定的 busy 标记从可恢复状态中移除，避免重建后永久为 `true`。
6. 确认所有远程操作只由显式点击、IME action 或明确菜单触发；重建和状态恢复不得重新进入提交函数。
7. 对不能恢复的确认 Dialog 采用“关闭并回到触发项”的统一策略。
8. 为远程变更使用可控挂起的 fake repository/gateway；不增加 DI 框架，只在现有 data-source/controller 边界提供最小测试替身。
9. 用 Activity recreation 覆盖至少以下现有场景：

   - 聊天 Composer 文本和附件
   - 配对 URI、配对中的 controller 与内存 claim
   - 设置编辑输入或草稿
   - Coding 与 Office 各自的新建
   - 保存
   - 删除、归档与重命名
   - 模式 toggle
   - 配对
   - thread command

对每个独立变更入口都断言：重建前 fake 调用计数为 1，重建后仍为 1；恢复不进入提交函数；旧协程取消时 busy 被释放或保留中的 controller 继续持有准确 busy；最终状态重新加载；草稿仍在。Coding 与 Office 路径分别覆盖，不以其中一条代替另一条。

敏感状态增加独立自动化门禁：

1. 使用固定且唯一的 sentinel 分别填入 Composer/附件、配对 URI/claim 和 Windows 设置草稿。
2. `ActivityScenario.recreate()` 后断言 sentinel 仍由内存 holder 提供。
3. 使用 host-side adb/Gradle harness 终止目标进程并重新启动，断言新 holder 和对应输入均为空或来自重新加载的服务端快照，不恢复 sentinel。
4. Debug 测试通过 `run-as` 检查应用的 SharedPreferences、files、no_backup 和 databases 中不存在 sentinel。
5. 增加 source contract，禁止这些字段接入 `rememberSaveable`、Saver、SavedStateHandle 或现有持久 Store。

完成门槛：

- 配置变化期间无用户草稿丢失；进程死亡不持久化敏感草稿或令牌。
- 无远程命令自动重复发送。
- 无 busy 控件永久禁用。
- 关闭的 Dialog 有稳定焦点回退。
- 若此工作包不通过，停止阶段 0 的主题切换开发。

### 6.2 工作包 0B：原生浅色/深色主题资源

修改：

- `app/src/main/res/values/themes.xml`

新增：

- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values-v29/themes.xml`，只补 API 29+ 的 Force Dark 属性

需要定义三个 style：

- `Theme.MyCodex.Light`
- `Theme.MyCodex.Dark`
- `Theme.MyCodex`

关系：

- 默认 `values/themes.xml` 中，`Theme.MyCodex` 继承 Light。
- `values-night/themes.xml` 中，`Theme.MyCodex` 继承 Dark。
- Manifest 继续使用 `Theme.MyCodex`。
- API 26–30 显式模式在 `super.onCreate()` 前选择固定 Light 或 Dark。

两个固定主题至少显式设置：

- `android:windowBackground`
- `android:colorBackground`
- `android:textColorPrimary`
- `android:textColorSecondary`
- `android:colorAccent`
- `android:colorControlActivated`
- `android:colorControlNormal`
- `android:colorControlHighlight`
- `android:statusBarColor`
- `android:navigationBarColor`
- `android:windowLightStatusBar`
- `android:windowLightNavigationBar`
- API 29+ overlay 中的 `android:forceDarkAllowed`

约束：

- 固定 Light/Dark style 必须引用固定命名的颜色资源，不能引用会随 `-night` 改变的同名颜色。
- Light 和 Dark 都使用透明系统栏策略，并由 edge-to-edge 明确控制图标明暗。
- 固定 Light/Dark 在 `values-v29` overlay 中关闭 Force Dark，避免 API 29–30 在系统深色时再次处理原生 View，同时不把新平台属性无条件放进低版本资源。
- 第一版不增加 SplashScreen 依赖或双 Launcher；平台启动窗口使用 Manifest day/night alias 和对应 `windowBackground`，显式主题与系统相反时接受已记录的首帧边界。
- XML 颜色与阶段 1 Kotlin palette 的必要重复由测试校验，不引入代码生成。

资源测试：

- `ContextThemeWrapper(Theme.MyCodex.Light)` 解析为浅色原生属性。
- `ContextThemeWrapper(Theme.MyCodex.Dark)` 解析为深色原生属性。
- day/night 默认 alias 指向正确主题。
- `colorAccent`、`colorControlActivated` 和 `windowBackground` 与对应 Compose palette 一致。
- 系统栏图标明暗属性与主题匹配。

### 6.3 工作包 0C：主题偏好与纯映射

新增：

- `app/src/main/java/com/mycodex/mobile/ui/theme/ThemeMode.kt`
- `app/src/test/java/com/mycodex/mobile/ui/theme/ThemeModeTest.kt`
- `app/src/androidTest/java/com/mycodex/mobile/ui/theme/ThemePreferenceStoreTest.kt`

实现内容：

1. `ThemeMode` 三态与稳定持久值。
2. `fromPersistedValue()`。
3. `resolveDark(systemDark)`。
4. 所有 API 的固定 Activity style 映射。
5. System 模式在全局 `systemDark=true/false` 下的 style 映射。
6. `ThemePreferenceStore.load()` / `save()`。

单元测试必须覆盖：

- `null`、空值和未知值回退 System。
- `system/light/dark` 正确往返。
- System 在 `systemDark=true/false` 下的结果。
- Light/Dark 忽略系统值。
- System/Light/Dark 的固定 style 映射。
- 选择当前模式不调用写入函数。
- 可替换写入函数返回 `false` 时，选择处理器返回失败且状态更新和 recreate 调用计数均为 0。

持久化测试必须在每个用例前后清理专用 preferences，避免污染应用或其他测试。

### 6.4 工作包 0D：Application、Activity 与系统栏接线

修改：

- `app/src/main/java/com/mycodex/mobile/MyCodexApplication.kt`
- `app/src/main/java/com/mycodex/mobile/MainActivity.kt`
- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`

#### Application 进程启动

`MyCodexApplication.onCreate()`：

1. 调用 `super.onCreate()`。
2. 不读取主题偏好。
3. 不取得 `UiModeManager`，也不调用 `setApplicationNightMode()`。

Application 不持有主题、Compose 状态或 Flow。主题解析全部留在 Activity，避免目标 HyperOS 的固定包级夜间覆盖压住标准全局 `uiMode`。

Manifest 的 day/night alias 只负责启动窗口跟随系统。显式 Light/Dark 与系统相反时，进程内代码无法在第一帧 starting window 之前选择固定 style；该边界按总规格保留，不为零闪屏引入 AppCompat、SplashScreen 依赖或双 Launcher。

#### 所有 API 的 Activity 创建

`MainActivity.onCreate()` 的顺序：

1. 读取 `ThemeMode`。
2. 所有 API 按模式和标准全局系统配置选择 Light 或 Dark style，并在 `super.onCreate()` 前调用 `setTheme()`。
3. 根据持久偏好和当前标准系统配置计算 `resolvedDark`。
4. 所有 API 都用该结果配置 edge-to-edge `SystemBarStyle`。
5. 调用 `super.onCreate()`。
6. 创建 Compose。

Application 不再承担平台夜间模式同步；Activity 统一解析原生主题、Compose 和系统栏。

#### Compose 接线

MainActivity Composition 顶层持有当前 `ThemeMode`：

- System 使用全局标准 `Resources` 的当前 `uiMode`，避免包级平台覆盖反过来污染“系统模式”的判断。
- Light/Dark 返回固定布尔值。
- `MyCodexTheme(darkTheme = resolvedDark, dynamicColor = false)`。
- `MyCodexApp` 必须接收当前 `themeMode` 和 `onThemeModeSelected: (ThemeMode) -> Boolean`。

MainActivity 拥有 Store 和重建副作用。LocalSettings 只调用该回调：`true` 表示持久化成功，`false` 表示失败。`MyCodexApp` 的主题参数和回调都设为必填，Preview 和测试显式传入，避免生产调用遗漏后静默使用 no-op 默认值。

#### 用户选择顺序

共同顺序：

1. 只响应显式激活。
2. 当前模式相同则直接返回。
3. MainActivity 同步 `commit()` 新偏好。
4. 保存失败时回调返回 `false`：保持旧模式和旧选中态，不排队焦点请求、不重建；LocalSettings 显示 polite 错误。
5. 只有保存成功后才更新当前模式或重建。
6. 只有切换前后的最终浅/深结果不同时，MainActivity 才创建一次带 generation 和来源 Activity-instance token 的待恢复焦点请求。

焦点请求不直接写入旧 Composition 的 `rememberSaveable` 状态。MainActivity 将 pending request 保存在普通 Activity 字段中，并只在实际发生配置保存的 `onSaveInstanceState()` 写入 Bundle；新 Activity 读取后才把 request 传给 LocalSettings。这样旧 AndroidView 不会在销毁前提前消费目标。

1. 比较切换前后的最终浅/深结果。
2. 结果不同则先排队一次 focus request，再只调用一次 `recreate()`；旧 Activity 不先显示一套 Compose-only 颜色。
3. 结果相同则不创建 focus request，只更新当前模式状态和选中说明；已激活的原节点保持焦点，不重建、不强制重新发送 accessibility focus。

System 模式下系统 `uiMode` 变化时，标准配置变化负责低版本 Activity 重建；API 31+ 额外监听标准配置广播并用全局 `Resources` 复核，在目标 OEM 的历史包级覆盖压住常规 Activity 配置变化时仍只重建本 Activity。不得读取或写入 OEM 私有设置。

#### edge-to-edge

- 不继续依赖默认检测在固定覆盖模式下猜测系统栏图标。
- 使用最终 `resolvedDark` 明确构造 status/navigation bar 的 `SystemBarStyle`。
- 背景保持透明，Light 使用深色图标，Dark 使用浅色图标。
- 手势导航和三键导航都进入真机矩阵。

### 6.5 工作包 0E：最小本机主题设置页

修改：

- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`
- `app/src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt`，仅增加一次性 focus generation 防重，不做阶段 1 样式
- `app/src/main/res/values/ids.xml`，增加 View 内部焦点 generation tag
- `app/src/androidTest/java/com/mycodex/mobile/MainScaffoldTest.kt`

沿用现有手写导航，增加：

- `AppScreen.LocalSettings`
- 页面标题“本机设置”
- `AppFocusTarget.ProfileLocalSettings`
- Profile “本机设置”入口的真实导航回调
- LocalSettings → Profile 的系统返回与可见返回链
- 主题选项焦点恢复 target

页面只包含三个 `NativeAccessibleActionRow`：

| 模式 | 标题 | 正文 | 稳定 tag |
| --- | --- | --- | --- |
| System | 跟随系统 | 已选中 / 未选中 | `local_theme_system` |
| Light | 浅色 | 已选中 / 未选中 | `local_theme_light` |
| Dark | 深色 | 已选中 / 未选中 | `local_theme_dark` |

每一行：

- 是唯一可聚焦和可操作节点。
- `contentDescription` 包含模式名称和“已选中/未选中”。
- 可选图标或勾选仅作视觉展示，不暴露第二个节点。
- 不增加“应用”按钮。
- 获得普通焦点、TalkBack 浏览焦点或重组时不得切换主题。
- 只有点击、TalkBack 双击、Enter 或 Space 才执行选择。
- 选择当前模式不做任何反馈。

为保证外接键盘一致性，阶段 0 在原生行上增加受 enabled 门控的 Enter/Space key listener：只在非重复的 key-up 调用一次 `performClick()`，并消费对应 key-down/key-up，避免再落入平台默认路径造成双触发。disabled 时移除 listener，最终 `isClickable=false`。

焦点恢复顺序：

1. 只有最终浅/深结果变化且偏好保存成功时，MainActivity 才创建带 generation、来源 Activity instance 和稳定 tag 的 request。
2. 旧 Activity 不把 request 暴露给当前 Composition；只有实际重建时通过 `onSaveInstanceState()` 交给新实例。
3. 新 Activity 校验来源 instance 不同、generation 尚未消费，并保持在 LocalSettings。
4. 目标行在把 Runnable 入队前同步记录“该 generation 已排队”，防止额外 update 排入多个 `post`。
5. 目标行执行一次 `post → requestFocus() → ACTION_ACCESSIBILITY_FOCUS → onFocusRestored`。
6. `onFocusRestored` 消费 generation 并清除 request；额外重组不得再次恢复。
7. 保存失败、选择当前模式或最终浅/深结果相同时不创建 request，不强制重新播报。
8. 返回 Profile 时使用现有链路恢复到“本机设置”入口。

`NativeAccessibleActionRow` 继续保留现有 Boolean `restoreFocus` 契约，并增加一个有默认值的 generation 参数。View 在 `post` 前把该 generation 写入内部 tag；相同 generation 的重复 update 不再排队。`restoreFocus=false` 后允许未来的新 generation 正常恢复。其他既有调用点不需要为阶段 0 批量改写。

错误行为：

- 偏好写入失败时显示可见错误。
- 错误使用 polite live region，且相同错误不因重组重复播报。
- 页面持有 `errorText` 和单调递增的 `failureGeneration`；generation 只在一次新的显式保存失败时增加，普通重组不得重写错误状态。
- 第二次显式尝试若仍返回相同文案，使用新的 generation 产生一次新的播报；同一 generation 的额外重组不产生新事件。
- 保存成功或用户离开页面时清除错误，不保留空 live-region 节点。
- 不显示成功 Toast、Snackbar 或持久结果行。

### 6.6 阶段 0 自动测试

| 测试层 | 内容 | 通过条件 |
| --- | --- | --- |
| JVM | ThemeMode 解析、序列化、resolveDark、style 映射 | 全部分支确定且非法值回退 System |
| Instrumentation | SharedPreferences 保存/读取/损坏值 | 三态稳定且测试互不污染 |
| Instrumentation | 原生 Theme 属性解析 | Light/Dark/default night alias 与 palette 一致 |
| Compose UI | Profile → 本机设置 | 入口真实可用，返回逐级正确 |
| Compose UI | 三个主题选项 | 每项一个节点、选中说明正确；点击、Enter、Space 分别只回调一次；聚焦和浏览不回调 |
| Compose UI | Store 写入失败 | fake writer 返回 false；旧选择保留，recreate 计数为 0；每个新 failure generation 播报一次，同一 generation 重组不重复发事件 |
| Activity | 准备偏好与标准全局系统模式后启动 MainActivity | 原生 `isLightTheme`、Compose 标记和系统栏一致 |
| Activity | `recreate()` | 仍在本机设置；新 Activity 目标 View 获得普通/无障碍焦点，回调恰好一次，额外重组不再恢复 |
| 生命周期 | 可控挂起 fake 覆盖每个变更入口 | 重建前后提交计数均为 1，最终状态重载，不丢内存草稿、不永久禁用 |
| 进程死亡安全 | 敏感 sentinel + host-side restart + `run-as` 扫描 | 配置重建保留；进程死亡后不恢复，应用持久目录无 sentinel |
| 外部冷启动 harness | 在目标进程创建前准备偏好并重启进程 | 验证 starting window 跟随系统、Activity 按产品偏好解析且不改写系统模式 |

现有 `MainScaffoldTest` 使用普通 `ComponentActivity`，不能证明真正的启动主题，因此必须新增真实 `MainActivity` instrumentation 覆盖。但普通 instrumentation 进程的 Application 通常早于测试方法创建：测试内只预写 preference 再启动 `ActivityScenario`，不能证明 `MyCodexApplication.onCreate()`。

测试分三层：

1. Store/UI 测试使用可替换的最小写入函数或 fake Store，不增加接口层级；失败注入必须能断言 recreate 未调用。
2. MainActivity 测试在 launch 前设置 preference 和标准全局系统模式，只验证 Activity 创建后的原生/Compose/系统栏一致性；teardown 恢复原系统模式并清理测试 preference。
3. Application 冷启动使用外部 adb/Gradle harness、独立测试进程或等价定制 runner，在新目标进程创建前准备状态并真正 force-stop/start；同时捕获 starting window，并断言应用未改写系统模式。不得用普通 ActivityScenario 冒充该覆盖。

### 6.7 阶段 0 真机验收

目标环境：

- 当前已连接的 Android 15 / HyperOS 手机：API 31+、OEM、TalkBack 和真实启动窗口主验收。
- 至少一个 API 30 emulator/device：覆盖 `setTheme() + recreate()`、`values-night`、Force Dark 和系统栏。
- 若要完整验证 minSdk 承诺，再增加 API 26 emulator；若暂时无法提供，必须把未覆盖风险显式保留，不能宣称最低版本真机门禁已完成。

主题矩阵：

- System → Light
- System → Dark
- Light → Dark
- Dark → Light
- Light → System
- Dark → System
- 三种模式分别冷启动、热启动和杀进程重启
- System 模式下改变系统浅色/深色
- 固定 Light/Dark 下改变系统模式

每次检查：

- 启动窗口、Activity、Compose 和原生 View 无主题分裂。
- 系统栏背景及图标可读。
- System 模式启动窗口与 Activity 一致；显式模式与系统相反时只接受已记录的 starting window 边界。
- 切换最多重建一次。
- TalkBack 浏览选项不切换；双击后回到新选中行并播报新状态。
- 系统返回回到 Profile 并恢复“本机设置”焦点。
- 2.0 字体下三个选项自然增高，无裁切。
- 使用可控失败 writer 在 TalkBack 下验证一次偏好写入失败：旧选择不变、无主题切换、错误只播报一次。

HyperOS 专项门禁：

- Dark/Light → System 后立即服从当前系统模式。
- 随后再次切换系统模式，应用继续跟随。
- 冷启动仍跟随。

目标 HyperOS 已确认包级平台 API 不可靠；实现改为直接使用公开全局配置并重建本 Activity，仍须验证当前模式、后续系统切换和冷启动三条路径。

### 6.8 阶段 0 完成定义

- 0A–0E 全部完成。
- JVM、lint、Debug、androidTest 构建通过。
- 当前 Android 15 真机主题矩阵通过。
- TalkBack 和 2.0 字体通过。
- API 30 低版本分支 connected test 通过；API 26 覆盖状态被明确记录。
- 无配置变化导致的数据或业务回归。
- 敏感状态进程死亡自动化通过，未进入 saved-state 或应用持久目录。
- 不修改业务页面布局。
- 阶段 0 形成独立可回滚提交。

## 7. 阶段 1：全局 token 与原生控件详细实施

### 7.1 阶段 1 入口门禁

开始前必须确认：

- 阶段 0 已提交并通过真机验收。
- Activity Context、Compose 和原生 View 已使用同一主题结果。
- `values-night/themes.xml` 已生效。
- API 31+ 与 API 26–30 分支均有测试证据。

阶段 0 未通过时，不允许用阶段 1 的程序化 tint 掩盖原生主题错误。

### 7.2 工作包 1A：完整颜色角色

修改：

- `app/src/main/java/com/mycodex/mobile/ui/theme/Color.kt`
- `app/src/main/java/com/mycodex/mobile/ui/theme/Theme.kt`
- `app/src/test/java/com/mycodex/mobile/ui/theme/ThemeColorContrastTest.kt`

实施规则：

1. 把总规格 6.3 和 6.4 中的所有浅色/深色角色显式写入 ColorScheme。
2. 修正深色 `surfaceVariant` 为规格值 `#353945`。
3. 颜色常量以 Material 角色命名，不继续扩展 `BluePrimary`、`GreenTertiary` 这类实现色相名称。
4. 保留一份 ARGB 常量，构造 ColorScheme 时转换为 `Color`；不为每个角色维护同义双份属性。
5. `scrim` 浅深色共用 `#000000`。
6. 动态色参数保留，但默认继续为 `false`。
7. Light/Dark scheme 调整为 `internal`，让测试直接验证真实映射，不增加测试专用 getter。

当前 Material 3 还包含总规格表未单列的角色。为保证“不混入默认紫色”，采用以下固定映射，不创造新颜色：

| 额外角色 | 映射 |
| --- | --- |
| `surfaceTint` | 当前主题 `primary` |
| 浅色 `surfaceBright` | `surface` |
| 浅色 `surfaceDim` | `surfaceContainerHighest` |
| 深色 `surfaceBright` | `surfaceContainerHighest` |
| 深色 `surfaceDim` | `surfaceContainerLowest` |
| `primaryFixed` | `#D7E3FF` |
| `primaryFixedDim` | `#A8C8FF` |
| `onPrimaryFixed` | `#001B3F` |
| `onPrimaryFixedVariant` | `#0D477F` |
| `secondaryFixed` | `#BCEBF5` |
| `secondaryFixedDim` | `#A4CBD5` |
| `onSecondaryFixed` | `#0A2025` |
| `onSecondaryFixedVariant` | `#28505A` |
| `tertiaryFixed` | `#B9F1CD` |
| `tertiaryFixedDim` | `#A5D0B6` |
| `onTertiaryFixed` | `#082114` |
| `onTertiaryFixedVariant` | `#27513A` |

三组 fixed 角色在浅色和深色 ColorScheme 中使用同一组值，符合 fixed 角色不随当前主题反转的语义。

### 7.3 工作包 1B：Typography 与 Shapes

修改：

- `app/src/main/java/com/mycodex/mobile/ui/theme/Type.kt`
- `app/src/main/java/com/mycodex/mobile/ui/theme/Theme.kt`

Typography 显式定义：

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
| `headlineMedium` | 28sp | 36sp | Normal |

`headlineMedium` 是审阅阶段补充的实际使用角色，用于配对短码；保持当前 Material 默认视觉尺寸，但不再依赖未来库版本的默认变化。

Shapes 直接在 `Theme.kt` 中定义一个 `AppShapes`：

- extraSmall：8dp
- small：12dp
- medium：16dp
- large：20dp
- extraLarge：28dp

约束：

- 继续使用系统 Sans。
- 不新增字体文件。
- 不新建通用 spacing/token 框架。
- 关键文字容器不设固定高度。
- `MaterialTheme` 显式传入 `colorScheme`、`typography` 和 `shapes`。

### 7.4 工作包 1C：`NativeAccessibleActionRow`

修改：

- `app/src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt`
- `app/src/main/java/com/mycodex/mobile/MyCodexApp.kt`，只补一处已知 `enabled` 透传
- `app/src/main/java/com/mycodex/mobile/coding/CodingScreens.kt`，只补两处已知 `enabled` 透传

视觉更新：

- 标题：enabled 使用 `onSurface`，disabled 使用 `onSurfaceVariant`。
- 正文：`onSurfaceVariant`。
- Footer：enabled 使用 `primary`，disabled 使用 `onSurfaceVariant`。
- 标题严格对应 `titleMedium`：16sp / 24sp / SemiBold。
- 正文严格对应 `bodyMedium`：14sp / 20sp / Normal。
- Footer 严格对应 `bodySmall`：12sp / 16sp / Normal。
- 根行 `minimumHeight = 48dp`，不设置固定高度。
- 根背景保持透明；页面容器样式留给阶段 3/4。
- factory 中对主题 Ripple 调用 `mutate()`，避免共享 Drawable 状态污染其他行。
- update 中原位更新现有 Ripple 的颜色，不替换根 View 或 foreground。
- Ripple 使用 `primary` 的轻量透明色；disabled 时因不可点击而不触发。

必须保持不变：

- 根节点保持 `isFocusable=true` 和 `IMPORTANT_FOR_ACCESSIBILITY_YES`；`isClickable` 严格跟随 enabled。
- 三个子 TextView 不可聚焦且 `IMPORTANT_FOR_ACCESSIBILITY_NO`。
- 一行一个名称和默认点击动作。
- `contentDescription` 每次 update 同步。
- 自定义 action 继续使用现有应用资源 ID。
- AccessibilityDelegate 每次 update 获取最新动作和回调。
- 阶段 0 增加的 Enter/Space 单次激活 listener 及其 enabled 门控保持不变。
- disabled 同时移除点击、长按和全部自定义 action。
- 先安装或清除 click/long-click listener，最后显式设置：

  - `row.isClickable = enabled`
  - `row.isLongClickable = enabled && onLongClick != null`

  不能依赖 `setOnClickListener(null)` 或 `setOnLongClickListener(null)` 自动清除平台 flag。
- `bodyMaxLines` 只影响视觉截断，完整名称仍可读。
- `post → requestFocus → ACTION_ACCESSIBILITY_FOCUS → onFocusRestored` 顺序。
- 不使用 `key(darkTheme)` 重建 AndroidView。

调用方最小修复：

- 已知三处“lambda 内判断 enabled、但未向 `NativeAccessibleActionRow` 传参”的选择行在本工作包直接补齐 `enabled = enabled`。
- 不改变三处的布局、文案、动作 ID、长按菜单或业务条件。
- 阶段 3 仍负责全页面复核，但不能把这三处已知无障碍缺口继续留到阶段 3。

### 7.5 工作包 1D：`NativeAccessibleTextInput`

视觉更新：

- 文本：enabled 使用 `onSurface`，disabled 使用 `onSurfaceVariant`。
- Hint：`onSurfaceVariant`。
- `backgroundTintList`：

  - focused：`primary`
  - enabled/default：`outline`
  - disabled：`outlineVariant`

- 字体严格对应 `bodyLarge`：16sp / 24sp / Normal。
- 选择高亮可使用带透明度的 `primary`。
- 光标和选择柄优先由阶段 0 的 `colorAccent` / `colorControlActivated` 提供。
- API 26–28 不使用反射修改 cursor Drawable。

严格更新顺序：

1. 移除旧 TextWatcher。
2. 只有外部文本真实变化时才 `setText()`。
3. 仅该分支把 selection 放到新文本末尾。
4. 更新 enabled、名称、hint、行数、文字和 tint。
5. 更新焦点与 IME listener。
6. 安装新 TextWatcher。
7. 最后执行一次性焦点恢复。

disabled 时移除 IME action listener，或在 listener 内以 `enabled` 做同等强度的显式 guard；`IME_ACTION_SEND` 不得调用提交回调。

主题变化本身不得：

- 清空或改写文本。
- 移动光标或选择范围。
- 触发 `onTextChange`。
- 触发 IME Send。
- 改变普通焦点或 TalkBack 焦点。

### 7.6 工作包 1E：`NativeAccessibleButton`

视觉更新：

- `isAllCaps = false`
- `minimumWidth` 和 `minimumHeight` 均不低于 48dp
- 字体严格对应 `labelLarge`：14sp / 20sp / Medium
- enabled 背景：`primary`
- enabled 文字：`onPrimary`
- disabled 背景：`surfaceContainerHighest`
- disabled 文字：`onSurfaceVariant`
- 原位更新 tint/Ripple，不替换 Button 根实例

严格禁用行为：

- `button.isEnabled = enabled`
- 先按 enabled 安装 listener 或调用 `setOnClickListener(null)`
- listener 更新完成后，最后设置 `button.isClickable = enabled`

必须保持：

- `text` 和 `contentDescription` 同步。
- 原生 Button 类型。
- `isFocusable = true`。
- `IMPORTANT_FOR_ACCESSIBILITY_YES`。
- 现有焦点恢复顺序和回调时机。

### 7.7 阶段 1 颜色测试

扩展 `ThemeColorContrastTest`，直接检查真实 Light/Dark ColorScheme。

必须覆盖：

- 所有 `onPrimary/primary`、container、Secondary、Tertiary 和 Error 配对，正文门槛 4.5:1。
- `onBackground/background` 和 `onSurface/surface`。
- `onSurfaceVariant` 对实际使用的全部 Surface 层级。
- 透明原生行的真实组合：`onSurface/background`、`onSurfaceVariant/background`、`primary/background`。
- `error/background`，覆盖当前页面级错误文字。
- 若透明行实际落在 Surface Container 上，则检查 `onSurface`、`onSurfaceVariant` 和 `primary` 对每个实际 container。
- `primary/primaryContainer` 的选中状态。
- disabled Button 的 `onSurfaceVariant/surfaceContainerHighest`。
- inverse 角色。
- 三组 fixed 角色。
- `outline` 对所有可能承载输入边界的 Surface，门槛 3:1。
- 所有非 scrim 角色为不透明色。
- 每个 ColorScheme 属性与规格 ARGB 精确相等。
- `MyCodexDynamicColorDefault == false`。

`outlineVariant` 只允许作为装饰性分隔或 disabled 装饰，不把它当作焦点轮廓或唯一必要边界，因此不强行要求 3:1。

### 7.8 阶段 1 原生控件测试

新增：

`app/src/androidTest/java/com/mycodex/mobile/AccessibleNativeControlsThemeTest.kt`

在同一 Composition 中切换 Light/Dark，验证：

1. 三类 AndroidView 实例没有被主题重组替换。
2. 行标题、正文、Footer 颜色正确更新。
3. EditText 文字、Hint 和 background tint 正确更新。
4. Button 文字和背景 tint 正确更新。
5. 输入框的普通焦点、文本和选择位置不变。
6. `restoreFocus=false` 时，主题更新不触发 row click、long click、自定义 action、文本回调、IME Send、Button click 或焦点恢复回调。
7. `restoreFocus=true` 时，测试宿主在 `onFocusRestored` 中同步消费为 false；恢复恰好一次，随后主题更新不再次恢复，也不触发 click/action。
8. disabled ActionRow：

   - `isEnabled=false`
   - `isClickable=false`
   - `isLongClickable=false`
   - 无 click listener
   - 无 long-click listener
   - AccessibilityNodeInfo 无传入的 custom action
   - AccessibilityNodeInfo 无默认 `ACTION_CLICK` 和 `ACTION_LONG_CLICK`
   - `performClick()`、Accessibility `ACTION_CLICK`、Enter、Space 和长按均不执行回调
   - 执行 custom action 不调用回调

9. disabled TextInput：

   - `isEnabled=false`
   - `IME_ACTION_SEND` 不执行回调
   - 主题更新不触发文本回调

10. disabled Button：

   - `isEnabled=false`
   - `isClickable=false`
   - 无 click listener
   - AccessibilityNodeInfo 无默认 `ACTION_CLICK`
   - `performClick()` 不调用回调

11. 行与按钮命中尺寸不低于 48dp。
12. 主题更新后 custom action 仍能显式执行一次。
13. 行的三个显示子节点仍不单独暴露。

Compose 语义树不能直接证明原生 TextView 的 `currentTextColor` 或 Drawable tint；这些断言使用 instrumentation 的 ViewAssertion，不增加 Robolectric。

同时扩展现有 `AccessibilityActionContractTest`，为 Button 增加 disabled listener 契约；保留现有 ActionRow、自定义 action、输入和焦点恢复契约。

### 7.9 阶段 1 真机验收

在 Light、Dark 和 System 三种模式下检查：

- ActionRow、Button、EditText、Ripple，以及公共控件实际接收到的 disabled 状态与 Compose 表面一致。
- 不存在默认紫色。
- 原生行仍为一个 TalkBack 节点，子文字不重复播报。
- 删除、归档、重命名、子代理和复制等自定义 action 数量、标签和结果不变。
- 所有业务上 disabled 的已知选择行都实际传入 `enabled=false`，不报告可点击，不响应长按，不暴露自定义 action。
- IME Send、光标、选择、文本和焦点恢复未回归。
- 1.0、1.3、2.0 字体下无裁切，目标至少 48×48dp。
- TalkBack 真实 accessibility focus 在主题重建和返回后恢复一次。

阶段 1 只修复已经定位的三处透传缺口，不借此批量改造页面。阶段 3 再复核其他调用点是否存在同类遗漏。

### 7.10 阶段 1 完成定义

- 阶段 1 的 token 与原生控件工作包全部完成。
- 所有精确颜色映射和对比度测试通过。
- JVM、lint、Debug、androidTest 构建通过。
- connected androidTest 通过。
- 当前 Android 15 真机 Light/Dark/System、TalkBack 和 2.0 字体通过。
- 无新的依赖、页面重构或通用设计系统抽象。
- 阶段 1 形成两个独立可回滚提交。

## 8. 实施与提交顺序

| 顺序 | 提交建议 | 内容 | 合并门槛 |
| --- | --- | --- | --- |
| 1 | `fix(ui): preserve state across theme recreation` | 工作包 0A 的必要状态连续性修复和测试 | 草稿、busy、远程操作门禁通过 |
| 2 | `feat(ui): establish native light and dark theme chain` | 原生资源、ThemeMode、Store、Application、Activity、edge-to-edge、阶段 0 测试 | API 分支与启动主题测试通过 |
| 3 | `feat(ui): add accessible local theme settings` | 本机设置页、三态选择、返回与焦点恢复 | TalkBack/返回/选择测试通过 |
| 4 | `feat(ui): complete color typography and shape tokens` | Color、Typography、Shapes、精确映射和对比度测试 | JVM 测试通过，无默认色泄漏 |
| 5 | `feat(ui): synchronize accessible native controls with app theme` | ActionRow、EditText、Button、三处 enabled 透传和 instrumentation | 原生控件与无障碍契约通过 |

每个提交：

- 只包含对应工作包。
- 先运行相应小测试，再运行本阶段完整门禁。
- 不混入 README、协议、自动化或其他业务改动。
- 失败时只回退当前小提交，不使用破坏工作树的 reset。

## 9. 验证命令

每个阶段至少执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

设备可用时执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

阶段 0 和阶段 1 完成后：

1. 构建最新 Debug APK。
2. 安装到当前无线调试设备。
3. 执行本文真机矩阵。
4. 记录设备型号、Android 版本、ROM、主题模式和结果。
5. 所有阻断项通过后再更新总规格和 README 的完成状态。

## 10. 审阅记录

### 10.1 架构审阅

结论：通过。

理由：

- 使用现有 SharedPreferences 和手写导航。
- `MyCodexTheme` 已支持显式布尔值，无需主题状态框架。
- 所有 API 使用同一套 Activity 固定主题解析，不写平台夜间模式。
- Manifest、Gradle 依赖和业务 repository 无需为主题改造。
- 不通过 `configChanges` 吞掉原生 View 重建。

修订项：

- 将 SharedPreferences 定义为产品来源，标准全局 `uiMode` 只用于解析 System。
- 明确禁止 Application 或 Activity 写入包级/全局夜间模式。
- 把所有 API 的 resolved theme 与 edge-to-edge 顺序写成同一条 Activity 创建链。
- 明确主题选择回调返回同步保存结果，失败不会更新状态或重建。
- 避免“最终颜色相同”时无意义重建。

### 10.2 无障碍审阅

结论：有条件通过；条件已经写成阶段门禁。

确认保留：

- 一个主题选项一个原生可访问节点。
- 显式激活，不允许焦点触发工作。
- 原生 action ID、点击、长按、IME 和焦点恢复链不变。
- disabled 同时移除默认点击、长按、键盘、IME 和自定义 action。
- 最小 48dp 且大字体自然增高。
- 系统返回与可见返回同层级。
- 主题 focus request 只经实际 Activity saved-state 交给新实例，并以 generation 防止提前或重复消费。
- 相同错误文案以 failure generation 区分“新的显式失败”和“普通重组”，每次失败只播报一次。

阻断条件：

- TalkBack 浏览主题选项时发生切换。
- 重建后焦点回到首页或重复恢复。
- 子 TextView 被重复播报。
- 主题样式更新触发业务回调。
- 2.0 字体裁切或命中目标不足。

### 10.3 生命周期与业务安全审阅

结论：发现前置风险，已增加工作包 0A。

发现：

- Composer、附件、配对 URI 和部分 Dialog 输入使用普通 `remember`。
- 部分 in-progress 值使用 `rememberSaveable`，但执行它们的 Compose coroutine 会在重建时取消。
- 另一些 in-progress 值不会恢复，可能在服务端最终状态尚未刷新时过早重新开放操作。

处理：

- 用仅驻内存的 ViewModel/holder 跨配置保留用户输入；conversation、附件、pairing token 和设置草稿不写入 Bundle 或磁盘。
- 用 sentinel + host-side process restart + `run-as` 扫描自动证明进程死亡后敏感状态消失且未落盘。
- 不保存失去对应协程的 busy=true。
- 重建不自动重发操作。
- 使用可控挂起 fake 逐一验证 Coding/Office 的 create、save、delete/archive/rename、toggle、pair 和 thread command 计数。
- 服务端数据重新加载；进程死亡按安全策略丢弃敏感草稿。
- 不能安全恢复的 Dialog 关闭并恢复触发项焦点。
- 该门禁通过前不启用可触发重建的主题设置。

### 10.4 视觉 token 审阅

结论：通过，已补两处总规格未展开项。

补充：

- 当前 Material 3 还包含 surface bright/dim、surface tint 和 fixed roles，全部显式映射，防止默认紫色泄漏。
- 项目实际使用 `headlineMedium`，将其固定为 28sp / 36sp / Normal。
- 深色 `surfaceVariant` 修正为 `#353945`。
- `outlineVariant` 只用于非必要装饰或 disabled 装饰，不承担 3:1 必要边界。
- 原生文本明确映射到对应 Typography 的字号、行高和字重。
- 对比度矩阵增加透明原生行直接落在 background/container 上的真实组合。
- disabled listener 更新后最后清除 clickable/longClickable 平台 flag。

### 10.5 测试可行性审阅

结论：通过；设备覆盖是实施门禁。

修订项：

- 普通 ActivityScenario 不再被视为 Application 冷启动测试。
- API 31+ 使用外部进程冷启动 harness，并在 teardown 清理测试偏好、恢复原系统主题。
- 增加 API 30 emulator/device 验证低版本 Activity/theme 分支；API 26 覆盖状态必须明确记录。
- 偏好 commit failure 使用可替换写入函数注入。
- 焦点恢复测试拆分为 `restoreFocus=false` 和 generation 一次性消费两个场景。
- 三处已经定位的 `enabled` 未透传在阶段 1 一并修复；阶段 3 只继续做全页面复核。
- 敏感草稿使用 sentinel 同时验证“配置重建保留”和“进程死亡不恢复/不落盘”。

### 10.6 最小化审阅

结论：通过。

明确不做：

- 不新增依赖。
- 不创建主题 repository/use case/CompositionLocal。
- 不创建 Navigation Compose 图。
- 不迁移原生无障碍控件到 Compose。
- 不建立通用 spacing 或组件工厂。
- 不批量修改阶段 2–4 页面。
- 不为低版本零闪屏引入双 Launcher 或 AppCompat。

## 11. 风险、信号与处置

| 风险 | 早期信号 | 处置 |
| --- | --- | --- |
| HyperOS 的历史包级固定覆盖压住全局状态 | 系统设置记录为深色，但应用进程公开 `uiMode` 仍为浅色 | 最终代码不再调用包级夜间 API；历史测试版覆盖通过一次明确的应用数据清理移除 |
| 显式偏好与启动窗口不同 | 显式 Light/Dark 与系统相反时，冷启动首帧短暂跟随系统 | Activity 创建前选择固定 style；接受已记录边界，不写平台模式 |
| 双重重建 | 选择一次出现两次生命周期重建或焦点播报 | 只在最终颜色变化时调用一次 `recreate()` |
| Compose 与原生 View 分裂 | 原生 Ripple/Button 仍为浅色 | Activity 创建前设主题；阶段 1 原位同步 tint；真机阻断 |
| XML 与 Kotlin 色值漂移 | 启动窗口/原生 accent 与 Compose 不同 | instrumentation 精确比较必要属性 |
| 默认 Material 色泄漏 | 未显式角色出现紫色 | 测试真实 ColorScheme 的全部角色和精确 ARGB |
| 配置变化丢草稿 | 系统切主题后输入清空 | 0A 用内存 holder 保留；敏感内容不进入 saved-state 或磁盘 |
| busy 状态失配 | 重建后按钮永久禁用或立刻重复可用 | busy 不盲目保存；取消旧 scope、重新加载最终状态 |
| 焦点提前或重复恢复 | 旧 Activity 清掉 target、TalkBack 重复播报或触发点击 | request 只经实际 onSaveInstanceState 传给新实例；generation 入队去重并一次性消费 |
| 大字体裁切 | 2.0 字体下行高固定 | 仅设 minimum size，内容驱动增高 |

## 12. 开发启动前检查表

- [ ] 用户确认按本文执行阶段 0 和阶段 1。
- [ ] 当前工作树中的规格文档变更已单独确认，不与应用代码混提交。
- [ ] 记录基线测试结果。
- [ ] 先完成 0A 状态连续性门禁。
- [ ] 再实现原生资源和 ThemeMode。
- [ ] 阶段 0 自动测试通过。
- [ ] 阶段 0 HyperOS + TalkBack 真机通过。
- [ ] 才开始完整 ColorScheme、Typography 和 Shapes。
- [ ] 再修改三个原生控件。
- [ ] 阶段 1 自动测试与真机通过。
- [ ] 更新总规格、README 状态并提交最终验收记录。

## 13. 官方实现依据

- [Android 深色主题指南](https://developer.android.com/develop/ui/views/theming/darktheme)
- [UiModeManager.setApplicationNightMode](https://developer.android.com/reference/android/app/UiModeManager#setApplicationNightMode(int))
- [Android 运行时配置变化](https://developer.android.com/guide/topics/resources/runtime-changes)
- [AndroidX EdgeToEdge API](https://developer.android.com/reference/androidx/activity/EdgeToEdge)
- [AOSP UiModeManagerService 当前应用夜间模式实现](https://android.googlesource.com/platform/frameworks/base/+/android16-release/services/core/java/com/android/server/UiModeManagerService.java)
