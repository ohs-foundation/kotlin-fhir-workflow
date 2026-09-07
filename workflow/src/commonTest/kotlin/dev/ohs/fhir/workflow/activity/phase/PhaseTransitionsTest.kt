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
package dev.ohs.fhir.workflow.activity.phase

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.workflow.activity.phase.request.PlanPhase
import dev.ohs.fhir.workflow.activity.phase.request.ProposalPhase
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PhaseTransitionsTest {
  private suspend fun proposal(
    repo: InMemoryWorkflowRepository
  ): ProposalPhase<CPGCommunicationRequest> {
    val cr =
      CPGCommunicationRequest(
          CommunicationRequest(
            id = "cr-1",
            status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
          )
        )
        .apply { setIntent(Intent.PROPOSAL) }
    repo.create(cr.resource)
    return ProposalPhase(repo, cr)
  }

  @Test
  fun shouldPreparePlanWhenFlowIsInProposalPhase() = runTest {
    val repo = InMemoryWorkflowRepository()
    val prepared = PlanPhase.prepare<CPGCommunicationRequest>(proposal(repo))
    assertTrue(prepared.isSuccess)
  }
}
