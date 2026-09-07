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
package dev.ohs.fhir.workflow.activity.phase.request

import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.phase.Phase
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource

/**
 * Provides implementation of the proposal phase of the activity flow. See
 * [general-activity-flow](https://build.fhir.org/ig/HL7/cqf-recommendations/activityflow.html#general-activity-flow)
 * for more info.
 *
 * @param repository implementation of [WorkflowRepository] to store / retrieve FHIR resources.
 * @param r concrete implementation of sealed [CPGRequestResource] class. e.g.
 *   `CPGCommunicationRequest`.
 */
class ProposalPhase<R : CPGRequestResource<*>>(repository: WorkflowRepository, r: R) :
  BaseRequestPhase<R>(repository, r, Phase.PhaseName.PROPOSAL)
