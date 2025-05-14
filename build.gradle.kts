plugins {
    id("java")
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.13.3" // 👈 旧版 DSL 插件
    id("org.jetbrains.changelog") version "2.2.1"
    id("org.jetbrains.qodana") version "2025.1.1"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.github.nfzsh.intellijrancherplugin"
version = "1.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

intellij {
    type.set("IC") // IntelliJ Community Edition
    version.set("2021.1") // 旧版 IntelliJ 平台

    // 插件依赖（如果有）
    plugins.set(listOf(/*"com.intellij.java", ...*/))
}

changelog {
    groups.empty()
    repositoryUrl.set("https://github.com/nfzsh/intellij-rancher-plugin")
}

tasks {
    patchPluginXml {
        sinceBuild.set("201.1")
        untilBuild.set("251.*")

        pluginDescription.set(
            providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"
                with(it.lines()) {
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").trim()
                }
            }
        )

    }

    wrapper {
        gradleVersion = "8.10.2"
    }

    runIde {
        // 可以加 VM 参数验证你的插件是否跑得起来
        jvmArgs = listOf(
            "-Dide.mac.message.dialogs.as.sheets=false",
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Djb.consents.confirmation.enabled=false"
        )
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
