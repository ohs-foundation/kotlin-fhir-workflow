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

/**
 * For the activity flow, we are interested in a few status and they are represented as individual
 * values here. Everything else is represented by [OTHER].
 *
 * See [codesystem-resource-status](https://build.fhir.org/codesystem-resource-status.html) for the
 * list of the status.
 */
sealed interface Status {
  data object DRAFT : Status

  data object ACTIVE : Status

  data object ONHOLD : Status

  data object REVOKED : Status

  data object COMPLETED : Status

  data object ENTEREDINERROR : Status

  class OTHER(val code: String?) : Status
}
