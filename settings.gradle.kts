pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "Kasha"
include(":kashaCore", ":composeApp", ":runtime", ":desktopApp")
