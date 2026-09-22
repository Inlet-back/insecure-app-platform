plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.insecureappplatform"
    compileSdk = 35

    /*
      第2章 (IPC 境界) のために、被害者アプリと攻撃者アプリを別々の鍵で署名する。
      既定の debug keystore を共有すると両者の証明書が一致し、
      signature permission を宣言した攻撃者アプリにアクセスが「付与されてしまう」。
      鍵はコミットせず、無ければビルド時に生成する (buildSrc/TeachingKeystore.kt)。
     */
    signingConfigs {
        getByName("debug") {
            storeFile = TeachingKeystore.ensure(
                rootProject.file("keys/victim-teaching.p12"),
                alias = "victim",
                commonName = "InsecureAppPlatform Victim (teaching only)",
            )
            storePassword = TeachingKeystore.STORE_PASSWORD
            keyAlias = "victim"
            keyPassword = TeachingKeystore.KEY_PASSWORD
        }
    }

    defaultConfig {
        applicationId = "com.example.insecureappplatform.squeeze"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-extra"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
