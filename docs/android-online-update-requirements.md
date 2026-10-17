# MyCodex Android 在线更新最终需求规格

状态：已确认

确认日期：2026-08-07

适用仓库：`MyCodex-android`

目标发行渠道：GitCode 独立分发

## 1. 文档目的

本文档定义 MyCodex Android 在线更新首版的最终产品需求、领域规则、协议约束、用户交互、无障碍要求、发布规则和验收边界。实现计划、任务顺序和文件级变更见 `docs/android-online-update-development-plan.md`。

以下 ADR 是本需求的上游约束：

- `docs/adr/0001-use-gitcode-for-independent-android-distribution.md`
- `docs/adr/0002-use-package-installer-sessions.md`（已被 ADR 0006 取代）
- `docs/adr/0003-publish-immutable-apks-before-the-stable-pointer.md`
- `docs/adr/0004-bootstrap-online-update-with-version-code-2.md`
- `docs/adr/0005-android-repository-owns-android-update-publishing.md`
- `docs/adr/0006-use-fileprovider-installer-handoff.md`

## 2. 目标

首版必须实现以下完整闭环：

1. Android 应用直接从 GitCode 获取稳定更新清单。
2. 应用根据 Android `versionCode` 判断是否存在适用于当前设备的新版本。
3. 应用支持自动检查和用户手动检查。
4. 用户能够查看中文更新日志和安装包大小。
5. 用户明确操作后，应用下载并验证 APK。
6. 应用通过只读 FileProvider URI 和通用 APK 打开 Intent 发起 Installation Handoff，不指定安装器组件。
7. 安装被取消或失败后，用户能够复用仍然有效的已验证 APK 重试。
8. Android 仓库能够独立构建、生成清单并向现有 GitCode 分发仓库发布 stable 更新。
9. 整个流程能够由屏幕阅读器用户独立完成。

## 3. 明确不在首版范围内

首版不实现：

- Google Play 构建变体或 Google Play 应用内更新。
- Windows 或 Relay 参与 Android 更新检查、下载、缓存或安装。
- 强制更新、最低支持版本阻断或旧版本禁用。
- stable 之外的测试、预览或自定义更新渠道。
- 用户可填写的清单 URL。
- 自动检查开关。
- APK 自动下载或自动安装。
- 移动网络专属提示、仅 Wi-Fi 下载设置或计费网络判断。
- 可靠后台下载、下载前台服务、下载通知或 WorkManager 下载任务。
- HTTP Range 断点续传。
- 应用层自动重试。
- 永久忽略某个版本。
- 系统更新通知。
- 自建安装界面、静默安装、固定指定系统或第三方安装器组件，以及 `ACTION_INSTALL_PACKAGE` 安装路径。
- 允许降级、覆盖已发布 APK 或复用已发布 `versionCode`。
- 远端撤回已经下载到离线设备的 APK。
- `signing.7z` 内容审计、Git 历史清理、签名材料保管整改或密钥轮换。

## 4. 仓库与运行边界

### 4.1 源码所有权

`MyCodex-android` 独立拥有：

- Android 更新客户端。
- Android 版本定义。
- Android 更新清单模型与示例。
- APK 构建和验证逻辑。
- GitCode Android Release 发布脚本。
- Android 更新自动化测试和真机验收说明。

`MyCodex` Windows 仓库继续独立拥有 Windows 更新，不调用 Android 构建或发布脚本。

`MyCodex-relay` 不参与本功能，不增加协议、命令、事件、文件传输或状态字段。

### 4.2 分发存储

Windows 与 Android 可以共用以下公开 GitCode 分发仓库：

```text
https://gitcode.com/gcw_SpGZ48lW/mycodex-updates
```

但 Android 必须使用独立标签和固定清单地址，不读取或修改 Windows 的 `updates-stable`。

建议命名：

```text
版本化 APK Release：android-v<versionName>-c<versionCode>
稳定指针：updates-android-stable
稳定清单附件：android-stable.json
```

正式稳定清单固定地址：

```text
https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/updates-android-stable/attach_files/android-stable.json/download
```

## 5. 版本规则

### 5.1 权威比较字段

- 更新可用性只根据 APK 的整数 `versionCode` 判断。
- 远端 `versionCode` 必须严格大于当前安装版本，才是 Available Update。
- `versionName` 仅用于显示、文件名和 Release 标签，不参与大小比较。
- 每个公开构建的 `versionCode` 必须唯一且严格递增。
- 不得覆盖、复用或重新解释已经发布的 `versionCode`。

### 5.2 首次引导版本

