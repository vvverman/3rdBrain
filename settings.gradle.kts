pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "3rdBrain"
include(":shared", ":composeApp", ":runtime", ":desktopApp")
