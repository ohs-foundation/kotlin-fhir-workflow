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

/**
 * STUB. CQL is authored, compiled to ELM offline, and evaluated here — but no KMP ELM interpreter
 * exists yet, so every call throws. Swap in a real implementation later without touching the router
 * or the processor.
 */
class ElmExpressionEvaluator : ExpressionEvaluator {
  // TODO: implement CQL/ELM evaluation on Kotlin Multiplatform (reference google/cql).
  override suspend fun evaluate(
    expression: ProtocolExpression,
    context: EvaluationContext,
  ): EvaluationResult =
    throw NotImplementedError(
      "CQL/ELM expression evaluation is not implemented on Kotlin Multiplatform. " +
        "Author conditions in text/fhirpath, or provide a custom ExpressionEvaluator."
    )
}
