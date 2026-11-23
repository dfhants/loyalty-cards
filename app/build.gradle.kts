import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.loyaltycards"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.loyaltycards"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Release builds are signed with the key in ~/.config/loyalty-cards/signing.properties
    // (or the file named by the LOYALTY_SIGNING property), kept outside the project. Without it
    // (anyone else building the code) they fall back to the local debug key.
    val signing = Properties().apply {
        val path = (findProperty("LOYALTY_SIGNING") as String?)
            ?: "${System.getProperty("user.home")}/.config/loyalty-cards/signing.properties"
        file(path).takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)
    implementation(libs.zxing.core)
    implementation(libs.palette)

    implementation(libs.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
