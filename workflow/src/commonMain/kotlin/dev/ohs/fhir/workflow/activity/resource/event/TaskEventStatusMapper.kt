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

import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.COMPLETED
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.ENTEREDINERROR
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.INPROGRESS
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.NOTDONE
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.ONHOLD
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.OTHER
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.PREPARATION
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.STOPPED
import dev.ohs.fhir.workflow.activity.resource.event.EventStatus.UNKNOWN

/**
 * EventStatus <-> Task status codes. A performed Task uses the Task status vocabulary, which lacks
 * `preparation`/`stopped`/`not-done`; map to the nearest Task states
 * (`ready`/`cancelled`/`failed`).
 */
object TaskEventStatusMapper : EventStatusCodeMapper {
  override fun mapCodeToStatus(code: String?): EventStatus =
    when (code) {
      "draft",
      "requested",
      "received",
      "accepted",
      "ready" -> PREPARATION

      "in-progress" -> INPROGRESS

      "on-hold" -> ONHOLD

      "completed" -> COMPLETED

      "cancelled",
      "rejected" -> STOPPED

      "failed" -> NOTDONE

      "entered-in-error" -> ENTEREDINERROR

      else -> OTHER(code)
    }

  override fun mapStatusToCode(status: EventStatus): String? =
    when (status) {
      PREPARATION -> "ready"
      INPROGRESS -> "in-progress"
      NOTDONE -> "failed"
      ONHOLD -> "on-hold"
      COMPLETED -> "completed"
      STOPPED -> "cancelled"
      ENTEREDINERROR -> "entered-in-error"
      UNKNOWN -> "ready"
      is OTHER -> status.code
    }
}
