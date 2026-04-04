### ⚠️ Areas for Improvement

#### 1. **Single Responsibility Principle Violation**

**Problem:** `UnitTestGenerator` has grown to on the order of **~2,300 lines** (historically ~400; count drifts—verify with `wc -l` on `UnitTestGenerator.java`).

**Responsibilities:**
- Test method creation
- Mock field wiring
- Exception analysis orchestration
- Type coercion
- Setter/getter resolution
- Reflection utilities
- Collection normalization
- Base class field extraction
- @Value field injection
- Logging setup
- Assertion building

**Recommendation:** Extract into focused services:

```java
// Suggested decomposition
class TestMockingOrchestrator {
    void wireMocks(TypeDeclaration<?> type, ClassOrInterfaceDeclaration testClass);
    void wireBaseClassFields(TypeDeclaration<?> baseClass);
}

class TypeCoercionService {
    Expression coerce(Expression expr, Type targetType);
    Expression normalizeStringPlaceholder(StringLiteralExpr literal);
}

class SetterResolverService {
    String resolveSetterName(TypeDeclaration<?> owner, FieldDeclaration field);
    String resolveGetterName(FieldDeclaration field);
    Optional<Type> resolveParameterType(TypeDeclaration<?> owner, String methodName);
}

class BeforeEachSetupHelper {
    MethodDeclaration buildBeforeEach(List<FieldDeclaration> mocks, List<ValueField> values);
}
```

#### 2. **Code Duplication**

**A. Setter/Getter Name Resolution** (3+ locations in `UnitTestGenerator`):
- `setterNameForField()`
- `getterMethodNameForField()`
- Related logic in `normalizeSetterPrecondition()`

**Recommendation:**
```java
class JavaBeansConventions {
    static String getterName(FieldDeclaration field);
    static String setterName(FieldDeclaration field);
    static boolean matchesBooleanConvention(FieldDeclaration field);
}
```

**B. Collection Type Checking** (5+ locations):
```java
// Appears in multiple methods:
type.equals("List") || type.equals("ArrayList") || type.equals("LinkedList")
    || type.equals("Set") || type.equals("HashSet") ...
```

**Recommendation:**
```java
class TypeInspector {
    static boolean isCollectionType(Type type);
    static boolean isMapType(Type type);
    static boolean isIterableType(Type type);
    static CollectionFamily getFamily(Type type); // LIST, SET, MAP
}
```

**C. Empty Collection Detection** (overlapping logic across classes):
- `isDefinitelyEmptyCollection()` in `UnitTestGenerator`
- `isEmptyCollection()` in `ExceptionAnalyzer` (separate implementation, same broad purpose)

**Recommendation:** Consolidate into `CollectionExpressionAnalyzer`

#### 3. **Deep Nesting & Complexity**

**Critical Method:** `handleExceptionResponse()` — about **90–100 lines** of branching (exact span drifts; search by method name in `UnitTestGenerator`)

**Issues:**
- Cyclomatic complexity: ~15-20
- Multiple boolean flags: `illegalArgumentSuppressionApplied`, `reinstatedNpe`
- Nested conditionals: 4-5 levels deep
- Mixed concerns: analysis + decision + assertion building

**Current Structure:**
```java
handleExceptionResponse(response, invocation) {
    if (ctx == null) { fallback; return; }
    
    type = analyzeException(ctx);
    currentArgs = extractTestArguments();
    seedCollectionArguments(ctx, currentArgs);  // Side effect!
    currentArgs = extractTestArguments();       // Re-extract
    
    willTrigger = analyzeWillTrigger(ctx, currentArgs);
    
    if (shouldSuppressIAE(ctx, currentArgs)) {
        willTrigger = false;
        illegalArgumentSuppressionApplied = true;
    }
    
    if (shouldSuppressNSEE(ctx)) {
        willTrigger = false;
    }
    
    if (!willTrigger) {
        if (!hasWhenStubs()) {
            willTrigger = true;
            reinstatedNpe = true;
        } else if (hasNullThenReturnStubs()) {
            willTrigger = true;
            reinstatedNpe = true;
        }
    }
    
    if (!willTrigger) {
        addAsserts(response, invocation);
    } else {
        if (reinstatedNpe && illegalArgumentSuppressionApplied) {
            // Align exception type
        }
        assertThrows(...);
    }
}
```

