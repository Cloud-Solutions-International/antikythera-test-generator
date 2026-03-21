# Antikythera Test Generator — Agent Reference Guide

**Purpose**: Generate unit, integration, and API tests for Java/Spring applications using the Antikythera core evaluation engine.

---

## Architecture Overview

```
antikythera (core)                    antikythera-test-generator
──────────────────────────────────    ──────────────────────────────────────
parser/AbstractCompiler               parser/ServicesParser
parser/AntikytheraRunTime             parser/RestControllerParser
evaluator/Evaluator                   generator/TestGenerator  (abstract)
evaluator/SpringEvaluator  ──────►   generator/UnitTestGenerator
evaluator/ITestGenerator   (iface)    generator/SpringTestGenerator
evaluator/GeneratorState   (shared)   generator/Factory
generator/MethodResponse              generator/Asserter
generator/TypeWrapper                 generator/JunitAsserter
                                      generator/TestNgAsserter
                                      generator/ControllerRequest
                                      generator/Antikythera  (CLI)
```

`SpringEvaluator` holds a `List<ITestGenerator>`. At each method exit point it calls
`generator.createTests(md, response)`. The concrete generators live here; the core has no
dependency on them.

Shared mutable state (Mockito `when/then` chains, extra imports) is held in
`evaluator/GeneratorState` (core) and accessed via `TestGenerator`'s static delegates.

---

## Key Classes

### `Antikythera` — CLI entry point
Singleton. Reads `generator.yml`, pre-processes the classpath, then calls
`generateUnitTests()` and/or `generateApiTests()`.

```java
Antikythera.getInstance().generateUnitTests();
Antikythera.getInstance().generateApiTests();
```

### `Factory` — TestGenerator factory
Creates the right generator type and caches it per class. The `type` argument is one of
`"unit"`, `"api"`, or `"integration"`.

```java
TestGenerator gen = Factory.create("unit", compilationUnit);
```

### `TestGenerator` — abstract base
- Manages test method name deduplication (`testMethodNames` set).
- `buildTestMethod(md)` scaffolds a `@Test` method with Javadoc.
- `removeDuplicateTests()` deduplicates by fingerprinting method bodies.
- `save()` calls `removeDuplicateTests()` then delegates to subclass.
- Static methods `addImport`, `addWhenThen`, `getWhenThen`, `clearWhenThen` delegate to `GeneratorState`.

### `UnitTestGenerator` — service-level unit tests
Generates Mockito-backed JUnit 5 / TestNG tests. Key internal steps inside `createTests()`:
1. `createInstance()` — emits `new ServiceClass(...)` or `@InjectMocks`
2. `mockArguments()` — emits `@Mock` field declarations
3. `applyPreconditions()` — sets up field state required to reach the branch
4. `addWhens()` — emits `Mockito.when(...).thenReturn(...)` from `GeneratorState.getWhenThen()`
5. `invokeMethod()` — emits the method call under test
6. `addAsserts(response)` — emits assertions on the return value or captured output

### `SpringTestGenerator` — REST API tests
Generates RESTAssured tests for `@RestController` endpoints. Uses `ControllerRequest` to
model each HTTP call (method, path, headers, body) and emits a fluent RESTAssured chain.

### `Asserter` / `JunitAsserter` / `TestNgAsserter`
Strategy pattern for assertion style. `JunitAsserter` emits `assertEquals`, `assertTrue`,
`assertNull`, `assertThrows` etc. Swap with `TestNgAsserter` via config `test_framework: testng`.

```java
gen.setAsserter(new JunitAsserter());
```

`JunitAsserter.addFieldAsserts(response, block)` iterates over Lombok/POJO field values
observed during evaluation and appends getter-based assertions.

### `ServicesParser` — entry point for service tests
Iterates configured service classes, creates `UnitTestGenerator + SpringEvaluator`, drives
the branch-coverage loop, and writes files.

### `RestControllerParser` — entry point for API tests
Iterates configured controllers, maps endpoints to `ControllerRequest` objects, drives
`SpringTestGenerator`, and writes files.

---

## Generation Pipeline (Unit Tests)

