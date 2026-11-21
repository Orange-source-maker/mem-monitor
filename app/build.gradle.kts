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
        versionCode = 2
        versionName = "1.1"
    }

    // 签名策略：release 也使用调试签名（AGP 自动生成的默认调试密钥）。
    // 云端每次构建密钥不同，覆盖安装前需先卸载旧版；同一构建产物可反复安装。
    // 个人自用工具的分发方式，不用于上架应用商店。
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
