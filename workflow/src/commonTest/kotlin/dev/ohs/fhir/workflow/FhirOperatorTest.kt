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

import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class FhirOperatorTest {
  @Test
  fun shouldApplyPlanDefinitionWhenGeneratingCarePlan() = runTest {
    val pd =
      PlanDefinition(
        id = "epi",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "bcg",
              title = FhirString(value = "BCG"),
              condition =
                listOf(
                  PlanDefinition.Action.Condition(
                    kind = Enumeration(value = PlanDefinition.ActionConditionKind.Applicability),
                    expression =
                      Expression(
                        language = Enumeration(value = Expression.ExpressionLanguage.Text_Fhirpath),
                        expression = FhirString(value = "Patient.active = true"),
                      ),
                  )
                ),
            )
          ),
      )
    val operator = FhirOperator(InMemoryWorkflowRepository())
    val carePlan =
      operator.generateCarePlan(
        planDefinition = pd,
        subject = Patient(id = "p1", active = dev.ohs.fhir.model.r4.Boolean(value = true)),
        today = LocalDate(2026, 7, 7),
      )
    assertEquals(1, carePlan.contained.filterIsInstance<RequestGroup>().single().action.size)
  }

  @Test
  fun shouldSkipActionWhenApplicabilityFieldIsAbsent() = runTest {
    val pd =
      PlanDefinition(
        id = "epi",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "bcg",
              title = FhirString(value = "BCG"),
              condition =
                listOf(
                  PlanDefinition.Action.Condition(
                    kind = Enumeration(value = PlanDefinition.ActionConditionKind.Applicability),
                    expression =
                      Expression(
                        language = Enumeration(value = Expression.ExpressionLanguage.Text_Fhirpath),
                        expression = FhirString(value = "Patient.active = true"),
                      ),
                  )
                ),
            )
          ),
      )
    val operator = FhirOperator(InMemoryWorkflowRepository())

    val carePlan =
      operator.generateCarePlan(
        planDefinition = pd,
        subject = Patient(id = "p1"),
        today = LocalDate(2026, 7, 7),
      )

    assertEquals(0, carePlan.contained.filterIsInstance<RequestGroup>().single().action.size)
  }

  @Test
  fun shouldResolvePlanDefinitionWithTheSuppliedResolver() = runTest {
    val pd = PlanDefinition(id = "epi", status = Enumeration(value = PublicationStatus.Active))
    val resolver = CanonicalResolver { type, canonical ->
      pd.takeIf { type == ResourceType.PlanDefinition && canonical == PLAN_DEFINITION_CANONICAL }
    }
    val operator = FhirOperator(InMemoryWorkflowRepository(), resolver = resolver)

    val carePlan =
      operator.generateCarePlan(
        planDefinitionCanonical = PLAN_DEFINITION_CANONICAL,
        subject = Patient(id = "p1"),
        today = LocalDate(2026, 7, 7),
      )

    assertEquals("epi", carePlan.id)
  }

  @Test
  fun shouldThrowWhenMeasureOrLibraryIsEvaluated() {
    val operator = FhirOperator(InMemoryWorkflowRepository())
    assertFailsWith<NotImplementedError> { operator.evaluateMeasure("Measure/x") }
    assertFailsWith<NotImplementedError> { operator.evaluateLibrary("Library/x") }
  }

  private companion object {
    const val PLAN_DEFINITION_CANONICAL = "http://example.org/PlanDefinition/epi"
  }
}
