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

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Procedure
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.phase.event.PerformPhase
import dev.ohs.fhir.workflow.activity.phase.request.ProposalPhase
import dev.ohs.fhir.workflow.activity.resource.event.CPGProcedureEvent
import dev.ohs.fhir.workflow.activity.resource.event.CPGTaskEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ActivityFlowTest {
  @Test
  fun shouldPreparePlanWhenFlowStartsFromProposal() = runTest {
    val repo = InMemoryWorkflowRepository()
    val request =
      CPGCommunicationRequest(
          CommunicationRequest(
            id = "cr-1",
            status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
          )
        )
        .apply { setIntent(Intent.PROPOSAL) }
    repo.create(request.resource)

    val flow = ActivityFlow.of(repo, request)
    flow.getCurrentPhase().shouldBeInstanceOf<ProposalPhase<*>>()
    assertTrue(flow.preparePlan().isSuccess)
  }

  @Test
  fun shouldStartInProposalPhaseWhenFlowIsCreatedForTaskRequest() = runTest {
    val repo = InMemoryWorkflowRepository()
    val taskRequest =
      CPGTaskRequest(
          Task(
            id = "task-1",
            status = Enumeration(value = Task.TaskStatus.Requested),
            intent = Enumeration(value = Task.TaskIntent.Proposal),
          )
        )
        .apply { setIntent(Intent.PROPOSAL) }
    repo.create(taskRequest.resource)

    val flow = ActivityFlow.of(repo, taskRequest)
    flow.getCurrentPhase().shouldBeInstanceOf<ProposalPhase<*>>()
  }

  @Test
  fun shouldReachPerformAndCompleteWhenTaskFlow() = runTest {
    val repo = InMemoryWorkflowRepository()
    val taskRequest =
      CPGTaskRequest(
          Task(
            id = "task-1",
            status = Enumeration(value = Task.TaskStatus.Requested),
            intent = Enumeration(value = Task.TaskIntent.Order),
          )
        )
        .apply { setIntent(Intent.ORDER) }
    repo.create(taskRequest.resource)

    val flow = ActivityFlow.of(repo, taskRequest)
    val event = flow.preparePerform<CPGTaskEvent>(CPGTaskEvent::class.simpleName!!).getOrThrow()
    val perform = flow.initiatePerform(event).getOrThrow()
    perform.shouldBeInstanceOf<PerformPhase<CPGTaskEvent>>()
    assertTrue(perform.start().isSuccess)
    assertTrue(perform.complete().isSuccess)
  }

  @Test
  fun shouldReturnPriorPhasesWhenPlanBasedOnProposal() = runTest {
    val repo = InMemoryWorkflowRepository()
    val proposal =
      CPGCommunicationRequest(
          CommunicationRequest(
            id = "cr-prop",
            status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
          )
        )
        .apply { setIntent(Intent.PROPOSAL) }
    repo.create(proposal.resource)

    val plan =
      CPGCommunicationRequest(
          CommunicationRequest(
            id = "cr-plan",
            status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
            basedOn =
              listOf(Reference(reference = FhirString(value = "CommunicationRequest/cr-prop"))),
          )
        )
        .apply { setIntent(Intent.PLAN) }
    repo.create(plan.resource)

    val previous = ActivityFlow.of(repo, plan).getPreviousPhases()

    assertEquals(1, previous.size)
    assertEquals(Phase.PhaseName.PROPOSAL, previous.single().getPhaseName())
    assertEquals("cr-prop", previous.single().getRequestResource().logicalId)
  }

  @Test
  fun shouldReturnActiveFlowsForPatientAndSkipCompleted() = runTest {
    val repo = InMemoryWorkflowRepository()
    repo.registerReferenceIndex("CommunicationRequest", "subject") {
      listOf((it as CommunicationRequest).subject?.reference?.value ?: "")
    }

    val active =
      CPGCommunicationRequest(
          CommunicationRequest(
            id = "cr-active",
            status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
            subject = Reference(reference = FhirString(value = "Patient/p1")),
          )
        )
        .apply { setIntent(Intent.PROPOSAL) }
    repo.create(active.resource)

    val completed =
      CPGCommunicationRequest(
          CommunicationRequest(
            id = "cr-done",
            status = Enumeration(value = CommunicationRequest.RequestStatus.Completed),
            subject = Reference(reference = FhirString(value = "Patient/p1")),
          )
        )
        .apply { setIntent(Intent.PROPOSAL) }
    repo.create(completed.resource)

    val flows = ActivityFlow.of(repo, "p1")

    assertEquals(1, flows.size)
    flows.single().getCurrentPhase().shouldBeInstanceOf<ProposalPhase<*>>()
  }

  @Test
  fun shouldReconstructServiceRequestFlowWhenDrivenToPerform() = runTest {
    val repo = InMemoryWorkflowRepository()
    repo.registerReferenceIndex("ServiceRequest", "subject") {
      listOf((it as ServiceRequest).subject.reference?.value ?: "")
    }
    repo.registerReferenceIndex("Procedure", "subject") {
      listOf((it as Procedure).subject.reference?.value ?: "")
    }

    val serviceRequest =
      CPGServiceRequest(
          ServiceRequest(
            id = "sr-1",
            status = Enumeration(value = ServiceRequest.RequestStatus.Active),
            intent = Enumeration(value = ServiceRequest.RequestIntent.Order),
            subject = Reference(reference = FhirString(value = "Patient/p1")),
          )
        )
        .apply { setIntent(Intent.ORDER) }
    repo.create(serviceRequest.resource)

    val flow = ActivityFlow.of(repo, serviceRequest)
    val event =
      flow.preparePerform<CPGProcedureEvent>(CPGProcedureEvent::class.simpleName!!).getOrThrow()
    flow.initiatePerform(event).getOrThrow()

    val flows = ActivityFlow.of(repo, "p1")

    assertEquals(1, flows.size)
    val perform = flows.single().getCurrentPhase().shouldBeInstanceOf<PerformPhase<*>>()
    assertEquals("ServiceRequest/sr-1", perform.getEventResource().getBasedOn()?.reference?.value)
  }
}
