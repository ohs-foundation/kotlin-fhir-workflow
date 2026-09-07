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

/** The phases the demo walks through, in order. [NONE] means the flow has run to completion. */
enum class FlowPhase {
  INITIALIZE,
  PROPOSAL,
  PLAN,
  ORDER,
  PERFORM,
  NONE,
}

/** A single phase's rendering in the demo UI. */
data class PhaseCard(val phase: FlowPhase, val details: String, val isActive: Boolean)

/** Everything the demo screen renders, as one immutable snapshot. */
data class DemoUiState(
  val phase: FlowPhase = FlowPhase.INITIALIZE,
  val cards: List<PhaseCard> = emptyList(),
  val progress: Boolean = false,
  val initialized: Boolean = false,
  val patientName: String = "",
)
