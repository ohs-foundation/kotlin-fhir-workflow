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
package dev.ohs.fhir.workflow.activity.phase

import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.workflow.activity.resource.event.CPGEventResource
import dev.ohs.fhir.workflow.activity.resource.request.CPGRequestResource
import dev.ohs.fhir.workflow.ref

/** Defines the various phases of a CPG Request. */
sealed interface Phase {
  /** The names of the phases an activity flow may be in. */
  enum class PhaseName {
    PROPOSAL,
    PLAN,
    ORDER,
    PERFORM,
  }

  /** Returns the [PhaseName] of this phase. */
  fun getPhaseName(): PhaseName

  /** Activity phases for a CPG Request. */
  interface RequestPhase<R : CPGRequestResource<*>> : Phase, ReadOnlyRequestPhase<R> {
    suspend fun update(r: R): Result<Unit>

    suspend fun suspendPhase(reason: String?): Result<Unit>

    suspend fun resume(): Result<Unit>

    suspend fun enteredInError(reason: String?): Result<Unit>

    suspend fun reject(reason: String?): Result<Unit>
  }

  /** Activity phases for a CPG Event. */
  interface EventPhase<E : CPGEventResource<*>> : Phase {
    fun getEventResource(): E

    suspend fun update(e: E): Result<Unit>

    suspend fun suspendPhase(reason: String?): Result<Unit>

    suspend fun resume(): Result<Unit>

    suspend fun enteredInError(reason: String?): Result<Unit>

    suspend fun start(): Result<Unit>

    suspend fun notDone(reason: String?): Result<Unit>

    suspend fun stop(reason: String?): Result<Unit>

    suspend fun complete(): Result<Unit>
  }
}

/** Provides a read-only view of a request phase. */
interface ReadOnlyRequestPhase<R : CPGRequestResource<*>> {
  /** Returns the [Phase.PhaseName] of this phase. */
  fun getPhaseName(): Phase.PhaseName

  /** Returns the request resource this phase wraps. */
  fun getRequestResource(): R
}

/** Checks if two references are equal by comparing their reference values. */
internal fun checkReferencesEqual(a: Reference, b: Reference): Boolean = a.ref == b.ref
