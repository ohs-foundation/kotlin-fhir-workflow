# kotlin-fhir-workflow
Kotlin Multiplatform library for implementing workflow patterns and logic

## Resolving knowledge artifacts

`FhirOperator` looks knowledge artifacts up through a `CanonicalResolver`, a single-method interface
over "give me the resource of this type published under this canonical URL":

```kotlin
fun interface CanonicalResolver {
  suspend fun resolve(type: ResourceType, canonical: String): Resource?
}
```

It is deliberately not limited to the `PlanDefinition`s and `ActivityDefinition`s that
`PlanDefinition/$apply` needs today. A `Library`, `ValueSet` or `StructureDefinition` resolves the
same way, so the CQL and validation work still to come needs no further API. From Kotlin, use the
reified form and let the type argument name the resource:

```kotlin
val plan = resolver.resolve<PlanDefinition>("http://example.org/PlanDefinition/anc|0.3.0")
val library = resolver.resolve<Library>("http://example.org/Library/FHIRHelpers")
```

The default is `RepositoryCanonicalResolver`, which reads the artifacts back out of the same
`WorkflowRepository` that holds the patient data — nothing to wire up if that is where you keep
them:

```kotlin
val operator = FhirOperator(repository)
```

### Backing it with the knowledge library

Pass your own implementation to keep the artifacts somewhere else. This is how
[kotlin-fhir-knowledge](https://github.com/ohs-foundation/kotlin-fhir-knowledge) plugs in — it
installs versioned FHIR NPM packages and indexes them by canonical URL, which is exactly what the
resolver asks for. Neither library depends on the other; the adapter lives in your application:

```kotlin
class KnowledgeCanonicalResolver(private val knowledgeManager: KnowledgeManager) :
  CanonicalResolver {

  override suspend fun resolve(type: ResourceType, canonical: String): Resource? =
    knowledgeManager.loadResources(canonical).firstOrNull { it::class.simpleName == type.code }
}
```

```kotlin
val knowledgeManager = KnowledgeManager.create(platformContext = context)
knowledgeManager.install(FhirNpmPackage("anc-cds", "0.3.0"))

val operator = FhirOperator(repository, resolver = KnowledgeCanonicalResolver(knowledgeManager))
val carePlan =
  operator.generateCarePlan(
    planDefinitionCanonical = "http://fhir.org/guides/who/anc-cds/PlanDefinition/anc|0.3.0",
    subject = patient,
    today = today,
  )
```

`loadResources` accepts the `|version` suffix directly, so a versioned canonical resolves to that
exact version and an unversioned one to every version indexed.
