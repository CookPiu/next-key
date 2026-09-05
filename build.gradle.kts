// This configuration has not been run yet. Two things to watch on the first sync:
//   1. the way untilBuild is disabled has changed between 2.x releases; check the current
//      documentation if it fails to compile
//   2. the compile dependency is PyCharm Community. The plugin only depends on
//      com.intellij.modules.platform, so intellijIdeaCommunity works just as well and is
//      stricter about accidental use of product-specific API
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
