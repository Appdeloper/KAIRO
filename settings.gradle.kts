pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Roborazzi publishes its Gradle plugin to Maven Central without a plugin marker for every
    // release, so point the plugin id at the module directly.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.takahirom.roborazzi") {
                useModule("io.github.takahirom.roborazzi:roborazzi-gradle-plugin:${requested.version}")
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

rootProject.name = "KAIRO"
include(":app")
