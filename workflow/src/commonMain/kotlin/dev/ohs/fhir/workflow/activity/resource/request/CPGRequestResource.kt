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

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.logicalId
import dev.ohs.fhir.workflow.reference
import dev.ohs.fhir.workflow.resourceTypeName

/**
 * This abstracts the
 * [CPG Request Resources](https://build.fhir.org/ig/HL7/cqf-recommendations/profiles.html#activity-profiles)
 * used in various activities. The various subclasses of [CPGRequestResource] act as a wrapper
 * around the resource they are derived from and help with the abstracted properties defined for
 * each [CPGRequestResource]. e.g. [CPGCommunicationRequest] is a wrapper around the
 * [CommunicationRequest] and helps with its [Intent], [Status] and basedOn [Reference]s.
 *
 * The wrapped [resource] is immutable, so every setter reassigns [resource] via `.copy(...)`.
 *
 * The application users may use the [Companion.of] factory to create the required
 * [CPGRequestResource]s. The factory dispatches on the type of the given [Resource]: a
 * [CommunicationRequest] produces a [CPGCommunicationRequest], a [MedicationRequest] a
 * [CPGMedicationRequest], a [Task] a [CPGTaskRequest] and a [ServiceRequest] a [CPGServiceRequest].
 * Any other resource type is rejected with an [IllegalArgumentException].
 */
sealed class CPGRequestResource<R : Resource>(internal val mapper: StatusCodeMapper) {
  abstract var resource: R
    protected set

  val resourceType: String
    get() = resource.resourceTypeName()

  val logicalId: String?
    get() = resource.logicalId

  internal abstract fun setIntent(intent: Intent)

  abstract fun getIntent(): Intent

  abstract fun setStatus(status: Status, reason: String? = null)

  fun getStatus(): Status = mapper.mapCodeToStatus(getStatusCode())

  abstract fun getStatusCode(): String?

  abstract fun setBasedOn(reference: Reference)

  abstract fun getBasedOn(): Reference?

  internal abstract fun copy(): CPGRequestResource<R>

  fun copy(id: String, status: Status, intent: Intent): CPGRequestResource<R> {
    val parentRef = asReference()
    return copy().apply {
      setId(id)
      setStatus(status)
      setIntent(intent)
      setBasedOn(parentRef)
    }
  }

  internal abstract fun setId(id: String)

  fun asReference(): Reference = reference("${resource.resourceTypeName()}/${resource.logicalId}")

  companion object {
    /**
     * Creates the [CPGRequestResource] appropriate for the given request [resource]. The type of
     * the resource describes the activity and is used to select the particular CPG request wrapper.
     *
     * @throws IllegalArgumentException if the resource is not a supported CPG request type.
     */
    fun <R : Resource> of(resource: R): CPGRequestResource<R> {
      @Suppress("UNCHECKED_CAST")
      return when (resource) {
        is CommunicationRequest -> CPGCommunicationRequest(resource)
        is MedicationRequest -> CPGMedicationRequest(resource)
        is Task -> CPGTaskRequest(resource)
        is ServiceRequest -> CPGServiceRequest(resource)
        else -> throw IllegalArgumentException("Unknown CPG Request type ${resource::class}.")
      }
        as CPGRequestResource<R>
    }
  }
}
