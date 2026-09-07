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
 * Since event resources may have different codes for the same status, each [CPGEventResource]
 * should provide its own mapper. See
 * [columns next to status](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#activity-lifecycle---event-phase)
 */
interface EventStatusCodeMapper {
  fun mapCodeToStatus(code: String?): EventStatus

  fun mapStatusToCode(status: EventStatus): String?
}

/** A base implementation where the status and code map each other. */
open class EventStatusCodeMapperImpl : EventStatusCodeMapper {
  override fun mapCodeToStatus(code: String?): EventStatus =
    when (code) {
      "preparation" -> PREPARATION
      "in-progress" -> INPROGRESS
      "not-done" -> NOTDONE
      "on-hold" -> ONHOLD
      "completed" -> COMPLETED
      "entered-in-error" -> ENTEREDINERROR
      "stopped" -> STOPPED
      "unknown" -> UNKNOWN
      else -> OTHER(code)
    }

  override fun mapStatusToCode(status: EventStatus): String? =
    when (status) {
      PREPARATION -> "preparation"
      INPROGRESS -> "in-progress"
      NOTDONE -> "not-done"
      ONHOLD -> "on-hold"
      COMPLETED -> "completed"
      ENTEREDINERROR -> "entered-in-error"
      STOPPED -> "stopped"
      UNKNOWN -> "unknown"
      is OTHER -> status.code
    }
}
