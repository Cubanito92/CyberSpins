import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  // Pinned so the native (C++/Oboe) build is reproducible on any machine or CI runner,
  // regardless of which NDK versions happen to already be installed locally.
  ndkVersion = "27.0.12077973"

  defaultConfig {
    applicationId = "com.aistudio.radiostreamer.qxnk"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    externalNativeBuild {
      cmake {
        cppFlags("-std=c++17 -O3 -frtti -fexceptions")
        arguments("-DANDROID_STL=c++_shared")
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  signingConfigs {
    // Debug builds are always signed with a local debug.keystore. If it isn't present
    // (e.g. a fresh clone on a new machine), the "ensureDebugKeystore" task below
    // generates one automatically so `assembleDebug` works out of the box.
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    // Release builds use a real upload keystore supplied via environment variables
    // (KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD) — see README.md "Publicar en modo release".
    // If those aren't set, this falls back to the debug keystore so `assembleRelease` /
    // `bundleRelease` still succeed locally for testing (the APK just won't be
    // Play-Store-ready — replace with your own keystore before publishing).
    create("release") {
      val keystorePathEnv = System.getenv("KEYSTORE_PATH")
      val storePasswordEnv = System.getenv("STORE_PASSWORD")
      val keyPasswordEnv = System.getenv("KEY_PASSWORD")
      if (keystorePathEnv != null && storePasswordEnv != null && keyPasswordEnv != null) {
        storeFile = file(keystorePathEnv)
        storePassword = storePasswordEnv
        keyAlias = "upload"
        keyPassword = keyPasswordEnv
      } else {
        logger.warn(
          "No se encontraron KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD: " +
            "el build 'release' se firmará con el keystore de debug (solo para pruebas locales)."
        )
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    prefab = true
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Generates a local debug.keystore on first build if one isn't already present, so the
// project compiles and runs out of the box on any machine without manual keystore setup.
val ensureDebugKeystore by tasks.registering {
  val keystoreFile = file("${rootDir}/debug.keystore")
  outputs.file(keystoreFile)
  doLast {
    if (!keystoreFile.exists()) {
      logger.lifecycle("No se encontró debug.keystore, generando uno nuevo en ${keystoreFile.path}")
      exec {
        commandLine(
          "keytool", "-genkeypair", "-v",
          "-keystore", keystoreFile.absolutePath,
          "-storepass", "android",
          "-alias", "androiddebugkey",
          "-keypass", "android",
          "-keyalg", "RSA",
          "-keysize", "2048",
          "-validity", "10000",
          "-dname", "CN=Android Debug,O=Android,C=US"
        )
      }
    }
  }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(ensureDebugKeystore) }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation("com.google.oboe:oboe:1.9.0")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
