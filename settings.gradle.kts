pluginManagement {
  repositories {
    if (System.getenv("CI") == null) {
      // Local: for main china
      maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
      maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    if (System.getenv("CI") == null) {
      // Local: for main china
      maven { url = uri("https://maven.aliyun.com/repository/google") }
      maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "imlog"

include(":app")
