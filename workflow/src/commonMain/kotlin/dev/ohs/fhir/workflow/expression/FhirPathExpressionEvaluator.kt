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

import dev.ohs.fhir.fhirpath.FhirPathEngine
import dev.ohs.fhir.fhirpath.forR4

/** Reference [ExpressionEvaluator]: evaluates `text/fhirpath` expressions via [FhirPathEngine]. */
class FhirPathExpressionEvaluator(private val engine: FhirPathEngine = FhirPathEngine.forR4()) :
  ExpressionEvaluator {

  override suspend fun evaluate(
    expression: ProtocolExpression,
    context: EvaluationContext,
  ): EvaluationResult {
    if (expression !is ProtocolExpression.FhirPath) {
      return EvaluationResult.Failure("FhirPathExpressionEvaluator cannot evaluate $expression")
    }
    return try {
      val result: Collection<Any> =
        engine.evaluateExpression(expression.expression, context.subject, context.variables)
      val list = result.toList()
      val single = list.singleOrNull()
      when (val value = single?.let(::coerceBoolean)) {
        null -> EvaluationResult.Values(list)
        else -> EvaluationResult.Bool(value)
      }
    } catch (t: Throwable) {
      EvaluationResult.Failure(t.message ?: "FHIRPath evaluation failed: ${expression.expression}")
    }
  }

  private fun coerceBoolean(value: Any): Boolean? =
    when (value) {
      is Boolean -> value
      is dev.ohs.fhir.model.r4.Boolean -> value.value
      else -> null
    }
}
