rootProject.name = "hiosdra-patches"

include(":extensions:extension")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("app.morphe.patches") version "1.3.3"
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://artifacts.bitmovin.com/artifactory/public-releases/")
            content {
                includeGroup("com.bitmovin.analytics")
                includeGroup("com.bitmovin.player")
            }
        }
    }
}
