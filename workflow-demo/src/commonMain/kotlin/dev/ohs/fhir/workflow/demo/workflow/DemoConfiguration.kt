/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.workflow.demo.workflow

/**
 * A selectable activity flow: the knowledge artifacts to install and the patient to run them
 * against. Mirrors android-fhir's workflow demo Configuration; the overflow menu picks one.
 */
data class DemoConfiguration(
  val id: String,
  val description: String,
  val patientId: String,
  val patientPath: String,
  val planDefinitionPath: String,
  val planDefinitionCanonical: String,
  val activityDefinitionPath: String,
)

/** "An apple a day" — the CPG example flow, same artifacts android-fhir's demo ships. */
val MEDICATION_DISPENSE =
  DemoConfiguration(
    id = "id_medication_dispense",
    description = "Apple a day",
    patientId = "active_apple_guy",
    patientPath = "files/patient/ActiveAppleGuy.json",
    planDefinitionPath = "files/pd/DailyAppleRecommendation.json",
    planDefinitionCanonical =
      "http://fhir.org/guides/cqf/cpg/example/PlanDefinition/DailyAppleRecommendation",
    activityDefinitionPath = "files/ad/DailyAppleActivity.json",
  )
