plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.aurora.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Cambiar por un id propio antes de publicar en Play Store (p. ej. io.github.<usuario>.aurora).
        applicationId = "app.aurora.music"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8 reduce y optimiza el código: la app arranca antes, va más fluida y ocupa menos memoria que la de depuración.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firmada con la clave de depuración de este equipo: se instala encima de la de depuración sin perder datos.
            // Cambiar por una clave propia antes de publicar en Play Store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // Los receptores de los widgets (Glance) están en :composeApp y lint no ve que extienden BroadcastReceiver.
        disable += "Instantiatable"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
}
