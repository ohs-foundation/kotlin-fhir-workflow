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
package dev.ohs.fhir.workflow.activity.resource.request

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import kotlin.test.Test
import kotlin.test.assertEquals

class CPGRequestResourceTest {
  @Test
  fun shouldRoundTripStatusAndIntentWhenStoredAsExtensions() {
    val cr =
      CommunicationRequest(
        id = "cr-1",
        status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
      )
    val wrapped = CPGRequestResource.of(cr)
    wrapped.setIntent(Intent.PROPOSAL)
    assertEquals(Intent.PROPOSAL, wrapped.getIntent())
    assertEquals(Status.ACTIVE, wrapped.getStatus())

    wrapped.setStatus(Status.ONHOLD)
    assertEquals(Status.ONHOLD, wrapped.getStatus())
  }

  @Test
  fun shouldSetBasedOnToParentWhenCopiedWithNewId() {
    val cr =
      CommunicationRequest(
        id = "cr-1",
        status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
      )
    val parent = CPGRequestResource.of(cr).apply { setIntent(Intent.PROPOSAL) }
    val child = parent.copy(id = "cr-1-plan", status = Status.DRAFT, intent = Intent.PLAN)
    assertEquals("CommunicationRequest/cr-1", child.getBasedOn()?.reference?.value)
    assertEquals(Intent.PLAN, child.getIntent())
  }
}
