// 注意：这份配置还没有实际跑过一次（本机没装 Gradle），首次同步时留意两处：
//   1. untilBuild 的禁用写法在 2.x 各小版本间变过，编译不过就查当前文档
//   2. 编译依赖用的是 PyCharm Community；插件只依赖 com.intellij.modules.platform，
//      换成 intellijIdeaCommunity 同样可以，而且更能挡住误用产品特有 API
plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "io.github.cookpiu"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 对着 since-build 声明的最低版本编译，才能发现用到的新 API
        pycharmCommunity("2024.3")
        pluginVerifier()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            // 不设上限：插件只用公开 API，没有随版本失效的东西
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    publishing {
        // 从 Marketplace 的 Vendor 页面生成，用环境变量传入，不要写进仓库
        token = providers.environmentVariable("INTELLIJ_MARKETPLACE_TOKEN")
    }
}
