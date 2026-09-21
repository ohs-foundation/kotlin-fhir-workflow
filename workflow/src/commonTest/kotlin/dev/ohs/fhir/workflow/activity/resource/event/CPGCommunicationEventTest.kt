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
package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Communication
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CPGCommunicationEventTest {
  @Test
  fun shouldCarryPriorityAndPayloadWhenCreatedFromRequest() {
    val request =
      CPGCommunicationRequest(
        CommunicationRequest(
          id = "cr-1",
          status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
          priority = Enumeration(value = CommunicationRequest.RequestPriority.Urgent),
          payload =
            listOf(
              CommunicationRequest.Payload(
                content =
                  CommunicationRequest.Payload.Content.String(FhirString(value = "call the CHW"))
              )
            ),
        )
      )

    val event = CPGCommunicationEvent.from(request)

    assertEquals("urgent", event.resource.priority?.value?.code)
    val content = event.resource.payload.single().content
    assertEquals("call the CHW", (content as Communication.Payload.Content.String).value.value)
  }
}
