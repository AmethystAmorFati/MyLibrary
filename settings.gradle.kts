pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2") {
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2") {
            content {
                includeGroupByRegex("org\\.jetbrains\\.kotlin.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "MyLibrary"
include(":app")
