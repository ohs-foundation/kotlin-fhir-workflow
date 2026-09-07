pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "kotlin-fhir-workflow"

include(":workflow")

include(":workflow-demo")
