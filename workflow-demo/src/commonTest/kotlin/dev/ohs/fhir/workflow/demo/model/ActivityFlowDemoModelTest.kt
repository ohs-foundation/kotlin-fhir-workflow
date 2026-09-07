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

import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.demo.data.InMemoryDemoRepository
import dev.ohs.fhir.workflow.demo.workflow.MEDICATION_DISPENSE
import dev.ohs.fhir.workflow.demo.workflow.ProposalCreationHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class ActivityFlowDemoModelTest {

  // An unconfined scope runs the model's launched actions eagerly, so each call completes before
  // the
  // next line — the demo's in-memory repository never really suspends.
  private fun TestScope.newModel(repository: WorkflowRepository = InMemoryDemoRepository()) =
    ActivityFlowDemoModel(repository, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

  private fun cards(model: ActivityFlowDemoModel) =
    model.uiState.value.cards.associateBy { it.phase }

  @Test
  fun shouldStartAtInitializeWhenDependenciesAreNotInstalled() = runTest {
    val model = newModel()
    model.refresh()

    assertEquals(FlowPhase.INITIALIZE, model.uiState.value.phase)
    assertFalse(model.uiState.value.initialized)
    cards(model).values.forEach { assertFalse(it.isActive) }
  }

  @Test
  fun shouldEnableProposalWhenDependenciesAreInstalled() = runTest {
    val model = newModel()
    model.installDependencies()

    assertTrue(model.uiState.value.initialized)
    assertEquals(FlowPhase.PROPOSAL, model.uiState.value.phase)
    assertTrue(cards(model).getValue(FlowPhase.PROPOSAL).isActive)
  }

  @Test
  fun shouldSurfaceThePatientNameOnceInstalled() = runTest {
    val model = newModel()
    model.installDependencies()

    assertEquals("Mr. John Doe Sr.", model.uiState.value.patientName)
  }

  @Test
  fun shouldGenerateProposalFromPlanDefinitionWhenProposalPhaseStarts() = runTest {
    val model = newModel()
    model.installDependencies()

    model.start(FlowPhase.PROPOSAL)

    val proposal = cards(model).getValue(FlowPhase.PROPOSAL)
    assertTrue(proposal.details.contains("Intent : proposal"))
    assertTrue(proposal.details.contains("Status : ACTIVE"))
    // The dosage the DailyApple ActivityDefinition declares, carried onto the generated request.
    assertTrue(proposal.details.contains("\"periodUnit\": \"d\""))

    // The proposal is done; the plan is what's startable next.
    assertEquals(FlowPhase.PLAN, model.uiState.value.phase)
    assertTrue(cards(model).getValue(FlowPhase.PLAN).isActive)
  }

  @Test
  fun shouldWalkProposalThroughPlanOrderAndPerform() = runTest {
    val model = newModel()
    model.installDependencies()
    model.start(FlowPhase.PROPOSAL)

    model.start(FlowPhase.PLAN)
    var phaseCards = cards(model)
    assertTrue(phaseCards.getValue(FlowPhase.PLAN).details.contains("Intent : plan"))
    assertTrue(phaseCards.getValue(FlowPhase.PROPOSAL).details.contains("Status : COMPLETED"))

    model.start(FlowPhase.ORDER)
    phaseCards = cards(model)
    assertTrue(phaseCards.getValue(FlowPhase.ORDER).details.contains("Intent : order"))
    assertTrue(phaseCards.getValue(FlowPhase.PLAN).details.contains("Status : COMPLETED"))

    // The dispense is initiated, not carried through to completion — as android-fhir's demo leaves
    // it.
    model.start(FlowPhase.PERFORM)
    phaseCards = cards(model)
    assertTrue(phaseCards.getValue(FlowPhase.PERFORM).details.contains("Status : PREPARATION"))
    assertTrue(phaseCards.getValue(FlowPhase.ORDER).details.contains("Status : COMPLETED"))
    assertEquals(FlowPhase.NONE, model.uiState.value.phase)
  }

  @Test
  fun shouldResumeAHalfFinishedFlowWhenRelaunched() = runTest {
    val repository = InMemoryDemoRepository()
    val abandoned = newModel(repository)
    abandoned.installDependencies()
    abandoned.start(FlowPhase.PROPOSAL)
    abandoned.start(FlowPhase.PLAN)

    // A fresh model over the same repository, as a relaunched app would be.
    val relaunched = newModel(repository)
    relaunched.refresh()

    assertEquals(FlowPhase.ORDER, relaunched.uiState.value.phase)
    val resumed = cards(relaunched)
    assertTrue(resumed.getValue(FlowPhase.PROPOSAL).details.contains("Intent : proposal"))
    assertTrue(resumed.getValue(FlowPhase.PLAN).details.contains("Intent : plan"))
    assertTrue(resumed.getValue(FlowPhase.ORDER).isActive)

    // And it can be driven on from there.
    relaunched.start(FlowPhase.ORDER)
    assertTrue(cards(relaunched).getValue(FlowPhase.ORDER).details.contains("Intent : order"))
  }

  @Test
  fun shouldNotResumeARestartedFlowWhenRelaunched() = runTest {
    val repository = InMemoryDemoRepository()
    val abandoned = newModel(repository)
    abandoned.installDependencies()
    abandoned.start(FlowPhase.PROPOSAL)
    abandoned.start(FlowPhase.PLAN)
    abandoned.restart()

    val relaunched = newModel(repository)
    relaunched.refresh()

    assertEquals(FlowPhase.PROPOSAL, relaunched.uiState.value.phase)
    cards(relaunched).values.forEach { assertEquals("—", it.details) }
  }

  @Test
  fun shouldSuppressProposalWhenThePatientAlreadyHasAnActiveOrder() = runTest {
    val repository = InMemoryDemoRepository()
    val handler = ProposalCreationHandler(repository)
    handler.installDependencies(MEDICATION_DISPENSE)
    repository.create(activeAppleOrder())

    // The plan's applicability condition rejects the patient, so $apply generates nothing.
    assertEquals(null, handler.generateProposal(MEDICATION_DISPENSE))
  }

  @Test
  fun shouldDeleteTheFlowsResourcesWhenRestarted() = runTest {
    val repository = InMemoryDemoRepository()
    val model = newModel(repository)
    model.installDependencies()
    model.start(FlowPhase.PROPOSAL)
    model.start(FlowPhase.PLAN)
    model.start(FlowPhase.ORDER)
    val orderId = idOf(model, FlowPhase.ORDER)

    model.restart()

    assertEquals(FlowPhase.PROPOSAL, model.uiState.value.phase)
    cards(model).values.forEach { assertEquals("—", it.details) }
    assertEquals(null, repository.read("MedicationRequest", orderId))

    model.start(FlowPhase.PROPOSAL)
    assertTrue(cards(model).getValue(FlowPhase.PROPOSAL).details.contains("Intent : proposal"))
  }

  private fun idOf(model: ActivityFlowDemoModel, phase: FlowPhase) =
    model.uiState.value.cards
      .first { it.phase == phase }
      .details
      .substringAfter("MedicationRequest/")
      .substringBefore("\n")

  /** An apple order the patient is already on, as the plan's applicability condition looks for. */
  private fun activeAppleOrder() =
    MedicationRequest(
      id = "existing-apple-order",
      status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
      intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Order),
      medication =
        MedicationRequest.Medication.CodeableConcept(
          CodeableConcept(text = FhirString(value = "Apple"))
        ),
      subject =
        Reference(reference = FhirString(value = "Patient/${MEDICATION_DISPENSE.patientId}")),
    )
}
