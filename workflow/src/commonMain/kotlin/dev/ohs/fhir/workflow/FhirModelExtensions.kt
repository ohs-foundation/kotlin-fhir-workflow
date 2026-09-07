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
package dev.ohs.fhir.workflow

import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString

internal fun fhirString(value: String?): FhirString? = value?.let { FhirString(value = it) }

internal fun reference(ref: String): Reference = Reference(reference = FhirString(value = ref))

internal val Reference.ref: String?
  get() = reference?.value

/** Resource type name as used in references, e.g. "CommunicationRequest". */
internal fun Resource.resourceTypeName(): String = this::class.simpleName ?: error("no type")

/** The bare logical id, without any trailing `/_history/{version}` (or other) segment. */
internal val Resource.logicalId: String?
  get() = id?.substringBefore("/")
