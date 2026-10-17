# MyCodex Android 在线更新开发计划

状态：已实施，随现行实现维护

最后修订：2026-08-08

需求基线：`docs/android-online-update-requirements.md`

## 1. 当前架构

Android 在线更新只使用一个 GitCode stable 通道：

```text
客户端固定清单
  updates-android-stable/android-stable.json
        ↓
版本化不可变 APK Release
  android-v<versionName>-c<versionCode>
        ↓
用户明确选择“立即更新”
        ↓
下载 → 验证 → 授权 → Installation Handoff
```

不再提供：

- Gradle 更新渠道参数。
- test 清单或 test 指针。
- 测试更新源客户端。
- 用户可见的渠道标记。
- test 到 stable 的二阶段推广。
- `ReusePublishedApk` 发布模式。

普通单元测试、Android UI 测试和本地 `-PrepareOnly` 候选验证继续保留，它们与线上更新通道无关。

## 2. 代码所有权

### 2.1 构建和版本

- `version.properties`：唯一 `versionCode` 和 `versionName` 来源。
- `app/build.gradle.kts`：把唯一 stable 固定清单地址写入应用资源。
- 正式 Release 使用现有正式签名配置。

### 2.2 客户端

- `MyCodexApplication.kt`：创建应用级 `AndroidUpdateController`。
- `AndroidUpdateController.kt`：单操作状态机、检查节流、Toast 事件、下载、验证、授权恢复和安装器交接。
- `AndroidUpdateManifestParser.kt`：只接受 `channel: stable` 和受限 GitCode URL。
- `AndroidUpdateRepository.kt`：HTTPS 请求、私有缓存和 Partial Update Package 生命周期。
- `AndroidApkInspector.kt`：大小、SHA-256、包名、版本、兼容性和签名验证。
- `AndroidPackageInstaller.kt`：通过受限 FileProvider URI 和无指定组件的 `ACTION_VIEW` Intent 交接安装器。
- `UpdatePromptDialog.kt`：完整更新日志、APK 大小、“稍后”和“立即更新”。
- `UpdateToastHost.kt`：一次性短结果提示，避免持久状态反复被读屏浏览。

### 2.3 发布

- `tools/update/New-MyCodexAndroidUpdateManifest.ps1`：检查正式 APK，并生成唯一 `android-stable.json`。
- `tools/gitcode/Publish-GitCodeAndroidRelease.ps1`：准备候选或执行唯一 stable 发布。
- `updates/android-stable.example.json`：协议示例。

## 3. 客户端流程

### 3.1 自动检查

1. 应用进入可交互前台。
2. 24 小时节流允许时请求 stable 清单。
3. 最新版、失败和不兼容保持静默。
4. 发现更高 `versionCode` 时，在没有更高优先级模态交互的前提下显示更新对话框。
5. 自动检查不下载 APK。

### 3.2 手动检查

1. 用户激活“我的”中的“检查更新”。
2. 开始、最新版、失败或不兼容使用一次性 Toast。
3. 发现更新直接显示完整日志对话框，不进入独立更新页面。
4. 已有自动检查时由手动操作接管同一个请求，不创建第二个请求。

### 3.3 立即更新

1. 用户在更新对话框选择“立即更新”。
2. 下载到应用私有缓存的 `.part` 文件。
3. 验证完成后原子提升为 Verified Update Package。
4. 每次安装器交接前重新验证缓存。
5. 缺少未知来源权限时打开当前应用的系统授权页。
6. 授权返回且已允许时继续同一次显式请求。
7. 使用不指定 package/component 的通用 APK Intent，让用户或系统选择安装器。
8. 不显示第二个应用内“安装更新”确认框。

## 4. 状态和无障碍

- 全应用任意时刻只有一个 Update Operation。
- 焦点、浏览、旋转和 Compose 重组不得触发检查、下载或安装。
- Toast 使用事件编号，消费后不得因旋转或重新订阅而重放。
- “我的”页面只显示当前版本，不保留更新过程或错误状态。
- 更新对话框使用平台标准模态语义。
- 完整日志可滚动；“稍后”和“立即更新”在大字体下保持可到达。
- 下载开始只提示一次，不逐百分比强制朗读。
- 更高优先级的审批或提问对话框存在时，更新提示等待，不丢失也不抢焦点。

## 5. 安全边界

- 清单和 APK 初始地址必须为 HTTPS GitCode API 地址。
- 只允许 GitCode 自有 HTTPS CDN 重定向。
- 清单最大 256 KiB，APK 最大 200 MiB。
- `channel` 固定为 `stable`。
- 中文更新日志不能为空。
- APK 文件名和相对 URL 不允许路径穿越。
- APK 必须匹配声明的大小、SHA-256、application ID、较高版本、设备兼容性和正式签名。
- Partial Update Package 永不可安装，失败或进程重建时清理。
- FileProvider 不导出，路径只覆盖 `cache/updates/`，只授予目标安装器临时只读权限。
- 不记录令牌、完整 SHA、完整本地路径、异常堆栈或安装器内部错误详情。

## 6. 自动化验证

每次修改在线更新必须至少运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:assembleRelease
```

重点测试：

- stable 清单解析和所有信任边界拒绝路径。
- 版本比较和设备兼容性。
- 自动检查节流及手动接管单请求。
- Toast 一次性消费和旧事件不清除新事件。
- 完整日志对话框的滚动内容与两个明确动作。
- 下载、验证、缓存恢复、授权返回和安装器返回。
- 发布器的不可变 APK、公开复验和 stable 指针最后切换。
- 构建脚本、客户端和发布脚本中不存在第二更新通道。

## 7. 发布流程

### 7.1 准备候选

1. 提升 `version.properties`。
2. 确保测试、Lint 和 Release 构建通过。
3. 执行：

   ```powershell
   .\tools\gitcode\Publish-GitCodeAndroidRelease.ps1 `
     -PrepareOnly `
     -ReleaseNotesZhCn "中文更新说明"
   ```

4. 核对 APK 包名、版本、stable 固定清单地址、正式签名、大小和 SHA-256。

### 7.2 stable 发布

1. 确认源码工作树干净。
2. 执行：

   ```powershell
   .\tools\gitcode\Publish-GitCodeAndroidRelease.ps1 `
     -ReleaseNotesZhCn "中文更新说明"
   ```

3. 发布器先创建或核对不可变版本 Release。
4. 从公开地址下载并复验 APK 字节。
5. 最后删除并重建 `updates-android-stable` 指针。
6. 从固定 stable URL 重新读取并核对公开清单。

同一版本标签已存在时，只允许 APK 字节与本地候选完全一致；不一致必须提升 `versionCode`。

## 8. 真机验收

- 旧 stable 版本能够发现新 stable 版本。
- 最新版手动检查只显示一次 Toast，不留下持久状态。
- 更新对话框展示完整中文日志和 APK 大小。
- “稍后”关闭对话框且不永久忽略版本。
- “立即更新”连续完成下载、验证、授权和安装器交接。
- 用户配置的第三方安装器能够接管；MyCodex 不指定安装器组件。
- 授权拒绝、安装器取消和交接失败不自动重试，并保留仍有效的缓存。
- Wi-Fi 和移动网络完全使用相同流程。
- 横竖屏旋转、Home/恢复和系统设置往返不会丢失活动更新身份。
- TalkBack 或目标读屏能够完成全部动作，且没有重复、持久或逐百分比的冗余朗读。
