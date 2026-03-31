# Evaluator & test generator improvement plan

This document captures the analysis behind flaky or failing generated tests (e.g. Spring services using config DTOs, DAOs, Gson, Jackson, and `Long.parseLong` on string fields), and a **project-agnostic** plan to improve the **expression evaluation engine** (`antikythera`) and the **test generator** (`antikythera-test-generator`).

**Principle:** Application-specific types and paths belong in **`generator.yml`** (and the codebase under test), not in hardcoded FQCNs inside the engine.

---

## 1. Recurring patterns (from representative service code)

| Pattern | What goes wrong |
|--------|------------------|
| **Config / typed getters** — `get*(…, SomeDto.class)` then `getRelease()` / `getMap()` and `containsKey` / `get` without null-checks | Symbolic values often use **minimal** DTOs with **null** nested maps; tests mock config and still get **NPE** on `getRelease()`. |
| **String IDs + `Long.parseLong`** | Dummy placeholder strings are not numeric → **NumberFormatException** at runtime. |
| **Gson `toJson` / rich graphs** | Evaluator may predict Gson failures; Mockito-heavy runs may succeed or fail earlier — **assertion mismatch**. |
| **Validation / catch + custom runtime exceptions** | One branch **throws**; another **completes**; generated **`assertThrows`** does not match the path the test actually exercises. |
| **DAO / `Page` / nullable returns** | Symbolic **null** vs **`RETURNS_DEEP_STUBS`** non-null proxies → **`assertNull(resp)`** fails. |
| **ObjectMapper + Mockito proxies** | Jackson may traverse **mock internals** → serialization **errors**. |
| **Optional / Envers / `EntityManager`** | Partial evaluation (`isPresent`, etc.) can derail or skip methods. |

---

## 2. Root causes (engine vs generator)

| Layer | Issue |
|-------|--------|
| **Symbolic values** | Nested **Map/List** fields on synthetic beans left **null** though code **dereferences** them. |
| **Literals** | **String** dummies not constrained by use in **`parseLong`/`parseInt`**. |
| **Branches** | Exception vs non-exception paths **not aligned** with the **single** generated test per method. |
| **Mocks vs symbolic null** | **`assertNull`** from symbolic null while runtime uses **deep stubs** → non-null. |
| **Libraries** | Gson / Jackson / Mockito behavior differs from **pure AST** execution. |

---

## 3. Plan: expression evaluation engine (`antikythera`)

### Phase A — Safer default values (generic)

- **A.1** — For beans returned from **generic typed getters** (e.g. `get*(…, Class<T>)`), initialize **empty** `Map` / `List` fields when the method body (or a shallow callee) calls getters that imply immediate use (`getRelease()`, `getX()` returning `Map`/`List`). Prefer **JavaBeans / field types** or light AST inspection — **no application FQCNs**.
- **A.2** — **Numeric-looking string literals** when dataflow (or pattern match) shows the string flows into **`Long.parseLong`**, **`Integer.parseInt`**, etc. (extend **`Reflect` / variable factories**).
- **A.3** — Harden **`Optional`** evaluation (`isPresent` / `get`) where it currently blocks full method evaluation.

### Phase B — Repository & pagination consistency

- **B.1** — Align **null vs non-null** DAO results with existing **branching** (e.g. `stubEntityReturnWithBranching`) across **all** relevant repository call shapes.
- **B.2** — For **`Page`**, **`List`**, and nullable entity returns, ensure **`MethodResponse`** reflects the branch the generated test will execute, or mark **uncertainty** (see Phase D).

### Phase C — JSON / mapping

- **C.1** — For **`Gson.toJson`** / **`ObjectMapper.convertValue`**, prefer **evaluator-built** instances over mocks for arguments where possible, or attach **low-confidence** metadata for assertions.
- **C.2** — Avoid emitting tests that pass **pure mocks** into Jackson where the engine detects **serialization risk**.

---

## 4. Plan: test generator (`antikythera-test-generator`)

### Phase D — Assertions tied to confidence

- **D.1** — Introduce **confidence / strictness** on **`MethodResponse`** (e.g. high vs low). For **low confidence** or **mock-heavy** return types (`Page`, interfaces), avoid strict **`assertNull(resp)`**; prefer **`assertNotNull`**, **`assertDoesNotThrow`**, or **no return assertion** — driven by **types + flags**, not hardcoded class names.
- **D.2** — For **throw vs complete** branches, generate **multiple** tests when the truth table has both, or emit **`assertDoesNotThrow`** when the chosen path does not throw (align with **branching** state).
- **D.3** — Keep **Gson**-related handling in **`JunitAsserter`** as **third-party library** behavior, or fold into a general **serialization confidence** flag from the evaluator.

### Phase E — Configuration only for project-specific needs

