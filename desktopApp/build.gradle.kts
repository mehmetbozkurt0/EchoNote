import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.echonote.echonote.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.echonote.echonote"
            packageVersion = "1.0.0"

            // icon.ico geçerli ICO formatında olmadığı için paketleyici reddediyor;
            // gerçek bir .ico üretilince bu blok geri açılabilir.
            // windows {
            //     iconFile.set(project.file("src/main/resources/icon.ico"))
            // }
        }
    }
}