**Recommendation:** Extract a **single collaborator** (e.g. `ExceptionResponseAsserter`) with **private helpers** and/or a small **outcome enum** (`assertThrows` vs `addAsserts` vs legacy path). Avoid a Strategy/Chain framework unless the rule set keeps growing and tests prove you need plug-in style handlers.

#### 4. **Method Length Violations**

Long methods (approximate body length in the current `UnitTestGenerator`; re-measure after edits):
- `handleExceptionResponse()` — ~95 lines
- `injectMockedFieldsViaReflection()` — ~50 lines
- `normalizeInlineObjectCreationNulls()` — ~30 lines
- `mockFieldWithSetter()` — ~30 lines
- `createFieldInitializer()` — ~20 lines (nearby helpers add more surface area)

**Recommendation:** Apply Extract Method refactoring to each

#### 5. **Magic Strings & Numbers**

**Examples:**
- `"src/test/java"`, `"src/main/java"` - Repeated path fragments
- `"InjectMocks"`, `"Mock"`, `"ReflectionTestUtils"` - Annotation/class names
- `"Antikythera"` - Placeholder string
- `"0"`, `"0L"` - Default literals

**Recommendation:**
```java
class TestGenerationConstants {
    static final String SRC_MAIN_JAVA = "src/main/java";
    static final String SRC_TEST_JAVA = "src/test/java";
    static final String INJECT_MOCKS = "InjectMocks";
    static final String MOCK = "Mock";
    static final String REFLECTION_TEST_UTILS = "org.springframework.test.util.ReflectionTestUtils";
    static final String PLACEHOLDER_STRING = "Antikythera";
    static final String NUMERIC_PLACEHOLDER = "0";
}
```

#### 6. **Tight Coupling to Global State**

**Dependencies:**
- `AntikytheraRunTime` - Static method calls
- `Settings` - Global configuration
- `MockingRegistry` - Global registry
- `TestGenerator.getImports()` - Static import list

**Impact:**
- Hard to test in isolation
- Implicit dependencies
- Global state mutations

**Recommendation:** Dependency Injection

```java
class UnitTestGenerator {
    private final AntikytheraRunTime runtime;
    private final Settings settings;
    private final MockingRegistry mockRegistry;
    private final ImportManager importManager;
    
    UnitTestGenerator(
        CompilationUnit cu,
        AntikytheraRunTime runtime,
        Settings settings,
        MockingRegistry mockRegistry,
        ImportManager importManager
    ) {
        this.runtime = runtime;
        this.settings = settings;
        // ...
    }
}
```

#### 7. **Lack of Interface Segregation**

**Problem:** `UnitTestGenerator` exposes a **large method surface** (on the order of **~100** methods) with many package-visible entry points and no narrow interfaces.

**Recommendation:** Prefer **package-private collaborators** (`MockFieldSupport`, assertion helper) over many narrow interfaces on `UnitTestGenerator` unless several generators must share the same contract.

---

## SOLID Principles Scorecard

| Principle | Grade | Rationale |
|-----------|-------|-----------|
| **Single Responsibility** | C | `UnitTestGenerator` violates (~2,300 lines, many responsibilities); newer types (`TargetClassifier`, `ExceptionAnalyzer`) are more focused |
| **Open/Closed** | B+ | Enums enable extension without modification; some hardcoded logic in giant methods |
| **Liskov Substitution** | A | Inheritance hierarchies respected; no LSP violations detected |
| **Interface Segregation** | C | `UnitTestGenerator` too broad; `ITestGenerator` minimal; new classes focused |
| **Dependency Inversion** | C | Heavy reliance on static methods and global state; tight coupling to `AntikytheraRunTime`, `Settings` |

**Overall SOLID Grade:** C+ (Pulled down by SRP and DIP violations in core generator class)

---

## Refactoring Recommendations

### Principles

- Prefer **plain extractions** (helpers, small types, `record`s where they clarify data) over adopting named GoF patterns as a goal.
- **JavaParser already builds ASTs**; do not add a parallel fluent Builder API unless a pilot on one code path shows clear duplication reduction.
- **Refactor for duplication and clarity**, not for pattern checklists.

