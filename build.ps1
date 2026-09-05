# 编译并打包 Next Key 插件。
# 不需要 Gradle：直接用目标 IDE 自带的 JBR 编译，classpath 指向该 IDE 的 lib 目录。
#
#   .\build.ps1              仅编译打包，产物在 build\next-key.jar
#   .\build.ps1 -Install     打包后安装到 PyCharm 的 plugins 目录（需重启 IDE 生效）
#   .\build.ps1 -Ide "D:\Applications\IntelliJ IDEA 2025.2" -Config IntelliJIdea2025.2
#
param(
    [string]$Ide = "D:\Applications\PyCharm 2026.2.1",
    [string]$Config = "PyCharm2026.2",
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

# 插件目录可能被 idea.properties 的 idea.plugins.path 重定向，用户级配置优先
function Resolve-PluginsPath {
    param([string]$IdeHome, [string]$ConfigName)
    $candidates = @(
        (Join-Path $env:APPDATA "JetBrains\$ConfigName\idea.properties"),
        (Join-Path $IdeHome "bin\idea.properties")
    )
    foreach ($file in $candidates) {
        if (-not (Test-Path $file)) { continue }
        $line = Get-Content $file |
            Where-Object { $_ -match '^\s*idea\.plugins\.path\s*=' } |
            Select-Object -Last 1
        if ($line) {
            $value = ($line -split '=', 2)[1].Trim()
            $value = $value -replace '\$\{idea\.config\.path\}',
                (Join-Path $env:APPDATA "JetBrains\$ConfigName")
            return $value.Replace('/', '\')
        }
    }
    return (Join-Path $env:APPDATA "JetBrains\$ConfigName\plugins")
}
$Out = Join-Path $Root "build"
$Classes = Join-Path $Out "classes"
$Jar = Join-Path $Out "next-key.jar"

$javac = Join-Path $Ide "jbr\bin\javac.exe"
if (-not (Test-Path $javac)) { throw "找不到 javac: $javac" }

# JBR 不带 jar 工具，借用本机 JDK 8 的；jar 只做归档，与字节码版本无关
$jarTool = "D:\JDK1.8\bin\jar.exe"
if (-not (Test-Path $jarTool)) {
    $jarTool = (Get-Command jar -ErrorAction SilentlyContinue).Source
    if (-not $jarTool) { throw "找不到 jar 工具" }
}

if (Test-Path $Classes) { Remove-Item $Classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Classes | Out-Null

# 版本号在 gradle.properties 和 plugin.xml 各有一份，发版时最容易漏改其中一处
$props = Join-Path $Root "gradle.properties"
$manifest = Join-Path $Root "src\main\resources\META-INF\plugin.xml"
if ((Test-Path $props) -and (Test-Path $manifest)) {
    $gradleVersion = (Get-Content $props | Select-String -Pattern '^pluginVersion\s*=' |
        Select-Object -First 1) -replace '^pluginVersion\s*=\s*', ''
    $xmlVersion = ([xml](Get-Content $manifest -Raw)).'idea-plugin'.version
    if ($gradleVersion -and $xmlVersion -and $gradleVersion.Trim() -ne $xmlVersion.Trim()) {
        throw "版本号不一致: gradle.properties=$($gradleVersion.Trim()) plugin.xml=$($xmlVersion.Trim())"
    }
}

$sources = Get-ChildItem (Join-Path $Root "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
Write-Host "编译 $($sources.Count) 个源文件..."
# 强制英文诊断信息：Windows 控制台的 GBK 代码页会把中文报错显示成乱码
& $javac "-J-Duser.language=en" "-J-Duser.country=US" `
    --release 21 -encoding UTF-8 -Xlint:-options -cp "$Ide\lib\*" -d $Classes @sources
if ($LASTEXITCODE -ne 0) { throw "编译失败" }

# 整个 resources 目录都要进 jar：META-INF/plugin.xml 之外还有 messages/*.properties
Copy-Item (Join-Path $Root "src\main\resources\*") $Classes -Recurse -Force

if (Test-Path $Jar) { Remove-Item $Jar -Force }
& $jarTool cf $Jar -C $Classes .
if ($LASTEXITCODE -ne 0) { throw "打包失败" }
Write-Host "产物: $Jar"

if ($Install) {
    $pluginsRoot = Resolve-PluginsPath -IdeHome $Ide -ConfigName $Config
    $dest = Join-Path $pluginsRoot "next-key\lib"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item $Jar (Join-Path $dest "next-key.jar") -Force
    Write-Host "已安装到: $dest"
    Write-Host "重启 IDE 后生效。"
}
