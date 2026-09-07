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
import dev.ohs.fhir.workflow.activity.phase.event.PerformPhase
import dev.ohs.fhir.workflow.activity.phase.request.OrderPhase
import dev.ohs.fhir.workflow.activity.phase.request.PlanPhase
import dev.ohs.fhir.workflow.activity.resource.event.CPGCommunicationEvent
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.fhir.workflow.reference
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ActivityFlowDemoTest {

  private fun newProposal() =
    CPGCommunicationRequest(
        CommunicationRequest(
          id = "com-req-01",
          status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
          subject = reference("Patient/pat-01"),
        )
      )
      .apply { setIntent(Intent.PROPOSAL) }

  @Test
  fun shouldWalkProposalToPerformWhenDrivingACommunicationRequest() = runTest {
    val repo = InMemoryWorkflowRepository()
    val proposal = newProposal()
    repo.create(proposal.resource)

    // Proposal -> Plan
    val flow = ActivityFlow.of(repo, proposal)
    val preparedPlan = flow.preparePlan().getOrThrow()
    val planPhase = flow.initiatePlan(preparedPlan).getOrThrow()
    planPhase.shouldBeInstanceOf<PlanPhase<*>>()
    // proposal is now completed in the store
    val storedProposal = repo.read("CommunicationRequest", "com-req-01") as CommunicationRequest
    assertTrue(storedProposal.status.value == CommunicationRequest.RequestStatus.Completed)

    // Activate the plan before it can move to Order
    val activePlan = planPhase.getRequestResource().apply { setStatus(Status.ACTIVE) }
    assertTrue(planPhase.update(activePlan).isSuccess)

    // Plan -> Order
    val preparedOrder = flow.prepareOrder().getOrThrow()
    val orderPhase = flow.initiateOrder(preparedOrder).getOrThrow()
    orderPhase.shouldBeInstanceOf<OrderPhase<*>>()

    // Activate the order before it can move to Perform
    val activeOrder = orderPhase.getRequestResource().apply { setStatus(Status.ACTIVE) }
    assertTrue(orderPhase.update(activeOrder).isSuccess)

    // Order -> Perform (Communication event)
    val preparedEvent =
      flow.preparePerform<CPGCommunicationEvent>("CPGCommunicationEvent").getOrThrow()
    val performPhase = flow.initiatePerform(preparedEvent).getOrThrow()
    performPhase.shouldBeInstanceOf<PerformPhase<*>>()

    // start then complete the event
    assertTrue(performPhase.start().isSuccess)
    assertTrue(performPhase.complete().isSuccess)
    assertTrue(performPhase.getEventResource().getStatus() == EventStatus.COMPLETED)
  }

  @Test
  fun shouldFailToPreparePlanWhenFlowIsInOrderPhase() = runTest {
    val repo = InMemoryWorkflowRepository()
    val order =
      newProposal().copy(id = "com-req-01-order", status = Status.ACTIVE, intent = Intent.ORDER)
        as CPGCommunicationRequest
    repo.create(order.resource)
    val flow = ActivityFlow.of(repo, order)
    flow.getCurrentPhase().shouldBeInstanceOf<OrderPhase<*>>()
    assertTrue(flow.preparePlan().isFailure)
  }
}
