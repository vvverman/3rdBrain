import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
plugins { alias(libs.plugins.kotlinMultiplatform); alias(libs.plugins.kotlinSerialization) }
kotlin {
    jvm(); jvmToolchain(21)
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies { implementation(libs.kotlinx.serialization.json); implementation(libs.kotlinx.coroutines.core) }
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0") }
    }
}
