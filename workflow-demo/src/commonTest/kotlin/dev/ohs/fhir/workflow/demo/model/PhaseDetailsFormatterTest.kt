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
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhaseDetailsFormatterTest {
  @Test
  fun shouldRenderDashWhenResourceIsAbsent() {
    assertEquals("—", PhaseDetailsFormatter.request(null))
    assertEquals("—", PhaseDetailsFormatter.event(null))
  }

  @Test
  fun shouldRenderIdIntentAndStatusForARequest() {
    val request =
      CPGMedicationRequest(
        MedicationRequest(
          id = "mr-1",
          status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
          intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Proposal),
          medication = MedicationRequest.Medication.CodeableConcept(CodeableConcept()),
          subject = Reference(reference = FhirString(value = "Patient/p1")),
        )
      )
    val details = PhaseDetailsFormatter.request(request)

    assertTrue(details.contains("ID     : MedicationRequest/mr-1"))
    assertTrue(details.contains("Intent : proposal"))
    assertTrue(details.contains("Status : ACTIVE"))
  }
}
