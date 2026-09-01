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

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.activity.ActivityFlow
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CPGProcedureEventTest {
  private fun serviceRequest() =
    CPGServiceRequest(
        ServiceRequest(
          id = "sr-1",
          status = Enumeration(value = ServiceRequest.RequestStatus.Active),
          intent = Enumeration(value = ServiceRequest.RequestIntent.Order),
          code = CodeableConcept(coding = listOf(Coding(code = Code(value = "CBC")))),
          subject = Reference(reference = FhirString(value = "Patient/p1")),
        )
      )
      .apply { setIntent(Intent.ORDER) }

  @Test
  fun shouldCreateProcedureEventFromServiceRequest() {
    val event = CPGProcedureEvent.from(serviceRequest())
    assertEquals(EventStatus.PREPARATION, event.getStatus())
    assertEquals("CBC", event.resource.code?.coding?.firstOrNull()?.code?.value)
    assertEquals("Patient/p1", event.resource.subject.reference?.value)
  }

  @Test
  fun shouldCarrySingleBasedOnWhenSet() {
    val event = CPGProcedureEvent.from(serviceRequest())
    event.setBasedOn(serviceRequest().asReference())
    assertEquals(listOf("ServiceRequest/sr-1"), event.resource.basedOn.map { it.reference?.value })
  }

  @Test
  fun shouldResolveToProcedureEventWhenServiceRequest() {
    assertTrue(CPGEventResource.from(serviceRequest(), "CPGProcedureEvent") is CPGProcedureEvent)
  }

  @Test
  fun shouldRunFullLifecycleWhenEventIsSuspendedAndResumed() = runTest {
    val repo = InMemoryWorkflowRepository()
    val request = serviceRequest()
    repo.create(request.resource)

    val flow = ActivityFlow.of(repo, request)
    val draft =
      flow.preparePerform<CPGProcedureEvent>(CPGProcedureEvent::class.simpleName!!).getOrThrow()
    val perform = flow.initiatePerform(draft).getOrThrow()

    assertTrue(perform.start().isSuccess)
    assertEquals(EventStatus.INPROGRESS, perform.getEventResource().getStatus())

    assertTrue(perform.suspendPhase("awaiting-consent").isSuccess)
    assertEquals(EventStatus.ONHOLD, perform.getEventResource().getStatus())

    assertTrue(perform.resume().isSuccess)
    assertEquals(EventStatus.INPROGRESS, perform.getEventResource().getStatus())

    assertTrue(perform.complete().isSuccess)
    assertEquals(EventStatus.COMPLETED, perform.getEventResource().getStatus())
  }
}
