rootProject.name = "Phone"
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://www.jitpack.io") }
        // mavenLocal() is opt-in so release builds cannot pick up a stale local Commons.
        // Use -PlocalCommons when testing unpublished Commons changes (see FORK.md).
        if (gradle.startParameter.projectProperties.containsKey("localCommons")) {
            mavenLocal()
        }
    }
}
include(":app")
