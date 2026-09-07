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
 * [PROPOSAL], [PLAN] and [ORDER] are the only intents we are interested in. All the other Request
 * Intent values are represented by [OTHER].
 *
 * See [codesystem-request-intent](https://www.hl7.org/FHIR/codesystem-request-intent.html) for the
 * list of intents.
 */
sealed class Intent(val code: String?) {
  data object PROPOSAL : Intent("proposal")

  data object PLAN : Intent("plan")

  data object ORDER : Intent("order")

  class OTHER(code: String?) : Intent(code)

  override fun toString(): String = code ?: "null"

  companion object {
    fun of(code: String?): Intent =
      when (code) {
        "proposal" -> PROPOSAL
        "plan" -> PLAN
        "order" -> ORDER
        else -> OTHER(code)
      }
  }
}
