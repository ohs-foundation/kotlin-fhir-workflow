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

import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest

/**
 * CPG request wrapper for a lab/service order ([ServiceRequest]); status uses the request pattern.
 */
class CPGServiceRequest(resource: ServiceRequest) :
  CPGRequestResource<ServiceRequest>(StatusCodeMapperImpl()) {

  override var resource: ServiceRequest = resource

  override fun setId(id: String) {
    resource = resource.copy(id = id)
  }

  override fun setIntent(intent: Intent) {
    resource =
      resource.copy(
        intent = Enumeration(value = ServiceRequest.RequestIntent.fromCode(intent.code ?: "order"))
      )
  }

  override fun getIntent(): Intent = Intent.of(resource.intent.value?.getCode())

  override fun setStatus(status: Status, reason: String?) {
    resource =
      resource.copy(
        status =
          Enumeration(
            value =
              ServiceRequest.RequestStatus.fromCode(mapper.mapStatusToCode(status) ?: "unknown")
          )
      )
  }

  override fun getStatusCode(): String? = resource.status.value?.getCode()

  override fun setBasedOn(reference: Reference) {
    resource = resource.copy(basedOn = resource.basedOn + reference)
  }

  override fun getBasedOn(): Reference? = resource.basedOn.lastOrNull()

  override fun copy(): CPGRequestResource<ServiceRequest> = CPGServiceRequest(resource.copy())
}
