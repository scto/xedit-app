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
        maven {
            setUrl("https://jitpack.io")
        }
    }
    versionCatalogs {
      create("libs") {
        from(files("gradle/libs.versions.toml"))
      }
    }
}

rootProject.name = "xedit-app"
include(
    ":app",
    ":alerter",
    ":bypass",
    ":crash", 
    ":editor", 
    ":document", 
    ":piecetable", 
    ":treesitter",
    ":treeview"
)
