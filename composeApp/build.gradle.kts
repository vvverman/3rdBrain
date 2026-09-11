import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
compose.resources { publicResClass = true; packageOfResClass = "brain.studio.resources"; generateResClass = always }
kotlin {
    jvm(); jvmToolchain(21)
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser { commonWebpackConfig { outputFileName = "composeApp.js" } }; binaries.executable() }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kashaCore")); implementation(compose.runtime); implementation(compose.foundation)
            implementation(compose.material3); implementation(compose.ui); implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core); implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core); implementation(libs.ktor.client.content.negotiation); implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0") }
        wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
    }
}
