pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Shizuku API 13.1.5 已发布在 Maven Central（官方 README 确认）
        mavenCentral()
    }
}

rootProject.name = "MemMonitor"
include(":app")
