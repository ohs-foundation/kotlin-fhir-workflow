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
package dev.ohs.fhir.workflow.activity.phase.event

import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.phase.checkReferencesEqual
import dev.ohs.fhir.workflow.activity.phase.request.BaseRequestPhase
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.activity.resource.request.Status

/**
 * Provides the implementation of the perform phase of the activity flow. See
 * [general-activity-flow](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#general-activity-flow)
 * for more info.
 *
 * @param repository the [WorkflowRepository] used to store / retrieve FHIR resources.
 * @param e a concrete implementation of the sealed [CPGEventResource] class, e.g.
 *   `CPGCommunicationEvent`.
 */
@Suppress("UNCHECKED_CAST") // Cast type erased CPGEventResource<*> to a concrete event type.
class PerformPhase<E : CPGEventResource<*>>(private val repository: WorkflowRepository, e: E) :
  Phase.EventPhase<E> {
  private var event: E = e.copy() as E

  override fun getPhaseName(): Phase.PhaseName = Phase.PhaseName.PERFORM

  override fun getEventResource(): E = event.copy() as E

  override suspend fun update(e: E): Result<Unit> = runCatching {
    require(e.getStatus() in listOf(EventStatus.PREPARATION, EventStatus.INPROGRESS)) {
      "Status is ${e.getStatusCode()}"
    }
    repository.update(e.resource)
    event = e
  }

  override suspend fun suspendPhase(reason: String?): Result<Unit> = runCatching {
    check(event.getStatus() == EventStatus.INPROGRESS) {
      "Can't suspend an event with status ${event.getStatusCode()}"
    }
    event.setStatus(EventStatus.ONHOLD, reason)
    repository.update(event.resource)
  }

  override suspend fun resume(): Result<Unit> = runCatching {
    check(event.getStatus() == EventStatus.ONHOLD) {
      "Can't resume an event with status ${event.getStatusCode()}"
    }
    event.setStatus(EventStatus.INPROGRESS)
    repository.update(event.resource)
  }

  override suspend fun enteredInError(reason: String?): Result<Unit> = runCatching {
    event.setStatus(EventStatus.ENTEREDINERROR, reason)
    repository.update(event.resource)
  }

  override suspend fun start(): Result<Unit> = runCatching {
    check(event.getStatus() == EventStatus.PREPARATION) {
      "Can't start an event with status ${event.getStatusCode()}"
    }
    event.setStatus(EventStatus.INPROGRESS)
    repository.update(event.resource)
  }

  override suspend fun notDone(reason: String?): Result<Unit> = runCatching {
    check(event.getStatus() == EventStatus.PREPARATION) {
      "Can't not-done an event with status ${event.getStatusCode()}"
    }
    event.setStatus(EventStatus.NOTDONE, reason)
    repository.update(event.resource)
  }

  override suspend fun stop(reason: String?): Result<Unit> = runCatching {
    check(event.getStatus() == EventStatus.INPROGRESS) {
      "Can't stop an event with status ${event.getStatusCode()}"
    }
    event.setStatus(EventStatus.STOPPED, reason)
    repository.update(event.resource)
  }

  override suspend fun complete(): Result<Unit> = runCatching {
    check(event.getStatus() == EventStatus.INPROGRESS) {
      "Can't complete an event with status ${event.getStatusCode()}"
    }
    event.setStatus(EventStatus.COMPLETED)
    repository.update(event.resource)
  }

  companion object {
    private val AllowedIntents = listOf(Intent.PROPOSAL, Intent.PLAN, Intent.ORDER)
    private val AllowedPhases =
      listOf(Phase.PhaseName.PROPOSAL, Phase.PhaseName.PLAN, Phase.PhaseName.ORDER)
    private val AllowedStatusForPhaseStart = listOf(EventStatus.INPROGRESS, EventStatus.PREPARATION)

    /**
     * Creates a draft event of type [E], named by [eventClassName] (e.g.
     * `"CPGMedicationDispenseEvent"`), based on the state of the provided [inputPhase]. See
     * [beginPerform](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#perform)
     * for more details.
     */
    fun <E : CPGEventResource<*>> prepare(eventClassName: String, inputPhase: Phase): Result<E> =
      runCatching {
        check(inputPhase.getPhaseName() in AllowedPhases) {
          "Event can't be created for a flow in ${inputPhase.getPhaseName().name} phase."
        }
        val inputRequest = (inputPhase as BaseRequestPhase<*>).request
        check(inputRequest.getIntent() in AllowedIntents) {
          "Event can't be created for a request with ${inputRequest.getIntent()} intent."
        }
        check(inputRequest.getStatus() == Status.ACTIVE) {
          "${inputPhase.getPhaseName().name} request is still in ${inputRequest.getStatusCode()} status."
        }
        val event = CPGEventResource.from(inputRequest, eventClassName)
        event.setStatus(EventStatus.PREPARATION)
        event.setBasedOn(inputRequest.asReference())
        event as E
      }

    /**
     * Starts a new [PerformPhase] from the [inputPhase] and the [inputEvent] draft created by
     * [prepare]. The [inputEvent] is created in the [repository] and the request it is based on is
     * marked as completed.
     */
    suspend fun <E : CPGEventResource<*>> initiate(
      repository: WorkflowRepository,
      inputPhase: Phase,
      inputEvent: E,
    ): Result<PerformPhase<E>> = runCatching {
      check(inputPhase.getPhaseName() in AllowedPhases) {
        "A Perform can't be started for a flow in ${inputPhase.getPhaseName().name} phase."
      }
      val currentPhase = inputPhase as BaseRequestPhase<*>
      val basedOn = inputEvent.getBasedOn()
      require(basedOn != null) { "${inputEvent.resource::class.simpleName}.basedOn can't be null." }
      require(checkReferencesEqual(basedOn, currentPhase.request.asReference())) {
        "Provided draft is not based on the request in current phase."
      }
      val basedOnResource =
        repository.read(currentPhase.request.resourceType, currentPhase.request.logicalId!!)
      val basedOnRequest = basedOnResource?.let { CPGRequestResource.of(it) }
      require(basedOnRequest != null) {
        "Couldn't find ${basedOn.reference?.value} in the database."
      }
      require(basedOnRequest.getIntent() in AllowedIntents) {
        "Order can't be based on a request with ${basedOnRequest.getIntent()} intent."
      }
      require(basedOnRequest.getStatus() == Status.ACTIVE) {
        "Plan can't be based on a request with ${basedOnRequest.getStatusCode()} status."
      }
      require(inputEvent.getStatus() in AllowedStatusForPhaseStart) {
        "Input event is in ${inputEvent.getStatusCode()} status."
      }
      basedOnRequest.setStatus(Status.COMPLETED)
      repository.create(inputEvent.resource)
      repository.update(basedOnRequest.resource)
      PerformPhase(repository, inputEvent)
    }
  }
}
