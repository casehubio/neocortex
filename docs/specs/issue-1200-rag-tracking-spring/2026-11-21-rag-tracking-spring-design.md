# Retrieval tracking for Spring Boot — design

**Issue:** casehubio/neocortex#1200 (motivated by casehubio/casehub-langchain4j#20)
**Pattern:** framework-neutral core + Quarkus + Spring, as in #279

## Problem

`SqliteRetrievalTracker` is a CDI bean: package-private `@ConfigProperty` fields, an injected
`ObjectMapper`, `@PostConstruct`/`@PreDestroy`. `rag-tracking` depends on `quarkus-arc` and
`quarkus-jackson`. `TrackingCaseRetriever` is a CDI `@Decorator`. A Spring Boot application can
therefore neither create the SQLite tracker nor get `CaseRetriever` retrievals tracked — which
also leaves hybrid search untracked in casehub-langchain4j's Spring modules.

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `rag-tracking-core` (new) | `casehub-neocortex-rag-tracking-core` | Plain Java. Depends on `rag-api`, `sqlite-support`, `jackson-databind`, `jboss-logging`. No Quarkus, no Spring, no Jandex index. |
| `rag-tracking` | `casehub-neocortex-rag-tracking` (unchanged) | Quarkus CDI wiring over the core. |
| `rag-tracking-spring` (new) | `casehub-neocortex-rag-tracking-spring` | Spring Boot auto-configuration over the core. |

All three get root `dependencyManagement` entries.

## rag-tracking-core

Package `io.casehub.neocortex.rag.tracking` — the existing package, so the fully qualified names of
the moved classes do not change.

- **`SqliteRetrievalTracker implements RetrievalTracker, AutoCloseable`** — public constructor
  `SqliteRetrievalTracker(String path, int maxPoolSize, int busyTimeoutMs, ObjectMapper objectMapper)`.
  The constructor creates the data source (`SqliteDataSourceFactory.create(path, maxPoolSize,
  busyTimeoutMs, 64000)`) and runs the migrations (`classpath:db/rag-tracking/migration`), as
  `init()` does today. `close()` closes the data source (idempotent). All SQL is moved unchanged.
  Package-private `clearAll()` stays for tests.
- **Migrations** — `V1__retrieval_tracking.sql` and `V2__feedback_context.sql` move to this jar at
  the same classpath location: same schema, same Flyway history, existing databases keep working.
