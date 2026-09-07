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
import dev.ohs.fhir.model.r4.MedicationDispense
import dev.ohs.fhir.model.r4.Procedure
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.activity.resource.request.CPGCommunicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.logicalId
import dev.ohs.fhir.workflow.resourceTypeName

/**
 * This abstracts the
 * [CPG Event Resources](https://build.fhir.org/ig/HL7/cqf-recommendations/profiles.html#activity-profiles)
 * used in various activities. The various subclasses of [CPGEventResource] act as a wrapper around
 * the resource they are derived from and help with the abstracted properties defined for each
 * [CPGEventResource]. e.g. [CPGCommunicationEvent] is a wrapper around the [Communication] and
 * helps with its [EventStatus] and basedOn [Reference]s.
 *
 * The wrapped [resource] is immutable, so every setter reassigns [resource] with a `.copy(...)` of
 * the previous value instead of mutating it in place.
 *
 * The application users may use the appropriate [Companion.of] factory to create the required
 * [CPGEventResource]s.
 */
sealed class CPGEventResource<R : Resource>(internal val mapper: EventStatusCodeMapper) {
  abstract var resource: R
    protected set

  val resourceType: String
    get() = resource.resourceTypeName()

  val logicalId: String?
    get() = resource.logicalId

  abstract fun setStatus(status: EventStatus, reason: String? = null)

  fun getStatus(): EventStatus = mapper.mapCodeToStatus(getStatusCode())

  abstract fun getStatusCode(): String?

  abstract fun setBasedOn(reference: Reference)

  abstract fun getBasedOn(): Reference?

  abstract fun copy(): CPGEventResource<R>

  companion object {
    internal fun from(from: CPGRequestResource<*>, eventClassName: String): CPGEventResource<*> =
      when (from) {
        is CPGCommunicationRequest -> CPGCommunicationEvent.from(from)
        is CPGMedicationRequest -> CPGOrderMedicationEvent.from(from, eventClassName)
        is CPGTaskRequest -> CPGTaskEvent.from(from)
        is CPGServiceRequest -> CPGProcedureEvent.from(from)
      }

    fun of(event: Resource): CPGEventResource<*> =
      when (event) {
        is Communication -> CPGCommunicationEvent(event)
        is MedicationDispense -> CPGMedicationDispenseEvent(event)
        is Task -> CPGTaskEvent(event)
        is Procedure -> CPGProcedureEvent(event)
        else -> throw IllegalArgumentException("Unknown CPG event type ${event::class}.")
      }
  }
}
