rootProject.name = "Aurora"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// El objetivo Android solo se configura si hay un SDK de Android instalado
// (local.properties -> sdk.dir, o ANDROID_HOME / ANDROID_SDK_ROOT).
// Así el escritorio compila y se prueba en máquinas sin SDK.
val localProps = java.util.Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val sdkDir = localProps.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
val hasAndroidSdk = sdkDir != null && file(sdkDir).isDirectory
gradle.extra["hasAndroidSdk"] = hasAndroidSdk

include(":composeApp")
if (hasAndroidSdk) include(":androidApp")
