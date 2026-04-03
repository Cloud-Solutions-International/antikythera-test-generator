# Full-Project Test Discovery Fallback

## Summary
When `generator.yml` omits both `services` and `controllers`, Antikythera should enter a new implicit fallback mode that discovers test targets across the whole application instead of doing nothing.

This fallback should target all source classes with real executable logic, not just Spring services. It should still skip categories that are low-value or unsuitable for generated unit tests. Existing explicit `services` and `controllers` behavior remains unchanged and takes precedence.

### Module Boundary Note
- Any change that belongs to the expression evaluation engine, preprocessing pipeline, type-resolution layer, runtime metadata cache, or other reusable core-analysis behavior must go into `antikythera`.
- Only code that is specifically about selecting test targets, classifying generation candidates, orchestrating generation runs, or emitting generated-test-specific behavior should go into `antikythera-test-generator`.
- If a helper is useful outside test generation, place it in `antikythera` and consume it from `antikythera-test-generator`.

## Key Changes

### Orchestration
- Keep the existing explicit modes unchanged:
  - If `controllers` is non-empty, run the current controller/API generation flow.
  - If `services` is non-empty, run the current service/unit generation flow.
- Add a new fallback branch:
  - If both `controllers` and `services` are empty or absent, run full-project discovery for unit-test generation only.
- Do not auto-generate API tests in fallback mode.

### Candidate Discovery and Classification
- Build fallback candidates from `AntikytheraRunTime.getResolvedTypes()`, filtered to types whose fully-qualified name starts with `base_package` (from `generator.yml`). This ensures that dependency classes pulled in during preprocessing are never treated as generation targets.
- Introduce a `TargetClassifier` with a single method `ClassificationResult classify(TypeWrapper)`.
- `ClassificationResult` carries a `ClassificationDecision` (`UNIT_TARGET` or `SKIP`) together with a `SkipReason` enum value and a human-readable explanation for logging.
- Rules must be applied in a fixed, documented order so that decisions are deterministic.

#### SKIP Rules (applied in this order)

**1. Structural / type-system-level skips (cheapest checks first)**
- Annotation type declarations (`@interface`)
- `@FeignClient` interfaces (use explicit reason: `FEIGN_CLIENT`)
- Interfaces — covers Spring Data `Repository`/`JpaRepository`/`CrudRepository` extension interfaces, `*RepositoryCustom` contracts, marker interfaces, functional interfaces, and all other interface types
- Enums — skip by default; enum types that define non-trivial methods are a future opt-in via `include_enum_logic: true`
- Records — purely data-carrying; no point testing auto-generated accessors
- Abstract classes — cannot be instantiated; synthetic subclass generation is out of scope

**2. Spring MVC / web layer**
- `@RestController`, `@Controller`
- `@RestControllerAdvice`, `@ControllerAdvice`
- Classes that extend `ResponseEntityExceptionHandler`

**3. Persistence / data model**
- `@Entity`, `@Embeddable`, `@MappedSuperclass`, `@IdClass`
- Spring Data repository interfaces (explicit named reason: `SPRING_DATA_REPOSITORY`)
- Classes annotated with `@Repository` **and** implementing Spring Data repository interfaces when they are pure proxy-style definitions
- Hand-written `@Repository` implementations with real logic (`EntityManager`, criteria/native query logic) are **not** blanket-skipped

**4. Application wiring and bootstrapping**
- `@Configuration`, `@ConfigurationProperties`
- `@SpringBootApplication`
- Classes with `public static void main(String[])`

**5. AOP aspects**
- `@Aspect`

**6. Messaging infrastructure**
- Feign fallback `@Component` stubs with no real logic should be skipped by no-logic checks

**7. Constant and property-holder classes**
- Classes whose members are only constants (`public static final`), trivial constructors, or passive `@Value` holders with no behavior

**8. Pure exception classes**
- Classes extending `Throwable` chain where members are constructors/getters only
- Exception classes with real validation/business logic are not auto-skipped

**9. Data carriers — behaviour-first, heuristics second**
- Primary: no non-boilerplate methods (getters/setters/trivial constructors/equals/hashCode/toString/builder chainers)
- Secondary: Lombok data-holder signals (`@Data`, `@Value`, etc.)
- Tertiary: naming suffixes (`Dto`, `DTO`, `Request`, `Response`, `Model`, `VO`, `Payload`, `Form`, `Command`, `Event`)
- Never skip on name alone when real logic exists

