import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.maven.publish)
}

val mavenGroupId: String by project
val mavenArtifactId: String by project
val mavenVersion: String by project

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.ohs.fhir.workflow"
    compileSdk = 36
    minSdk = 26
  }

  jvm("desktop")
  // iosX64 (Intel iOS simulator) is omitted: fhir-model/fhir-path no longer publish it, and the
  // demo already targets only iosArm64 + iosSimulatorArm64.
  iosArm64()
  iosSimulatorArm64()

  @OptIn(ExperimentalWasmDsl::class) wasmJs { nodejs() }

  targets.configureEach {
    compilations.configureEach {
      compilerOptions.configure {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.ohs.fhir.model)
        implementation(libs.ohs.fhir.path)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.datetime)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotest.assertions.core)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
  }
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(mavenGroupId, mavenArtifactId, mavenVersion)

  pom {
    name = "Kotlin FHIR Workflow"
    description =
      "A Kotlin Multiplatform library for FHIR clinical reasoning workflows such as " +
        "PlanDefinition/\$apply"
    inceptionYear = "2026"
    url = "https://github.com/ohs-foundation/kotlin-fhir-workflow"
    licenses {
      license {
        name = "The Apache License, Version 2.0"
        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
      }
    }
    developers {
      developer {
        id = "ohs-foundation"
        name = "Open Health Stack Foundation"
        url = "https://ohs.dev/"
      }
    }
    scm {
      url = "https://github.com/ohs-foundation/kotlin-fhir-workflow/"
      connection = "scm:git:git://github.com/ohs-foundation/kotlin-fhir-workflow.git"
      developerConnection = "scm:git:ssh://git@github.com/ohs-foundation/kotlin-fhir-workflow.git"
    }
  }
}
