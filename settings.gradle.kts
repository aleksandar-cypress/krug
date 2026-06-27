pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Read Krug-local secrets from local.properties (gitignored, in project root).
val krugLocalProps = java.util.Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication { create<BasicAuthentication>("basic") }
            credentials {
                username = "mapbox"
                password = krugLocalProps.getProperty("KRUG_MAPBOX_DOWNLOADS_TOKEN")
                    ?: providers.gradleProperty("KRUG_MAPBOX_DOWNLOADS_TOKEN").orNull
                    ?: System.getenv("KRUG_MAPBOX_DOWNLOADS_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "Krug"
include(":app")
include(":benchmark")