### Priority: utilities and constants (highest ROI)

Consolidate without new architecture:

- **`JavaBeansConventions`** — getter/setter naming (replaces scattered logic in `UnitTestGenerator`).
- **`TypeInspector`** (or extend an existing helper) — collection/map raw-type checks used in many places.
- **`CollectionExpressionAnalyzer`** — one place for "empty collection" heuristics shared by `UnitTestGenerator` and `ExceptionAnalyzer` where possible.
- **`TestGenerationConstants`** — paths, annotation simple names, common placeholders.

### Priority: exception path in generated tests

- Extract **`ExceptionResponseAsserter`** (name TBD): one class used from `UnitTestGenerator` that:
  - Computes what to do (e.g. `assertThrows` vs success-path asserts) using **private methods** and optionally a **small enum** for the outcome.
  - Keeps **side effects** (e.g. re-seeding collection arguments) explicit and documented.
- **Do not** introduce Strategy interfaces, handler chains, or a pluggable `decision.apply()` model unless the rules keep changing independently.

### Priority: mock and setup wiring (if still hard to navigate after utilities)

- Move chunks of mock identification, reflection injection, and setter-based mock setup into **one or two package-private types** (e.g. `MockFieldSupport`).
- **Defer** mock-injection "strategies" until a third real injection style appears or the same conditional ladder churns every sprint.

### Priority: dependency injection (optional)

- Passing collaborators through **constructors** is worthwhile when you need isolated unit tests of generator pieces.
- Avoid a large **factory + pipeline class hierarchy** until more than one generation workflow truly shares the same steps.

### Explicitly out of scope for this plan

- **Facade + Template Method** pipeline base class and subclasses.
- **Fluent `TestMethodBuilder` / Given–When–Then** layers on top of JavaParser.
- **AssertionStrategy** / **MockInjectionStrategy** registries and **Chain-of-Responsibility** handler hierarchies.
- Success metrics based on **how many named patterns** appear in code.

### Optional sketch (exception helper)

```java
// Illustrative only — names and signatures TBD
final class ExceptionResponseAsserter {
    void handle(MethodResponse response, String invocation,
                MethodDeclaration testMethod, MethodDeclaration methodUnderTest) {
        Outcome o = resolveOutcome(response, testMethod);
        switch (o) {
            case ASSERT_THROWS -> { /* emit assertThrows */ }
            case SUCCESS_PATH_ASSERTS -> { /* addAsserts */ }
            case LEGACY -> { /* previous behavior */ }
        }
    }
    private Outcome resolveOutcome(...) { /* private helpers */ }
    private enum Outcome { ASSERT_THROWS, SUCCESS_PATH_ASSERTS, LEGACY }
}
```


---

## Testing Assessment

### Test Coverage

**New Test Classes:**
1. `ClassificationResultTest` (44 lines) - Value object validation
2. `FallbackClassificationTest` (91 lines) - Override precedence
3. `TargetClassifierTest` (402 lines) - Comprehensive classification rules
4. `TestExceptionAnalyzer` (177 lines) - Exception analysis logic
5. `TestExceptionAnalyzerDebug` (54 lines) - Debug helpers
6. `JunitAsserterTest` - Enhanced with Gson, IAE→NPE tests

**Updated Test Classes:**
- `UnitTestGeneratorTest` — significantly expanded (reflection wiring, type coercion, setter/getter resolution, exception suppression, `@Value` injection, Feign/plain-mock config, etc.); line and method counts drift with ongoing changes.
  - Reflection wiring tests
  - Type coercion tests
  - Setter/getter resolution tests
  - Exception suppression tests
  - @Value field injection tests

**Test Quality:**
- ✅ Comprehensive coverage of new features
- ✅ Edge cases tested (empty collections, null literals, type mismatches)
- ✅ Integration tests for complex flows
- ⚠️ Some tests rely on file fixtures (antikythera-test-helper)
- ⚠️ Limited negative testing (invalid inputs)

---

## Performance Considerations

**Potential Concerns:**

1. **Fallback Discovery** - Iterates all resolved types (O(n) where n = total types in classpath)
   - **Mitigation:** Early filtering by base_package prefix
   - **Impact:** Minimal for typical projects (<1000 classes in base package)

