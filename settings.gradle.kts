pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {

    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":app",
    ":syncthing"
)
