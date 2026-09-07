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

import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CPGMedicationDispenseEventTest {
  private fun category(text: String) = CodeableConcept(text = FhirString(value = text))

  private fun medicationRequest(categories: List<CodeableConcept>) =
    CPGMedicationRequest(
      MedicationRequest(
        id = "mr-1",
        status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
        intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Order),
        medication = MedicationRequest.Medication.CodeableConcept(category("amoxicillin")),
        subject = Reference(reference = FhirString(value = "Patient/p1")),
        category = categories,
      )
    )

  @Test
  fun shouldOmitCategoryWhenRequestHasMultiple() {
    val event =
      CPGMedicationDispenseEvent.from(medicationRequest(listOf(category("a"), category("b"))))
    assertNull(event.resource.category)
  }

  @Test
  fun shouldKeepCategoryWhenRequestHasExactlyOne() {
    val event = CPGMedicationDispenseEvent.from(medicationRequest(listOf(category("vaccine"))))
    assertEquals("vaccine", event.resource.category?.text?.value)
  }
}
