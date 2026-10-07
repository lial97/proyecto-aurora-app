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
            // Selector de carpetas nativo: xdg-desktop-portal en Linux (respeta GTK y KDE) y el de Windows.
            implementation(libs.filekit.dialogs)
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
        // Memoria de Java: sin esto la JVM se reserva hasta 1/4 de la RAM y casi no la devuelve al sistema.
        // - Tope de 1 GB (sobra incluso con bibliotecas grandes; las imágenes van fuera de esta memoria).
        // - G1 hace una limpieza cada 15 s si la app está tranquila y devuelve lo que sobra a Windows.
        // - Deduplicar textos: artistas, álbumes y géneros se repiten en miles de pistas.
        jvmArgs += listOf(
            "-Xms64m", "-Xmx1g",
            "-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=15000",
            "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30",
            "-XX:+UseStringDeduplication",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            // VLC va dentro del instalador (resources/linux/vlc y resources/windows/vlc): no hay que instalarlo aparte.
            // Lo preparan scripts/empaquetar-linux.sh y build-windows.bat; la app lo busca con BundledVlc.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            packageName = "Aurora"
            packageVersion = "0.3.0" // también en packageWindowsExe (auroraVersion), abajo
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

// Instalador de Windows (build-windows.bat): jpackage arma el .exe desde la versión portable con windows/main.wxs,
// que al desinstalar borra la configuración y la caché (no al actualizar). No se usa packageReleaseExe de Compose
// porque vacía y pasa su propia carpeta de recursos al final, y jpackage se queda con la última.
val auroraVersion = "0.3.0"
tasks.register<Exec>("packageWindowsExe") {
    group = "compose desktop"
    description = "Instalador .exe de Windows que borra los datos al desinstalar."
    dependsOn("createReleaseDistributable", ":unzipWix")
    val appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/Aurora")
    val dest = layout.buildDirectory.dir("compose/binaries/main-release/exe-limpio")
    val wix = rootProject.layout.buildDirectory.dir("wix311")
    inputs.dir(appImage); inputs.dir("windows"); outputs.dir(dest)
    val jpackage = File(System.getProperty("java.home"), "bin/jpackage.exe")
    doFirst {
        dest.get().asFile.deleteRecursively()
        environment("PATH", wix.get().asFile.absolutePath + File.pathSeparator + System.getenv("PATH"))
    }
    commandLine(
        jpackage.absolutePath, "--type", "exe",
        "--app-image", appImage.get().asFile.absolutePath,
        "--dest", dest.get().asFile.absolutePath,
        "--resource-dir", file("windows").absolutePath,
        "--name", "Aurora", "--app-version", auroraVersion, "--vendor", "Aurora",
        "--description", "Reproductor de música y video de código abierto", "--copyright", "GPL-3.0",
        "--license-file", rootProject.file("LICENSE").absolutePath,
        "--win-dir-chooser", "--win-per-user-install", "--win-shortcut", "--win-menu", "--win-menu-group", "Aurora",
        "--win-upgrade-uuid", "6f1b8a52-3c0e-4e7a-9d0b-a1b2c3d4e5f6",
    )
}

// `./gradlew :composeApp:run` es una compilación de depuración: muestra las opciones de prueba (Ajustes › Acerca de).
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    systemProperty("aurora.debug", "true")
}
