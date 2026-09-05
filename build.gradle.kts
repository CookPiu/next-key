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
        // Compile against the oldest version since-build claims, so use of newer API shows up
        // here rather than in someone's IDE. IntelliJ IDEA Community is the baseline: the plugin
        // only depends on com.intellij.modules.platform, and building against IC keeps
        // product-specific API out by construction.
        intellijIdeaCommunity("2024.3")
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
