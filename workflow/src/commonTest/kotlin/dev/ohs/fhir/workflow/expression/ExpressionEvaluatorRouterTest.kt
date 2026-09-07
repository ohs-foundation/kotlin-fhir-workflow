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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class ExpressionEvaluatorRouterTest {
  private val router = ExpressionEvaluatorRouter()
  private val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))

  @Test
  fun shouldUseFhirPathEvaluatorWhenLanguageIsFhirPath() = runTest {
    val r = router.evaluate(ProtocolExpression.FhirPath("Patient.id = 'p1'"), ctx)
    assertTrue(r is EvaluationResult.Bool)
  }

  @Test
  fun shouldThrowNotImplementedWhenElmExpression() = runTest {
    assertFailsWith<NotImplementedError> { router.evaluate(ProtocolExpression.Elm("{}"), ctx) }
  }
}
