# Expression support in `$apply`: FHIRPath now, room for CQL & StructureMap

This library's `PlanDefinition/$apply` evaluates **FHIRPath** for the logic in a plan (action
applicability conditions and `dynamicValue`), instantiates request resources from
**`ActivityDefinition.kind`**, and routes expression evaluation through a pluggable
`ExpressionEvaluator` seam. **CQL** and **StructureMap** are deliberately deferred — the seam
accommodates CQL when a Kotlin-Multiplatform ELM engine exists, and StructureMap is a separate
*transform* concern with no current consumer.

This note explains why FHIRPath, what it covers, its known gaps, and where CQL and StructureMap fit.

---

## `$apply` has two independent axes

It helps to separate them, because FHIRPath, CQL, and StructureMap do not all sit on the same one.

| Axis | Question it answers | FHIR element | This library |
|------|---------------------|--------------|--------------|
| **Logic / expression** | *Is this action applicable? What value goes here?* | `action.condition`, `dynamicValue` | **FHIRPath** (CQL deferred) |
| **Transform / instantiation** | *Which resource, populated how?* | `ActivityDefinition.kind`, `.transform` | **kind-aware + `dynamicValue`** (StructureMap deferred) |

- **FHIRPath vs CQL** are two *languages* for the **logic** axis — interchangeable at the structural
  level (`{ path, expression, language }`); only the evaluator differs.
- **StructureMap** lives on the **transform** axis. It is *not* a CQL alternative — it maps a source
  structure into a target resource, rather than evaluating a condition or a value.

---

## Why FHIRPath, now

- **Multiplatform.** The FHIRPath engine runs on every target (Android, JVM, iOS, web/wasm). The CQL
  runtime (ELM interpretation) has no KMP implementation yet — the reference stack is JVM/HAPI-only.
- **It covers the need.** Community workflows (immunization, ANC/PNC, FP, CMAM, campaigns) express
  their applicability and computed fields as FHIRPath: existence checks ("already given?"),
  offset-from-anchor scheduling, simple predicates.
- **Lean.** No library compilation, no ELM, no terminology service required for the common cases.
- **Pluggable.** Evaluation is behind an `ExpressionEvaluator` seam and routed by
  `Expression.language`. FHIRPath is the implemented route; CQL slots in later **without touching the
  write-back** (the JSON path-setter and value conversion are language-agnostic).

## What FHIRPath covers well

- **Applicability conditions** — `action.condition` of kind `applicability`.
- **`dynamicValue`** — evaluate an expression and write the result at a path on the generated
  resource (the write is a reflection-free JSON path-setter).
- **Kind-aware instantiation** — `ActivityDefinition.kind` (a finite valueset) selects the request
  type (`MedicationRequest` / `ServiceRequest` / `CommunicationRequest` / `Task` default), and static
  fields plus `dynamicValue` populate it.

---

## Known gaps

### 1. FHIRPath the language (vs CQL)

- **No data retrieval — the one that shapes authoring most.** FHIRPath sees only the subject resource
  and the `%variables` the caller passes. It cannot *fetch* additional resources the way CQL's
  `retrieve` can. So the **consumer must prefetch** everything an expression needs and bind it — which
  is why the engine binds `%immunizations`, `%observations`, `%serviceRequests`, `%catchment`, etc.
- **No terminology service.** `memberOf(valueSet)` and code-system expansion need a backend; without
  one, valueset-driven logic falls back to enumerating codes inline.
- **No library / reuse.** Expressions are inline — no named `define`s shared across conditions and
  dynamicValues. CQL libraries solve this; FHIRPath does not.
- **Weaker temporal & query semantics.** Fine for "6 weeks after birth"; awkward for
  overlapping-interval reasoning or cross-resource aggregation.

### 2. Engine quirks (documented workarounds)

- **List-bound `%variables` evaluate empty.** Pass a list as a **collection `Bundle`** and author
  `%immunizations.entry.resource.where(...)`, not a bare list variable.
- **Quoted UCUM units crash date arithmetic.** Use unquoted `today() - 5 years`, not `5 'years'`.

### 3. This implementation

- **`dynamicValue` empty result throws** rather than skipping the write — an empty FHIRPath result is
  a valid outcome, so a "skip on empty" refinement is a likely follow-up.
- **Single value only** — the first evaluated value is written; multi-cardinality results are deferred.
- **JSON-shaped path contract** — `dynamicValue` paths use explicit list indices
  (`dosageInstruction[0].…`) and concrete choice names (`medicationCodeableConcept`, not
  `medication`). There is no schema-based path validation; a typo surfaces at strict decode, not up
  front.
- **`relatedAction`** is an unconditional applicability gate (relationship-type semantics unported).
- **Unknown `kind`** degrades to `Task` rather than that exact request type.

---

## StructureMap — where it fits, and why it's deferred

StructureMap is the **transform** axis. In FHIR tooling it shows up in two distinct jobs:

1. **Questionnaire extraction (`$extract`)** — transform a `QuestionnaireResponse` into resources. A
   form-extraction concern, unrelated to `$apply`.
2. **Resource generation via `ActivityDefinition.transform` / `action.transform`** — a StructureMap
   builds the request resource(s) from a source (`ActivityDefinition` + data), an alternative to
   field-copy instantiation.

This library replaces the common case of (2) with **kind-aware instantiation + FHIRPath
`dynamicValue`**: a known request type whose fields are set by value. StructureMap's *unique* value is
**arbitrary structural transformation** — multi-resource output from one action, complex conditional
restructuring — which value-level `dynamicValue` cannot express.

It is deferred, not rejected: no current workflow needs a transform beyond "known type + set fields,"
and there is no KMP StructureMap engine. When a real need appears, the natural extension is a
`transform` seam alongside the existing `ExpressionEvaluator` seam, with today's kind-aware
instantiation as the default.

## CQL — deferred behind the seam

CQL routes through the same `ExpressionEvaluator` seam; today `text/cql` reaches a stub that throws
`NotImplementedError`, so a CQL-authored IG cannot run yet. Because routing is by
`Expression.language` and the `dynamicValue` write-back is language-agnostic, adding CQL is
*"plug in a real ELM evaluator"* — the path-setter and value conversion are reused unchanged.

What CQL would add over FHIRPath: **libraries and reuse**, a **retrieve** model (fetch data without
prefetch), a **terminology provider**, and richer temporal/query semantics — i.e. it directly closes
gaps §1 above. It remains deferred until a KMP ELM runtime exists.

---

## Authoring guidance (today)

- Author conditions and `dynamicValue` in **`text/fhirpath`**.
- **Prefetch** the data an expression needs and bind it as a **collection `Bundle`**; author
  `%var.entry.resource.where(...)`.
- Use **unquoted units** in date math (`today() - 5 years`).
- Write `dynamicValue` paths **JSON-shaped**: explicit list indices and concrete choice element names.
- Expect **fail-loud** behavior: an unsupported language, a non-boolean condition, or an evaluation
  failure throws rather than silently skipping.
