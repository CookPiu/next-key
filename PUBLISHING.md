# 发布到 JetBrains Marketplace

## 提交前必须填的

| 位置 | 项 | 现状 |
|---|---|---|
| `plugin.xml` | `<vendor email>` | 已填 `cookpiu@outlook.com`（会在插件页面公开显示） |
| `plugin.xml` | `<vendor url>` | 暂填 `https://github.com/CookPiu/next-key`，仓库还没建 |
| `plugin.xml` | `<id>` | 暂定 `io.github.cookpiu.nextkey`。**发布后不能改**，先确认 |
| `build.gradle.kts` | `group` | 同上，`io.github.cookpiu` |
| `LICENSE` | 版权人 | CookPiu |
| Marketplace | Vendor profile | 未注册。上传前要注册并接受开发者协议 |

`<id>` 必须全网唯一且永久不变，这是唯一一个改不了的决定，其余都能随版本更新。

## 构建

本机没装 Gradle。两条路：

1. **用 IntelliJ IDEA 打开这个目录**（本机有 2025.2），它会自动下载 Gradle
   发行版并生成 wrapper，然后在 Gradle 面板里跑任务。首次同步要下载 PyCharm
   Community 2024.3 作为编译依赖，约 1GB，注意 `gradle.properties` 里那条缓存路径。
2. 装了 Gradle 之后命令行跑 `gradle wrapper` 再用 `./gradlew`。

常用任务：

```
gradlew buildPlugin      # 产出 build/distributions/next-key-0.4.0.zip，这就是提交物
gradlew verifyPlugin     # 跑 Plugin Verifier，检查声明的版本区间是否真的兼容
gradlew runIde           # 起一个装了本插件的沙箱 IDE
gradlew publishPlugin    # 需要环境变量 INTELLIJ_MARKETPLACE_TOKEN
```

`build.ps1` 那条手工路径保留着，用于快速迭代——它直接用本机 PyCharm 的 JBR 和
lib 目录编译，不下载任何东西，但产出的是裸 jar，不能提交。

## 兼容范围必须实测

`plugin.xml` 里写的 `since-build="243"`（2024.3）是估的，实际只在 PyCharm
2026.2.1（`PY-262.9437.214`）上运行过。`build.gradle.kts` 已经把编译依赖钉在
2024.3，能在编译期发现用了更新的 API；`verifyPlugin` 会进一步检查字节码层面的
兼容性。这两步跑通之前，不要按 243 提交——真跑不过就把下限提到能过的版本。

用到的平台 API 只有这些，都是长期稳定的：

- `AppLifecycleListener`、`applicationService` + `Disposable`
- `ApplicationManager.getApplication()`：`getService`、`executeOnPooledThread`
- `KeymapManager` / `Keymap` / `KeyboardShortcut`、`ActionManager`
- `PathManager.getConfigPath()`
- `DynamicBundle.getLocale()`、`Logger`
- `Configurable`、`JBTable` / `JBScrollPane` / `JBCheckBox`

其余全是 JDK 的 Swing 和 AWT。没有第三方依赖。

## 发布更新

每次发版：

1. 改 `gradle.properties` 的 `pluginVersion` 和 `plugin.xml` 的 `<version>`（两处必须一致，
   `build.ps1` 会校验；Gradle 构建以 `gradle.properties` 为准）
2. 在 `CHANGELOG.md` 加一节，并把同样的内容写进 `plugin.xml` 的 `<change-notes>`
   ——Marketplace 页面展示的是后者
3. `gradlew verifyPlugin` 通过
4. `set INTELLIJ_MARKETPLACE_TOKEN=...` 后 `gradlew publishPlugin`

**所有更新都要人工审核**，通常两个工作日内出结果，超过 3–4 个工作日没动静就发信到
marketplace@jetbrains.com。上传之后只有兼容性范围（`since-build` / `until-build`）能改，
别的写错了只能再发一版，所以第 1、2 步值得多看一眼。

想跳过等待可以发到自定义 release channel（`publishPlugin` 的 `channels` 参数，例如
`eap`）：当 stable 频道存在 120 天以内的已批准版本时，这类更新可以自动批准，用户手动
添加订阅源后能立刻装到；stable 频道仍走正常审核。

## 审核要点

JetBrains 对每个新插件和每次更新做人工审核。对照
[Approval Guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html)：

- 名称和描述要能说清插件做什么，且**英文描述必须是第一份描述** —— 已在
  `plugin.xml` 的 `<description>` 里写好
- 插件必须与描述行为一致
- 需要提供 EULA，开源许可证即可 —— 已加 MIT
- 单个插件包上限 400 MB，这个插件 50 KB 上下

建议再补一两张截图挂到插件页面（Marketplace 页面上传，不进包）。

## 还没做的

- **没有自动化测试**。逻辑里值得测的是 `ShortcutIndex.mergeNumbered`、
  `Category.guess`、`HintSettings` 的解析与回写，都是纯函数，好写。
  目前只有 `tools/RenderTest.java` 这个离线渲染工具。
- **只在 Windows 默认 keymap 上验证过**。内置白名单是按它的 action id 列的；
  Mac 与 Eclipse 等 keymap 里 action id 相同、快捷键不同，理论上没问题，没实测。
  Mac 上还多一个 Cmd 修饰键，`Category` 与面板都能处理，同样没实测。
- **设置界面只能装进 IDE 里验证**。`JBTable` 在 IDE 外初始化会抛
  `Must be precomputed`，`tools/RenderTest.java` 只能渲染提示面板。
- **单独按住 Shift 会触发**。Shift 组默认全隐藏所以看不到弹窗，但如果用户开了
  `show-all`，打字时按住 Shift 停顿就会弹。发布前可以考虑加一个"忽略哪些修饰键"
  的开关。
- **诊断日志留在了代码里**：`NextKeyBundle` 会打印一行取到的 locale，
  `ShortcutIndex` 会打印索引构建耗时。都是 info 级、每次启动各一行，
  留着便于排查，介意的话发布前删掉。