- **`TrackingLogic`** — moved unchanged.
- **`TrackingCaseRetriever implements CaseRetriever`** — the plain decorator: public constructor
  `(CaseRetriever delegate, RetrievalTracker tracker, Consumer<RetrievalRecorded> eventSink)`,
  public `delegate()`. Behaviour unchanged (already-tracked skip, record, event, stamp
  `_trackingId`, failure logged and the delegate's chunks returned).
- **`RetentionScheduler`** — plain: constructor `(RetrievalTracker tracker, int retentionDays)`,
  public `start()` (no-op when `retentionDays <= 0`; otherwise a daemon thread
  `rag-retention-purge`, first purge after 1 h, then every 24 h), `stop()`, and package-private
  `purge()`. Behaviour unchanged.

## rag-tracking (Quarkus)

CDI classes in `io.casehub.neocortex.rag.tracking.quarkus`. Same properties, same gates, same
behaviour.

- **Tracker producer** — `@Produces @Singleton @Typed({SqliteRetrievalTracker.class,
  RetrievalTracker.class})` from `casehub.rag.tracking.sqlite.path` (required),
  `…pool.max-size` (default 5), `…busy-timeout-ms` (default 5000) and the injected
  `ObjectMapper`; a disposer calls `close()`. `@Singleton` instead of `@ApplicationScoped`: the core
  class has no no-arg constructor for a client proxy, and nothing needs the laziness.
- **`TrackingCaseRetrieverDecorator`** — `@Decorator @Priority(40)
  @IfBuildProperty(name = "casehub.rag.tracking.enabled", stringValue = "true")`, delegating to a
  core `TrackingCaseRetriever` built with `Event<RetrievalRecorded>::fire`.
- **`RetentionSchedulerBean`** — `@ApplicationScoped`, same `@IfBuildProperty`, reads
  `casehub.rag.tracking.retention.days` (default 90), starts a core `RetentionScheduler` in
  `@PostConstruct` and stops it in `@PreDestroy` — exactly today's bean shape.

`quarkus-arc` and `quarkus-jackson` stay dependencies of this module only.

## rag-tracking-spring

`io.casehub.neocortex.rag.tracking.spring.RagTrackingAutoConfiguration`, registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
`@ConditionalOnClass(SqliteRetrievalTracker.class)`. Property names are the Quarkus ones.

| Bean | Condition | Notes |
|---|---|---|
| `SqliteRetrievalTracker` (`destroyMethod = "close"`) | `casehub.rag.tracking.sqlite.path` set, and `@ConditionalOnMissingBean(RetrievalTracker.class)` | pool and busy-timeout properties with the Quarkus defaults; a Jackson 2 `ObjectMapper` bean if one exists, else `new ObjectMapper()` |
| `RetentionScheduler` (`initMethod = "start"`, `destroyMethod = "stop"`) | `casehub.rag.tracking.enabled=true` | `casehub.rag.tracking.retention.days`, default 90 |
| static `BeanPostProcessor` wrapping every `CaseRetriever` bean in a `TrackingCaseRetriever` | `casehub.rag.tracking.enabled=true` | skips beans that already are a `TrackingCaseRetriever`; `RetrievalRecorded` published through `ApplicationEventPublisher` |

- The post-processor resolves the tracker through an `ObjectProvider` when it wraps a bean. With
  `enabled=true` and no `RetrievalTracker` bean the context fails with a message naming
  `casehub.rag.tracking.sqlite.path` (the Quarkus equivalent fails on the missing injection).
  Several trackers fail as Spring's `NoUniqueBeanDefinitionException` does.
- The retention scheduler, too, needs a tracker; it gets one through the same rule.
- Dependencies: `rag-tracking-core`, `rag-api`, `spring-boot-autoconfigure`, `spring-context`;
  `rag-tracking` at `provided` scope, as the other neocortex Spring modules do with their Quarkus
  siblings.

## Tests

- **core** — the existing `SqliteRetrievalTrackerTest` (contract test) now builds the tracker
  through the constructor; plus: two trackers on the same file share data (migrations
  idempotent) and `close()` twice is safe. `TrackingCaseRetrieverTest`, `TrackingLogicTest` and
  `RetentionSchedulerTest` move with their classes, adapted to the constructors.
- **Quarkus** — a unit test of the decorator delegating to the core logic, so the CDI module
  keeps a test.
- **Spring** (`ApplicationContextRunner`):
  - with a path (temp file), a `SqliteRetrievalTracker` bean records and reads back;
  - without a path, no tracker bean, context starts;
  - an application `RetrievalTracker` bean is not replaced;
  - `enabled=true`: a `CaseRetriever` bean is wrapped, a retrieval is recorded, chunks carry
    `_trackingId`, a `RetrievalRecorded` listener receives the event;
  - `enabled` unset: not wrapped, no retention scheduler;
  - `enabled=true` without a tracker: context fails mentioning `casehub.rag.tracking.sqlite.path`;
  - `enabled=true`: retention scheduler bean exists; `retention.days=0` starts no thread.

## Documentation

README module table, consumer guide (module row, Spring usage), contributor guide (module rows,
the decorator's new class name).

## Out of scope / noted

- The Quarkus `RetentionScheduler` is an `@ApplicationScoped` bean nothing injects; ArC may
  remove it as unused, in which case retention never runs in Quarkus. This change keeps the bean
  shape exactly; if a check confirms the removal, it gets its own issue.
- `memory-cbr-tracking` is untouched.

## Downstream: casehub-langchain4j (#20)

After this lands and the SNAPSHOT is published:

- `tracking-spring` test with `casehub-neocortex-rag-tracking-spring` at test scope: the
  application declares no tracker, sets `casehub.rag.tracking.sqlite.path`, and LangChain4j
  retrievals are recorded in SQLite.
- Javadoc of `TrackingAutoConfiguration`, consumer guide and the retrieval tracking spec: replace
  "declare your own tracker" and "Spring hybrid retrievals are not tracked" with how to add
  `rag-tracking-spring` (and `casehub.rag.tracking.enabled=true` for hybrid search).
- `Closes #20`.
