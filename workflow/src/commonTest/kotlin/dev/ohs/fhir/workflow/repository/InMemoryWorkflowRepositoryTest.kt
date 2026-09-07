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
package dev.ohs.fhir.workflow.repository

import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class InMemoryWorkflowRepositoryTest {
  @Test
  fun shouldReturnResourceWhenReadAfterCreate() = runTest {
    val repo = InMemoryWorkflowRepository()
    repo.create(CommunicationRequest(id = "cr-1", status = statusActive()))
    val read = repo.read("CommunicationRequest", "cr-1")
    assertEquals("cr-1", read?.id)
  }

  @Test
  fun shouldReturnNullWhenResourceIsMissing() = runTest {
    assertNull(InMemoryWorkflowRepository().read("CommunicationRequest", "nope"))
  }

  private fun statusActive() =
    dev.ohs.fhir.model.r4.Enumeration(value = CommunicationRequest.RequestStatus.Active)
}
