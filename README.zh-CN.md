# Next Key

[English](README.md)

在 JetBrains IDE 里按住修饰键，看清接下来能按什么。

按住 `Ctrl`——或者 Alt、Ctrl+Shift、任意组合——半秒，活动窗口右下角弹出一块面板，
列出以它开头的所有快捷键。

- **跟着修饰键走。** 面板还在时再按下 `Alt`，立刻换成 Ctrl+Alt 那一组，不重新计时；
  松开 `Alt` 换回去。修饰键全部松开面板才收起。
- **两击快捷键。** 按下某个两击快捷键的第一击（比如 `Ctrl+Num *`），所有可能的第二击
  立即列出，不等延时。这些前缀键在修饰键列表里标着 `→ N 个后续`，一眼能看出哪些键
  还能往下走。
- **不碍事。** 按下不构成前缀的键就收起；倒计时期间动鼠标会取消——Ctrl+滚轮缩放、
  Ctrl+悬停看类型都不会把它招出来。

条目按动作的用途分节（编辑、导航、查找、重构、运行调试、版本控制、窗口与工具、其他），
分类由 action id 的命名规律推断，可以逐条改。

键名按键盘上印的写法显示：`[`、`/`、`-`、`` ` ``、`↑`、`PgUp`、`Num *`，而不是
`KeyEvent.getKeyText()` 给的 `Open Bracket`、`Slash`、`Subtract`、`Back Quote`、`Up`、
`Page Up`、`Multiply`。这张映射表还顺带把键名钉成了英文——`getKeyText` 是按 JVM 区域
取的，否则会把它们翻译掉。

完整 keymap 光 Ctrl 后面就藏着几百条快捷键，所以默认只列常用动作（内置约 120 个），
并且合并重复项：同一动作绑多个键的并成 `C,Ins`，Ctrl+0..9 的十条书签跳转并成一行 `0-9`。

## 配置

**设置界面**——`Settings | Tools | Next Key`。表格列出当前 keymap 里每一条带修饰键的
快捷键：勾选决定是否显示，*显示为* 那列可以改名，分类可以从下拉里选也可以直接输入。
表格上方是两个全局开关、按住时长、面板不透明度和过滤框。应用时整份写回配置文件。

**配置文件**——IDE 配置目录下的 `next-key.conf`（Windows 上是
`%APPDATA%\JetBrains\<IDE><版本>\`）。它和设置界面是同一份存储，手工编辑同样有效。
首次运行时按当前 keymap 生成，按修饰键组合分节：

```
show-all = false        # true 则显示全部快捷键，忽略下面的逐条开关
merge-numbered = true   # 把只差末位数字的一组动作合并成一行，如 0-9 书签
delay-ms = 500          # 修饰键按住多久后弹出提示，100-2000 毫秒
opacity = 1.0           # 提示浮窗的不透明度，取值 0.2 到 1.0

# ---- Ctrl ----
EditorDuplicate                     # Ctrl+D       Duplicate Line
-EditorLookupUp                     # Ctrl+↑       Lookup Up
GotoDeclaration = 跳转到定义         # Ctrl+B       Go to Declaration
ShowSettings = 设置 | Windows       # Ctrl+Alt+S   Settings
```

每行四种写法：`<id>` 显示、`-<id>` 隐藏、`<id> = 名字` 改名、`<id> = 名字 | 分类`
再指定分类（名字留空写成 `<id> = | 分类`）。行尾 `#` 之后是注释，注释里标着该动作的
快捷键和原名，不必去别处查 action id。分类存的是英文稳定值（`Editing`、`Navigation`
等），也可以写任意文本自成一类。

改动在下次弹出时生效，不必重启 IDE——索引缓存把配置文件的修改时间算进了缓存键。

## 构建

日常改动用 `build.ps1`。它不需要 Gradle 也不需要 SDK：直接用目标 IDE 自带的 JBR 编译，
classpath 指向该 IDE 的 `lib` 目录，不下载任何东西。要出可提交的插件包（以及跑
Plugin Verifier）用 Gradle，见 [PUBLISHING.md](PUBLISHING.md)。

```powershell
.\build.ps1              # 产物 build\next-key.jar
.\build.ps1 -Install     # 顺带安装到 IDE，重启生效
```

装到别的 JetBrains IDE：

```powershell
.\build.ps1 -Ide "D:\Applications\IntelliJ IDEA 2025.2" -Config IntelliJIdea2025.2 -Install
```

