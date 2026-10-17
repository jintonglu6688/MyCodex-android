# Android 在线更新人工验证清单

本清单用于 `1.1.0/code 2` 引导版本以及更高版本 Corrective Release 的首次在线更新。`1.1.1/code 3` 已验证下载与校验，但其直接 PackageInstaller Session 交接在 Xiaomi HyperOS 上绕过了用户配置的第三方安装器，因此不得覆盖发布，必须提升版本。`1.1.2/code 4` 已验证第三方安装器交接；`1.1.3/code 5` 已完成 Toast、完整日志对话框、单次“立即更新”和唯一 stable 通道的真实升级验证。正式功能版本从 `1.2.0/code 6` 开始发布。自动化不能替代未知来源设置、真实安装器选择、正式签名覆盖安装、真实 GitCode 重定向和目标读屏软件验证。

## 发布前记录

- 记录 Android 分发账号选择、`com.mycodex.mobile` 登记状态和正式签名证书公开 SHA-256 登记状态。
- 不审计或修改 `signing.7z`。
- 记录版本、Release 标签、APK SHA-256、清单 SHA-256、固定清单 URL、验证设备和验证时间。

## code 2 引导

1. 以 stable 渠道运行发布器 `-PrepareOnly`，检查 APK 为 `1.1.0/code 2`。
2. 正式发布时先发布不可变 `android-v1.1.0-c2` APK，公开验证后最后更新 `updates-android-stable`。
3. 在测试手机上手动覆盖安装 code 2；确认应用数据、配对、主题、音效和导航状态保留。
4. 手动检查应显示当前已是最新版；自动检查无提示。

## stable 版本升级

1. 在专用设备保留同签名的旧 stable 版本；不要提前安装待发布版本。
2. 提升源码中的 `versionCode` 和 `versionName`。已经公开的 `versionCode` 不得复用或覆盖。
3. 先在本地运行全量单元测试、Lint、Android UI 测试包编译和正式 Release 构建。
4. 使用以下命令只准备并校验候选，不发布：

   ```powershell
   .\tools\gitcode\Publish-GitCodeAndroidRelease.ps1 `
     -PrepareOnly `
     -ReleaseNotesZhCn "中文更新说明"
   ```

5. 核对正式签名、包名、版本、stable 固定清单地址、APK 大小和 SHA-256。
6. 确认源码工作树干净后，使用唯一发布命令创建不可变版本 Release，并最后切换 stable 指针：

   ```powershell
   .\tools\gitcode\Publish-GitCodeAndroidRelease.ps1 `
     -ReleaseNotesZhCn "中文更新说明"
   ```

7. 发布器必须先公开复验版本化 APK，最后才删除并重建 `updates-android-stable` 指针；中途失败不得让客户端发现不完整版本。
8. 在旧 stable 客户端手动检查，确认完整日志对话框只提供“稍后”和“立即更新”。
9. 完成下载、校验、授权和覆盖安装，确认用户配置的第三方安装器接管交接。
10. 记录 Wi-Fi、移动网络、旋转、Home/恢复、断网后手动重试、SHA 篡改、错包名、低版本、异签名、授权拒绝/返回、安装器取消/重试和离线缓存安装结果。
11. 使用 TalkBack/目标读屏验证动作名称、Toast、完整日志、系统设置往返和安装器往返。焦点移动不得联网或安装。
12. 确认更新期间 Windows 任务继续，Android 不显示额外活动任务提示。

## 预期边界

- “立即更新”后应连续完成下载和验证；不再出现应用内“安装更新”二次确认。缺少未知来源授权时进入系统设置，授权返回后继续同一次请求。
- 安装交接使用不指定 package/component 的 `ACTION_VIEW` APK Intent。确认设备由用户配置的第三方安装器接管；没有配置时允许平台选择安装器。
- 更新专用 FileProvider 不导出，只临时授予目标安装器读取当前 APK 的权限，且 paths 仅覆盖 `cache/updates/`。
- 下载或安装失败不自动重试；使用简短 Toast 反馈，安装器取消或返回后保留有效缓存。
- 手动检查最新版使用 Toast 提示；发现更新直接显示包含完整日志、大小、“稍后”和“立即更新”的对话框。
- 半成品只存在于私有缓存 `updates` 目录并以 `.part` 结尾；取消、失败或进程重建后删除。
- Verified Update Package 无固定过期时间，离线时仍可重新校验并交给用户或平台选择的安装器。