#### UNIT_TARGET Rules
- Include `@Service` and logic-bearing `@Component` classes.
- Include non-service classes not skipped above that contain at least one non-boilerplate method.
- Explicit include examples:
  - JPA `Specification` factory classes
  - `ConstraintValidator` implementations
  - JPA `AttributeConverter` implementations
  - Spring `Converter` implementations
  - `@KafkaListener` logic-bearing components
  - `@Scheduled` logic-bearing services
  - Custom Jackson serializers/deserializers
  - Hand-written `@Repository` implementations with real query logic

### Nested / Inner Class Handling
- `AntikytheraRunTime` already registers nested types during preprocessing.
- Apply classification to each registered type independently for discovery purposes.
- Generation/orchestration must still remain compilation-unit aware: the current `ServicesParser` / `UnitTestGenerator` flow is organized around a cached `CompilationUnit` and its top-level public test suite, not a fully independent nested-type output path.
- Static inner `Builder` classes should typically be caught by boilerplate/data-carrier checks.
- Private inner classes are not reachable from tests and should be skipped.
- Nested `@Entity` / `@Embeddable` follow normal entity skip rules.

### User-Controlled Overrides (new config options)
```yaml
skip:
  - com.example.myapp.legacy
  - com.example.myapp.generated.SomeAutogeneratedClass

include:
  - com.example.myapp.dao.impl
```

- Evaluate after automatic rules.
- `skip` wins over `include` when both match.

### `SkipReason` Enum
```text
ANNOTATION_TYPE, INTERFACE, ENUM, RECORD, ABSTRACT_CLASS,
CONTROLLER, CONTROLLER_ADVICE,
ENTITY, EMBEDDABLE, MAPPED_SUPERCLASS, SPRING_DATA_REPOSITORY,
CONFIGURATION, SPRING_BOOT_APPLICATION,
AOP_ASPECT,
FEIGN_CLIENT,
CONSTANT_CLASS, EXCEPTION_CLASS,
DATA_CARRIER_BY_STRUCTURE, DATA_CARRIER_BY_ANNOTATION, DATA_CARRIER_BY_NAME,
NO_TESTABLE_METHODS,
USER_SKIP_LIST
```

### Leveraging Existing Antikythera Infrastructure
- Reuse `AbstractCompiler.shouldSkip(String)` for `skip`-list suffix matching.
- Reuse `EntityMappingResolver.isEntity(TypeWrapper)` for robust `@Entity` detection across `javax` and `jakarta` plus binary classes.
- Add dedicated checks for `@Embeddable`, `@MappedSuperclass`, and `@IdClass`; those are not currently covered by `EntityMappingResolver.isEntity(TypeWrapper)`.
- Reuse `AntikytheraRunTime.getResolvedTypes()` for discovery (no file-system re-scan).
- Prefer `BaseRepositoryParser.isJpaRepository(TypeWrapper|TypeDeclaration<?>)` for Spring Data repository detection.
- `AntikytheraRunTime.findImplementations(String)` can still be used as supporting metadata where useful, but it should not be the primary detector for repository interfaces.
- Reuse `TypeWrapper.isInterface()` and JavaParser native checks (`isAnnotationDeclaration`, `isRecordDeclaration`, `isAbstract`).

### Core/Metadata Support
- Add `isRepository` and `isConfiguration` to `TypeWrapper` to reduce repeated annotation checks.
- Keep entity detection delegated to `EntityMappingResolver.isEntity(TypeWrapper)`.
- Keep classification centralized in `TargetClassifier`.
- Add fallback summary logs:
  - total discovered under `base_package`
  - selected `UNIT_TARGET`
  - skipped counts by `SkipReason`
  - DEBUG per-class decision line

### Documentation
- Update `generator.yml` docs:
  - explicit `services` / `controllers` behavior is unchanged
  - absent both implies fallback discovery mode
  - fallback skips low-value/non-logic categories
  - `skip` / `include` overrides are available

