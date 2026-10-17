import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val releaseSigningPropertiesFile = rootProject.file("app/signing/release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

val versionPropertiesFile = rootProject.file("version.properties")
check(versionPropertiesFile.isFile) { "Missing version.properties" }
val versionProperties = Properties().apply {
    versionPropertiesFile.inputStream().use(::load)
}
val myCodexVersionCode = versionProperties.getProperty("VERSION_CODE")
    ?.trim()
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("VERSION_CODE must be a positive integer")
val myCodexVersionName = versionProperties.getProperty("VERSION_NAME")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: error("VERSION_NAME must not be empty")
val myCodexUpdateManifestUrl =
    "https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/updates-android-stable/attach_files/android-stable.json/download"

android {
    namespace = "com.mycodex.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mycodex.mobile"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 36
        versionCode = myCodexVersionCode
        versionName = myCodexVersionName
        resValue("string", "update_manifest_url", myCodexUpdateManifestUrl)
    }

    signingConfigs {
        create("officialRelease") {
            storeFile = releaseSigningProperties.getProperty("storeFile")
                ?.takeIf(String::isNotBlank)
                ?.let(::file)
            storePassword = releaseSigningProperties.getProperty("storePassword")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("officialRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      resValues = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.okhttp)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.org.json)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

}
