# Consolidate Flyway Migrations into Single Initial Schemas

## Problem

No production database exists, but `memory-cbr-jpa` has accumulated 5 incremental Flyway migrations (V1–V5) instead of maintaining a single initial schema script. Additionally, `memory-jpa` uses V1000 as its version number, inconsistent with all other modules which use V1.

## Scope

Two modules, two changes:

### 1. memory-cbr-jpa — Consolidate V1–V5

Replace 5 incremental migration files with a single `V1__initial_schema.sql` containing the final-state schema.

**Files to delete:**
- `V1__create_cbr_case.sql`
- `V2__add_outcome_detail_columns.sql`
- `V3__add_supersession.sql`
- `V4__add_scope.sql`
- `V5__add_reinstated_at.sql`

**File to create:** `V1__initial_schema.sql`

Final schema: one `CREATE TABLE cbr_case` with 23 columns and 4 indexes:

| Column | Type | Constraint |
|--------|------|------------|
| id | UUID | PK, NOT NULL, DEFAULT gen_random_uuid() |
| tenant_id | VARCHAR(255) | NOT NULL |
| domain | VARCHAR(255) | NOT NULL |
| case_type | VARCHAR(255) | NOT NULL |
| cbr_type | VARCHAR(50) | NOT NULL, DEFAULT 'plan' |
| entity_id | VARCHAR(255) | NOT NULL |
| case_id | VARCHAR(255) | nullable |
| problem | TEXT | NOT NULL |
| solution | TEXT | NOT NULL |
| outcome | TEXT | nullable |
| confidence | DOUBLE PRECISION | nullable |
| features | TEXT | NOT NULL, DEFAULT '{}' |
| plan_traces | TEXT | nullable |
| stored_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT now() |
| trust_score | DOUBLE PRECISION | nullable |
| producer_agent_id | VARCHAR(255) | nullable |
| outcome_detail | TEXT | nullable |
| last_outcome_at | TIMESTAMP WITH TIME ZONE | nullable |
| superseded_at | TIMESTAMP WITH TIME ZONE | nullable |
| superseding_case_id | VARCHAR(255) | nullable |
| supersession_reason | TEXT | nullable |
| scope | VARCHAR(1024) | NOT NULL, DEFAULT '' |
| reinstated_at | TIMESTAMP | nullable |

Indexes:
- `cbr_case_lookup_idx` ON (tenant_id, domain, case_type, scope)
- `cbr_case_entity_idx` ON (entity_id, tenant_id)
- `cbr_case_stored_at_idx` ON (stored_at)
- `cbr_case_superseded_idx` ON (superseded_at)

### 2. memory-jpa — Normalize version number

Rename `V1000__memory_entry.sql` to `V1__memory_entry.sql`. Content unchanged.

## Verification

`mvn test -pl memory-cbr-jpa,memory-jpa` — Flyway runs at startup against H2 in PostgreSQL mode (`MODE=PostgreSQL`). JPA entity field mappings validate against the schema. If tests pass, the consolidated schema matches what the code expects.

No Java code changes. No config changes. Test `application.properties` already points at the correct Flyway locations.

## Decision Record

- D1: V1 for all modules — each module has its own Flyway location, version numbers never collide across modules. V1000 had no purpose. All other modules already use V1.

## References

- `memory-cbr-jpa/src/main/resources/db/cbr/migration/V1__create_cbr_case.sql` — original base table
- `memory-cbr-jpa/src/main/resources/db/cbr/migration/V2–V5` — incremental ALTERs
- `memory-cbr-jpa/src/main/java/io/casehub/neocortex/memory/cbr/jpa/CbrCaseEntity.java` — JPA entity defining expected columns
- `memory-cbr-jpa/src/test/resources/application.properties` — Flyway config (H2, PostgreSQL mode)
- `memory-jpa/src/main/resources/db/memory/migration/V1000__memory_entry.sql` — V1000 to normalize
- casehubio/platform#226 — parent issue for migration consolidation across platform