- 当前既有版本：`1.0 / versionCode 1`。
- Bootstrap Release：`1.1.0 / versionCode 2`。
- code 2 通过现有手动安装方式分发，因为 code 1 没有在线更新能力。
- First Online Update：`1.1.1 / versionCode 3`。
- First Online Update 和后续 Corrective Release 都通过唯一 stable 指针发布；发现问题时必须提升 `versionCode`，不得覆盖旧 APK。

## 6. 更新清单协议

### 6.1 首版 JSON 形状

```json
{
  "schemaVersion": "1",
  "channel": "stable",
  "generatedAt": "2026-08-07T00:00:00Z",
  "packageBaseUrl": "https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/android-v1.1.0-c2/attach_files/",
  "app": {
    "applicationId": "com.mycodex.mobile",
    "versionCode": 2,
    "versionName": "1.1.0",
    "minimumAndroidApi": 26,
    "releaseDate": "2026-08-07",
    "releaseNotes": {
      "zh-CN": "中文纯文本更新说明。",
      "en-US": "Optional English release notes."
    },
    "asset": {
      "url": "MyCodex-Android-v1.1.0-c2.apk/download",
      "fileName": "MyCodex-Android-v1.1.0-c2.apk",
      "sha256": "<64位小写十六进制SHA-256>",
      "size": 9285230
    }
  }
}
```

### 6.2 必填与校验

客户端必须拒绝以下清单：

- 空响应或超过 256 KiB 的响应。
- `schemaVersion` 不是字符串 `"1"`。
- `channel` 不是字符串 `"stable"`。
- `generatedAt` 不是有效 UTC 时间。
- `packageBaseUrl` 不是允许的 HTTPS GitCode Release 附件基址。
- `app.applicationId` 不是 `com.mycodex.mobile`。
- `versionCode` 缺失、不是正整数或超出 Android 支持范围。
- `versionName` 为空或超过合理显示长度。
- `minimumAndroidApi` 缺失或不是正整数。
- stable 清单缺少非空 `releaseNotes.zh-CN`。
- 更新日志超过规定长度。
- `asset.url` 是绝对 URL、包含路径穿越或无法安全解析到 `packageBaseUrl`。
- `asset.fileName` 为空、包含路径或不是 `.apk` 文件名。
- `asset.sha256` 不是 64 位十六进制字符串。
- `asset.size` 小于等于零或超过 200 MiB。

允许的网络行为：

- 初始清单和 APK URL 必须是 HTTPS。
- 允许 GitCode API 跳转到 GitCode 自有 HTTPS 文件 CDN。
- 不允许 HTTP 降级。
- 不允许清单把 APK 指向任意第三方绝对地址。

### 6.3 兼容性结果

- 远端 `versionCode` 小于或等于当前版本时，结果为已是最新版。
- 远端版本较新但 `minimumAndroidApi` 高于当前设备时，不得下载；手动检查显示“当前设备不支持此版本”，自动检查保持静默。
- 未知 schema、无效字段或安全校验失败属于清单错误，不得降级为“已是最新版”。

## 7. 唯一稳定构建

- 所有构建都使用唯一的 stable 固定清单地址。
- 不提供 Gradle 更新渠道参数、测试清单、预览指针或客户端渠道状态。
- 正式包没有渠道设置、自定义 URL、调试菜单或隐藏长按入口。
- 真机在线升级验证直接使用 stable 发布路径；发现问题时发布更高 `versionCode` 的 Corrective Release。

## 8. 自动检查

### 8.1 触发与节流

- GitCode Independent Distribution Build 始终启用自动检查，不提供设置开关。
- 主界面已经建立并可交互后，应用可以尝试一次自动检查。
- 自动检查与配对设备、Windows 在线状态和 Relay 连接状态无关。
- 只有实际开始网络请求时才持久化 `lastAutomaticCheckAttemptAt`。
- 距离上次自动尝试不足 24 小时时，不再自动请求。
- 成功、超时、DNS 失败、HTTP 失败和清单无效都进入相同的 24 小时节流期。
- 用户修改系统时间造成的节流偏差不在首版补偿。

### 8.2 自动检查结果

- 已是最新版：静默。
- 请求或清单失败：静默并记录脱敏诊断类别。
- 设备不兼容：静默。
- 发现 Available Update 且应用处于前台：显示一次标准对话框。
- 发现 Available Update 但应用不在前台：保存可用更新状态，不发送系统通知。
- 同一 `versionCode` 在同一个 24 小时检查周期内最多自动提示一次。
- 更新提示不得抢占审批、用户提问或其他已经显示的模态交互。

