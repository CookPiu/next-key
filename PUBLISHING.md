# 发布到 JetBrains Marketplace

## 提交前的检查项

| 位置 | 项 | 状态 |
|---|---|---|
| `plugin.xml` | `<id>` | `io.github.cookpiu.nextkey`，**发布后不可更改** |
| `plugin.xml` | `<vendor email>` | `cookpiu@outlook.com`，会在插件页面公开显示 |
| `plugin.xml` | `<vendor url>` | https://github.com/CookPiu/next-key |
| `build.gradle.kts` | `group` | `io.github.cookpiu` |
| `LICENSE` | 许可证 | MIT |
| Marketplace | Vendor profile | 待注册，上传前需注册并接受开发者协议 |

插件 ID 是唯一一项发布后无法修改的内容，其余均可随版本更新。

## 构建

出插件包需要 Gradle，本仓库未提交 wrapper。两种方式：

1. 用 IntelliJ IDEA 打开本目录，IDE 会自动下载 Gradle 发行版并生成 wrapper，
   之后在 Gradle 面板中执行任务。首次同步需要下载约 1GB 的 IDE 依赖作为编译基线，
   缓存位置可在 `gradle.properties` 中指定。
2. 本地已安装 Gradle 时，执行 `gradle wrapper` 生成 wrapper，之后使用 `./gradlew`。

常用任务：

```
gradlew buildPlugin      # 产出 build/distributions/next-key-<version>.zip，即提交物
gradlew verifyPlugin     # 运行 Plugin Verifier，核对声明的版本区间
gradlew runIde           # 启动装有本插件的沙箱 IDE
gradlew publishPlugin    # 需要环境变量 INTELLIJ_MARKETPLACE_TOKEN
```

`build.ps1` 用于日常迭代：直接调用目标 IDE 自带的 JBR 编译，classpath 指向该 IDE 的
`lib` 目录，不下载任何依赖。它产出的是裸 jar，不能作为提交物。

## 兼容范围待验证

`plugin.xml` 声明 `since-build="243"`（2024.3），而实际运行验证只在 PyCharm 2026.2.1
（`PY-262.9437.214`）上完成。`build.gradle.kts` 将编译依赖固定在 2024.3，可在编译期
暴露对更高版本 API 的依赖；`verifyPlugin` 进一步在字节码层面核对兼容性。这两步通过前
不应按 243 提交；若未通过，应将下限提升至可通过的版本。

所用平台 API 如下，均为长期稳定接口：

- `AppLifecycleListener`、`applicationService` + `Disposable`
- `ApplicationManager.getApplication()`：`getService`、`executeOnPooledThread`
- `KeymapManager` / `Keymap` / `KeyboardShortcut`、`ActionManager`
- `PathManager.getConfigPath()`
- `DynamicBundle.getLocale()`、`Logger`
- `Configurable`、`JBTable` / `JBScrollPane` / `JBCheckBox`

其余均为 JDK 的 Swing 与 AWT，无第三方依赖。

## 发布更新

每次发版：

1. 更新 `gradle.properties` 的 `pluginVersion` 与 `plugin.xml` 的 `<version>`。两处必须
   一致，`build.ps1` 会校验；Gradle 构建以 `gradle.properties` 为准。
2. 在 `CHANGELOG.md` 增加一节，并将同样内容写入 `plugin.xml` 的 `<change-notes>`。
   Marketplace 页面展示的是后者。
3. 确认 `gradlew verifyPlugin` 通过。
4. 设置 `INTELLIJ_MARKETPLACE_TOKEN` 环境变量后执行 `gradlew publishPlugin`。

所有更新均需人工审核，通常在两个工作日内出结果；超过 3–4 个工作日无反馈可联系
marketplace@jetbrains.com。上传后仅兼容性范围（`since-build` / `until-build`）可修改，
其余内容有误只能发布新版本，因此第 1、2 步需要复核。

发布到自定义 release channel（`publishPlugin` 的 `channels` 参数，如 `eap`）可以跳过等待：
当 stable 频道存在 120 天以内的已批准版本时，此类更新可自动批准，用户添加订阅源后即可
安装；stable 频道仍走正常审核。

## 审核要点

JetBrains 对每个新插件及每次更新执行人工审核。对照
[Approval Guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html)：

- 名称与描述需明确说明插件功能，且英文描述必须是第一份描述——已在 `plugin.xml` 的
  `<description>` 中提供
- 插件行为需与描述一致
- 需提供 EULA，开源许可证即可——已采用 MIT
- 单个插件包上限 400 MB，本插件约 50 KB

插件页面还需补充截图（在 Marketplace 页面上传，不打入插件包），至少包含提示面板与设置
界面各一张。同一组截图也应放进仓库并在两份 README 中引用，占位注释已留在文件顶部。

上传时需要选择标签。同类插件的取法：Key Promoter X 用 Notification and Visualizers +
Productivity，Which-Key 用 Editor，Which Key Lazy 用 Editor + Navigation。本插件适用
Editor 与 Productivity。

描述长度参照同类插件控制在数段之内：Which Key Lazy 约 220 字符，Which-Key 约 550 字符，
Key Promoter X 约六句加链接。插件列表中用户只会读前两句。

发布后可在 README 顶部补上 Marketplace 徽章（版本号与下载量），插件 ID 确定后
shields.io 的对应徽章即可生效。

## 尚未完成

- **无自动化测试**。适合测试的部分是 `ShortcutIndex.mergeNumbered`、`Category.guess`
  以及 `HintSettings` 的解析与回写，均为纯函数。当前仅有 `tools/RenderTest.java`
  这一离线渲染工具。
- **仅在 Windows 默认 keymap 上验证**。内置的常用动作清单依据其 action id 编写；
  Mac、Eclipse 等 keymap 中 action id 相同而快捷键不同，预期可用但未实测。Mac 另有
  Cmd 修饰键，`Category` 与面板均已支持，同样未实测。
- **设置界面只能在 IDE 内验证**。`JBTable` 在 IDE 外初始化会抛 `Must be precomputed`，
  `tools/RenderTest.java` 仅能渲染提示面板。
- **单独按住 Shift 会触发面板**。Shift 组动作默认全部隐藏，开启 `show-all` 后打字期间
  按住 Shift 停顿会弹出。可考虑增加"忽略指定修饰键"的开关。
- **诊断日志保留在代码中**：`NextKeyBundle` 输出一行界面语言，`ShortcutIndex` 输出索引
  构建耗时，均为 info 级、每次启动各一行。如需精简可在发布前移除。
