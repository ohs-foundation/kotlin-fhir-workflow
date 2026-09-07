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
package dev.ohs.fhir.workflow.expression

sealed class EvaluationResult {
  data class Bool(val value: Boolean) : EvaluationResult()

  data class Values(val value: List<Any>) : EvaluationResult()

  data class Failure(val message: String) : EvaluationResult()

  fun asBoolean(): Boolean? = (this as? Bool)?.value

  fun asValues(): List<Any> = (this as? Values)?.value ?: emptyList()
}
