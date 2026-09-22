plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.attacker"
    compileSdk = 35

    /*
      被害者アプリとは別の鍵で署名する。ここが一致していると、
      AndroidManifest に書いた READ_SESSION の <uses-permission> が
      実際に付与されてしまい、P4 が「通ってしまう」。
      教材の結論が署名の分離に依存していることを、ここで担保している。
     */
    signingConfigs {
        getByName("debug") {
            storeFile = TeachingKeystore.ensure(
                rootProject.file("keys/attacker-teaching.p12"),
                alias = "attacker",
                commonName = "InsecureAppPlatform Attacker (teaching only)",
            )
            storePassword = TeachingKeystore.STORE_PASSWORD
            keyAlias = "attacker"
            keyPassword = TeachingKeystore.KEY_PASSWORD
        }
    }

    defaultConfig {
        applicationId = "com.example.attacker"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