- **E.1** — Optional **`generator.yml`** knobs: e.g. **`assertion_strictness`**, or **declarative post-generation** replace rules as **data** (not string literals in Java).

---

## 5. Suggested implementation order

1. **A.1** + **A.2** (nested empty collections + numeric string literals) — largest impact on NPE / parse failures.
2. **D.1** (confidence-aware return assertions) — fixes **Page / null** vs deep stubs without naming types.
3. **B.1** / branching alignment for DAOs.
4. **D.2** (exception vs complete branch tests).
5. **C.1** / **C.2** (ObjectMapper / Jackson safety).

---

## 6. Checklist

Use this list to track work; check items when merged and covered by tests where feasible.

### Engine (`antikythera`)

- [x] **A.1** Empty `Map`/`List`/`Set` for fields **without initializers** (avoids NPE on immediate `getMap()` / `containsKey` chains; no app FQCNs)
- [x] **A.2** Reflective `Long.parseLong` / `Integer.parseInt`: non-numeric string args coerced to parseable `"1"` (placeholder strings like default `"Antikythera"` no longer throw)
- [x] **A.3** Empty **`Optional`**: `handleOptionalEmpties` short-circuits `isPresent` → `false`, `isEmpty` → `true`, `orElse` → evaluated default, `orElseGet` → lambda/supplier; `orElseThrow` unchanged. Tests: `TestOptional` + `Opt` helpers (`emptyOptionalIsPresent` / `IsEmpty` / `OrElseString`).
- [x] **B.1** Repository null/non-null branching consistent for main DAO call patterns (`stubEntityReturnWithBranching` uses `Branching.add` + `TRUE_PATH` like list/collection stubs; `Page` return uses the same two-pass queue pattern)
- [x] **B.2** `Page` / pagination paths aligned with `MethodResponse` or marked uncertain (`inferReturnAssertionConfidence` forces **LOW** for `Page` / `Slice`; Spring Data `Page` repository returns use `PageImpl` / `Page.empty` branching in `MockingEvaluator`)
- [x] **C.1** Gson / `ObjectMapper` paths: prefer real eval instances or mark low confidence (`MockReturnValueHandler` unchanged; `GeneratorState` + `MethodResponse#setSerializationConfidence` when evaluation marks risk)
- [x] **C.2** Detect high-risk Jackson arguments (mock proxies) and adjust generation or confidence (`Evaluator.maybeMarkSerializationRiskFromMocks` on `toJson` / `writeValue*` / `convertValue` with a Mockito first argument)

### Test generator (`antikythera-test-generator`)

- [x] **D.1** `MethodResponse` **`AssertionConfidence`** (reserved for future use; inferred in `SpringEvaluator`). **Generated tests do not use `assertNull`/`assertNotNull` as the only return check** — symbolic **null** → `assertDoesNotThrow` (or invocation + output assert); opaque non-null values with no field/output asserts → `assertDoesNotThrow` instead of lone `assertNotNull`.
- [x] **D.2** Multiple tests or aligned assertions for throw vs non-throw branches (one generated test per `SpringEvaluator.visitCallable` branch iteration; non-throwing paths use `assertDoesNotThrow` / side-effect asserts via `UnitTestGenerator`; serialization-low paths avoid strict `assertThrows` — see **D.3**)
- [x] **D.3** Gson / serialization assertion policy unified with evaluator flags (`JunitAsserter` uses `SerializationConfidence.LOW` + existing Gson class-name heuristics; `assertion_strictness: strict` disables softening)
- [x] **E.1** Optional `generator.yml` schema for strictness and/or declarative post-processing (`Settings.ASSERTION_STRICTNESS` — `lenient` default, `strict` forces `assertThrows` when an exception was recorded)

### Validation

- [x] Unit tests for new evaluator behaviors: `TestEvaluator` / `TestAKBuddy` updated for empty collections; `TestReflect` covers numeric-parse coercion
- [x] Generator tests for assertion selection (`UnitTestGeneratorTest` null + HIGH/LOW); core `MethodResponseTest` for confidence inference
- [ ] Regenerate sample / fixture projects and confirm **`mvn test`** still passes
- [x] Optional: re-run against an external Spring service module (config-driven) and record pass/fail per service class — use `antikythera-test-generator/scripts/validate_external_services.py` (see `docs/configurations.md` § *External Spring module validation*)

---

## 7. References

- Internal: `antikythera-test-generator/docs/unit-test-generation-sequence.md`
- Related components: `MockReturnValueHandler`, `MockingEvaluator.stubEntityReturnWithBranching`, `Reflect.createVariable` / `variableFactory`, `UnitTestGenerator.noSideEffectAsserts`, `JunitAsserter.assertThrows`

---

*Last updated: 2026-03-29 (external service validation script + doc)*