### 8.3 自动提示

自动提示和手动检查发现更新后的提示使用同一个标准对话框，显示：

- 新版本名称。
- APK 大小。
- 完整中文 Release Notes。
- “立即更新”。
- “稍后”。

“稍后”是临时延期，不永久忽略版本。下一次满足自动检查条件或用户再次手动检查且版本仍未安装时可以再次提示。“立即更新”是一次明确的用户授权，允许客户端连续完成下载、验证、安装授权和 Installation Handoff，不再要求第二次点击安装。

## 9. 手动检查和“我的”页面

### 9.1 入口位置

“我的”页面顺序必须为：

1. 设备管理。
2. 用户中心。
3. Windows 设置。
4. 本机设置。
5. 检查更新。
6. 分享诊断日志。

### 9.2 行为

- “检查更新”行默认显示当前 `versionName`。
- 激活该行立即开始 Manual Update Check，不先打开空详情页。
- 手动检查不受 24 小时限制，不修改自动检查计时。
- 检查开始、已是最新版、检查失败和设备不兼容使用简短 Toast 反馈，不在“我的”页面留下持久状态文本。
- Toast 是一次性事件，旋转、重组或重新订阅状态不得重复显示旧提示。
- 发现 Available Update 时直接显示包含完整 Release Notes 的更新对话框，不进入独立页面。

### 9.3 已有操作时的优先级

- 自动检查正在运行时，手动操作接管同一个请求；不发送第二个请求，结果按手动检查展示。
- 下载、验证或 Installation Handoff 正在运行时，激活“检查更新”只显示“更新正在处理中”；不重新检查或开始第二个操作。
- 已有 Verified Update Package 时，激活“检查更新”重新显示更新对话框；选择“立即更新”后重新验证缓存并直接交接，不重复下载。
- 任意时刻全应用只允许一个 Update Operation。

## 10. 更新对话框

必须显示：

- 远端 `versionName`。
- 中文 Release Notes。
- APK 格式化大小。
- “稍后”。
- “立即更新”。

Release Notes 按纯文本完整显示，保留普通换行，不解析 HTML、Markdown、脚本、图片或远程链接。长日志必须可滚动，两个决策动作在大字体下仍可到达。

## 11. 下载

### 11.1 用户控制

- APK 永远不能由自动检查下载。
- 只有用户在更新对话框中明确激活“立即更新”后才开始下载。
- Wi-Fi 与移动网络完全使用相同流程。
- 不判断计费网络，不显示移动网络专属确认。
- APK 大小仅作为普通信息展示。

### 11.2 文件与生命周期

- Partial Update Package 写入应用缓存内的受控 `updates` 子目录。
- 下载中使用 `.part` 后缀。
- 下载、取消和异常路径不得写入公共下载目录。
- 横竖屏旋转或 Activity 配置变更不能重新开始下载或丢失进度。
- 进程存活时，短暂切到后台可以继续下载。
- 首版不承诺 Android 终止进程后的继续下载。
- 进程重建后发现 Partial Update Package 必须删除，并从头重试。
- 首版不实现 Range 断点续传。
- 应用正常结束时取消活动下载并清理 Partial Update Package。

### 11.3 前后台行为

- 更新对话框在开始下载前关闭；不打开下载详情页或第二个安装确认对话框。
- 用户可以继续使用应用；短暂切到后台不取消下载。
- 首版精简流程不提供下载中的页面级取消按钮；进程终止仍按 Partial Update Package 规则清理。

### 11.4 失败和重试

- 应用层不自动重试。
- 下载失败后删除 Partial Update Package。
- 下载失败使用简短 Toast 提示，不留下持久错误区域。
- 用户再次激活“检查更新”并选择“立即更新”后从头下载。

## 12. 下载后验证

APK 下载完成后必须按顺序验证：

1. 实际文件大小与清单一致。
2. 实际 SHA-256 与清单一致。
3. APK 能被 Android PackageManager 正确解析。
4. APK application ID 是 `com.mycodex.mobile`。
5. APK `versionCode` 与清单一致且严格高于当前安装版本。
6. APK `versionName` 与清单一致。
7. APK minSdk 与清单一致且设备兼容。
8. APK 当前签名或有效签名历史与已安装应用相容。

任何一步失败：

- 删除 APK。
- 不进入安装流程。
- 不自动重新下载。
- 用户看到“更新包验证失败，请稍后重试”。
- 诊断日志记录失败类别，不记录完整路径、哈希、临时下载 URL、凭据或系统堆栈。

最终由用户或平台选择的 Android 安装器以及包管理系统负责平台级签名和包兼容验证；客户端预检用于防止无效包进入安装流程并提供可理解的错误。

