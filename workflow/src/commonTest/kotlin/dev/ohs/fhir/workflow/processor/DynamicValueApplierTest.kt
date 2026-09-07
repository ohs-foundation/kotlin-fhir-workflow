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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class DynamicValueApplierTest {
  private val empty = buildJsonObject {}

  @Test
  fun shouldSetTopLevelFieldWhenSimplePath() {
    val result = DynamicValueApplier.set(empty, "priority", JsonPrimitive("routine"))
    assertEquals(buildJsonObject { put("priority", "routine") }, result)
  }

  @Test
  fun shouldStripResourceTypePrefixWhenPresent() {
    val result =
      DynamicValueApplier.set(empty, "MedicationRequest.priority", JsonPrimitive("routine"))
    assertEquals(buildJsonObject { put("priority", "routine") }, result)
  }

  @Test
  fun shouldCreateIntermediatesWhenNestedIndexedPath() {
    val result =
      DynamicValueApplier.set(
        empty,
        "dosageInstruction[0].timing.repeat.frequency",
        JsonPrimitive(1),
      )
    val expected = buildJsonObject {
      putJsonArray("dosageInstruction") {
        add(
          buildJsonObject {
            putJsonObject("timing") { putJsonObject("repeat") { put("frequency", 1) } }
          }
        )
      }
    }
    assertEquals(expected, result)
  }

  @Test
  fun shouldOverwriteExistingValueWhenPathPresent() {
    val start = buildJsonObject { put("priority", "stat") }
    val result = DynamicValueApplier.set(start, "priority", JsonPrimitive("routine"))
    assertEquals(buildJsonObject { put("priority", "routine") }, result)
  }
}
