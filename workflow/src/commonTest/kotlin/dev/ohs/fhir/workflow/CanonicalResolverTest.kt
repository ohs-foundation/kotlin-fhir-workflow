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

import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.ConceptMap
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Library
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.workflow.testing.InMemoryWorkflowRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class CanonicalResolverTest {
  private val url = "http://example.org/ActivityDefinition/ad-1"

  @Test
  fun shouldResolveWhenVersionPinMatches() = runTest {
    val repo = repositoryWith(activityDefinition("ad-1", "1.0.0"))

    val resolved = RepositoryCanonicalResolver(repo).resolve<ActivityDefinition>("$url|1.0.0")

    assertEquals("ad-1", resolved?.id)
  }

  @Test
  fun shouldResolveTheExactVersionWhenMultipleCoexist() = runTest {
    val repo =
      repositoryWith(activityDefinition("ad-v1", "1.0.0"), activityDefinition("ad-v2", "2.0.0"))
    val resolver = RepositoryCanonicalResolver(repo)

    assertEquals("ad-v1", resolver.resolve<ActivityDefinition>("$url|1.0.0")?.id)
    assertEquals("ad-v2", resolver.resolve<ActivityDefinition>("$url|2.0.0")?.id)
  }

  @Test
  fun shouldReturnNothingWhenVersionPinDoesNotMatch() = runTest {
    val repo = repositoryWith(activityDefinition("ad-1", "1.0.0"))

    assertNull(RepositoryCanonicalResolver(repo).resolve<ActivityDefinition>("$url|2.0.0"))
  }

  @Test
  fun shouldReturnNothingWhenPinnedButArtifactHasNoVersion() = runTest {
    val repo = repositoryWith(activityDefinition("ad-1", version = null))

    assertNull(RepositoryCanonicalResolver(repo).resolve<ActivityDefinition>("$url|1.0.0"))
  }

  @Test
  fun shouldResolveWhenNoVersionPin() = runTest {
    val repo = repositoryWith(activityDefinition("ad-1", "1.0.0"))

    assertEquals("ad-1", RepositoryCanonicalResolver(repo).resolve<ActivityDefinition>(url)?.id)
  }

  @Test
  fun shouldReturnNothingWhenUrlUnknown() = runTest {
    val repo = repositoryWith(activityDefinition("ad-1", "1.0.0"))

    assertNull(
      RepositoryCanonicalResolver(repo)
        .resolve<ActivityDefinition>("http://example.org/ActivityDefinition/absent")
    )
  }

  /** Any canonical resource resolves, not only the two kinds `$apply` happens to need. */
  @Test
  fun shouldResolveALibrary() = runTest {
    val libraryUrl = "http://example.org/Library/FHIRHelpers"
    val repo = repositoryWith(library(libraryUrl))

    val resolved = RepositoryCanonicalResolver(repo).resolve<Library>(libraryUrl)

    assertEquals("fhir-helpers", resolved?.id)
  }

  @Test
  fun shouldReturnNothingWhenTheArtifactIsOfAnotherType() = runTest {
    val libraryUrl = "http://example.org/Library/FHIRHelpers"
    val repo = repositoryWith(library(libraryUrl))

    assertNull(RepositoryCanonicalResolver(repo).resolve<PlanDefinition>(libraryUrl))
  }

  @Test
  fun shouldHonorThePinForAnyCanonicalResourceType() = runTest {
    val conceptMapUrl = "http://example.org/ConceptMap/cm"
    val repo =
      InMemoryWorkflowRepository().apply {
        registerUriIndex("ConceptMap", "url") { listOf((it as ConceptMap).url?.value ?: "") }
        create(
          ConceptMap(
            id = "cm-v1",
            url = Uri(value = conceptMapUrl),
            version = FhirString(value = "1.0.0"),
            status = Enumeration(value = PublicationStatus.Active),
          )
        )
      }

    assertEquals(
      "cm-v1",
      RepositoryCanonicalResolver(repo).resolve<ConceptMap>("$conceptMapUrl|1.0.0")?.id,
    )
    assertNull(RepositoryCanonicalResolver(repo).resolve<ConceptMap>("$conceptMapUrl|9.9.9"))
  }

  private suspend fun repositoryWith(vararg artifacts: ActivityDefinition) =
    InMemoryWorkflowRepository().apply {
      registerUriIndex("ActivityDefinition", "url") {
        listOf((it as ActivityDefinition).url?.value ?: "")
      }
      artifacts.forEach { create(it) }
    }

  private suspend fun repositoryWith(library: Library) =
    InMemoryWorkflowRepository().apply {
      registerUriIndex("Library", "url") { listOf((it as Library).url?.value ?: "") }
      create(library)
    }

  private fun activityDefinition(id: String, version: String?) =
    ActivityDefinition(
      id = id,
      url = Uri(value = url),
      version = version?.let { FhirString(value = it) },
      status = Enumeration(value = PublicationStatus.Active),
    )

  /** R4 makes `Library.type` and `Library.status` mandatory. */
  private fun library(url: String) =
    Library(
      id = "fhir-helpers",
      url = Uri(value = url),
      status = Enumeration(value = PublicationStatus.Active),
      type = CodeableConcept(coding = listOf(Coding(code = Code(value = "logic-library")))),
    )
}
