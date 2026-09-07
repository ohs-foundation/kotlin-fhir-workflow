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

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.String as FhirString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EvaluatedValueJsonTest {
  @Test
  fun shouldConvertKotlinStringWhenPrimitive() {
    assertEquals(JsonPrimitive("routine"), evaluatedValueToJson("routine"))
  }

  @Test
  fun shouldConvertCodeableConceptWhenComplex() {
    val cc = CodeableConcept(coding = listOf(Coding(code = Code(value = "BCG"))))
    val json = evaluatedValueToJson(cc)
    assertTrue(json is JsonObject)
    val code = json["coding"]!!.jsonArray.first().jsonObject["code"]!!.jsonPrimitive.content
    assertEquals("BCG", code)
  }

  @Test
  fun shouldThrowWhenUnsupportedType() {
    assertFailsWith<IllegalStateException> { evaluatedValueToJson(FhirString(value = "x") to 1) }
  }
}
