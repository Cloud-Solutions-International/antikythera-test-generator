# Full-Project Test Discovery — Implementation Checklist

Work items are ordered by dependency: each group should be completed before the next begins.
See [full-project-discovery-plan.md](full-project-discovery-plan.md) for detailed specification.

### Module Boundary Rule
- [x] **Put core engine changes in `antikythera`** — preprocessing, expression-evaluation behavior, type resolution, runtime metadata, and reusable analysis helpers belong in core.
- [x] **Keep generation-only code in `antikythera-test-generator`** — target selection orchestration, fallback mode handling, and generated-test emission behavior stay in generator.

### Phase 0 — Audit and expose existing infrastructure
- [x] Make `AbstractCompiler.shouldSkip(String)` callable from the classifier path (public wrapper or visibility change).
- [x] Use `EntityMappingResolver.isEntity(TypeWrapper)` for `@Entity` detection only; add separate checks for `@Embeddable`, `@MappedSuperclass`, and `@IdClass`.
- [x] Confirm `AntikytheraRunTime.getResolvedTypes()` is fully populated after preprocess.
- [x] Prefer `BaseRepositoryParser.isJpaRepository(TypeWrapper|TypeDeclaration<?>)` for Spring Data repository detection; use `AntikytheraRunTime.findImplementations(String)` only as supporting metadata if needed.
- [x] Add `isRepository` and `isConfiguration` flags to `TypeWrapper` and populate in `AbstractCompiler.processType()`.

### Phase 1 — Core model (no behavior change yet)
- [x] Add `SkipReason` enum.
- [x] Add `ClassificationDecision` enum (`UNIT_TARGET`, `SKIP`).
- [x] Add `ClassificationResult` (decision + reason + explanation).

### Phase 2 — `TargetClassifier`
- [ ] Create `TargetClassifier` with `ClassificationResult classify(TypeWrapper)`.
- [ ] Implement structural skips (`annotation/feign-interface/interface/enum/record/abstract`) with explicit rule ordering so `FEIGN_CLIENT` is not swallowed by generic `INTERFACE`.
- [ ] Implement web-layer skips (`controller/advice/ResponseEntityExceptionHandler`).
- [ ] Implement persistence skips (entity-family + Spring Data repo interfaces).
- [ ] Implement bootstrap skips (`configuration/config-props/boot app/main`).
- [ ] Implement AOP skip (`@Aspect`).
- [ ] Implement constant/property-holder skip.
- [ ] Implement pure-exception skip.
- [ ] Implement data-carrier detection (behavior first, then annotation/name heuristics).
- [ ] Implement positive `UNIT_TARGET` pass-through rules.

### Phase 3 — `TargetClassifier` unit tests
- [ ] Cover all `SKIP` reasons.
- [ ] Cover all key `UNIT_TARGET` categories.
- [ ] Add edge cases (`*Dto` with logic, repository impl with real logic, pass-through delegation-only repository).

### Phase 4 — Config overrides
- [ ] Reuse `skip` list from `Settings` in fallback mode.
- [ ] Add optional `include` list to `Settings`.
- [ ] Apply override precedence: auto rules first, then `include`, then `skip` (skip wins).
- [ ] Add tests for `skip` and `include` override behavior.

### Phase 5 — Discovery orchestration in `Antikythera`
- [ ] Add `isFallbackMode()` helper (both `services` and `controllers` absent/empty).
- [ ] Add `discoverUnitTargets()` using resolved types + classifier.
- [ ] Wire fallback into unit generation flow.
- [ ] Keep discovery/classification per-type, but make generation remain compilation-unit aware so nested discovered types do not assume standalone test output without extra orchestration.
- [ ] Add INFO summary and DEBUG per-class decision logs.

### Phase 6 — Integration tests
- [ ] **Create or extend fixtures in `antikythera-test-helper`** with all class shapes from plan section “Integration Fixture Tests”; do not place fixtures under `antikythera-test-generator/src/test/resources`.
- [ ] Verify only intended target classes produce generated tests.
- [ ] Add mode-selection integration tests for all 4 config combinations.

### Phase 7 — Validation and documentation
- [ ] Validate against `csi-ehr-opd-patient-pomr-java-sev` with fallback mode enabled.
- [ ] Record selected/ignored classes and skip-reason counts.
- [ ] Update `generator.yml` docs for fallback and override semantics.
- [ ] Ensure module-boundary note is reflected in contributor docs.
