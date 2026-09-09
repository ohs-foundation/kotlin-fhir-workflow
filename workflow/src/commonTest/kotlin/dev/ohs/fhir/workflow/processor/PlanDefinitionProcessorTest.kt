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
package dev.ohs.fhir.workflow.processor

import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Dosage
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.ExtensibleEnumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.workflow.RepositoryCanonicalResolver
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class PlanDefinitionProcessorTest {
  @Test
  fun shouldYieldRequestGroupActionWhenActionIsApplicable() = runTest {
    // ActivityDefinition to resolve
    val adUrl = "http://example.org/ActivityDefinition/bcg"
    val repo =
      InMemoryWorkflowRepository().apply {
        registerUriIndex("ActivityDefinition", "url") {
          listOf((it as ActivityDefinition).url?.value ?: "")
        }
        create(
          ActivityDefinition(
            id = "bcg",
            url = Uri(value = adUrl),
            status = Enumeration(value = PublicationStatus.Active),
            kind = Enumeration(value = ActivityDefinition.RequestResourceType.ServiceRequest),
            title = FhirString(value = "BCG vaccine"),
          )
        )
      }
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
                        language =
                          ExtensibleEnumeration.of(
                            value = Expression.ExpressionLanguage.Text_Fhirpath
                          ),
                        expression = FhirString(value = "Patient.active = true"),
                      ),
                  )
                ),
              definition = PlanDefinition.Action.Definition.Canonical(Canonical(value = adUrl)),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx =
      EvaluationContext(
        subject = Patient(id = "p1", active = dev.ohs.fhir.model.r4.Boolean(value = true)),
        today = LocalDate(2026, 7, 7),
      )
    val carePlan = processor.apply(pd, ctx)

    val requestGroup = carePlan.contained.filterIsInstance<RequestGroup>().single()
    assertEquals(1, requestGroup.action.size)
    assertEquals("BCG", requestGroup.action.single().title?.value)
  }

  @Test
  fun shouldOmitActionWhenItIsNotApplicable() = runTest {
    val repo = InMemoryWorkflowRepository()
    val pd =
      PlanDefinition(
        id = "epi",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "bcg",
              condition =
                listOf(
                  PlanDefinition.Action.Condition(
                    kind = Enumeration(value = PlanDefinition.ActionConditionKind.Applicability),
                    expression =
                      Expression(
                        language =
                          ExtensibleEnumeration.of(
                            value = Expression.ExpressionLanguage.Text_Fhirpath
                          ),
                        expression = FhirString(value = "Patient.active = false"),
                      ),
                  )
                ),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx =
      EvaluationContext(
        subject = Patient(id = "p1", active = dev.ohs.fhir.model.r4.Boolean(value = true)),
        today = LocalDate(2026, 7, 7),
      )
    val carePlan = processor.apply(pd, ctx)
    assertEquals(0, carePlan.contained.filterIsInstance<RequestGroup>().single().action.size)
  }

  @Test
  fun shouldThrowWhenApplicabilityConditionUsesCql() = runTest {
    val processor =
      PlanDefinitionProcessor(
        ExpressionEvaluatorRouter(),
        RepositoryCanonicalResolver(InMemoryWorkflowRepository()),
      )
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    assertFailsWith<NotImplementedError> {
      processor.apply(planWithApplicability(Expression.ExpressionLanguage.Text_Cql, "true"), ctx)
    }
  }

  @Test
  fun shouldThrowWhenApplicabilityConditionIsNotBoolean() = runTest {
    val processor =
      PlanDefinitionProcessor(
        ExpressionEvaluatorRouter(),
        RepositoryCanonicalResolver(InMemoryWorkflowRepository()),
      )
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    assertFailsWith<IllegalStateException> {
      processor.apply(
        planWithApplicability(Expression.ExpressionLanguage.Text_Fhirpath, "'not-a-boolean'"),
        ctx,
      )
    }
  }

  private fun planWithApplicability(language: Expression.ExpressionLanguage, expression: String) =
    PlanDefinition(
      id = "epi",
      status = Enumeration(value = PublicationStatus.Active),
      action =
        listOf(
          PlanDefinition.Action(
            id = "a1",
            condition =
              listOf(
                PlanDefinition.Action.Condition(
                  kind = Enumeration(value = PlanDefinition.ActionConditionKind.Applicability),
                  expression =
                    Expression(
                      language = ExtensibleEnumeration.of(value = language),
                      expression = FhirString(value = expression),
                    ),
                )
              ),
          )
        ),
    )

  @Test
  fun shouldCopyActionExtensionWhenApplying() = runTest {
    val repo = InMemoryWorkflowRepository()
    val ext = Extension(url = "https://ohs.fhir.org/StructureDefinition/schedule-offset")
    val pd =
      PlanDefinition(
        id = "pd-1",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "a1",
              title = FhirString(value = "A1"),
              extension = listOf(ext),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    val carePlan = processor.apply(pd, ctx)

    val rg = carePlan.contained.filterIsInstance<RequestGroup>().single()
    assertEquals(
      "https://ohs.fhir.org/StructureDefinition/schedule-offset",
      rg.action.single().extension.single().url,
    )
  }

  @Test
  fun shouldInstantiateTaskWhenActionHasActivityDefinition() = runTest {
    val adUrl = "https://ohs.fhir.org/ActivityDefinition/ad-bcg"
    val repo =
      InMemoryWorkflowRepository().apply {
        registerUriIndex("ActivityDefinition", "url") {
          listOf((it as ActivityDefinition).url?.value ?: "")
        }
        create(
          ActivityDefinition(
            id = "ad-bcg",
            url = Uri(value = adUrl),
            status = Enumeration(value = PublicationStatus.Active),
            kind = Enumeration(value = ActivityDefinition.RequestResourceType.Task),
            code = CodeableConcept(coding = listOf(Coding(code = Code(value = "BCG")))),
          )
        )
      }
    val pd =
      PlanDefinition(
        id = "pd-1",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "bcg",
              title = FhirString(value = "BCG"),
              definition = PlanDefinition.Action.Definition.Canonical(Canonical(value = adUrl)),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    val carePlan = processor.apply(pd, ctx)

    val task = carePlan.contained.filterIsInstance<Task>().single()
    assertEquals("BCG", task.code?.coding?.first()?.code?.value)
    val rg = carePlan.contained.filterIsInstance<RequestGroup>().single()
    assertEquals("#${task.id}", rg.action.single().resource?.reference?.value)
  }

  @Test
  fun shouldEmitNestedActionsWhenActionHasChildren() = runTest {
    val repo = InMemoryWorkflowRepository()
    val pd =
      PlanDefinition(
        id = "anc",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "contact-1",
              title = FhirString(value = "Contact 1"),
              action =
                listOf(
                  PlanDefinition.Action(id = "bp", title = FhirString(value = "BP")),
                  PlanDefinition.Action(id = "tt", title = FhirString(value = "TT")),
                ),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    val carePlan = processor.apply(pd, ctx)

    val contact = carePlan.contained.filterIsInstance<RequestGroup>().single().action.single()
    assertEquals("contact-1", contact.id)
    assertEquals(listOf("bp", "tt"), contact.action.map { it.id })
  }

  @Test
  fun shouldInstantiateMedicationRequestWhenKindIsMedicationRequest() = runTest {
    val repo = InMemoryWorkflowRepository()
    val pd =
      repo.planForKind(
        ActivityDefinition.RequestResourceType.MedicationRequest,
        ActivityDefinition.Product.CodeableConcept(
          CodeableConcept(text = FhirString(value = "Apple, daily"))
        ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    val carePlan = processor.apply(pd, ctx)

    val medicationRequest = carePlan.contained.filterIsInstance<MedicationRequest>().single()
    assertEquals("Patient/p1", medicationRequest.subject.reference?.value)
    assertEquals(
      "Apple, daily",
      (medicationRequest.medication as MedicationRequest.Medication.CodeableConcept)
        .value
        .text
        ?.value,
    )
    assertEquals(0, carePlan.contained.filterIsInstance<Task>().size)
  }

  @Test
  fun shouldCopyProfilePriorityAndDosageWhenActivityDefinitionDeclaresThem() = runTest {
    val repo = InMemoryWorkflowRepository()
    val profile = "http://hl7.org/fhir/uv/cpg/StructureDefinition/cpg-medicationrequest"
    val pd =
      repo.planForKind(
        ActivityDefinition.RequestResourceType.MedicationRequest,
        profile = Canonical(value = profile),
        priority = ActivityDefinition.RequestPriority.Routine,
        dosage = listOf(Dosage(text = FhirString(value = "One apple a day"))),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    val carePlan = processor.apply(pd, ctx)

    val medicationRequest = carePlan.contained.filterIsInstance<MedicationRequest>().single()
    assertEquals(listOf(profile), medicationRequest.meta?.profile?.map { it.value })
    assertEquals(MedicationRequest.RequestPriority.Routine, medicationRequest.priority?.value)
    assertEquals(
      listOf("One apple a day"),
      medicationRequest.dosageInstruction.map { it.text?.value },
    )
  }

  @Test
  fun shouldInstantiateServiceRequestWhenKindIsServiceRequest() = runTest {
    val repo = InMemoryWorkflowRepository()
    val pd = repo.planForKind(ActivityDefinition.RequestResourceType.ServiceRequest)
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7))
    val carePlan = processor.apply(pd, ctx)

    val serviceRequest = carePlan.contained.filterIsInstance<ServiceRequest>().single()
    assertEquals("Patient/p1", serviceRequest.subject.reference?.value)
    // The generated CarePlan links back to the PlanDefinition it was applied from.
    assertEquals(
      listOf("https://ohs.fhir.org/PlanDefinition/pd-1"),
      carePlan.instantiatesCanonical.map { it.value },
    )
    // Bare ids, no decoration; contained resources referenced with a leading '#'.
    assertEquals("pd-1", carePlan.id)
    assertEquals("#pd-1", serviceRequest.basedOn.single().reference?.value)
  }

  @Test
  fun shouldLinkInstantiatedRequestToItsActivityDefinition() = runTest {
    val adUrl = "https://ohs.fhir.org/ActivityDefinition/ad-1"
    val service = InMemoryWorkflowRepository()
    val serviceRequest =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(service))
        .apply(
          service.planForKind(ActivityDefinition.RequestResourceType.ServiceRequest),
          EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7)),
        )
        .contained
        .filterIsInstance<ServiceRequest>()
        .single()
    assertEquals(listOf(adUrl), serviceRequest.instantiatesCanonical.map { it.value })

    val task = InMemoryWorkflowRepository()
    val taskRequest =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(task))
        .apply(
          task.planForKind(ActivityDefinition.RequestResourceType.Task),
          EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 7)),
        )
        .contained
        .filterIsInstance<Task>()
        .single()
    assertEquals(adUrl, taskRequest.instantiatesCanonical?.value)
  }

  @Test
  fun shouldApplyDynamicValuesToInstantiatedRequest() = runTest {
    val repo = InMemoryWorkflowRepository()
    val adUrl = "https://ohs.fhir.org/ActivityDefinition/ad-1"
    repo.registerUriIndex("ActivityDefinition", "url") {
      listOf((it as ActivityDefinition).url?.value ?: "")
    }
    repo.create(
      ActivityDefinition(
        id = "ad-1",
        url = Uri(value = adUrl),
        status = Enumeration(value = PublicationStatus.Active),
        kind = Enumeration(value = ActivityDefinition.RequestResourceType.MedicationRequest),
        product =
          ActivityDefinition.Product.CodeableConcept(
            CodeableConcept(text = FhirString(value = "Apple"))
          ),
        dynamicValue =
          listOf(
            ActivityDefinition.DynamicValue(
              path = FhirString(value = "priority"),
              expression =
                Expression(
                  language =
                    ExtensibleEnumeration.of(value = Expression.ExpressionLanguage.Text_Fhirpath),
                  expression = FhirString(value = "'routine'"),
                ),
            )
          ),
      )
    )
    val pd =
      PlanDefinition(
        id = "pd-1",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "a1",
              definition = PlanDefinition.Action.Definition.Canonical(Canonical(value = adUrl)),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 13))
    val carePlan = processor.apply(pd, ctx)

    val medicationRequest = carePlan.contained.filterIsInstance<MedicationRequest>().single()
    assertEquals("routine", medicationRequest.priority?.value?.code)
  }

  @Test
  fun shouldPreferActionDynamicValueWhenSamePathAsActivityDefinition() = runTest {
    val repo = InMemoryWorkflowRepository()
    val adUrl = "https://ohs.fhir.org/ActivityDefinition/ad-1"
    repo.registerUriIndex("ActivityDefinition", "url") {
      listOf((it as ActivityDefinition).url?.value ?: "")
    }
    repo.create(
      ActivityDefinition(
        id = "ad-1",
        url = Uri(value = adUrl),
        status = Enumeration(value = PublicationStatus.Active),
        kind = Enumeration(value = ActivityDefinition.RequestResourceType.MedicationRequest),
        product =
          ActivityDefinition.Product.CodeableConcept(
            CodeableConcept(text = FhirString(value = "Apple"))
          ),
        dynamicValue =
          listOf(
            ActivityDefinition.DynamicValue(
              path = FhirString(value = "priority"),
              expression =
                Expression(
                  language =
                    ExtensibleEnumeration.of(value = Expression.ExpressionLanguage.Text_Fhirpath),
                  expression = FhirString(value = "'stat'"),
                ),
            )
          ),
      )
    )
    val pd =
      PlanDefinition(
        id = "pd-1",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "a1",
              definition = PlanDefinition.Action.Definition.Canonical(Canonical(value = adUrl)),
              dynamicValue =
                listOf(
                  PlanDefinition.Action.DynamicValue(
                    path = FhirString(value = "priority"),
                    expression =
                      Expression(
                        language =
                          ExtensibleEnumeration.of(
                            value = Expression.ExpressionLanguage.Text_Fhirpath
                          ),
                        expression = FhirString(value = "'routine'"),
                      ),
                  )
                ),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 13))
    val carePlan = processor.apply(pd, ctx)

    val medicationRequest = carePlan.contained.filterIsInstance<MedicationRequest>().single()
    assertEquals("routine", medicationRequest.priority?.value?.code)
  }

  @Test
  fun shouldThrowWhenDynamicValuePathIsUnknownField() = runTest {
    val repo = InMemoryWorkflowRepository()
    val adUrl = "https://ohs.fhir.org/ActivityDefinition/ad-1"
    repo.registerUriIndex("ActivityDefinition", "url") {
      listOf((it as ActivityDefinition).url?.value ?: "")
    }
    repo.create(
      ActivityDefinition(
        id = "ad-1",
        url = Uri(value = adUrl),
        status = Enumeration(value = PublicationStatus.Active),
        kind = Enumeration(value = ActivityDefinition.RequestResourceType.MedicationRequest),
        product =
          ActivityDefinition.Product.CodeableConcept(
            CodeableConcept(text = FhirString(value = "Apple"))
          ),
        dynamicValue =
          listOf(
            ActivityDefinition.DynamicValue(
              path = FhirString(value = "notAField"),
              expression =
                Expression(
                  language =
                    ExtensibleEnumeration.of(value = Expression.ExpressionLanguage.Text_Fhirpath),
                  expression = FhirString(value = "'routine'"),
                ),
            )
          ),
      )
    )
    val pd =
      PlanDefinition(
        id = "pd-1",
        status = Enumeration(value = PublicationStatus.Active),
        action =
          listOf(
            PlanDefinition.Action(
              id = "a1",
              definition = PlanDefinition.Action.Definition.Canonical(Canonical(value = adUrl)),
            )
          ),
      )
    val processor =
      PlanDefinitionProcessor(ExpressionEvaluatorRouter(), RepositoryCanonicalResolver(repo))
    val ctx = EvaluationContext(subject = Patient(id = "p1"), today = LocalDate(2026, 7, 13))
    assertFailsWith<Exception> { processor.apply(pd, ctx) }
  }

  private suspend fun InMemoryWorkflowRepository.planForKind(
    kind: ActivityDefinition.RequestResourceType,
    product: ActivityDefinition.Product? = null,
    profile: Canonical? = null,
    priority: ActivityDefinition.RequestPriority? = null,
    dosage: List<Dosage> = listOf(),
  ): PlanDefinition {
    val adUrl = "https://ohs.fhir.org/ActivityDefinition/ad-1"
    registerUriIndex("ActivityDefinition", "url") {
      listOf((it as ActivityDefinition).url?.value ?: "")
    }
    create(
      ActivityDefinition(
        id = "ad-1",
        url = Uri(value = adUrl),
        status = Enumeration(value = PublicationStatus.Active),
        kind = Enumeration(value = kind),
        code = CodeableConcept(coding = listOf(Coding(code = Code(value = "X")))),
        product = product,
        profile = profile,
        priority = priority?.let { Enumeration(value = it) },
        dosage = dosage,
      )
    )
    return PlanDefinition(
      id = "pd-1",
      url = Uri(value = "https://ohs.fhir.org/PlanDefinition/pd-1"),
      status = Enumeration(value = PublicationStatus.Active),
      action =
        listOf(
          PlanDefinition.Action(
            id = "a1",
            definition = PlanDefinition.Action.Definition.Canonical(Canonical(value = adUrl)),
          )
        ),
    )
  }
}
