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

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.demo.data.AssetReader
import dev.ohs.fhir.workflow.demo.data.bundledAssets
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json

/**
 * Installs a [DemoConfiguration]'s knowledge artifacts and generates its proposal by running
 * `PlanDefinition/$apply` over them.
 *
 * Mirrors android-fhir's ProposalCreationHandler, minus the KnowledgeManager: artifacts live in the
 * same [WorkflowRepository] as the patient data, and `CanonicalResolver` resolves them by url.
 */
class ProposalCreationHandler(
  private val repository: WorkflowRepository,
  private val assets: AssetReader = bundledAssets,
  private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) {
  private val operator = FhirOperator(repository)

  /**
   * True once [installDependencies] has put the configuration's PlanDefinition in the repository.
   */
  suspend fun checkInstalledDependencies(configuration: DemoConfiguration): Boolean =
    repository
      .searchByUri(DemoFhir.PLAN_DEFINITION, "url", configuration.planDefinitionCanonical)
      .isNotEmpty()

  suspend fun installDependencies(configuration: DemoConfiguration) {
    if (checkInstalledDependencies(configuration)) return
    listOf(
        configuration.patientPath,
        configuration.planDefinitionPath,
        configuration.activityDefinitionPath,
      )
      .forEach { repository.create(parse(assets(it))) }
  }

  /**
   * Runs `$apply` for the configuration's PlanDefinition and persists the MedicationRequest it
   * generates. Returns null when the plan's applicability condition rejects the patient — e.g. an
   * apple order is already active.
   */
  suspend fun generateProposal(configuration: DemoConfiguration): CPGMedicationRequest? {
    val patient =
      repository.read(DemoFhir.PATIENT, configuration.patientId) as? Patient
        ?: error("Patient/${configuration.patientId} is not installed")

    val carePlan =
      operator.generateCarePlan(
        planDefinitionCanonical = configuration.planDefinitionCanonical,
        subject = patient,
        variables = mapOf("requests" to existingRequests(configuration.patientId)),
        today = today,
      )

    val generated =
      carePlan.contained.filterIsInstance<MedicationRequest>().firstOrNull() ?: return null

    // $apply derives a stable id from the plan and action, so give each proposal a fresh one:
    // restarting the flow generates the same request again, and the repository would collide.
    val proposal = CPGMedicationRequest(generated.copy(id = Uuid.random().toString()))
    repository.create(proposal.resource)
    return proposal
  }

  /**
   * The patient's MedicationRequests, as the collection Bundle the plan's applicability condition
   * reads through `%requests`.
   */
  private suspend fun existingRequests(patientId: String): Bundle {
    val requests =
      repository.searchByReferenceParam(
        DemoFhir.MEDICATION_REQUEST,
        "subject",
        "Patient/$patientId",
      )
    return Bundle(
      type = Enumeration(value = Bundle.BundleType.Collection),
      entry = requests.map { Bundle.Entry(resource = it) },
    )
  }

  private fun parse(json: String): Resource = demoJson.decodeFromString(Resource.serializer(), json)
}

private val demoJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = false
  explicitNulls = false
}
