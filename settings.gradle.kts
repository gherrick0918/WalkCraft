pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.google.dagger.hilt.android" -> {
                    useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")
                }
                "org.jetbrains.kotlin.android",
                "org.jetbrains.kotlin.kapt",
                "org.jetbrains.kotlin.plugin.compose" -> {
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                }
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WalkCraft"
include(":app")