## 13. Verified Update Package 缓存

- 验证完成的 APK 从 `.part` 原子转换为最终缓存文件。
- 缓存身份由 `versionCode`、文件名、声明大小和 SHA-256 共同决定。
- 不设置 7 天、30 天等人为过期时间。
- 用户稍后安装、取消系统安装或系统安装失败时保留仍有效的缓存。
- 每次复用前重新检查至少文件存在、大小、SHA-256、包信息和签名相容性。
- 当前应用已经是相同或更高 `versionCode` 时删除缓存。
- stable 清单的包身份变化时删除旧缓存。
- 本地验证失败时删除缓存。
- Android 清除缓存后正常回到需要下载状态。
- 不提供“删除更新包”按钮。
- Verified Update Package 可以在无网络时安装，不要求安装前重新读取 stable 清单。

## 14. 安装授权

- Manifest 声明 `android.permission.REQUEST_INSTALL_PACKAGES`。
- 每次 Installation Handoff 前调用 `PackageManager.canRequestPackageInstalls()`。
- “立即更新”已经构成明确的安装意图；下载验证完成但未授权时，直接打开当前包的 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`。
- 从系统设置返回后重新检查授权状态。
- 用户完成授权后继续同一次“立即更新”请求，重新验证缓存并直接进入 Installation Handoff，不要求第二次点击。
- 用户拒绝授权时使用简短 Toast 提示并保留 Verified Update Package；以后仍需再次明确选择“立即更新”才能重试。

## 15. 安装器交接

### 15.1 唯一安装路径

- 使用 `Intent.ACTION_VIEW`、`application/vnd.android.package-archive` 和 `content://` URI 交接 Verified Update Package。
- Intent 不设置 package 或 component，由用户或平台选择已配置的安装器。
- 只授予 `FLAG_GRANT_READ_URI_PERMISSION`；不授予写入、持久化或目录前缀权限。
- 不扩大现有诊断 FileProvider 的共享路径；更新包使用独立 FileProvider。

### 15.2 FileProvider 边界

- 更新专用 FileProvider 必须 `android:exported="false"` 且 `android:grantUriPermissions="true"`。
- authority 固定派生自当前 application ID，避免与诊断分享 provider 混用。
- paths XML 只允许 `cache/updates/`，不得使用 `path="."`、外部存储根或其他宽泛目录。
- 只有用户明确激活“立即更新”且 APK 在交接前验证成功后才创建 URI 并启动 Intent。

### 15.3 结果

- 启动 Intent 后由目标安装器提供持续界面，MyCodex 不再显示第二个安装提示页。
- 安装替换应用后，新版本下次启动根据已安装 `versionCode` 清理旧状态和缓存。
- 从安装器返回且已安装 `versionCode` 未提升时，使用简短 Toast 提示并保留有效缓存。
- 无可用安装器或交接启动失败时使用简短 Toast 提示并记录脱敏失败类别。
- 不自动再次打开安装器；每次重试都必须由用户明确激活。

### 15.4 Remote Work Continuity

- 不检查 Windows 是否有活动回复、命令或自动化。
- 不增加活动任务确认。
- 不向 Windows 或 Relay 发送更新相关命令。
- Android 安装导致的连接中断不改变 Windows 工作所有权或状态。

## 16. 状态模型

实现必须覆盖以下可观察状态，不要求这些名称直接成为 UI 文案：

```text
Idle
CheckingAutomatic
CheckingManual
UpToDate
Incompatible
UpdateAvailable
Downloading
Verifying
ReadyToInstall
InstallAuthorizationRequired
PreparingInstallationHandoff
AwaitingInstaller
```

必须满足：

- 任意时刻只有一个主状态。
- 自动检查可以被手动操作提升为 `CheckingManual`，但底层请求不变。
- `Downloading` 和 `Verifying` 不允许再次检查或开始第二个下载。
- `ReadyToInstall` 可以在无网络时进入安装授权和 Installation Handoff。
- 进程重建后不能恢复 `Downloading`；Partial Update Package 被清理。
- 进程重建后可以通过持久化身份和重新验证恢复 `ReadyToInstall`。
- 错误通过一次性 Toast 提示，状态仍必须保留足够上下文，使用户再次激活“检查更新”后可以重新显示对话框或重试。

## 17. 错误和诊断

用户错误必须简洁，不显示：

- GitCode 临时签名查询参数。
- 本地完整文件路径。
- SHA-256 全值。
- 签名证书完整内容。
- 异常堆栈。
- 安装器或系统返回的原始内部错误详情。

