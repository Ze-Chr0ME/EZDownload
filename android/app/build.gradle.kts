plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.ezsocial.video"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ezsocial.video"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "2.0.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        create("compact") {
            initWith(getByName("release"))
            // Preserve the test-install identity so this APK updates the first build.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { jniLibs.useLegacyPackaging = true }
}
dependencies {
    implementation(project(":engine"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
