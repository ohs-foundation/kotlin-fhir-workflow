import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  `maven-publish`
}

group = "dev.ohs.fhir"

version = "2.0.0-alpha01"

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
