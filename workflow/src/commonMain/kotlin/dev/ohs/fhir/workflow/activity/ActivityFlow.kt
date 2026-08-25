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
package dev.ohs.fhir.workflow.activity

import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.phase.ReadOnlyRequestPhase
import dev.ohs.fhir.workflow.activity.phase.event.PerformPhase
import dev.ohs.fhir.workflow.activity.phase.request.OrderPhase
import dev.ohs.fhir.workflow.activity.phase.request.PlanPhase
import dev.ohs.fhir.workflow.activity.phase.request.ProposalPhase
import dev.ohs.fhir.workflow.activity.resource.event.CPGCommunicationEvent
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.event.CPGProcedureEvent
import dev.ohs.fhir.workflow.activity.resource.event.CPGTaskEvent
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.fhir.workflow.ref

/**
 * Manages the workflow of clinical recommendations according to the FHIR Clinical Practice
 * Guidelines (CPG) specification. This class implements an
 * [activity flow](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#activity-lifecycle---request-phases-proposal-plan-order),
 * allowing you to take proposals and guide them through the various phases (proposal, plan, order,
 * perform) of a clinical recommendation. You can also resume existing workflows from any phase.
 *
 * **NOTE**
 * * The `prepare` and `initiate` apis of [ActivityFlow] and the apis of [Phase] are `suspend`
 *   functions that touch the [WorkflowRepository] and should be called from a coroutine.
 * * [ActivityFlow] is not thread safe and concurrent changes to the flow/phase from multiple
 *   coroutines may produce undesired results.
 *
 * **Creating an ActivityFlow:**
 *
 * Use the appropriate [ActivityFlow.of] factory function to create an instance. You can start a new
 * flow with a [CPGRequestResource] or resume an existing flow from a [CPGRequestResource] or
 * [CPGEventResource] based on the last state of the flow.
 *
 * ```kotlin
 * val request = CPGMedicationRequest(medicationRequestGeneratedByCarePlan)
 * val flow = ActivityFlow.of(repository, request)
 * ```
 *
 * **Navigating Phases:**
 *
 * An [ActivityFlow] progresses through a series of phases, represented by the [Phase] interface.
 * You can access the current phase using [getCurrentPhase].
 *
 * ```kotlin
 * when (val phase = flow.getCurrentPhase()) {
 *   is ProposalPhase -> // Handle proposal phase
 *   is PlanPhase -> // Handle plan phase
 *   is OrderPhase -> // Handle order phase
 *   is PerformPhase -> // Handle perform phase
 * }
 * ```
 *
 * **Transitioning Between Phases:**
 *
 * [ActivityFlow] provides functions to prepare and initiate the next phase.
 * * The `prepare` api creates a new request or event based on the current phase and returns it back
 *   to you. It doesn't make any changes to the current phase request and doesn't persist anything
 *   to the [repository].
 * * The `initiate` api creates a new phase based on the current phase and the provided
 *   request/event. It does change the current phase request and the provided request and persists
 *   them to the [repository].
 *
 * For example, to move from the proposal phase to the plan phase:
 * ```kotlin
 * val preparePlanResult = flow.preparePlan()
 * if (preparePlanResult.isFailure) {
 *   // Handle failure
 * }
 *
 * val preparedPlan = preparePlanResult.getOrThrow()
 * // ... modify preparedPlan
 * val planPhase = flow.initiatePlan(preparedPlan)
 * ```
 *
 * **Note:** The `prepare` and `initiate` calls that succeed depend on the current phase.
 *
 * **Transitioning to Perform Phase:**
 *
 * Since perform creates a [CPGEventResource] and the same flow could create different event
 * resources, you need to provide the name of the appropriate event class to [preparePerform].
 *
 * ```kotlin
 * // Prepare and initiate the perform phase
 * val preparedPerformEvent =
 *   flow.preparePerform<CPGMedicationDispenseEvent>("CPGMedicationDispenseEvent").getOrThrow()
 * // update preparedPerformEvent
 * val performPhase = flow.initiatePerform(preparedPerformEvent).getOrThrow()
 * ```
 *
 * **Updating states in a phase:**
 *
 * [ProposalPhase], [PlanPhase] and [OrderPhase] are all a type of [Phase.RequestPhase] and allow
 * you to update the state of the request.
 *
 * ```kotlin
 * val planPhase = flow.initiatePlan(preparedPlan).getOrThrow()
 * val medicationRequest = planPhase.getRequestResource()
 * // update medicationRequest
 * planPhase.update(updatedMedicationRequest)
 * ```
 *
 * [PerformPhase] is a type of [Phase.EventPhase] and allows you to update the state of the event.
 *
 * ```kotlin
 * val performPhase = ...
 * val medicationDispense = performPhase.getEventResource()
 * // update medicationDispense
 * performPhase.update(updatedMedicationDispense)
 * performPhase.complete()
 * ```
 */
