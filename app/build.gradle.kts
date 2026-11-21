plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.memmonitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.memmonitor"
        minSdk = 26          // Android 8.0 及以上
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    // 固定签名：密钥库随仓库公开（个人开源工具分发的常规做法）。
    // 所有构建签名一致，用户可直接覆盖升级，无需卸载。
    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = "memmonitor"
            keyAlias = "memmonitor"
            keyPassword = "memmonitor"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Shizuku：免 root 获取系统级内存数据（需手机上安装并激活 Shizuku）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
