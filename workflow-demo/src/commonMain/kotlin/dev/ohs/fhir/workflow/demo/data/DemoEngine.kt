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
package dev.ohs.fhir.workflow.demo.data

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider

/**
 * Returns the shared [FhirEngine] instance, initializing [FhirEngineProvider] on first call.
 *
 * @param platformContext Platform-specific context (e.g. Android `Context`). Ignored on
 *   desktop/iOS.
 */
fun fhirEngine(platformContext: Any = Unit): FhirEngine {
  if (FhirEngineProvider.isNotInitialized()) {
    FhirEngineProvider.init(
      FhirEngineConfiguration(enableEncryptionIfSupported = false),
      platformContext,
    )
  }
  return FhirEngineProvider.getInstance(platformContext)
}