## Public API / Interface Changes
- New optional `generator.yml` field: `include` (list)
- Existing `skip` field is reused in fallback classification
- Existing `services` / `controllers` semantics unchanged
- New internal type: `TargetClassifier`
- New internal type: `ClassificationResult`
- New internal enum: `SkipReason`
- `TypeWrapper` additions: `isRepository`, `isConfiguration`
- Logging: fallback mode emits INFO summary + DEBUG per-class decisions

## Test Plan

### Mode Selection Tests
- `services` only → explicit service mode
- `controllers` only → explicit controller mode
- both present → both modes
- both absent → fallback mode

### Classifier Unit Tests
Each test creates a `TypeWrapper` from a hand-crafted `CompilationUnit` and asserts expected decision/reason.

| Class shape | Expected decision | Expected reason |
|---|---|---|
| `@Service` with non-trivial method | `UNIT_TARGET` | — |
| `@Component` with non-trivial method | `UNIT_TARGET` | — |
| `@Repository` impl with `EntityManager` + real logic | `UNIT_TARGET` | — |
| `@RestController` | `SKIP` | `CONTROLLER` |
| `@ControllerAdvice` | `SKIP` | `CONTROLLER_ADVICE` |
| `@Entity` | `SKIP` | `ENTITY` |
| `@Embeddable` | `SKIP` | `EMBEDDABLE` |
| `@MappedSuperclass` | `SKIP` | `MAPPED_SUPERCLASS` |
| `interface extends JpaRepository` | `SKIP` | `SPRING_DATA_REPOSITORY` |
| `@Configuration` | `SKIP` | `CONFIGURATION` |
| `@SpringBootApplication` | `SKIP` | `SPRING_BOOT_APPLICATION` |
| `@Aspect` | `SKIP` | `AOP_ASPECT` |
| `interface` | `SKIP` | `INTERFACE` |
| `enum` | `SKIP` | `ENUM` |
| `record` | `SKIP` | `RECORD` |
| `abstract class` | `SKIP` | `ABSTRACT_CLASS` |
| `@Data` class, all getters/setters | `SKIP` | `DATA_CARRIER_BY_ANNOTATION` |
| only getters/setters/ctors | `SKIP` | `DATA_CARRIER_BY_STRUCTURE` |
| `extends RuntimeException`, constructors only | `SKIP` | `EXCEPTION_CLASS` |
| class in `skip` list | `SKIP` | `USER_SKIP_LIST` |
| class in `include` list otherwise skipped | `UNIT_TARGET` | — |
| `*Dto` with real method | `UNIT_TARGET` | — |

### Integration Fixture Tests
Add the fixture project and supporting sample classes in `antikythera-test-helper`, following the existing cross-module fixture pattern used by this repository. Do not add these fixtures under `antikythera-test-generator/src/test/resources`. The helper module should contain:
- `@Service` with logic → included
- `@Component` helper with logic → included
- `@Component` Feign fallback with stubs → excluded
- `@KafkaListener @Component` with logic → included
- Hand-written `@Repository` impl with `EntityManager` → included
- Spring Data `interface extends JpaRepository` → excluded
- `@Entity` / `@Embeddable` → excluded
- `@MappedSuperclass` / `@IdClass` → excluded
- DTO with `@Data` and no logic → excluded
- DTO-like name with real method → included
- `@RestController` / `@ControllerAdvice` → excluded
- `@Configuration` / `@SpringBootApplication` / `@Aspect` → excluded
- `interface` / `enum` / `record` / `abstract class` → excluded
- custom exception with constructors only → excluded
- `ConstraintValidator` implementation → included
- class in user `skip` list → excluded
- verify generated tests are created only for intended classes

### Validation Against Real Project
- Run fallback discovery against `csi-ehr-opd-patient-pomr-java-sev` with `services` and `controllers` removed from `generator.yml`
- Record totals, selected targets, and skip counts by reason
- Compare selected classes with current manually-maintained service list for parity

## Assumptions and Defaults
- Explicit config (`services`, `controllers`) always wins.
- Fallback mode is unit-test generation only.
- `base_package` is the primary scope guard.
- Abstract-class instantiation synthesis is out of scope.
- DTO detection remains behavior-first.
- No blanket skip for all `@Repository` classes.
- `@Scheduled` / `@KafkaListener` classes with real logic are included.
- Enum logic opt-in is future work.
- `skip` and `include` are applied after auto rules; `skip` wins.
