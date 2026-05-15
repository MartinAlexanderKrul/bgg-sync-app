plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun localProperty(name: String): String =
    (localProps.getProperty(name) ?: "").trim()

android {
    namespace = "cz.nicolsburg.boardflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.nicolsburg.boardflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 402
        versionName = "4.0.2"

        buildConfigField(
            "String",
            "BGG_PASSWORD",
            buildConfigString((System.getenv("BGG_PASSWORD") ?: localProperty("BGG_PASSWORD")).trim())
        )
        buildConfigField(
            "String",
            "BGG_XML_API_TOKEN",
            buildConfigString((System.getenv("BGG_XML_API_TOKEN") ?: localProperty("BGG_XML_API_TOKEN")).trim())
        )
    }

    signingConfigs {
                        getByName("debug") {
                            // Uses default debug keystore, which should be registered in Google Cloud for debug sign-in
                        }
        create("release") {
            storeFile = file(localProps["RELEASE_STORE_FILE"] as? String ?: "release.jks")
            storePassword = localProps["RELEASE_STORE_PASSWORD"] as? String ?: ""
            keyAlias = localProps["RELEASE_KEY_ALIAS"] as? String ?: ""
            keyPassword = localProps["RELEASE_KEY_PASSWORD"] as? String ?: ""
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            pickFirsts += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

}

android.applicationVariants.configureEach {
    outputs.configureEach {
        (this as BaseVariantOutputImpl).outputFileName = "board-flow-${name}.apk"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.security.crypto)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.accompanist.permissions)

    // Google Sign-In + API clients
    implementation(libs.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.services.drive) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.services.sheets) {
        exclude(group = "org.apache.httpcomponents")
    }

    // ZXing QR generation
    implementation(libs.zxing.core)

    // Jackson for BGG cache JSON
    implementation(libs.jackson.databind)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Jetpack Glance — home-screen widgets
    implementation(libs.androidx.glance.appwidget)
}