`-Config` 是 `%APPDATA%\JetBrains\` 下的配置目录名。安装目标不一定是该目录下的
`plugins`：脚本会先读 `%APPDATA%\JetBrains\<Config>\idea.properties`（其次是 IDE 安装
目录的 `bin\idea.properties`）里的 `idea.plugins.path`，有重定向就装到那里。

## 实现要点

按键用 `KeyboardFocusManager.addKeyEventDispatcher` 监听，dispatcher 一律返回 `false`，
不消费任何事件，因此不会影响正常输入；鼠标事件通过 `Toolkit.addAWTEventListener` 监听，
仅用于在倒计时期间抑制误弹。两者都是 JDK 公开 API，不依赖平台内部类。

当前按住的修饰键取自 `KeyEvent.getModifiersEx()`，按下时并上刚按下的那一位、松开时手工
剔除，不依赖 AWT 对 KEY_RELEASED 事件 modifiers 的语义。

面板装在 `JWindow` 而不是 `JBPopup` 里：修饰键组合一变就要换内容，而 JBPopup 只能取消
重建，每换一次闪一次。

索引在启动后由后台线程构建。遍历 keymap 逐个读动作显示名要几百毫秒，留到第一次按键再做
会卡住 EDT——等索引建完、面板刚显示，排在后面的 KEY_RELEASED 立刻又把它关掉。

## 可调参数

| 位置 | 常量 | 默认 | 说明 |
|---|---|---|---|
| `NextKeyController` | `AUTO_HIDE_MS` | 10000 | 兜底自动隐藏，防止修饰键的 KEY_RELEASED 丢失时浮窗挂住 |
| `HintSettings` | `MIN_DELAY_MS` / `MAX_DELAY_MS` | 100 / 2000 | 可配置的按住时长会被夹在这个范围内 |
| `HintSettings` | `STAMP_TTL_MS` | 1000 | 配置文件时间戳的信任时长，过期才再读一次磁盘 |
| `HintSettings` | `DEFAULT_VISIBLE` | 约 120 个 id | 内置常用动作清单，决定生成模板时哪些行不带减号 |
| `HintPanel` | `PAD` / `COL_GAP` / `KEY_GAP` | 14 / 22 / 12 | 内边距与各处间距 |
| `HintPanel` | `KEY_PAD` / `KEY_RADIUS` / `CAP_INSET` | 7 / 4 / 2 | 键帽的内边距、圆角与上下缩进 |
| `HintPanel` | `CORNER_RADIUS` | 10 | 浮窗圆角，`HintWindow` 裁剪窗口形状时复用 |
| `HintPanel` | `MAX_NAME_WIDTH` | 240 | 动作名超出就截断，避免一条撑宽整列 |
| `HintWindow` | `MARGIN_RIGHT` / `MARGIN_BOTTOM` | 40 / 60 | 相对活动窗口右下角的偏移 |
| `ShortcutIndex` | `MERGE_THRESHOLD` | 3 | 至少多少条只差末位数字的动作才合并 |

面板先按屏幕高度算出至少需要几列（最多占屏高 84%、屏宽 92%），再按平均高度切分，避免
最后一列只剩一两条；分类标题不会落在列尾。宽度放不下的整列会被丢弃，并在副标题里注明。

## 排版验证

`tools/RenderTest.java` 用 TSV（bucket、键名、action id）离线把面板渲染成 PNG：

```powershell
$Ide = "D:\Applications\PyCharm 2026.2.1"
& "$Ide\jbr\bin\javac.exe" --release 21 -encoding UTF-8 -cp "build\classes;$Ide\lib\*" -d build\test-classes tools\RenderTest.java
& "$Ide\jbr\bin\java.exe" "-Dstdout.encoding=UTF-8" -cp "build\classes;build\test-classes;$Ide\lib\*" dev.nextkey.RenderTest shortcuts.tsv out.png dark 0
```

最后一个参数是 bucket：0=Ctrl、1=Ctrl+Shift、2=Ctrl+Alt、3=Ctrl+Alt+Shift。classpath 要带
IDE 的 `lib`：面板会经 `Category` 取本地化分类名，那条链最终会碰到平台的 `DynamicBundle`。
无 IDE 环境下取不到界面语言，会落到英文。

设置界面没法这样离线渲染——`JBTable` 在 IDE 外初始化会抛 `Must be precomputed`，只能装进
IDE 里看。

## 界面语言

默认英文，文案在 `src/main/resources/messages/` 下。语言跟随 **IDE 的界面语言**
（Settings | Appearance & Behavior | System Settings | Language and Region），取自
`DynamicBundle.getLocale()`，而不是操作系统的区域设置——中文系统上把 IDE 设成英文的人
不少，跟错对象会让插件和 IDE 说两种语言。

加载 bundle 时用了 `ResourceBundle.Control.getNoFallbackControl`。`getBundle` 默认的查找链
是「请求的 locale → **JVM 默认 locale** → 无后缀的 base」，中间那步会让中文系统上选了英文
界面的 IDE 照样加载到中文文案。这个坑在中文系统上自测时很难发现，因为两个 locale 恰好
都是中文。

分类的存储值是英文（`Editing`、`Navigation` 等），显示时才本地化，所以换语言不会让配置
文件里的分类失效。

## 已知限制

- 在同一 keymap 内修改快捷键后，需重启 IDE 才会反映；切换 keymap 会自动重建索引。这是
  为了不订阅平台的 keymap 变更事件而做的取舍，见 `ShortcutIndex.get()`。
- 单独按住 Shift 也会触发。Shift 组快捷键很少，而且打字时按下 Shift 后紧接着就是字母键，
  会把倒计时取消掉，实际很少碰到。
- 面板显示满 `AUTO_HIDE_MS` 会自动收起，防止焦点切到别的程序后收不到修饰键的
  KEY_RELEASED 导致浮窗挂住。
- 内置常用动作清单是按平台自带的 action id 写的，各 IDE 插件带来的动作不在其中，需要的话
  在 `next-key.conf` 里取消隐藏。
- 装了新插件后，它带来的快捷键不会自动加进已生成的 `next-key.conf`，这类动作按内置清单
  判断（基本都是隐藏）。想重新生成就删掉该文件重启 IDE。
- 不透明度依赖窗口合成，远程桌面和部分 Linux 合成器下不生效，此时面板保持不透明；圆角
  同理，不支持逐像素透明时退化成直角。
- 某个分类整体放不下时，分列会退回按屏幕高度填满，此时列高可能明显不均。要真正均衡就得
  允许同一分类跨列断开，那样反而更难读。

## 卸载

删除 IDE 插件目录下的 `next-key` 目录后重启。
