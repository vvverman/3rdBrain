import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared"))
    implementation(project(":composeApp"))
    implementation(project(":runtime"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
}

compose.desktop {
    application {
        mainClass = "brain.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dfile.encoding=UTF-8", "-Dapple.awt.application.name=3rdBrain")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "3rdBrain"
            packageVersion = "0.2.0"
            vendor = "Vyacheslav Verman"
            description = "Локальные голосовые заметки"
            includeAllModules = true
            appResourcesRootDir.set(layout.projectDirectory.dir("bundle"))
            macOS {
                bundleID = "ru.vrmn.thirdbrain"
                dockName = "3rdBrain"
                minimumSystemVersion = "13.3"
                appCategory = "public.app-category.productivity"
                iconFile.set(layout.projectDirectory.file("packaging/3rdBrain.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>3rdBrain записывает ваш голос для локальных заметок. Аудио не отправляется в облако.</string>
                        <key>NSHighResolutionCapable</key><true/>
                        <key>CFBundleDevelopmentRegion</key><string>ru</string>
                        <key>CFBundleLocalizations</key><array><string>ru</string></array>
                    """.trimIndent()
                }
            }
        }
    }
}
