# Enrichment Standard — Per-Memory Cognitive Fields

This document defines which enrichment fields are expected per memory domain,
how to verify coverage, and common pipeline issues that cause gaps.

## Enrichment Matrix

| Field | Experience | Reflection | Mood | Relationship |
|-------|-----------|-----------|------|-------------|
| pad | REQUIRED | REQUIRED | REQUIRED | REQUIRED |
| occ_emotions | REQUIRED | REQUIRED | recommended | REQUIRED |
| sec_appraisal | REQUIRED | REQUIRED | — | recommended |
| action_tendencies | REQUIRED | recommended | — | recommended |
| sub_thoughts | REQUIRED | recommended | — | — |
| cognitive_distortions | recommended | recommended | — | — |
| drive_impact | REQUIRED | REQUIRED | recommended | REQUIRED |
| confidence | REQUIRED | REQUIRED | recommended | REQUIRED |
| habituation | REQUIRED | — | — | — |
| caps_inputs | REQUIRED | — | — | — |
| goal_impacts | REQUIRED | recommended | — | — |
| gut_feeling | recommended | — | — | — |
| relationship | recommended | — | — | REQUIRED |

**REQUIRED**: >90% coverage expected. Assembly reports a warning below this.
**recommended**: desirable but not gated. Absence is legitimate for some memories.
**—**: not expected for this domain.

## Domain Definitions

### Experience (domain="experience")
Biographical events from the primary extraction pass. These carry the richest
cognitive data — every experience is a data point for CARMA, CAPS, drive tracking,
and goal modulation. Full enrichment is essential.

### Reflection (domain="reflection")
Synthesized insights from entity-level enrichment (phase 2b). These represent
higher-order cognitive patterns across experiences. They carry emotional valence
and drive implications, so PAD, OCC, SEC, and drive_impact are required.

### Mood (domain="mood")
Period-level emotional snapshots from entity-level enrichment (phase 2b).
PAD is the primary data (it IS the mood measurement). OCC/drive_impact are
recommended because the mood text contains biographical narrative that can
inform discrete emotions and drive modulation.

### Relationship (domain="relationship")
Interaction records with other agents. PAD, OCC, and drive_impact are required
because every interaction carries emotional and motivational content.

## Pipeline Stages and Enrichment

```
extract_structure.py  →  memories (experience + some relationship)
enrich_cognitive.py   →  enriches ALL memories from extract_structure
enrich_entity.py      →  synthesizes NEW mood snapshots + reflections
enrich_cognitive.py --backfill  →  enriches phase-2b memories
```

The backfill pass is critical — without it, phase-2b memories (mood snapshots,
some reflections) have no enrichment beyond their synthesized PAD values.

## Common Issues

### Mood snapshots skipped by backfill
Mood snapshots from `enrich_entity.py` originally used `context` field without
a `text` field. The backfill checks `mem.get('text')`. Fixed at two levels:
- **Root cause**: `enrich_entity.py` now synthesizes `text` from `period`/`context`
  at creation time, so mood snapshots are born with a `text` field.
- **Safety net**: `enrich_cognitive.py` backfill also constructs `text` from
  `period`/`context` for any mood snapshot still missing it.

### Experience memories misclassified as mood
The extraction LLM sometimes assigns domain="mood" to experience memories
that describe emotional states. Fixed at two levels:
- **Root cause**: `extract_structure.py` prompt explicitly says "DO NOT USE
  domain='mood' in phase-1 extraction" and the post-processing code forces
  any mood-domain memory to experience.
- **Safety net**: `assemble_corpus.py` reclassifies phase-1 memories with
  observation/outcome event types from mood back to experience.

### Empty vs absent enrichment fields
An empty field (e.g., `cognitive_distortions: []`) means the enrichment ran
and found nothing applicable. An absent field means the enrichment didn't run
for this memory. The audit distinguishes between these.

## Verification

The assembly step runs `audit_enrichment_completeness()` which checks each
domain against this matrix and reports warnings for required fields below 90%.
This appears in the assembly output and in `corpus.json` stats.

Run verification standalone:
```bash
python3 -c "
import json, sys
sys.path.insert(0, 'extraction/scripts')
from assemble_corpus import audit_enrichment_completeness
with open('extraction/output/corpus.json') as f:
    corpus = json.load(f)
report = audit_enrichment_completeness(corpus['memories'])
for w in report.get('warnings', []):
    print(f'WARNING: {w}')
if not report.get('warnings'):
    print('All domains pass enrichment completeness checks.')
"
```
