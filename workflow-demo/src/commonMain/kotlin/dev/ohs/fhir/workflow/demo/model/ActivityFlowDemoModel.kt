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
package dev.ohs.fhir.workflow.demo.model

import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.ActivityFlow
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.event.CPGMedicationDispenseEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.demo.workflow.ActivityHandler
import dev.ohs.fhir.workflow.demo.workflow.DemoConfiguration
import dev.ohs.fhir.workflow.demo.workflow.DemoFhir
import dev.ohs.fhir.workflow.demo.workflow.MEDICATION_DISPENSE
import dev.ohs.fhir.workflow.demo.workflow.ProposalCreationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the demo's [ActivityFlow] lifecycle: installs the knowledge artifacts, generates a proposal
 * by running `PlanDefinition/$apply` over them, walks it through plan/order/perform, and exposes
 * the whole screen as a single [DemoUiState].
 *
 * Actions ([refresh], [installDependencies], [start], [restart]) run on the injected [scope], so
 * the UI just calls them; each shows the progress spinner while it runs.
 */
class ActivityFlowDemoModel(
  private val repository: WorkflowRepository,
  private val scope: CoroutineScope,
  private val configuration: DemoConfiguration = MEDICATION_DISPENSE,
  private val proposalHandler: ProposalCreationHandler = ProposalCreationHandler(repository),
) {
  private var activityFlow: ActivityFlow<CPGMedicationRequest, CPGEventResource<*>>? = null
  private var handler: ActivityHandler? = null

  private var proposal: CPGMedicationRequest? = null
  private var plan: CPGMedicationRequest? = null
  private var order: CPGMedicationRequest? = null
  private var event: CPGMedicationDispenseEvent? = null

  private var phase = FlowPhase.INITIALIZE
  private var initialized = false
  private var patientName = ""

  private val _uiState = MutableStateFlow(render(progress = false))
  val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

  /**
   * Picks up where a previous run left off: the repository may already hold the knowledge
   * artifacts, and a flow left half-finished is reconstructed from the requests it persisted, so
   * the demo resumes at the phase it was on rather than starting over.
   */
  fun refresh() = withProgress {
    initialized = proposalHandler.checkInstalledDependencies(configuration)
    if (!initialized) {
      phase = FlowPhase.INITIALIZE
      return@withProgress
    }
    loadPatientName()
    phase = resumeFlow() ?: FlowPhase.PROPOSAL
  }

  fun installDependencies() = withProgress { install() }

  /** Runs the given phase; only the currently active phase is startable from the UI. */
  fun start(started: FlowPhase) = withProgress {
    when (started) {
      FlowPhase.INITIALIZE -> install()
      FlowPhase.PROPOSAL -> createProposal()
      FlowPhase.PLAN -> advance(FlowPhase.ORDER) { requireHandler().prepareAndInitiatePlan() }
      FlowPhase.ORDER -> advance(FlowPhase.PERFORM) { requireHandler().prepareAndInitiateOrder() }
      FlowPhase.PERFORM -> advance(FlowPhase.NONE) { requireHandler().prepareAndInitiatePerform() }
      FlowPhase.NONE -> Unit
    }
  }

  /**
   * Abandons the flow and starts over, deleting the resources it created: they are requests against
   * the patient, and the plan's applicability condition reads the patient's requests when deciding
   * whether to propose again.
   */
  fun restart() = withProgress {
    listOfNotNull(proposal, plan, order).forEach { request ->
      request.logicalId?.let { repository.delete(request.resourceType, it) }
    }
    event?.let { dispense ->
      dispense.logicalId?.let { repository.delete(dispense.resourceType, it) }
    }

    activityFlow = null
    handler = null
    proposal = null
    plan = null
    order = null
    event = null
    phase = if (initialized) FlowPhase.PROPOSAL else FlowPhase.INITIALIZE
  }

  private suspend fun install() {
    proposalHandler.installDependencies(configuration)
    initialized = true
    loadPatientName()
    phase = FlowPhase.PROPOSAL
  }

  private suspend fun loadPatientName() {
    val patient = repository.read(DemoFhir.PATIENT, configuration.patientId) as? Patient ?: return
    patientName =
      patient.name.firstOrNull()?.let { name ->
        (name.prefix.mapNotNull { it.value } +
            name.given.mapNotNull { it.value } +
            listOfNotNull(name.family?.value) +
            name.suffix.mapNotNull { it.value })
          .joinToString(" ")
      } ?: ""
  }

  /**
   * Rebuilds the flow from the patient's persisted requests and returns the phase it left off at,
   * or null when there is nothing to resume.
   */
  @Suppress("UNCHECKED_CAST")
  private suspend fun resumeFlow(): FlowPhase? {
    val resumed =
      ActivityFlow.of(repository, configuration.patientId).firstOrNull()
        as? ActivityFlow<CPGMedicationRequest, CPGEventResource<*>> ?: return null

    activityFlow = resumed
    handler = ActivityHandler(resumed)

    resumed.getPreviousPhases().forEach { record(it.getPhaseName(), it.getRequestResource()) }
    when (val current = resumed.getCurrentPhase()) {
      is Phase.EventPhase<*> -> event = current.getEventResource() as? CPGMedicationDispenseEvent
      is Phase.RequestPhase<*> -> record(current.getPhaseName(), current.getRequestResource())
    }

    // The phase to run next is the first one the flow has no resource for, as upstream does.
    return when {
      proposal == null -> FlowPhase.PROPOSAL
      plan == null -> FlowPhase.PLAN
      order == null -> FlowPhase.ORDER
      event == null -> FlowPhase.PERFORM
      else -> FlowPhase.NONE
    }
  }

  private fun record(phaseName: Phase.PhaseName, request: CPGRequestResource<*>?) {
    val medicationRequest = request as? CPGMedicationRequest ?: return
    when (phaseName) {
      Phase.PhaseName.PROPOSAL -> proposal = medicationRequest
      Phase.PhaseName.PLAN -> plan = medicationRequest
      Phase.PhaseName.ORDER -> order = medicationRequest
      else -> Unit
    }
  }

  private suspend fun createProposal() {
    val generated =
      proposalHandler.generateProposal(configuration)
        ?: error(
          "\$apply generated no proposal: the plan's applicability condition rejected the patient."
        )

    activityFlow = ActivityFlow.of(repository, generated)
    handler = ActivityHandler(requireNotNull(activityFlow))
    proposal = generated
    plan = null
    order = null
    event = null
    phase = FlowPhase.PLAN
  }

  /** Runs a phase transition, then records the resource it produced and the phase it unlocks. */
  private suspend fun advance(next: FlowPhase, transition: suspend () -> Result<Unit>) {
    transition().getOrThrow()

    when (next) {
      FlowPhase.ORDER -> plan = currentRequestResource()
      FlowPhase.PERFORM -> order = currentRequestResource()
      else -> event = currentEventResource()
    }

    // A transition completes the resource it was based on (initiating the order completes the
    // plan), so re-read the requests we already know about rather than keep stale copies.
    proposal = proposal?.let { reread(it) }
    plan = plan?.let { reread(it) }
    order = order?.let { reread(it) }
    phase = next
  }

  private fun requireHandler() =
    requireNotNull(handler) { "Create the proposal before advancing the flow." }

  private fun withProgress(block: suspend () -> Unit) {
    scope.launch {
      _uiState.value = render(progress = true)
      try {
        block()
      } finally {
        _uiState.value = render(progress = false)
      }
    }
  }

  private fun render(progress: Boolean) =
    DemoUiState(
      phase = phase,
      cards =
        listOf(
          PhaseCard(
            FlowPhase.PROPOSAL,
            PhaseDetailsFormatter.request(proposal),
            phase == FlowPhase.PROPOSAL,
          ),
          PhaseCard(FlowPhase.PLAN, PhaseDetailsFormatter.request(plan), phase == FlowPhase.PLAN),
          PhaseCard(
            FlowPhase.ORDER,
            PhaseDetailsFormatter.request(order),
            phase == FlowPhase.ORDER,
          ),
          PhaseCard(
            FlowPhase.PERFORM,
            PhaseDetailsFormatter.event(event),
            phase == FlowPhase.PERFORM,
          ),
        ),
      progress = progress,
      initialized = initialized,
      patientName = patientName,
    )

  @Suppress("UNCHECKED_CAST")
  private fun currentRequestResource(): CPGMedicationRequest? =
    (activityFlow?.getCurrentPhase() as? Phase.RequestPhase<*>)?.getRequestResource()
      as? CPGMedicationRequest

  @Suppress("UNCHECKED_CAST")
  private fun currentEventResource(): CPGMedicationDispenseEvent? =
    (activityFlow?.getCurrentPhase() as? Phase.EventPhase<*>)?.getEventResource()
      as? CPGMedicationDispenseEvent

  private suspend fun reread(request: CPGMedicationRequest): CPGMedicationRequest {
    val id = request.logicalId ?: return request
    val stored = repository.read(request.resourceType, id) as? MedicationRequest ?: return request
    return CPGMedicationRequest(stored)
  }
}
