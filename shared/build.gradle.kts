import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("EchoDatabase") {
            packageName.set("com.echonote.echonote.db")
        }
    }
}

kotlin {
    jvm()

    androidLibrary {
        namespace = "com.echonote.echonote.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        // AGP 8.x'te KMP kütüphanesinde Android kaynakları deneysel bayrakla açılır
        // (AGP 9'daki karşılığı: androidResources { enable = true })
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
        withHostTestBuilder {
        }.configure {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.sqldelight.androidDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsCore)
            // Tasarimdaki bicimlendirme/gezinme ikonlari core sette yok.
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiBackhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // strictly: Material3'ün getirdiği 0.7.1, supabase-kt'nin Realtime zaman damgası
            // çözümlemede kullandığı InstantIso8601Serializer'ı içermiyor (runtime crash)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime") {
                version { strictly(libs.versions.kotlinx.datetime.get()) }
            }
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.multiplatformSettings.core)
            implementation(libs.multiplatformSettings)
            implementation(libs.multiplatformSettings.coroutines)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}