2. **Exception Analysis** - Multiple analyses per method with exceptions
   - **Concern:** Nested exception cause traversal, path condition checks
   - **Impact:** Negligible (exception paths are minority)

3. **Type Resolution** - Repeated `AntikytheraRunTime.getTypeDeclaration()` calls
   - **Mitigation:** Runtime cache already exists
   - **Impact:** Minimal

4. **Reflection Operations** - `ReflectionTestUtils.setField()` in generated tests
   - **Impact:** Runtime only, not generation-time

**Recommendation:** No immediate performance optimizations needed; monitor for projects with >5000 classes.

---

## Migration Path for Existing Projects

### For Projects Using Explicit `services` List

**No Changes Required** - Existing configuration works as-is.

```yaml
# Existing config - no migration needed
services:
  - com.example.UserService
  - com.example.OrderService
```

### For Projects Wanting Fallback Mode

**Step 1:** Remove explicit lists
```yaml
# Before
services:
  - com.example.UserService
  - com.example.OrderService
  - ... (50+ services)

# After
# (empty - automatic discovery)
```

**Step 2:** Add skip list for exclusions
```yaml
skip:
  - com.example.legacy         # Skip legacy package
  - com.example.Generated      # Skip generated code
```

**Step 3:** Add include list for rescues
```yaml
include:
  - com.example.dao.impl       # Include DAO implementations
```

**Step 4:** Run and review logs
```
INFO: Fallback discovery under base_package 'com.example': 
      234 type(s) in scope, 47 unit target(s), 
      skip counts: {INTERFACE=56, ENTITY=32, CONTROLLER=12, ...}
```

---

## Known Limitations

1. **Binary-Only Types** - Limited classification for types without source AST
2. **Nested Classes** - Discovery per type, but generation still CU-aware (potential disconnect)
3. **Complex Inheritance** - Setter resolution may fail for deep hierarchies with non-standard naming
4. **Feign / client mocks** - Types whose simple name ends in `Client` default to `RETURNS_DEEP_STUBS`. Exceptions are **configuration-driven** via `plain_mock_dependency_simple_names` in `generator.yml` (not hard-coded project types).
5. **Enum Logic** - Enums always skipped (future: `include_enum_logic: true` opt-in)

---

## Plan: Remove hardcoded `ProblemFeignClient` (config-driven plain mocks)

**Goal:** Delete the special case `!simple.equals("ProblemFeignClient")` from `UnitTestGenerator.applyMockAnnotationForDependencyType` so the generator stays project-agnostic, while **all tests keep passing** and existing POMR-style behavior is preserved through YAML.

### Design

| Concern | Approach |
|--------|----------|
| **No hardcoded type names** | Use `Settings.PLAIN_MOCK_DEPENDENCY_SIMPLE_NAMES` → YAML key `plain_mock_dependency_simple_names` (list of **simple** names only). |
| **Default for `*Client`** | Unlisted clients continue to receive `@Mock(answer = RETURNS_DEEP_STUBS)`. |
| **Regression tests** | `src/test/resources/generator.yml` lists `ProblemFeignClient` so `testProblemFeignClientUsesPlainMocksWhenListedInConfig` (renamed from `testProblemFeignClientUsesPlainMocks`) still expects a plain `@Mock`. |
| **Config-off behavior** | `generator-no-plain-mock-clients.yml` omits the key; `testClientTypesUseDeepStubsWhenPlainMockListUnset` asserts `ProblemFeignClient` gets deep stubs—proves the mechanism, not the old hardcode. |
| **Real projects** | Ship `plain_mock_dependency_simple_names` in project `generator.yml` (see `src/main/resources/generator.yml` example for POMR). |

### Implementation status

