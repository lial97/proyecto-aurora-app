import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val hasAndroidSdk = gradle.extra["hasAndroidSdk"] as Boolean
if (hasAndroidSdk) apply(plugin = libs.plugins.androidKmpLibrary.get().pluginId)

kotlin {
    jvmToolchain(21)

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.compose.backhandler)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // Solo el lector de JSON (JsonElement): no hace falta el plugin de serialización.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.vlcj)
            implementation(libs.jaudiotagger)
        }
    }
}

if (hasAndroidSdk) {
    kotlin.targets.withType<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>().configureEach {
        namespace = "app.aurora.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
    }
    kotlin.sourceSets.getByName("androidMain").dependencies {
        // Reproductor nativo de Android: audio, video y segundo plano con MediaSession.
        implementation(libs.media3.exoplayer)
        implementation(libs.media3.session)
        implementation(libs.media3.ui)
        implementation(libs.media3.ui.compose)
        implementation(libs.androidx.datastore.preferences)
        // Widgets de la pantalla de inicio (Material You con glance-material3).
        implementation(libs.glance.appwidget)
        implementation(libs.glance.material3)
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "app.aurora.resources"
}

compose.desktop {
    application {
        mainClass = "app.aurora.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            // VLC va dentro del instalador (resources/linux/vlc y resources/windows/vlc): no hay que instalarlo aparte.
            // Lo preparan scripts/empaquetar-linux.sh y build-windows.bat; la app lo busca con BundledVlc.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            packageName = "Aurora"
            packageVersion = "0.3.0"
            description = "Reproductor de música y video de código abierto"
            vendor = "Aurora"
            copyright = "GPL-3.0"
            licenseFile.set(rootProject.file("LICENSE"))
            // Módulos de Java que usan vlcj (JNA) y el cliente HTTP de las letras.
            modules("java.net.http", "java.naming", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icons/aurora.ico"))
                menuGroup = "Aurora"
                // Accesos directos en el escritorio y en el menú Inicio, y elegir carpeta de instalación.
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                // Identificador fijo: así una versión nueva actualiza la anterior en lugar de duplicarla.
                upgradeUuid = "6f1b8a52-3c0e-4e7a-9d0b-a1b2c3d4e5f6"
            }
            linux {
                iconFile.set(project.file("icons/aurora.png"))
                shortcut = true
                appCategory = "AudioVideo"
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}

// Pruebas con capturas: AURORA_SHOTS=/ruta guarda las imágenes que generan.
tasks.withType<Test>().configureEach {
    System.getenv("AURORA_SHOTS")?.let { systemProperty("aurora.shots", it) }
}
