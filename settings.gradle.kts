pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.xpdustry.com/snapshots") {
            name = "xpdustry-snapshots"
            mavenContent { snapshotsOnly() }
        }
    }
}

rootProject.name = "sentinel"
