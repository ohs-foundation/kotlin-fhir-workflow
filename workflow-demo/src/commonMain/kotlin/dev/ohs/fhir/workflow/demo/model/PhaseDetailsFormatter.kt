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
package dev.ohs.fhir.workflow.demo.model

import dev.ohs.fhir.model.r4.Dosage
import dev.ohs.fhir.workflow.activity.resource.event.CPGMedicationDispenseEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGMedicationRequest
import kotlinx.serialization.json.Json

/** Renders a phase's request/event resource as the monospace text block shown on its card. */
internal object PhaseDetailsFormatter {
  fun request(request: CPGMedicationRequest?): String {
    if (request == null) return "—"
    return listOf(
        "ID     : ${request.resourceType}/${request.logicalId}",
        "Intent : ${request.getIntent().code}",
        "Status : ${request.getStatus()}",
        "BasedOn: ${request.getBasedOn()?.reference?.value ?: "—"}",
        "",
        "Additional Info: ${dosage(request.resource.dosageInstruction)}",
      )
      .joinToString("\n")
  }

  fun event(event: CPGMedicationDispenseEvent?): String {
    if (event == null) return "—"
    return listOf(
        "ID     : ${event.resourceType}/${event.logicalId}",
        "Status : ${event.getStatus()}",
        "BasedOn: ${event.getBasedOn()?.reference?.value ?: "—"}",
        "",
        "Additional Info: ${dosage(event.resource.dosageInstruction)}",
      )
      .joinToString("\n")
  }

  private fun dosage(dosageInstruction: List<Dosage>): String =
    dosageInstruction.firstOrNull()?.let { json.encodeToString(Dosage.serializer(), it) } ?: "—"

  private val json = Json {
    prettyPrint = true
    encodeDefaults = false
    explicitNulls = false
  }
}
