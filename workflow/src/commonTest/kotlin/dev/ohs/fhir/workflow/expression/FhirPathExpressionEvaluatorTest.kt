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

import dev.ohs.fhir.model.r4.Patient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class FhirPathExpressionEvaluatorTest {
  private val evaluator = FhirPathExpressionEvaluator()
  private val ctx =
    EvaluationContext(
      subject = Patient(id = "p1", active = boolTrue()),
      today = LocalDate(2026, 7, 7),
    )

  @Test
  fun shouldReturnTrueWhenConditionHolds() = runTest {
    val r = evaluator.evaluate(ProtocolExpression.FhirPath("Patient.active = true"), ctx)
    assertEquals(true, r.asBoolean())
  }

  @Test
  fun shouldReturnFalseWhenConditionDoesNotHold() = runTest {
    val r = evaluator.evaluate(ProtocolExpression.FhirPath("Patient.active = false"), ctx)
    assertEquals(false, r.asBoolean())
  }

  private fun boolTrue() = dev.ohs.fhir.model.r4.Boolean(value = true)
}
