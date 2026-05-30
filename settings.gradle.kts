pluginManagement {
    repositories {
        // 1. 阿里云 Gradle 插件镜像
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 2. 阿里云公共仓库（代理 mavenCentral 和 jcenter 等）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 3. 官方源作为最终备选
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 1. 阿里云 Google 镜像
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 2. 阿里云公共仓库镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 3. 官方源作为回退（保证冷门库或最新版可用）
        google()
        mavenCentral()
    }
}

rootProject.name = "imlog"
include(":app")
