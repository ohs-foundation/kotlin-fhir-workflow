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
package dev.ohs.fhir.workflow.activity.resource.event

import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.activity.resource.request.CPGTaskRequest
import dev.ohs.fhir.workflow.activity.resource.request.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CPGTaskEventTest {
  private fun taskRequest() =
    CPGTaskRequest(
        Task(
          id = "task-1",
          status = Enumeration(value = Task.TaskStatus.Requested),
          intent = Enumeration(value = Task.TaskIntent.Order),
        )
      )
      .apply { setIntent(Intent.ORDER) }

  @Test
  fun shouldCreateTaskEventInPreparationWhenFromRequest() {
    val event = CPGTaskEvent.from(taskRequest())
    assertEquals(EventStatus.PREPARATION, event.getStatus())
  }

  @Test
  fun shouldCarrySingleBasedOnWhenSet() {
    val request = taskRequest()
    val event = CPGTaskEvent.from(request)
    event.setBasedOn(request.asReference())
    assertEquals(listOf("Task/task-1"), event.resource.basedOn.map { it.reference?.value })
  }

  @Test
  fun shouldResolveToTaskEventWhenTaskRequestOrResource() {
    assertTrue(CPGEventResource.from(taskRequest(), "CPGTaskEvent") is CPGTaskEvent)
    assertTrue(
      CPGEventResource.of(
        Task(
          id = "t",
          status = Enumeration(value = Task.TaskStatus.Ready),
          intent = Enumeration(value = Task.TaskIntent.Order),
        )
      ) is CPGTaskEvent
    )
  }
}
