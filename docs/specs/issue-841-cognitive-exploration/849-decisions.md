## D1: Test harness architecture

**Choice:** Builder + convenience layer
**Alternatives:**
- Low-level builder — maximum flexibility but verbose per-test code, no convenience methods
- Declarative scenario DSL — most readable but introduces a DSL to maintain alongside the builder
**Rationale:** Fluent builder for component wiring with sensible defaults (in-memory stores, no-op orchestrators). Convenience methods (`tickAndConsolidate`, `renderPrompt`, `observe`) compose common sequences. Tests remain standard JUnit + AssertJ — no custom framework. Follows CognitiveEmergenceTest pattern but broader scope.
**Trade-offs:** More infrastructure than a simple test class (builder + convenience methods), but this is the investment for reusability. A follow-up issue will migrate existing manually-wired tests once the harness is proven.
**Sources:** CognitiveEmergenceTest (caps-testing), RecurringThoughtIntegrationTest, SearchBudgetPathologicalThresholdTest, CuriosityDriveTest
**Exploration:** quick
**Status:** captured

## D2: Consolidation handling

**Choice:** Dual-mode — real phases + injection
**Alternatives:**
- Real phases only — always run consolidation phases, no injection. More realistic but harder to isolate specific behaviors (e.g., testing convergence collapse without needing full recurring thought state)
- Injection only — no consolidation integration, tests inject state directly. Simpler harness but doesn't test the consolidation→tick artifact flow
**Rationale:** `tickAndConsolidate()` runs real consolidation phases (RecurringThoughtPhase, etc.) then CognitionCore.tick(). Tests that need specific state without running full consolidation use injection methods (`setContinuumPosition`, `injectArtifact`). S3 uses real phases to verify escalation; S5 injects ContinuumPosition directly to test convergence collapse.
**Trade-offs:** Two code paths to maintain in the harness, but each is thin — the real path delegates to actual phases, the injection path sets state directly.
**Depends on:** D1 (builder architecture)
**Sources:** ConsolidationMediator, RecurringThoughtPhase, CognitionCore.lastContinuumMap
**Exploration:** quick
**Status:** captured

## D3: Agent personality configuration

**Choice:** Dual-mode — dispositions + direct defaults
**Alternatives:**
- Dispositions only — always derive via CognitiveDerivationEngine. More realistic but less control over exact parameter values; S5 depends on derivation producing the right threshold
- CognitiveDefaults only — skip derivation entirely. Direct control but doesn't test personality→config derivation; S1 would need manually crafted defaults
**Rationale:** Builder accepts DispositionAxes (derives CognitiveDefaults via CognitiveDerivationEngine for end-to-end tests) OR CognitiveDefaults directly (for precise parameter control). S1 uses dispositions to test personality differentiation end-to-end; S5 specifies SearchBudgetConfig directly.
**Trade-offs:** Two configuration paths in the builder, but each maps cleanly to existing APIs (CognitiveDerivationEngine vs CognitiveDefaults.withXxx).
**Depends on:** D1 (builder architecture)
**Sources:** CognitiveDerivationEngine, CognitiveDefaults, CognitiveDefaultsRegistry.forTesting()
**Exploration:** quick
**Status:** captured
