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
import dev.ohs.fhir.model.r4.CarePlan
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.EvaluationResult
import dev.ohs.fhir.workflow.expression.ExpressionEvaluator
import dev.ohs.fhir.workflow.expression.ProtocolExpression
import dev.ohs.fhir.workflow.fhirJson
import dev.ohs.fhir.workflow.logicalId
import dev.ohs.fhir.workflow.resolve
import dev.ohs.fhir.workflow.resourceTypeName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * FHIRPath-based `PlanDefinition/$apply`. Composes an [ExpressionEvaluator] to check action
 * applicability and a [CanonicalResolver] to resolve action titles from referenced
 * ActivityDefinitions. Produces a [CarePlan] carrying a contained [RequestGroup] whose actions
 * mirror the applicable PlanDefinition actions.
 */
class PlanDefinitionProcessor(
  private val evaluator: ExpressionEvaluator,
  private val resolver: CanonicalResolver,
) {
  suspend fun apply(planDefinition: PlanDefinition, context: EvaluationContext): CarePlan {
    val requests = mutableListOf<Resource>()
    val groupActions = processActions(planDefinition, planDefinition.action, context, requests)

    val requestGroup =
      RequestGroup(
        id = planDefinition.logicalId,
        status = Enumeration(value = RequestGroup.RequestStatus.Active),
        intent = Enumeration(value = RequestGroup.RequestIntent.Proposal),
        action = groupActions,
      )

    return CarePlan(
      id = planDefinition.logicalId,
      instantiatesCanonical =
        planDefinition.url?.value?.let { listOf(Canonical(value = it)) } ?: emptyList(),
      status = Enumeration(value = CarePlan.RequestStatus.Active),
      intent = Enumeration(value = CarePlan.CarePlanIntent.Plan),
      subject = subjectReference(context),
      contained = listOf<Resource>(requestGroup) + requests,
      activity =
        if (groupActions.isEmpty()) {
          emptyList()
        } else {
          listOf(
            CarePlan.Activity(
              reference = Reference(reference = FhirString(value = "#${requestGroup.logicalId}"))
            )
          )
        },
    )
  }

  /**
   * Processes a sibling list of actions into [RequestGroup.Action]s, recursing into nested
   * `action.action` (e.g. an ANC contact bundling sub-activities). `relatedAction` prerequisites
   * are gated within the sibling level. Each applicable action with a resolvable
   * [ActivityDefinition] instantiates a request resource of its `kind` (added to [requests])
   * referenced from the emitted action; group actions carry their processed children.
   */
  private suspend fun processActions(
    planDefinition: PlanDefinition,
    actions: List<PlanDefinition.Action>,
    context: EvaluationContext,
    requests: MutableList<Resource>,
  ): List<RequestGroup.Action> {
    val applicableActionIds = mutableSetOf<String>()
    val groupActions = mutableListOf<RequestGroup.Action>()
    for (action in actions) {
      val prerequisitesMet =
        action.relatedAction.all { rel -> applicableActionIds.contains(rel.actionId.value) }
      if (!prerequisitesMet) continue
      if (!isApplicable(action, context)) continue
      action.id?.let { applicableActionIds.add(it) }

      val title = action.title ?: resolveTitle(action)
      val request = instantiateRequest(planDefinition, action, context)
      request?.let { requests.add(it) }
      val children = processActions(planDefinition, action.action, context, requests)
      groupActions.add(
        RequestGroup.Action(
          id = action.id,
          title = title,
          description = action.description,
          extension = action.extension,
          resource = request?.let { Reference(reference = FhirString(value = "#${it.logicalId}")) },
          action = children,
        )
      )
    }
    return groupActions
  }

  private suspend fun isApplicable(
    action: PlanDefinition.Action,
    context: EvaluationContext,
  ): Boolean {
    val applicabilityConditions =
      action.condition.filter { it.kind.value == PlanDefinition.ActionConditionKind.Applicability }
    if (applicabilityConditions.isEmpty()) return true
    return applicabilityConditions.all { condition ->
      val expression =
        condition.expression
          ?: throw IllegalStateException(
            "Applicability condition on action '${action.id}' has no expression"
          )
      when (val result = evaluator.evaluate(expression.toProtocolExpression(), context)) {
        is EvaluationResult.Bool -> result.value

        is EvaluationResult.Failure ->
          throw IllegalStateException(
            "Applicability condition failed to evaluate: ${result.message}"
          )

        is EvaluationResult.Values ->
          if (result.value.isEmpty()) {
            false
          } else {
            throw IllegalStateException("Applicability condition did not evaluate to a boolean")
          }
      }
    }
  }

  private suspend fun resolveTitle(action: PlanDefinition.Action): FhirString? {
    val canonical = action.definition?.asCanonical()?.value?.value ?: return null
    return resolver.resolve<ActivityDefinition>(canonical)?.title
  }

  /**
   * Instantiates a proposal-intent request resource from the action's referenced
   * [ActivityDefinition], choosing the concrete type from its `kind` (MedicationRequest,
   * ServiceRequest, CommunicationRequest, or Task as the default). Static fields (`profile`,
   * `code`, medication `product`, `priority` and `dosage`) are copied, and `dynamicValue`
   * expressions from both the [ActivityDefinition] and the action are evaluated and written back
   * (the action's overriding the ActivityDefinition's on matching paths). Returns null when the
   * action has no resolvable definition.
   */
  private suspend fun instantiateRequest(
    planDefinition: PlanDefinition,
    action: PlanDefinition.Action,
    context: EvaluationContext,
  ): Resource? {
    val canonical = action.definition?.asCanonical()?.value?.value ?: return null
    val ad = resolver.resolve<ActivityDefinition>(canonical) ?: return null
    val id = action.id ?: ad.logicalId
    val subject = subjectReference(context)
    val basedOn = Reference(reference = FhirString(value = "#${planDefinition.logicalId}"))
    val request: Resource =
      when (ad.kind?.value) {
        ActivityDefinition.RequestResourceType.MedicationRequest ->
          MedicationRequest(
            id = id,
            meta = metaFrom(ad),
            instantiatesCanonical = listOf(Canonical(value = canonical)),
            status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
            intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Proposal),
            priority = priorityFrom(ad),
            medication = medicationFrom(ad),
            subject = subject,
            basedOn = listOf(basedOn),
            dosageInstruction = ad.dosage,
          )

        ActivityDefinition.RequestResourceType.ServiceRequest ->
          ServiceRequest(
            id = id,
            meta = metaFrom(ad),
            instantiatesCanonical = listOf(Canonical(value = canonical)),
            status = Enumeration(value = ServiceRequest.RequestStatus.Active),
            intent = Enumeration(value = ServiceRequest.RequestIntent.Proposal),
            code = ad.code,
            subject = subject,
            basedOn = listOf(basedOn),
          )

        ActivityDefinition.RequestResourceType.CommunicationRequest ->
          CommunicationRequest(
            id = id,
            meta = metaFrom(ad),
            status = Enumeration(value = CommunicationRequest.RequestStatus.Active),
            subject = subject,
            basedOn = listOf(basedOn),
          )

        else ->
          Task(
            id = id,
            meta = metaFrom(ad),
            instantiatesCanonical = Canonical(value = canonical),
            status = Enumeration(value = Task.TaskStatus.Requested),
            intent = Enumeration(value = Task.TaskIntent.Proposal),
            code = ad.code,
            description = action.description ?: ad.description,
            `for` = subject,
            basedOn = listOf(basedOn),
          )
      }
    val writes =
      ad.dynamicValue.mapNotNull { dv ->
        dv.path.value?.let { path -> DynamicWrite(path, dv.expression) }
      } +
        action.dynamicValue.mapNotNull { dv ->
          val path = dv.path?.value ?: return@mapNotNull null
          val expression = dv.expression ?: return@mapNotNull null
          DynamicWrite(path, expression)
        }
    return applyDynamicValues(request, writes, context)
  }

  private data class DynamicWrite(val path: String, val expression: Expression)

  private suspend fun applyDynamicValues(
    resource: Resource,
    writes: List<DynamicWrite>,
    context: EvaluationContext,
  ): Resource {
    if (writes.isEmpty()) return resource
    var json = fhirJson.encodeToJsonElement(Resource.serializer(), resource).jsonObject
    for (write in writes) {
      val value = evaluateSingle(write.expression, context)
      json = DynamicValueApplier.set(json, write.path, value)
    }
    return fhirJson.decodeFromJsonElement(Resource.serializer(), json)
  }

  private suspend fun evaluateSingle(
    expression: Expression,
    context: EvaluationContext,
  ): JsonElement =
    when (val result = evaluator.evaluate(expression.toProtocolExpression(), context)) {
      is EvaluationResult.Bool -> JsonPrimitive(result.value)

      is EvaluationResult.Values ->
        evaluatedValueToJson(
          result.value.firstOrNull()
            ?: throw IllegalStateException("dynamicValue expression produced no value")
        )

      is EvaluationResult.Failure ->
        throw IllegalStateException("dynamicValue failed to evaluate: ${result.message}")
    }

  /**
   * Carries the ActivityDefinition's declared profile (e.g. a CPG request profile) onto the
   * request.
   */
  private fun metaFrom(ad: ActivityDefinition): Meta? =
    ad.profile?.let { Meta(profile = listOf(it)) }

  private fun priorityFrom(
    ad: ActivityDefinition
  ): Enumeration<MedicationRequest.RequestPriority>? =
    ad.priority?.value?.getCode()?.let {
      Enumeration(value = MedicationRequest.RequestPriority.fromCode(it))
    }

  private fun medicationFrom(ad: ActivityDefinition): MedicationRequest.Medication =
    when (val product = ad.product) {
      is ActivityDefinition.Product.CodeableConcept ->
        MedicationRequest.Medication.CodeableConcept(product.value)

      is ActivityDefinition.Product.Reference ->
        MedicationRequest.Medication.Reference(product.value)

      null -> MedicationRequest.Medication.CodeableConcept(ad.code ?: CodeableConcept())
    }

  private fun subjectReference(context: EvaluationContext): Reference {
    val id = context.subject.id ?: "unknown"
    return Reference(reference = FhirString(value = "${context.subject.resourceTypeName()}/$id"))
  }
}

/** Routes a FHIR [Expression] to the workflow [ProtocolExpression] by its declared language. */
private fun Expression.toProtocolExpression(): ProtocolExpression {
  val text = expression?.value ?: throw IllegalStateException("Expression has no expression text")
  return when (language.value) {
    Expression.ExpressionLanguage.Text_Fhirpath -> ProtocolExpression.FhirPath(text)

    Expression.ExpressionLanguage.Text_Cql -> ProtocolExpression.Elm(text)

    else ->
      throw IllegalStateException("Unsupported expression language: ${language.value?.getCode()}")
  }
}
