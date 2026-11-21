# Retrieval tracking for Spring Boot — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot applications get neocortex's SQLite retrieval tracker, retention purge and `CaseRetriever` tracking, with Quarkus behaviour unchanged.

**Architecture:** Split `rag-tracking` into a plain-Java `rag-tracking-core`, a Quarkus CDI wiring module (`rag-tracking`) and a Spring auto-configuration module (`rag-tracking-spring`); then use it from casehub-langchain4j (#20).

**Tech Stack:** Java 21, Maven, Quarkus 3.39.3 (ArC), Spring Boot 4.1.1, SQLite + HikariCP + Flyway (`sqlite-support`), Jackson 2 databind.

**Spec:** `docs/specs/issue-1200-rag-tracking-spring/2026-11-21-rag-tracking-spring-design.md`

## Global Constraints

- Build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn …` (neocortex); `mvn` not `./mvnw`.
- Package of moved classes stays `io.casehub.neocortex.rag.tracking`; Quarkus wiring in `…tracking.quarkus`; Spring in `…tracking.spring`.
- Property names unchanged: `casehub.rag.tracking.enabled`, `casehub.rag.tracking.sqlite.path`, `…sqlite.pool.max-size` (5), `…sqlite.busy-timeout-ms` (5000), `casehub.rag.tracking.retention.days` (90).
- Migrations stay at `classpath:db/rag-tracking/migration`, files unchanged.
- `rag-tracking-core` has no Quarkus, Spring or Jandex.
- Commits reference `Refs #1200` (neocortex) / `Closes #20` (casehub-langchain4j); trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; stage by explicit path.
- casehub-langchain4j: never stage Quill/`.claude`/`.codex`/`.mcp.json`/`.mvn`/`AGENTS.md`/CLAUDE.md edits.

## Review Focus

1. Existing SQLite files from the CDI tracker open with the new core tracker (same Flyway history) — expect no migration error and old rows readable.
2. A Spring app with `enabled=true` and an application-defined `CaseRetriever` that is already a `TrackingCaseRetriever` — must not be double-wrapped.
3. Spring context shutdown closes the Hikari pool and stops the purge thread (no leaked non-daemon threads).
4. Quarkus app with `enabled` unset: no decorator, no scheduler, tracker still a bean exactly as before.
5. casehub-langchain4j Spring app with both its tracking module and neocortex's: a hybrid retrieval is recorded once (neocortex `_trackingId` carried to `retrieval_id`), not twice.

---

### Task 1: rag-tracking-core and Quarkus wiring

**Files:**
- Create: `rag-tracking-core/pom.xml`
- Move (`git mv`) to `rag-tracking-core/src/main/java/io/casehub/neocortex/rag/tracking/`: `SqliteRetrievalTracker.java`, `TrackingLogic.java`, `TrackingCaseRetriever.java`, `RetentionScheduler.java`; resources `db/rag-tracking/migration/*` to `rag-tracking-core/src/main/resources/`
- Move tests to `rag-tracking-core/src/test/java/…/tracking/`: `SqliteRetrievalTrackerTest`, `TrackingLogicTest`, `TrackingCaseRetrieverTest`, `RetentionSchedulerTest`
- Create: `rag-tracking/src/main/java/io/casehub/neocortex/rag/tracking/quarkus/{TrackingProducers,TrackingCaseRetrieverDecorator,RetentionSchedulerBean}.java`
- Create: `rag-tracking/src/test/java/io/casehub/neocortex/rag/tracking/quarkus/TrackingCaseRetrieverDecoratorTest.java`
- Modify: `rag-tracking/pom.xml` (depend on core; keep `quarkus-arc`, `quarkus-jackson`, jandex), root `pom.xml` (module `rag-tracking-core` before `rag-tracking`; dependencyManagement entry)

**Interfaces:**
- Produces: `public SqliteRetrievalTracker(String path, int maxPoolSize, int busyTimeoutMs, ObjectMapper objectMapper)`, `close()`; `public TrackingCaseRetriever(CaseRetriever, RetrievalTracker, Consumer<RetrievalRecorded>)`, `public CaseRetriever delegate()`; `public RetentionScheduler(RetrievalTracker, int retentionDays)`, `public void start()`, `public void stop()`, package-private `void purge()`, package-private `boolean started()`.

- [ ] **Step 1: Move files and adapt tests to constructors (RED).** `git mv` sources, migrations and tests. Tests: `SqliteRetrievalTrackerTest` builds `new SqliteRetrievalTracker(":memory:", 1, 5000, new ObjectMapper())`; `RetentionSchedulerTest` uses `new RetentionScheduler(tracker, 90)`. Add to `SqliteRetrievalTrackerTest`: `twoTrackersOnSameFile_shareData` (temp file; record with first, close, second reads the record via `findRecords`) and `close_twice_isSafe`. Add to `RetentionSchedulerTest`: `start_withZeroDays_startsNothing` (`started()` false) and `start_withPositiveDays_startsAndStops` (`started()` true, `stop()` then no exception).
- [ ] **Step 2: Run** `mvn -q test -pl rag-tracking-core -am` — Expected: compile failure (constructors missing).
- [ ] **Step 3: Implement core.** Constructor bodies do what `init()` did; `close()` idempotent; drop CDI annotations/imports; `TrackingCaseRetriever` keeps only the plain constructor (now public) plus `delegate()`; `RetentionScheduler` takes constructor args. Core pom deps: `rag-api`, `sqlite-support`, `jackson-databind`, `jboss-logging`; test: `rag-testing`, junit, assertj.
- [ ] **Step 4: Run** `mvn -q test -pl rag-tracking-core -am` — Expected: PASS (contract test 20 + new tests).
- [ ] **Step 5: Quarkus wiring.** `TrackingProducers` (`@ApplicationScoped` holder) with `@Produces @Singleton @Typed({SqliteRetrievalTracker.class, RetrievalTracker.class})` using `@ConfigProperty` params and injected `ObjectMapper`, `@Disposes` → `close()`. `TrackingCaseRetrieverDecorator` `@Decorator @Priority(40) @IfBuildProperty(name="casehub.rag.tracking.enabled", stringValue="true")`, constructor `@Inject (@Delegate @Any CaseRetriever, RetrievalTracker, Event<RetrievalRecorded>)` building a core `TrackingCaseRetriever`; package-private constructor `(CaseRetriever, RetrievalTracker, Consumer<RetrievalRecorded>)` for the test. `RetentionSchedulerBean` `@ApplicationScoped @IfBuildProperty(same)`, `@Inject RetrievalTracker`, `@ConfigProperty retention.days default 90`, `@PostConstruct` start / `@PreDestroy` stop.
- [ ] **Step 6: Test** `TrackingCaseRetrieverDecoratorTest.delegatesToCoreTracking` — retrieve through the decorator with `InMemoryRetrievalTracker`: one record, chunks carry `_trackingId`, event received. Run `mvn -q test -pl rag-tracking -am` — Expected: PASS.
- [ ] **Step 7: Commit** — `refactor(rag-tracking): framework-neutral rag-tracking-core; Quarkus wiring over it` / `Refs #1200`.

### Task 2: rag-tracking-spring

**Files:**
- Create: `rag-tracking-spring/pom.xml`, `rag-tracking-spring/src/main/java/io/casehub/neocortex/rag/tracking/spring/RagTrackingAutoConfiguration.java`, `rag-tracking-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `rag-tracking-spring/src/test/java/io/casehub/neocortex/rag/tracking/spring/RagTrackingAutoConfigurationTest.java`
- Modify: root `pom.xml` (module after `rag-tracking`; dependencyManagement entry)

**Interfaces:**
- Consumes: Task 1 core constructors.
- Produces: `RagTrackingAutoConfiguration` (public), constants `PATH_PROPERTY = "casehub.rag.tracking.sqlite.path"`, `ENABLED_PROPERTY = "casehub.rag.tracking.enabled"`.

- [ ] **Step 1: Write tests** (`ApplicationContextRunner`, `@TempDir` file path):
  - `path_createsSqliteTracker_recordsAndReads`
  - `noPath_noTracker_contextStarts`
  - `applicationTracker_notReplaced`
  - `enabled_wrapsCaseRetriever_recordsStampsAndPublishes` (listener bean receives `RetrievalRecorded`; chunk metadata has `_trackingId`)
  - `enabled_alreadyTracking_notRewrapped` (bean is a `TrackingCaseRetriever`; its `delegate()` is not one)
  - `notEnabled_notWrapped_noScheduler`
  - `enabledWithoutTracker_failsNamingPath` (`getFailure()` stack trace contains `casehub.rag.tracking.sqlite.path`)
  - `enabled_schedulerBeanStarted`; `retentionZero_schedulerNotStarted`
  - `contextClose_closesTracker` (after close, `record` throws)
- [ ] **Step 2: Run** `mvn -q test -pl rag-tracking-spring -am` — Expected: compile failure.
- [ ] **Step 3: Implement** per the spec table: tracker `@Bean(destroyMethod="close") @ConditionalOnProperty(PATH_PROPERTY) @ConditionalOnMissingBean(RetrievalTracker.class)` with `@Value` pool/busy defaults and `ObjectProvider<ObjectMapper>.getIfAvailable(ObjectMapper::new)`; nested `@Configuration @ConditionalOnProperty(name=ENABLED_PROPERTY, havingValue="true")` with `RetentionScheduler` bean (`initMethod="start", destroyMethod="stop"`) and a `static` `BeanPostProcessor` taking `ObjectProvider<RetrievalTracker>` and `ObjectProvider<ApplicationEventPublisher>`. Both resolve the tracker via one helper that throws `IllegalStateException` naming `PATH_PROPERTY` when none exists. Pom: core, `rag-api`, `spring-boot-autoconfigure`, `spring-context`, `rag-tracking` `provided`; test `spring-boot-starter-test`, `rag-testing`.
- [ ] **Step 4: Run** `mvn -q test -pl rag-tracking-spring -am` — Expected: PASS.
- [ ] **Step 5: Commit** — `feat(rag-tracking): Spring Boot auto-configuration` / `Refs #1200`.

### Task 3: neocortex documentation

**Files:** `README.md` (module tables), `docs/guides/consumer-guide.md` (module rows, a Spring usage paragraph with properties), `docs/guides/contributor-guide.md` (module rows, decorator class name `TrackingCaseRetrieverDecorator`), `CLAUDE.md` module list and Maven coordinates (new modules only — it is neocortex's checked-in project doc).

- [ ] **Step 1:** Edit the docs.
- [ ] **Step 2:** `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn -q install -pl rag-tracking-core,rag-tracking,rag-tracking-spring -am` — Expected: BUILD SUCCESS (installs the SNAPSHOT for Task 4).
- [ ] **Step 3: Commit** — `docs: rag-tracking-core and rag-tracking-spring` / `Closes #1200`.

### Task 4: casehub-langchain4j (#20)

**Files (repo `casehub-langchain4j`, branch `feat/spring-sqlite-tracker` from `main`):**
- Modify: `tracking-spring/pom.xml` (test dep `casehub-neocortex-rag-tracking-spring`), root `pom.xml` dependencyManagement if needed
- Test: `tracking-spring/src/test/java/io/casehub/langchain4j/tracking/spring/TrackingWithNeocortexSqliteTest.java`
- Modify: `TrackingAutoConfiguration` javadoc, `docs/guides/consumer-guide.md`, `docs/superpowers/specs/2026-11-20-retrieval-tracking-design.md` (section *Spring tracker*), `ARC42STORIES.MD` (issue #20 done)

- [ ] **Step 1: Test** `sqliteTrackerFromNeocortex_recordsLangChain4jRetrievals` — `ApplicationContextRunner` with `RagTrackingAutoConfiguration` + `TrackingAutoConfiguration`, an app `ContentRetriever`, `casehub.rag.tracking.sqlite.path=<temp>`; after one retrieval, `RetrievalTracker.findRecords(new CorpusRef("default","default"), EPOCH, MAX)` has one record. Second test `hybridStamped_notRecordedTwice`: app `ContentRetriever` returning content with `_trackingId` metadata → no new record, `retrieval_id` copied.
- [ ] **Step 2: Run** `mvn -q test -pl tracking-spring -am` — Expected: PASS (behaviour already exists; this pins the integration). A failure here is a finding, not a test to bend.
- [ ] **Step 3: Docs** — replace "declare your own tracker" / "Spring hybrid retrievals are not tracked" with adding `casehub-neocortex-rag-tracking-spring` (+ `casehub.rag.tracking.enabled=true` for hybrid search).
- [ ] **Step 4:** `mvn clean install` — Expected: BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(tracking): neocortex SQLite tracker for Spring` / `Closes #20`.
