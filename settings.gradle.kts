pluginManagement {
    repositories {
        google()
        mavenCentral { url = uri("https://repo1.maven.org/maven2/") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral { url = uri("https://repo1.maven.org/maven2/") }
    }
}

rootProject.name = "QuietEQ"
include(":app")
