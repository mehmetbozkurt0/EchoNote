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

            // jlink runtime'ı yalnızca listelenen JDK modüllerini paketler. SQLDelight'ın
            // sqlite-driver'ı JDBC üzerinden çalıştığı için java.sql şart; olmadan
            // paketlenmiş uygulama açılışta NoClassDefFoundError: java/sql/DriverManager
            // verir. `gradlew :desktopApp:run` tam JDK kullandığı için bunu göstermez —
            // ancak createDistributable/packageMsi çıktısında ortaya çıkar.
            modules("java.sql", "java.naming")

            // icon.ico eskiden 0 bayttı ve paketleyici reddediyordu. Artık icon.png'den
            // üretilmiş, 16/32/48/64/128/256 boyutlu geçerli bir ICO.
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}