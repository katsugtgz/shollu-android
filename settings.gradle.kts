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
    }
}

rootProject.name = "Shollu"
// Plugin-classpath dependency floors for GH Dependabot alerts are enforced in the root
// build.gradle.kts (buildscript resolutionStrategy forces + verifyDependencySecurity gate).
include(":app")
