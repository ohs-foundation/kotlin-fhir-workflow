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

import dev.ohs.fhir.workflow.activity.resource.request.Status.ACTIVE
import dev.ohs.fhir.workflow.activity.resource.request.Status.COMPLETED
import dev.ohs.fhir.workflow.activity.resource.request.Status.DRAFT
import dev.ohs.fhir.workflow.activity.resource.request.Status.ENTEREDINERROR
import dev.ohs.fhir.workflow.activity.resource.request.Status.ONHOLD
import dev.ohs.fhir.workflow.activity.resource.request.Status.OTHER
import dev.ohs.fhir.workflow.activity.resource.request.Status.REVOKED

/**
 * Since request resources may have different code for same status, each [CPGRequestResource] should
 * provide its own mapper. See
 * [columns next to status](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#activity-lifecycle---request-phases-proposal-plan-order)
 */
interface StatusCodeMapper {
  fun mapCodeToStatus(code: String?): Status

  fun mapStatusToCode(status: Status): String?
}

/** A base implementation where the status and code map each other. */
open class StatusCodeMapperImpl : StatusCodeMapper {
  override fun mapCodeToStatus(code: String?): Status =
    when (code) {
      "draft" -> DRAFT
      "active" -> ACTIVE
      "on-hold" -> ONHOLD
      "revoked" -> REVOKED
      "completed" -> COMPLETED
      "entered-in-error" -> ENTEREDINERROR
      else -> OTHER(code)
    }

  override fun mapStatusToCode(status: Status): String? =
    when (status) {
      DRAFT -> "draft"
      ACTIVE -> "active"
      ONHOLD -> "on-hold"
      REVOKED -> "revoked"
      COMPLETED -> "completed"
      ENTEREDINERROR -> "entered-in-error"
      is OTHER -> status.code
    }
}