The plain-mock configuration work is **done in this repo**. Track it as **section A** in [Implementation checklist](#implementation-checklist) below.

### Migration note for consumers

If you previously relied on the built-in `ProblemFeignClient` exception and your `generator.yml` does **not** yet list it, add:

```yaml
plain_mock_dependency_simple_names:
  - ProblemFeignClient
```

After migration, generated tests for that Feign client match the old failure-path behavior.

---

## Conclusion

The `kitchen-sink` branch delivers **substantial improvements** to test generation quality:

**✅ Wins:**
- Automatic test target discovery (saves manual configuration)
- Intelligent exception handling (reduces flaky tests)
- Robust dependency injection (handles edge cases)
- Better type safety and coercion (fewer runtime failures)
- Comprehensive documentation and testing

**⚠️ Technical Debt:**
- `UnitTestGenerator` SRP violation (~2,300 lines, many intertwined responsibilities)
- Code duplication in type checking and setter resolution
- High cyclomatic complexity in exception handling
- Tight coupling to global state
- Magic strings throughout

**📊 Assessment:**
- **Functionality:** A (Excellent value delivery)
- **Code Quality:** C+ (Works but needs refactoring)
- **Testing:** A- (Comprehensive with minor gaps)
- **Documentation:** A (Excellent)
- **Overall:** B+ (Strong delivery; refactoring backlog documented)

---

## Refactoring roadmap

### Phase 1: Stabilization and verification

Before larger structural refactors, confirm the current tree is healthy:

1. Run the full test suite: `JAVA_HOME=... mvn clean verify` (see `AGENTS.md` for module order).
2. Optionally generate a coverage report (JaCoCo) for a baseline.
3. Keep user-facing docs (README, generator guides) aligned with behavior changes.

---

### Phase 2: Incremental refactor (following sprints)

**Goal:** Shrink and clarify `UnitTestGenerator` using **helpers and collaborators**, not a pattern catalog. Revisit the **Refactoring Recommendations** section above for what is in vs out of scope.

#### Sprint 1: Utilities and constants (Week 1–2)

0. **Configuration hygiene (done)** — `plain_mock_dependency_simple_names` (see **Plan: Remove hardcoded ProblemFeignClient**).

1. **Extract utility types:** `JavaBeansConventions`, `TypeInspector`, `CollectionExpressionAnalyzer`, `TestGenerationConstants` (same as **Refactoring Recommendations**).

2. **Optional small types:** e.g. a `record` wrapping argument map for `handleExceptionResponse` if it improves readability—**not** a framework of strategy interfaces.

3. **Validation:** Full test suite green; refactor-only diffs.

**Estimated effort:** ~3–5 days · **Risk:** low

---

#### Sprint 2: Exception path and long methods (Week 3–4)

1. **Extract `ExceptionResponseAsserter`** (or equivalent): move logic out of `handleExceptionResponse` into one class with private helpers / small outcome enum.

2. **Extract method** on other long methods (`injectMockedFieldsViaReflection`, `mockFieldWithSetter`, etc.) without introducing new abstraction layers.

3. **Validation:** Same tests; optional diff of generated tests on a sample project.

**Estimated effort:** ~5–8 days · **Risk:** medium

---

#### Sprint 3: Mock wiring and optional DI (Week 5–6, as needed)

1. **Package-private collaborators** for mock field discovery and injection if `UnitTestGenerator` is still hard to navigate.

2. **Constructor injection** for selected dependencies **only if** it unlocks real unit tests; skip factory/pipeline hierarchies unless a second shared workflow appears.

3. **Validation:** Regression suite; compare generation time if you change hot paths.

**Estimated effort:** flexible · **Risk:** medium

**Stretch target (not a commitment):** materially shorter `UnitTestGenerator` (~2,300 lines today); exact line target matters less than **lower duplication** and **clear exception/mocking flow**.

---

### Success metrics

| Metric | Before | Target | Measurement |
|--------|--------|--------|-------------|
| **UnitTestGenerator LOC** | ~2,300 | (reduce meaningfully) | `wc -l` |
| **Duplication / complexity** | (measure) | Improve | SonarQube or IDE |
| **Test coverage** | (measure) | Maintain or improve | JaCoCo |
| **Clarity** | Large class | Easier navigation | Code review |

Do **not** use “number of design patterns” as a success metric.

---

### Risk mitigation

**Regression bugs**
- Keep existing tests as regression anchors; run the full suite after each extraction.
- Optionally diff generated output on a fixture project before/after large moves.

**Performance**
- Baseline generation time if you touch hot loops; expect negligible change from pure moves.

**Over-engineering**
- If a change does not remove duplication or shrink a hard method, **do not** add interfaces, chains, or builders “for consistency.”

**Long-running branch divergence**
- Small increments; integrate often.

---

### Why this approach works

1. **Highest leverage first** — utilities remove repeated bugs and noise with minimal structure.
2. **Exception logic isolated** — one place to read and test, without a plugin architecture.
3. **Room to stop** — Sprints 2–3 can be shortened or skipped if the class is already maintainable.

---

## Implementation checklist

**Sections B–E** are the active backlog: mark `[ ]` → `[x]` as you complete work.

**Section A** is different: it lists work **already merged** in this repository (config-driven `plain_mock_dependency_simple_names` instead of a hardcoded `ProblemFeignClient` exception). Those lines stay `[x]` so readers do not treat them as open tasks. If you are using this doc in another fork where that work is not present, treat A as your own todo list and uncheck until done.

### A. Configuration: plain `@Mock` for selected `*Client` types *(completed in this repo)*

- [x] Add `Settings.PLAIN_MOCK_DEPENDENCY_SIMPLE_NAMES` (`plain_mock_dependency_simple_names` in YAML).
- [x] Replace hardcoded `ProblemFeignClient` check in `UnitTestGenerator.applyMockAnnotationForDependencyType` with `Settings.getPropertyList(...)`.
- [x] Add `plain_mock_dependency_simple_names` entries to `src/test/resources/generator.yml` and sample `src/main/resources/generator.yml`.
- [x] Add `generator-no-plain-mock-clients.yml` and tests (`try` / `finally` reload `Settings`) proving config-on vs config-off behavior.
- [x] Document migration for consumers (this file + generator guides as needed).

### B. Sprint 1 — Utilities and constants

- [ ] Introduce `JavaBeansConventions` (or equivalent); switch `setterNameForField` / `getterMethodNameForField` / related call sites.
- [ ] Introduce `TypeInspector` (or extend one helper); replace repeated `List` / `Set` / `Map` raw-name checks in `UnitTestGenerator` (and elsewhere as needed).
- [ ] Introduce `CollectionExpressionAnalyzer` (or single shared helper); align `UnitTestGenerator` and `ExceptionAnalyzer` empty-collection detection where safe.
- [ ] Introduce `TestGenerationConstants`; replace repeated literals (paths, `"Mock"`, placeholders, etc.) without behavior change.
- [ ] Optional: add a small `record` for test-argument maps if it clarifies exception handling call sites.
- [ ] Run `antikythera-test-generator` tests + install `antikythera` first per `AGENTS.md`.

### C. Sprint 2 — Exception path and long methods

- [ ] Extract `ExceptionResponseAsserter` (name TBD): move body of `handleExceptionResponse` behind a thin method on `UnitTestGenerator`.
- [ ] Document side effects (e.g. collection re-seeding) in the new class; keep them explicit in the API or call order.
- [ ] Add or extend unit tests targeting the asserter’s decisions (null/IAE/NPE/suppression paths).
- [ ] Extract method(s) from `injectMockedFieldsViaReflection`, `mockFieldWithSetter`, `normalizeInlineObjectCreationNulls`, `createFieldInitializer` as separate steps; run tests after each extraction.
- [ ] Optional: diff generated tests for a sample project before/after the exception refactor.

### D. Sprint 3 — Mock wiring and optional DI (as needed)

- [ ] If `UnitTestGenerator` is still overloaded: extract package-private `MockFieldSupport` (or similar) for mock field discovery + wiring.
- [ ] If testability requires it: pass `AntikytheraRunTime` / `Settings` (or facades) via constructor for **new** test-focused seams—avoid large factory/pipeline hierarchies.
- [ ] Re-run full module tests and spot-check generation time on a representative project.

### E. Gates (repeat after each merge-ready chunk)

- [ ] `JAVA_HOME=... mvn test` (or `verify`) for `antikythera-test-generator` with `antikythera` installed as required by `AGENTS.md`.
- [ ] No new dependencies introduced without `pom.xml` / license review.
- [ ] User-visible behavior change documented in `README` or `docs/` when configuration or output changes.

---

## Further reading

*Design Patterns* (GoF) and similar references remain useful **vocabulary**, not a checklist. Prefer **Fowler-style refactorings** (extract method/class, move method) until a second real variant forces something richer.

---

**Document Version:** 2.4  
**Last Updated:** April 4, 2026
