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

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Task

class CPGTaskRequest(resource: Task) : CPGRequestResource<Task>(TaskStatusMapper) {

  override var resource: Task = resource

  override fun setId(id: String) {
    resource = resource.copy(id = id)
  }

  override fun setIntent(intent: Intent) {
    resource =
      resource.copy(intent = Enumeration(value = Task.TaskIntent.fromCode(intent.code ?: "order")))
  }

  override fun getIntent(): Intent = Intent.of(resource.intent.value?.code)

  override fun setStatus(status: Status, reason: String?) {
    resource =
      resource.copy(
        status =
          Enumeration(
            value = Task.TaskStatus.fromCode(mapper.mapStatusToCode(status) ?: "requested")
          ),
        statusReason =
          reason?.let { CodeableConcept(coding = listOf(Coding(code = Code(value = it)))) },
      )
  }

  override fun getStatusCode(): String? = resource.status.value?.code

  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(basedOn = resource.basedOn + reference)
  }

  override fun getBasedOn(): Reference? = resource.basedOn.lastOrNull()

  override fun copy(): CPGRequestResource<Task> = CPGTaskRequest(resource.copy())

  // Task status vocabulary differs from request-status; map CPG Status onto Task status.
  private object TaskStatusMapper : StatusCodeMapper {
    override fun mapCodeToStatus(code: String?): Status =
      when (code) {
        "draft" -> Status.DRAFT

        "requested",
        "received",
        "accepted",
        "ready",
        "in-progress" -> Status.ACTIVE

        "on-hold" -> Status.ONHOLD

        "cancelled",
        "rejected" -> Status.REVOKED

        "completed" -> Status.COMPLETED

        "entered-in-error" -> Status.ENTEREDINERROR

        else -> Status.OTHER(code)
      }

    override fun mapStatusToCode(status: Status): String? =
      when (status) {
        Status.DRAFT -> "draft"
        Status.ACTIVE -> "in-progress"
        Status.ONHOLD -> "on-hold"
        Status.REVOKED -> "cancelled"
        Status.COMPLETED -> "completed"
        Status.ENTEREDINERROR -> "entered-in-error"
        is Status.OTHER -> status.code
      }
  }
}
