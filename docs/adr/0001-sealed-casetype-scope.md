# 0001 — Sealed CaseTypeScope for Cross-Type CBR Retrieval

Date: 2026-09-07
Status: Accepted

## Context and Problem Statement

`CbrQuery.caseType` was a required `String` field (`Objects.requireNonNull`). Engine needs cross-type retrieval (#288) to compare outcomes across all case definitions. Making caseType nullable was considered but found unsafe — `ConcurrentHashMap.get(null)` throws NPE in three decorator/store locations, and `stored.caseType().equals(null)` silently filters all candidates.

## Decision Drivers

* Safety — every code path that consumes caseType must explicitly handle cross-type
* Compile-time enforcement — missing handling should be a compile error, not a runtime NPE
* Decorator transparency — existing decorator chain must work without per-decorator changes

## Considered Options

* **Option A** — Nullable `String caseType` (null = "all types")
* **Option B** — Sealed `CaseTypeScope` interface (Specific | AllInDomain)
* **Option C** — Separate `retrieveAcrossTypes()` SPI method

## Decision Outcome

Chosen option: **Option B — Sealed CaseTypeScope**, because exhaustive switch expressions catch missing handling at compile time. The adversarial decision review verified three `ConcurrentHashMap.get(null)` NPE sites and one `.equals(null)` silent-filtering bug that Option A would have introduced.

### Positive Consequences

* Missing switch cases are compile errors — impossible to forget cross-type handling
* `CbrQuery.caseType()` convenience accessor throws for AllInDomain, catching missed migrations
* Factory methods (`crossType()` vs `of()`) make intent explicit at construction

### Negative Consequences / Tradeoffs

* Larger migration than nullable — every `query.caseType()` call must change to pattern matching on `caseTypeScope()`
* One new sealed interface with two variants added to the API surface

## Pros and Cons of the Options

### Option A — Nullable String caseType

* Good, because minimal API change — just remove `requireNonNull`
* Bad, because `ConcurrentHashMap.get(null)` throws NPE in TrendEnrichment, Qdrant, InMemory stores
* Bad, because `stored.caseType().equals(null)` silently returns false, filtering all candidates
* Bad, because null semantics are ambiguous — "not set" vs "intentionally all types"

### Option B — Sealed CaseTypeScope

* Good, because exhaustive switch expressions — compile-time enforcement
* Good, because no NPE risk — pattern matching eliminates null paths
* Good, because self-documenting — Specific vs AllInDomain is semantically clear
* Bad, because more code to migrate — all `query.caseType()` callers must change

### Option C — Separate SPI method

* Good, because no changes to existing `retrieveSimilar` path
* Bad, because N decorators need new method or default impl — maintenance burden
* Bad, because dual code paths that must stay in sync

## Links

* [#288](https://github.com/casehubio/neocortex/issues/288) — CbrQuery.caseType should be optional
* [engine#1055](https://github.com/casehubio/engine/issues/1055) — cross-case-type CBR retrieval (blocked)
* specs/issue-288-cross-type-cbr-query/decisions.md — D1 decision record