class ActivityFlow<R : CPGRequestResource<*>, E : CPGEventResource<*>>
private constructor(
  private val repository: WorkflowRepository,
  requestResource: R? = null,
  eventResource: E? = null,
) {
  private var currentPhase: Phase =
    when {
      eventResource != null -> PerformPhase(repository, eventResource)

      requestResource != null ->
        when (requestResource.getIntent()) {
          Intent.PROPOSAL -> ProposalPhase(repository, requestResource)

          Intent.PLAN -> PlanPhase(repository, requestResource)

          Intent.ORDER -> OrderPhase(repository, requestResource)

          else ->
            throw IllegalArgumentException(
              "Couldn't create the flow for ${requestResource.getIntent()} intent. Supported: proposal, plan, order."
            )
        }

      else ->
        throw IllegalArgumentException("Either Request or Event is required to create a flow.")
    }

  /**
   * Returns the current phase of the flow. Callers may check the type of the phase by calling
   * [Phase.getPhaseName] on the value returned by [getCurrentPhase] and then cast it to the
   * appropriate class.
   *
   * The table below shows the mapping between [Phase.PhaseName] and the [Phase] implementations.
   *
   * | PhaseName                  | Class           |
   * |----------------------------|-----------------|
   * | [Phase.PhaseName.PROPOSAL] | [ProposalPhase] |
   * | [Phase.PhaseName.PLAN]     | [PlanPhase]     |
   * | [Phase.PhaseName.ORDER]    | [OrderPhase]    |
   * | [Phase.PhaseName.PERFORM]  | [PerformPhase]  |
   */
  fun getCurrentPhase(): Phase = currentPhase

  /**
   * Returns a read-only list of all the previous phases of the flow, walking the `basedOn` chain.
   */
  @Suppress("UNCHECKED_CAST")
  suspend fun getPreviousPhases(): List<ReadOnlyRequestPhase<R>> {
    val phases = mutableListOf<ReadOnlyRequestPhase<R>>()
    var current: Phase? = currentPhase
    while (current != null) {
      val basedOn: Reference? =
        when (val c = current) {
          is Phase.RequestPhase<*> -> c.getRequestResource().getBasedOn()
          is Phase.EventPhase<*> -> c.getEventResource().getBasedOn()
          else -> null
        }
      val basedOnRequest: R? =
        basedOn?.ref?.let { ref ->
          repository.read(ref.substringBefore("/"), ref.substringAfter("/"))?.let {
            CPGRequestResource.of(it) as R
          }
        }
      current =
        when (basedOnRequest?.getIntent()) {
          Intent.PROPOSAL -> ProposalPhase(repository, basedOnRequest)
          Intent.PLAN -> PlanPhase(repository, basedOnRequest)
          Intent.ORDER -> OrderPhase(repository, basedOnRequest)
          else -> null
        }
      current?.let { phases.add(it as ReadOnlyRequestPhase<R>) }
    }
    return phases
  }

  /**
   * Prepares a plan resource based on the state of the [currentPhase] and returns it to the caller
   * without persisting any changes into [repository].
   *
   * @return [Result] containing the plan if the action is successful, error otherwise.
   */
  suspend fun preparePlan(): Result<R> = PlanPhase.prepare(currentPhase)

  /**
   * Initiates a plan phase based on the state of the [currentPhase] and [preparedPlan]. This api
   * persists the [preparedPlan] into [repository].
   *
   * @return [PlanPhase] if the action is successful, error otherwise.
   */
  suspend fun initiatePlan(preparedPlan: R): Result<PlanPhase<R>> =
    PlanPhase.initiate(repository, currentPhase, preparedPlan).onSuccess { currentPhase = it }

  /**
   * Prepares an order resource based on the state of the [currentPhase] and returns it to the
   * caller without persisting any changes into [repository].
   *
   * @return [Result] containing the order if the action is successful, error otherwise.
   */
  suspend fun prepareOrder(): Result<R> = OrderPhase.prepare(currentPhase)

  /**
   * Initiates an order phase based on the state of the [currentPhase] and [preparedOrder]. This api
   * persists the [preparedOrder] into [repository].
   *
   * @return [OrderPhase] if the action is successful, error otherwise.
   */
  suspend fun initiateOrder(preparedOrder: R): Result<OrderPhase<R>> =
    OrderPhase.initiate(repository, currentPhase, preparedOrder).onSuccess { currentPhase = it }

  /**
   * Prepares an event resource of the type named by [eventClassName] (e.g.
   * `"CPGMedicationDispenseEvent"`) based on the state of the [currentPhase] and returns it to the
   * caller without persisting any changes into [repository].
   *
   * @return [Result] containing the event if the action is successful, error otherwise.
   */
  suspend fun <D : E> preparePerform(eventClassName: String): Result<D> =
    PerformPhase.prepare(eventClassName, currentPhase)

  /**
   * Initiates a perform phase based on the state of the [currentPhase] and [preparedEvent]. This
   * api persists the [preparedEvent] into [repository].
   *
   * @return [PerformPhase] if the action is successful, error otherwise.
   */
  suspend fun <D : E> initiatePerform(preparedEvent: D): Result<PerformPhase<D>> =
    PerformPhase.initiate(repository, currentPhase, preparedEvent).onSuccess { currentPhase = it }

  companion object {
    /**
     * Creates a flow for the
     * [Send Message](https://build.fhir.org/ig/HL7/cqf-recommendations/examples-activities.html#send-a-message)
     * activity, starting from the [CPGCommunicationRequest].
     */
    fun of(
      repository: WorkflowRepository,
      resource: CPGCommunicationRequest,
    ): ActivityFlow<CPGCommunicationRequest, CPGCommunicationEvent> =
      ActivityFlow(repository, resource)

    /**
     * Resumes the flow for the
     * [Send Message](https://build.fhir.org/ig/HL7/cqf-recommendations/examples-activities.html#send-a-message)
     * activity from an existing [CPGCommunicationEvent].
     */
    fun of(
      repository: WorkflowRepository,
      resource: CPGCommunicationEvent,
    ): ActivityFlow<CPGCommunicationRequest, CPGCommunicationEvent> =
      ActivityFlow(repository, null, resource)

    /**
     * Creates a flow for the
     * [Order a medication](https://build.fhir.org/ig/HL7/cqf-recommendations/examples-activities.html#order-a-medication)
     * activity, starting from the [CPGMedicationRequest].
     */
    fun of(
      repository: WorkflowRepository,
      resource: CPGMedicationRequest,
    ): ActivityFlow<CPGMedicationRequest, CPGEventResource<*>> = ActivityFlow(repository, resource)

    /** Creates a flow for a task based activity, starting from the [CPGTaskRequest]. */
    fun of(
      repository: WorkflowRepository,
      resource: CPGTaskRequest,
    ): ActivityFlow<CPGTaskRequest, CPGTaskEvent> = ActivityFlow(repository, resource)

    /** Creates a flow for a service based activity, starting from the [CPGServiceRequest]. */
    fun of(
      repository: WorkflowRepository,
      resource: CPGServiceRequest,
    ): ActivityFlow<CPGServiceRequest, CPGProcedureEvent> = ActivityFlow(repository, resource)

    /**
     * Returns the active (non-completed) flows for the [patientId], reconstructed from persistence.
     * Events and requests are searched by subject, chained via `basedOn`, and any flow whose latest
     * request/event is completed is dropped. When a new activity type is added, register its
     * event/request resource types below so this search can find it.
     *
     * Task-based flows are not reconstructed: a Task is both the request and the event, so a
     * subject search cannot tell the two roles apart without a role marker on the resource.
     */
    suspend fun of(
      repository: WorkflowRepository,
      patientId: String,
    ): List<ActivityFlow<CPGRequestResource<*>, CPGEventResource<*>>> {
      val subject = "Patient/$patientId"

      val events =
        listOf("MedicationDispense", "Communication", "Procedure")
          .flatMap { repository.searchByReferenceParam(it, "subject", subject) }
          .map { CPGEventResource.of(it) }

      // This is used to fetch the `basedOn` resource for a request/event to form RequestChain
      val idToRequestMap: MutableMap<String, CPGRequestResource<*>> =
        listOf("MedicationRequest", "CommunicationRequest", "ServiceRequest")
          .flatMap { repository.searchByReferenceParam(it, "subject", subject) }
          .map { CPGRequestResource.of(it) }
          .associateByTo(LinkedHashMap()) { "${it.resourceType}/${it.logicalId}" }

      fun addBasedOn(chain: RequestChain): RequestChain? {
        val basedOn = chain.request?.getBasedOn() ?: chain.event?.getBasedOn()
        return basedOn?.ref?.let { ref ->
          idToRequestMap[ref]?.let { requestResource ->
            idToRequestMap.remove(ref)
            RequestChain(request = requestResource).apply { this.basedOn = addBasedOn(this) }
          }
        }
      }

      val requestChain =
        events.map { RequestChain(event = it).apply { this.basedOn = addBasedOn(this) } } +
          idToRequestMap.values
            .filter {
              it.getIntent() == Intent.PROPOSAL ||
                it.getIntent() == Intent.PLAN ||
                it.getIntent() == Intent.ORDER
            }
            .sortedByDescending { it.getIntent().code ?: "" }
            .mapNotNull {
              if (idToRequestMap.containsKey("${it.resourceType}/${it.logicalId}")) {
                RequestChain(request = it).apply { this.basedOn = addBasedOn(this) }
              } else {
                null
              }
            }

      return requestChain
        .filter {
          when {
            it.event != null -> it.event.getStatus() != EventStatus.COMPLETED
            it.request != null -> it.request.getStatus() != Status.COMPLETED
            else -> false
          }
        }
        .map { ActivityFlow(repository, it.request, it.event) }
    }
  }
}

/**
 * The chain of event/requests of an activity flow. A [RequestChain] holds either a [request] or an
 * [event], plus the parent it is [basedOn].
 */
private data class RequestChain(
  val request: CPGRequestResource<*>? = null,
  val event: CPGEventResource<*>? = null,
  var basedOn: RequestChain? = null,
)
