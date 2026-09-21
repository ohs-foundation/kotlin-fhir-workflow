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
package dev.ohs.fhir.workflow

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves a knowledge artifact by canonical URL, honoring any `|version` suffix. Replaces the
 * cqframework KnowledgeManager.
 *
 * Any canonical resource can be asked for, not only the PlanDefinitions and ActivityDefinitions
 * that `$apply` needs today: a Library, a ValueSet or a StructureDefinition resolves the same way,
 * so the CQL and validation work still to come needs no further API.
 *
 * [RepositoryCanonicalResolver] reads the artifacts back out of the [WorkflowRepository] that also
 * holds the patient data. Consumers keeping their artifacts somewhere else — in a FHIR NPM package
 * cache managed by a knowledge library, say — pass their own implementation to [FhirOperator]
 * instead, so that neither library has to depend on the other.
 */
fun interface CanonicalResolver {
  /**
   * Returns the resource of [type] published under [canonical], or `null` if there is none.
   *
   * Prefer the reified [resolve] overload from Kotlin; this is the form Swift and Java callers see.
   *
   * @param canonical the canonical URL, with or without a `|version` suffix.
   */
  suspend fun resolve(type: ResourceType, canonical: String): Resource?
}

/**
 * Returns the [T] published under [canonical], or `null` if there is none of that type.
 *
 * ```
 * val library = resolver.resolve<Library>("http://example.org/Library/FHIRHelpers|4.0.1")
 * ```
 */
suspend inline fun <reified T : Resource> CanonicalResolver.resolve(canonical: String): T? =
  resolve(ResourceType.fromCode(T::class.simpleName ?: error("Anonymous resource type")), canonical)
    as? T

/**
 * The default [CanonicalResolver]: the knowledge artifacts live in the [WorkflowRepository].
 *
 * A `|version` suffix constrains the result to that exact business version, matching
 * `KnowledgeManager.loadResources`; without one, the first artifact published under the url wins.
 */
class RepositoryCanonicalResolver(private val repository: WorkflowRepository) : CanonicalResolver {

  override suspend fun resolve(type: ResourceType, canonical: String): Resource? {
    val url = canonical.substringBefore("|")
    val version = canonical.substringAfter("|", missingDelimiterValue = "")
    val matches = repository.searchByUri(type.code, "url", url)
    return if (version.isEmpty()) {
      matches.firstOrNull()
    } else {
      matches.firstOrNull { businessVersion(it) == version }
    }
  }

  private fun businessVersion(resource: Resource): String? =
    fhirJson
      .encodeToJsonElement(Resource.serializer(), resource)
      .jsonObject["version"]
      ?.jsonPrimitive
      ?.contentOrNull
}
