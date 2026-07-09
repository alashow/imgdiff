pluginManagement {
    repositories {
        val customRepoUrl = providers.gradleProperty("mavenRepo.customUrl").orNull
        if (customRepoUrl != null) {
            maven { url = uri(customRepoUrl) }
        } else {
            gradlePluginPortal()
            mavenCentral()
            google()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val customRepoUrl = providers.gradleProperty("mavenRepo.customUrl").orNull
        if (customRepoUrl != null) {
            maven { url = uri(customRepoUrl) }
        } else {
            mavenCentral()
            google()
        }
    }
}

rootProject.name = "imgdiff"
include(":frontend")