```
ServicesParser.evaluateMethod(md)
  └─ Factory.create("unit", cu)         → UnitTestGenerator
  └─ EvaluatorFactory.create(cls)       → SpringEvaluator
  └─ evaluator.addGenerator(gen)
  └─ evaluator.visit(md)                ← starts branch-coverage loop
       loop (up to 16 iterations):
         evaluator.executeMethod(md)
           └─ records branch conditions into Branching
           └─ at return stmt: calls gen.createTests(md, response)
                └─ UnitTestGenerator.buildTestMethod()
                └─ mockArguments / applyPreconditions / addWhens
                └─ invokeMethod + addAsserts
         currentConditional.transition()  ← mark path as travelled
       end loop
  └─ generator.save()                   → writes .java file
```

---

## Configuration Keys (generator.yml)

| Key | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `base_package` | string | — | Root package of the project under test |
| `base_path` | string | — | Absolute path to `src/main/java` |
| `output_path` | string | — | Where to write generated test files |
| `controllers` | list | — | FQN list of `@RestController` classes |
| `services` | list | — | FQN list of service classes |
| `test_framework` | string | `junit` | `junit` or `testng` |
| `mock_with` | string | `Mockito` | Mocking framework |
| `base_test_class` | string | — | Super-class for every generated test |
| `extra_imports` | list | — | Additional imports added to each generated file |
| `skip_void_no_side_effects` | boolean | `true` | Skip void methods with no observable effects |
| `generate_constructor_tests` | boolean | `false` | Generate tests for constructors |
| `log_appender` | string | — | FQN of `LogAppender` for log assertion support |

---

## Common Tasks

### Generate unit tests for a service class
```java
// In your driver / main:
Settings.loadConfigMap(new File("generator.yml"));
AbstractCompiler.preProcess();
Antikythera.getInstance().generateUnitTests();
```

### Generate API tests for a controller
```java
Antikythera.getInstance().generateApiTests();
```

### Add assertions based on field values (Lombok / POJOs)
```java
JunitAsserter asserter = new JunitAsserter();
MethodResponse mr = new MethodResponse();
mr.setBody(new Variable(evaluator));   // evaluator has run the method
BlockStmt block = new BlockStmt();
asserter.addFieldAsserts(mr, block);
// block now contains assertEquals calls for each observable field
```

### Plug a custom asserter
Implement `Asserter`, then:
```java
Factory.create("unit", cu).setAsserter(new MyCustomAsserter());
```

### Hook in a custom generator (Finch)
Place a `Finch` implementation JAR on the classpath. The core `Evaluator` calls
`Finch.loadFinches()` at startup; your finch can register additional `ITestGenerator`
instances via `SpringEvaluator.addGenerator()`.

---

## State Management

| State | Location | Lifetime |
| :--- | :--- | :--- |
| `when/then` mock chains | `GeneratorState.whenThen` | Cleared per method execution (`clearWhenThen()`) |
| Extra imports | `GeneratorState.imports` | Cleared between files (`clearImports()`) |
| Test method names | `TestGenerator.testMethodNames` | Per generator instance (one per class) |
| Branch conditions | `Branching` (core) | Cleared between service methods (`Branching.clear()`) |

---

## Agent Cheat Sheet

| Task | Class | Method |
| :--- | :--- | :--- |
| Start full generation run | `Antikythera` | `getInstance().generateUnitTests()` |
| Create a generator for a CU | `Factory` | `create("unit" \| "api", cu)` |
| Build a test method stub | `TestGenerator` | `buildTestMethod(md)` |
| Add a mock import | `TestGenerator` | `addImport(importDecl)` |
| Record a when/then | `TestGenerator` | `addWhenThen(expr)` |
| Emit assertions on fields | `JunitAsserter` | `addFieldAsserts(mr, block)` |
| Emit assertThrows | `TestGenerator` | `assertThrows(invocation, response)` |
| Remove duplicate tests | `TestGenerator` | `removeDuplicateTests()` |
| Write test file to disk | `TestGenerator` | `save()` |

---

## Extending the Generator

- **New assertion style**: extend `Asserter`, set via `gen.setAsserter(...)`.
- **New test type**: extend `TestGenerator`, implement `createTests()` and `addBeforeClass()`, register in `Factory`.
- **Pre/post hooks**: implement `Finch` and place on the classpath; the core engine discovers it automatically.