诊断日志可以记录：

- 操作阶段。
- HTTP 状态类别。
- 清单验证失败字段类别。
- 下载字节数和声明大小。
- APK 校验失败类别。
- Installation Handoff 脱敏失败类别。
- 当前和目标 `versionCode`。

诊断日志不得记录访问令牌、GitCode 凭据、签名密码或私钥信息。

## 18. 无障碍要求

- “检查更新”“稍后”“立即更新”必须是明确可激活的动作。
- 焦点移动、屏幕阅读器浏览、选择变化或页面恢复不能触发网络、下载或安装。
- 所有动作有简短语义名称，不在名称中重复“按钮”等控件类型。
- 检查开始、最新版、失败、授权拒绝和安装器返回使用简短 Toast；不得在“我的”页面留下会被反复浏览和朗读的更新状态区域。
- Toast 必须是一次性事件，不因 Compose 重组、旋转或状态重新订阅重复朗读。
- 下载开始 Toast 只公告一次，不逐百分比朗读进度。
- 自动更新提示不能意外移动已经位于更高优先级交互中的屏幕阅读器焦点。
- 更新对话框关闭后仍停留在原页面，焦点不得跳到无关区域。
- 从系统安装授权设置返回后继续同一次显式请求或短暂提示拒绝结果，不创建重复焦点节点。
- 横竖屏旋转后活动下载和更新身份不得消失或重新开始，旧 Toast 不得重放。

## 19. GitCode 发布规则

### 19.1 版本包

- 每个公开 APK 使用唯一 Release 标签和唯一附件文件名。
- 发布后不覆盖 APK 内容。
- APK 必须使用现有正式签名配置构建。
- 发布脚本验证 APK 包名、版本、签名、大小和 SHA-256。
- 上传后必须通过公开地址重新验证附件。

### 19.2 stable 指针

- 版本化 APK 成功且公开验证通过后，才允许生成指针清单。
- 指针是发布流程最后一步。
- GitCode 同名附件不视为可靠覆盖方式；删除并重建对应指针 Release。
- 发布脚本和客户端只接受唯一的 stable 固定清单地址。
- 发布完成后从固定公开地址重新读取清单并核对目标 APK。

### 19.3 失败和修复

- APK 上传失败时不更改指针。
- 指针发布失败时保留版本化 APK，但客户端不可发现。
- 发现严重问题时可以停止继续推广或暂时调整指针。
- 已经安装问题版本的用户必须通过更高 `versionCode` Corrective Release 修复。
- 不把降级安装作为恢复机制。

## 20. Android 开发者验证

- 客户端不调用 Android Developer Console API。
- 登记状态不进入运行时更新判断。
- 开发、code 2 手动引导和本地验证不受其阻塞。
- Public Stable Rollout 前应人工选择适当分发账号，并登记：

  - `com.mycodex.mobile`。
  - 现有正式签名证书 SHA-256 指纹。

- 此运营准备不包含 `signing.7z` 审计、Git 历史清理或签名密钥轮换。

## 21. 验收标准

只有同时满足以下条件，功能才算完成：

1. 所有清单、版本比较、状态机、下载验证和缓存规则有自动化测试。
2. 自动检查 24 小时节流在成功和失败路径均通过测试。
3. 自动检查被手动接管时只发生一个 HTTP 请求。
4. Wi-Fi 与移动网络不存在不同产品分支。
5. 旋转时更新对话框内容和活动下载不丢失、不重启，旧 Toast 不重放。
6. 网络失败和进程重建不留下可安装的 Partial Update Package。
7. 大小、哈希、包名、版本、兼容性和签名任一错误都阻止安装。
8. Verified Update Package 可以离线重新验证并发起安装。
9. 未授权安装未知应用时，用户能够进入正确系统设置；完成授权后继续同一次“立即更新”请求，拒绝授权则停止并保留缓存。
10. 安装器取消、返回和交接失败路径不会自动重试或丢失有效缓存。
11. TalkBack 或目标读屏软件能够完成手动检查、阅读完整日志、选择稍后或立即更新、授权和安装交接。
12. “我的”页面顺序、Toast 公告、对话框焦点和返回行为符合本文要求。
13. Release 发布脚本能够独立在 Android 仓库运行，不依赖相邻 Windows 或 Relay 仓库。
14. stable 指针完成旧版本到更高版本 Corrective Release 的同签名真机覆盖安装，并验证用户配置的第三方安装器能够接管交接。
15. stable 发布按“不可变 APK 先发布并公开复验、stable 指针最后切换”完成。
