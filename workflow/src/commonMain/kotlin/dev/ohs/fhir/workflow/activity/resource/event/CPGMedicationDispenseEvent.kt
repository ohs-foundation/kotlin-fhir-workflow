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

import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationDispense
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import kotlin.uuid.Uuid

class CPGMedicationDispenseEvent(resource: MedicationDispense) :
  CPGOrderMedicationEvent<MedicationDispense>(MedicationDispenseEventMapper) {

  override var resource: MedicationDispense = resource

  override fun setStatus(status: EventStatus, reason: String?) {
    resource =
      resource.copy(
        status =
          Enumeration(
            value =
              MedicationDispense.MedicationDispenseStatusCodes.fromCode(
                mapper.mapStatusToCode(status) ?: "unknown"
              )
          ),
        statusReason =
          reason?.let {
            MedicationDispense.StatusReason.CodeableConcept(
              CodeableConcept(coding = listOf(Coding(code = Code(value = it))))
            )
          },
      )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()

  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(authorizingPrescription = resource.authorizingPrescription + reference)
  }

  override fun getBasedOn(): Reference? = resource.authorizingPrescription.lastOrNull()

  override fun copy(): CPGEventResource<MedicationDispense> =
    CPGMedicationDispenseEvent(resource.copy())

  companion object {
    fun from(request: CPGMedicationRequest): CPGMedicationDispenseEvent {
      val src = request.resource
      return CPGMedicationDispenseEvent(
        MedicationDispense(
          id = Uuid.random().toString(),
          status =
            Enumeration(value = MedicationDispense.MedicationDispenseStatusCodes.Preparation),
          medication = medReqToDispenseMedication(src.medication),
          // Only set category if single, otherwise let the application fill it in.
          category = src.category.singleOrNull(),
          subject = src.subject,
          context = src.encounter,
          note = src.note,
          dosageInstruction = src.dosageInstruction,
          substitution =
            src.substitution?.let { substitution ->
              MedicationDispense.Substitution(
                id = substitution.id,
                extension = substitution.extension,
                modifierExtension = substitution.modifierExtension,
                // MedicationRequest.substitution.allowed records whether substitution is
                // *permitted* (as a Boolean or a CodeableConcept), whereas
                // MedicationDispense.substitution.wasSubstituted records whether a substitution
                // *actually occurred* (a plain Boolean). These are different facts and there is no
                // way to derive the latter at dispense-creation time, so we carry over the allowed
                // flag as a best-effort default, falling back to false when allowed was expressed
                // as a CodeableConcept (no boolean value to convert).
                wasSubstituted =
                  substitution.allowed.asBoolean()?.value ?: FhirBoolean(value = false),
                reason = substitution.reason?.let { listOf(it) } ?: listOf(),
              )
            },
          detectedIssue = src.detectedIssue,
          eventHistory = src.eventHistory,
        )
      )
    }

    private fun medReqToDispenseMedication(
      medication: MedicationRequest.Medication
    ): MedicationDispense.Medication =
      when (medication) {
        is MedicationRequest.Medication.CodeableConcept ->
          MedicationDispense.Medication.CodeableConcept(medication.value)

        is MedicationRequest.Medication.Reference ->
          MedicationDispense.Medication.Reference(medication.value)
      }
  }
}

private object MedicationDispenseEventMapper : EventStatusCodeMapperImpl() {
  override fun mapCodeToStatus(code: String?): EventStatus =
    if (code == "cancelled") EventStatus.NOTDONE else super.mapCodeToStatus(code)

  override fun mapStatusToCode(status: EventStatus): String? =
    if (status == EventStatus.NOTDONE) "cancelled" else super.mapStatusToCode(status)
}
