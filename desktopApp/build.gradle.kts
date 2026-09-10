import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
kotlin { jvmToolchain(21) }
val demoBuild = providers.gradleProperty("demoBuild").map { it.toBoolean() }.getOrElse(false)
dependencies {
    implementation(project(":shared")); implementation(project(":composeApp")); implementation(project(":runtime"))
    implementation(compose.desktop.currentOs); implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation(libs.kotlinx.serialization.json); testImplementation(kotlin("test-junit"))
}
compose.desktop {
    application {
        mainClass = "brain.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dfile.encoding=UTF-8", "-Dapple.awt.application.name=3rdBrain")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = if(demoBuild) "3rdBrain Test" else "3rdBrain"
            packageVersion = "1.1.2"
            vendor = "Vyacheslav Verman"
            description = if(demoBuild) "Тест интерфейса, ИИ имитируется" else "Локальные голосовые заметки"
            includeAllModules = true
            appResourcesRootDir.set(layout.projectDirectory.dir(if(demoBuild) "bundle-test" else "bundle"))
            macOS {
                bundleID = if(demoBuild) "ru.vrmn.thirdbrain.test" else "ru.vrmn.thirdbrain"
                dockName = if(demoBuild) "3rdBrain Test" else "3rdBrain"
                minimumSystemVersion = "13.3"
                appCategory = "public.app-category.productivity"
                iconFile.set(layout.projectDirectory.file("packaging/3rdBrain.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key><string>3rdBrain записывает ваш голос локально. В тестовой версии текст ИИ является примером.</string>
                        <key>NSHighResolutionCapable</key><true/>
                        <key>CFBundleDevelopmentRegion</key><string>en</string>
                        <key>CFBundleLocalizations</key><array><string>ru</string><string>en</string><string>es</string><string>fr</string><string>de</string><string>uk</string><string>be</string><string>kk</string></array>
                    """.trimIndent()
                }
            }
        }
    }
}
