pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral(); maven("https://jitpack.io") } }
rootProject.name = "EzSocialToVideo"
include(":app")
include(":engine", ":desktop")
project(":engine").projectDir = file("../engine")
project(":desktop").projectDir = file("../desktop")
