pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "payswitch"

include(
    "payment-domain",
    "payment-application",
    "payment-infrastructure",
    "payment-api",
    "bank-a-simulator",
)
