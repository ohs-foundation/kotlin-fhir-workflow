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
package dev.ohs.fhir.workflow.demo.workflow

/**
 * FHIR resource-type names and the CPG event class name the demo passes to the workflow library.
 */
internal object DemoFhir {
  const val PATIENT = "Patient"
  const val MEDICATION_REQUEST = "MedicationRequest"
  const val PLAN_DEFINITION = "PlanDefinition"
  const val MEDICATION_DISPENSE_EVENT = "CPGMedicationDispenseEvent"
}
