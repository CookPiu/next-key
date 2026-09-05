# Next Key

[![License](https://img.shields.io/github/license/CookPiu/next-key)](LICENSE)

[English](README.md)

**按住修饰键，看清接下来能按什么。**

按住 `Ctrl` 半秒，面板列出所有以 Ctrl 开头的快捷键。手别松，再按下 `Alt`，列表随即
变成 Ctrl+Alt 的那一组。松开手，面板消失。

装上就能用，也不会妨碍打字。

<!-- Screenshots: the popup over an editor, and Settings | Tools | Next Key. -->

## 安装

适用于 IntelliJ IDEA、PyCharm、WebStorm 等各款 JetBrains IDE。

在 IDE 里：**Settings | Plugins | Marketplace**，搜索 *Next Key*，安装后重启。

想装自己构建的版本，把 jar 放进 IDE 插件目录下的 `next-key/lib/` 再重启即可；
`build.ps1 -Install` 会代劳，见[构建](#构建)。

## 面板里有什么

快捷键按用途分组：编辑、导航、查找、重构、运行调试、版本控制、窗口。键名照着键盘上
印的写：`[`、`/`、`-`、`` ` ``、`↑`、`PgUp`。

列表来自你自己的 keymap。你实际在用的绑定是什么，面板里就是什么，包括别的插件带来的。
改了绑定，面板跟着变。

重复的会合并。同一个动作绑了两个键只占一行，显示成 `C,Ins`；Ctrl+0 到 Ctrl+9 那十个
书签快捷键也只占一行，显示成 `0-9`。

有些快捷键要按两次。按下第一击——比如折叠代码的 `Ctrl+Num *`——后面能接的键立刻列出来。
这类键在主列表里标着 `→ 5 个后续`，一眼看得出哪些还能往下走。

面板不碍事：按下没有后续的键它就消失；倒计时期间动一下鼠标它就不再出现。Ctrl+滚轮缩放、
Ctrl+悬停看类型都不会把它招出来。

## 按自己的习惯调

默认列出的是大多数人常用的那些。其余快捷键，以及外观上的各项，都在
**Settings | Tools | Next Key** 里：

- 显示或隐藏 keymap 里的任意快捷键
- 改成你自己叫它的名字
- 换个分类，或者自己起一个
- 改按住多久才弹出
- 把面板调得透明一些

这些设置同样存在一个纯文本文件里，改文件更顺手的话也可以。

## 配置文件

设置界面和配置文件是同一份东西，哪个顺手用哪个。

文件是 IDE 配置目录下的 `next-key.conf`（Windows 上是
`%APPDATA%\JetBrains\<IDE><版本>\`），插件第一次运行时按当前 keymap 写出，按修饰键
组合分节：

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

每行四种写法：

| 写法 | 效果 |
|---|---|
| `EditorDuplicate` | 显示，用 IDE 自带的动作名 |
| `-EditorDuplicate` | 不显示 |
| `EditorDuplicate = 复制行` | 显示，并换成你写的名字 |
| `EditorDuplicate = 复制行 \| Editing` | 再指定它归到哪个分类 |

只想改分类就把名字留空，写成 `EditorDuplicate = | Editing`。行尾 `#` 之后是注释，
每行的注释里已经标好了该快捷键和原本的动作名，不必去别处查 action id。

分类存的是英文（`Editing`、`Navigation` 等），显示时才翻译。写任意别的文本就自成一类。

改动在下次弹出面板时生效，不必重启。

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

- 在同一 keymap 内改了某个快捷键，需重启 IDE 面板才会反映；换成另一套 keymap 则会自动
  跟上。
- 单独按住 Shift 也会弹出面板。绑在单独 Shift 上的快捷键很少，而且打字时紧接着的字母键
  会取消倒计时，实际很少碰到。
- 面板显示满十秒会自动收起，以防焦点切到别的程序后漏掉修饰键的松开事件。
- 默认显示的是平台自带的那些动作。各 IDE 插件带来的动作默认隐藏，可以逐条打开。
- 新装插件带来的快捷键不会加进已有的配置文件。删掉该文件重启即可重新生成。
- 透明和圆角依赖窗口合成。远程桌面和部分 Linux 合成器下，面板会保持不透明的直角样式。
- 某个分类太高、和其他分类并排放不下时，各列高度会明显不均。

## 卸载

**Settings | Plugins** 里找到 Next Key，卸载后重启。手工安装的版本则直接删掉 IDE 插件
目录下的 `next-key` 目录。

设置存在 IDE 配置目录下的 `next-key.conf`，卸载不会带走它，想彻底清干净就一并删掉。

## 参与

问题和需求提到 [Issues](https://github.com/CookPiu/next-key/issues)。欢迎 PR——用
`build.ps1` 不必配置 Gradle 就能构建，`tools/RenderTest.java` 可以离线渲染面板，改排版
不用每次都起一个 IDE 来看。

## 许可证

[MIT](LICENSE)
