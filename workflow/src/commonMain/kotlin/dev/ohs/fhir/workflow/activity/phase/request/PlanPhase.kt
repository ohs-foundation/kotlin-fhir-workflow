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
package dev.ohs.fhir.workflow.activity.phase.request

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.phase.checkReferencesEqual
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.activity.resource.request.Status
import kotlin.uuid.Uuid

/**
 * Provides implementation of the plan phase of the activity flow. See
 * [general-activity-flow](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#general-activity-flow)
 * for more info.
 *
 * @param repository implementation of [WorkflowRepository] to store / retrieve FHIR resources.
 * @param r concrete implementation of sealed [CPGRequestResource] class. e.g.
 *   `CPGCommunicationRequest`.
 */
@Suppress("UNCHECKED_CAST")
class PlanPhase<R : CPGRequestResource<*>>(repository: WorkflowRepository, r: R) :
  BaseRequestPhase<R>(repository, r, Phase.PhaseName.PLAN) {

  companion object {

    /**
     * Creates a draft plan of type [R] based on the state of the provided [inputPhase]. See
     * [beginPlan](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#plan) for
     * more details.
     */
    fun <R : CPGRequestResource<*>> prepare(inputPhase: Phase): Result<R> = runCatching {
      check(inputPhase.getPhaseName() == Phase.PhaseName.PROPOSAL) {
        "A Plan can't be created for a flow in ${inputPhase.getPhaseName().name} phase."
      }
      val inputRequest = (inputPhase as BaseRequestPhase<*>).request
      check(inputRequest.getIntent() == Intent.PROPOSAL) {
        "Plan can't be created for a request with ${inputRequest.getIntent()} intent."
      }
      check(inputRequest.getStatus() == Status.ACTIVE) {
        "${inputPhase.getPhaseName().name} request is still in ${inputRequest.getStatusCode()} status."
      }
      inputRequest.copy(id = Uuid.random().toString(), status = Status.DRAFT, intent = Intent.PLAN)
        as R
    }

    /**
     * Creates a [PlanPhase] of request type [R] based on the [inputPhase] and [draftPlan]. See
     * [endPlan](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#plan) for more
     * details.
     */
    suspend fun <R : CPGRequestResource<*>> initiate(
      repository: WorkflowRepository,
      inputPhase: Phase,
      draftPlan: R,
    ): Result<PlanPhase<R>> = runCatching {
      check(inputPhase.getPhaseName() == Phase.PhaseName.PROPOSAL) {
        "A Plan can't be started for a flow in ${inputPhase.getPhaseName().name} phase."
      }
      val currentPhase = inputPhase as BaseRequestPhase<*>
      val basedOn = draftPlan.getBasedOn()
      require(basedOn != null) { "${draftPlan.resource::class.simpleName}.basedOn can't be null." }
      require(checkReferencesEqual(basedOn, currentPhase.request.asReference())) {
        "Provided draft is not based on the request in current phase."
      }
      val basedOnResource: Resource? =
        repository.read(draftPlan.resourceType, currentPhase.request.logicalId!!)
      val basedOnRequest = basedOnResource?.let { CPGRequestResource.of(it) }
      require(basedOnRequest != null) {
        "Couldn't find ${basedOn.reference?.value} in the database."
      }
      require(basedOnRequest.getIntent() == Intent.PROPOSAL) {
        "Plan can't be based on a request with ${basedOnRequest.getIntent()} intent."
      }
      require(basedOnRequest.getStatus() == Status.ACTIVE) {
        "Plan can't be based on a request with ${basedOnRequest.getStatusCode()} status."
      }
      require(draftPlan.getIntent() == Intent.PLAN) {
        "Input request has '${draftPlan.getIntent()}' intent."
      }
      require(draftPlan.getStatus() in AllowedStatusForPhaseStart) {
        "Input request is in ${draftPlan.getStatusCode()} status."
      }
      basedOnRequest.setStatus(Status.COMPLETED)
      repository.create(draftPlan.resource)
      repository.update(basedOnRequest.resource)
      PlanPhase(repository, draftPlan)
    }
  }
}
