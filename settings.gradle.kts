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
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            // JitPack нужен только для TDLib, остальное берём из официальных репозиториев.
            content { includeGroup("com.github.tdlibx") }
        }
    }
}
rootProject.name = "Tgmus"
include(":app")
