Antikythera Test Generator
==========================

**Automated Unit, Integration and API Test Generation for Java/Spring Applications**

`antikythera-test-generator` is a Maven module built on top of the [Antikythera](https://github.com/Cloud-Solutions-International/antikythera)
core library. It applies Antikythera's Java AST evaluation and expression engine to automatically produce
test suites — JUnit/TestNG unit tests, Mockito-backed service tests, and RESTAssured API tests — with minimal
manual effort.

---

## What It Does

### Unit Test Generation
Generates JUnit 5 tests for Spring service classes:

- **Full Branch Coverage**: Iteratively executes all code paths (up to 16 branches per method) and generates a dedicated test for each.
- **Mockito Scaffolding**: Automatically identifies injected dependencies and emits `@Mock`, `@InjectMocks`, and `when(...).thenReturn(...)` setup.
- **Smart Assertions**: Derives `assertEquals`, `assertTrue`, `assertNull` etc. from observed return values and side effects (logging, `System.out`).
- **Constructor Tests**: Optionally generates tests for constructors that have observable side effects.
- **Void Method Support**: Detects and asserts on side effects even when there is no return value.

### API / Spring MVC Test Generation
Generates RESTAssured or `MockMvc` tests for `@RestController` classes:

- Maps path variables and query parameters into typed test arguments.
- Generates one test per HTTP method + path combination.
- Handles `@PathVariable`, `@RequestParam`, and `@RequestBody` parameters.

### TestNG Support
All generated tests can target TestNG instead of JUnit 5 via configuration.

---

## How It Works

The generation pipeline follows three stages:

```
Spring Source Code
      │
      ▼
[ServicesParser / RestControllerParser]   ← parse & pre-process all classes
      │
      ▼
[SpringEvaluator + Branching]             ← symbolically execute each method,
      │                                     recording branch conditions
      ▼
[UnitTestGenerator / SpringTestGenerator] ← emit JUnit/TestNG test methods
      │                                     with mocks, preconditions, assertions
      ▼
  Generated Test Files
```

See [docs/unit-test-generation-sequence.md](docs/unit-test-generation-sequence.md)
for a detailed Mermaid sequence diagram of the full unit-test flow.

---

## Module Structure

```
antikythera-test-generator/
├── src/main/java/.../
│   ├── generator/
│   │   ├── Antikythera.java          — CLI entry point; orchestrates full generation run
│   │   ├── Factory.java              — creates the right TestGenerator for "unit" / "api" / "integration"
│   │   ├── TestGenerator.java        — abstract base; name deduplication, duplicate removal, save()
│   │   ├── UnitTestGenerator.java    — generates JUnit service-level tests
│   │   ├── SpringTestGenerator.java  — generates RESTAssured / MockMvc tests for controllers
│   │   ├── Asserter.java             — abstract assertion strategy
│   │   ├── JunitAsserter.java        — JUnit 5 assertion implementation
│   │   ├── TestNgAsserter.java       — TestNG assertion implementation
│   │   └── ControllerRequest.java    — models an HTTP request being generated
│   └── parser/
│       ├── ServicesParser.java       — entry point for service-class test generation
│       └── RestControllerParser.java — entry point for REST controller test generation
└── src/test/java/...                 — ~158 tests covering generators, parsers, asserters
```

---

## Getting Started

### Requirements

- Java 21
- Maven
- The `antikythera` core library installed locally (`mvn install` in `../antikythera/`)
- VM argument: `--add-opens java.base/java.util.stream=ALL-UNNAMED`

### Build

```bash
# First, install the core library
cd ../antikythera
mvn install -Dmaven.test.skip=true

# Then build and test the generator
cd ../antikythera-test-generator
mvn test
```

### Running the Generator

Point it at your project via a `generator.yml` config file, then run:

```bash
mvn exec:java
```

Or directly:

```bash
mvn exec:java -Dexec.mainClass="sa.com.cloudsolutions.antikythera.generator.Antikythera"
```

---

## Configuration (`generator.yml`)

```yaml
base_package: com.example                 # Base package of the project under test
base_path: /path/to/project/src/main/java
output_path: /path/to/project/src/test/java

# Which classes to process
controllers:
  - com.example.controller.UserController
services:
  - com.example.service.UserService

# Test framework
test_framework: junit                     # junit | testng
mock_with: Mockito

# Optional: skip void methods with no side effects (default: true)
skip_void_no_side_effects: true

# Optional: generate tests for constructors (default: false)
generate_constructor_tests: false

# Optional: base class every generated test extends
base_test_class: com.example.BaseTest

# Optional: extra imports added to every generated test class
extra_imports:
  - com.example.util.TestConstants
```

See [antikythera/docs/configurations.md](../antikythera/docs/configurations.md) for the full reference.

---

## Relationship to `antikythera` Core

`antikythera-test-generator` depends on `antikythera` for:

| Core Feature | Used By |
| :--- | :--- |
| `AbstractCompiler` / `AntikytheraRunTime` | Parsing and type resolution |
| `Evaluator` / `SpringEvaluator` | Symbolic execution and branch exploration |
| `GeneratorState` | Shared static state (mock imports, `when/then` chains) |
| `ITestGenerator` | Interface wired into `SpringEvaluator` without circular dependency |
| `MethodResponse`, `TruthTable`, `TypeWrapper` | Shared model types |

The core library deliberately has **no dependency** on this module — it communicates only through
the `ITestGenerator` interface and `GeneratorState`.

---

## Development & Testing

The test suite covers generators, asserters, parsers, and evaluator integration:

```
src/test/java/
├── evaluator/   TestFields, TestQueries, TestSpringEvaluator
├── finch/       TestFinch
├── generator/   UnitTestGeneratorTest, TestSpringGenerator, JunitAsserterTest,
│                FactoryTest, SideEffectTest, ProjectGeneratorTest, TestTruthTable,
│                BaseRepositoryQueryTest, TestTestGenerator
└── parser/      RestControllerParserTest, ServicesParserTest
```

Tests rely on:
- [antikythera-test-helper](https://github.com/Cloud-Solutions-International/antikythera-test-helper) — sample entities, services, controllers
- [antikythera-sample-project](https://github.com/Cloud-Solutions-International/antikythera-sample-project) — realistic Spring Boot project for integration tests

---

## License

See [LICENSE.txt](../antikythera/LICENSE.txt) for details.
