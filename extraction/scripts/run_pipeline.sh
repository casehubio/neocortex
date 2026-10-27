#!/bin/bash
# Cognitive extraction pipeline — convergence loop.
#
# Usage:
#   ./run_pipeline.sh <source.md> [output-dir] [--mode fixed-point|selective] [--max-passes N]
#
# The pipeline converges: after entity synthesis (Phase 2b) produces new
# memories, those go through structural extraction and enrichment. The loop
# terminates when no new memories are generated or max passes is reached.
#
# Key intermediates (enriched.json, nlp-baseline.json) are preserved in
# the output directory and tracked in git. Re-assembly (Step 7) and
# re-verification (Step 8) can re-run without LLM calls using these files.

set -euo pipefail

SOURCE="${1:?Usage: $0 <source.md> [output-dir] [--mode MODE] [--max-passes N]}"
OUTDIR="${2:-./output}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV="${SCRIPT_DIR}/.venv/bin/python3"

MODE="fixed-point"
MAX_PASSES=5
LIMIT=0

shift 2 2>/dev/null || shift $# 2>/dev/null
while [[ $# -gt 0 ]]; do
    case "$1" in
        --mode) MODE="$2"; shift 2;;
        --max-passes) MAX_PASSES="$2"; shift 2;;
        --limit) LIMIT="$2"; shift 2;;
        --schema) SCHEMA_PATH="$2"; shift 2;;
        *) shift;;
    esac
done

LIMIT_FLAG=""
if [ "$LIMIT" -gt 0 ]; then
    LIMIT_FLAG="--limit $LIMIT"
fi

if [ ! -f "$VENV" ]; then
    echo "Error: venv not found at $VENV"
    echo "Run: python3 -m venv ${SCRIPT_DIR}/.venv && ${SCRIPT_DIR}/.venv/bin/pip install -r ${SCRIPT_DIR}/requirements.txt"
    exit 1
fi

mkdir -p "$OUTDIR"
MANIFEST="$OUTDIR/manifest.json"

# Schema discovery: explicit > output-dir > docs fallback
if [ -z "${SCHEMA_PATH:-}" ]; then
    if [ -f "$OUTDIR/verify_schema.yaml" ]; then
        SCHEMA_PATH="$OUTDIR/verify_schema.yaml"
    else
        SCHEMA_PATH="$SCRIPT_DIR/../docs/verify_schema.yaml"
    fi
fi

# Read subject name from schema
SUBJECT=$($VENV -c "
import yaml, sys
with open('$SCHEMA_PATH') as f:
    schema = yaml.safe_load(f)
print(schema.get('converter', {}).get('subject_name', ''))
")
if [ -z "$SUBJECT" ]; then
    echo "Error: converter.subject_name not found in $SCHEMA_PATH"
    exit 1
fi

echo "=== Cognitive Extraction Pipeline ==="
echo "Source: $SOURCE"
echo "Output: $OUTDIR"
echo "Schema: $SCHEMA_PATH"
echo "Subject: $SUBJECT"
echo "Mode: $MODE"
echo "Max passes: $MAX_PASSES"
if [ "$LIMIT" -gt 0 ]; then echo "Passage limit: $LIMIT"; fi
echo ""

# --- Helper: check if a step is complete in the manifest ---
step_done() {
    if [ ! -f "$MANIFEST" ]; then return 1; fi
    $VENV -c "
import json, sys
m = json.load(open('$MANIFEST'))
passes = m.get('passes', [])
if not passes: sys.exit(1)
p = passes[-1]
sys.exit(0 if '$1' in p.get('steps_complete', []) else 1)
"
}

is_converged() {
    if [ ! -f "$MANIFEST" ]; then return 1; fi
    $VENV -c "
import json, sys
m = json.load(open('$MANIFEST'))
sys.exit(0 if m.get('converged') else 1)
"
}

current_pass() {
    if [ ! -f "$MANIFEST" ]; then echo "0"; return; fi
    $VENV -c "
import json
m = json.load(open('$MANIFEST'))
passes = m.get('passes', [])
print(passes[-1]['pass'] if passes else 0)
"
}

last_pass_status() {
    if [ ! -f "$MANIFEST" ]; then echo "none"; return; fi
    $VENV -c "
import json
m = json.load(open('$MANIFEST'))
passes = m.get('passes', [])
print(passes[-1]['status'] if passes else 'none')
"
}

# --- Initialize manifest for pass 1 if needed ---
PASS=$(current_pass)
if [ "$PASS" = "0" ]; then
    $VENV -c "
import sys; sys.path.insert(0, '$SCRIPT_DIR')
from pipeline_state import PipelineManifest
m = PipelineManifest('$MANIFEST', mode='$MODE', max_passes=$MAX_PASSES)
m.start_pass(1)
"
    PASS=1
fi

# --- Check for convergence from prior run ---
if is_converged; then
    echo "Pipeline already converged. Skipping to post-convergence."
    PASS=999
fi

# ===================== PASS 1 =====================
if [ "$PASS" = "1" ]; then
    echo "=== Pass 1: Initial Extraction ==="

    if ! step_done "chunk"; then
        echo "--- Step 1: Chunk ---"
        $VENV "$SCRIPT_DIR/chunk.py" "$SOURCE" -o "$OUTDIR/passages.json"
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('chunk')"
        echo ""
    fi

    if ! step_done "nlp_extract"; then
        echo "--- Step 2: NLP Baseline ---"
        $VENV "$SCRIPT_DIR/nlp_extract.py" "$OUTDIR/passages.json" -o "$OUTDIR/nlp-baseline.json"
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('nlp_extract')"
        echo ""
    fi

    if ! step_done "gap_sweep"; then
        echo "--- Step 3: LLM Gap Sweep ---"
        $VENV "$SCRIPT_DIR/llm_gap_sweep.py" "$OUTDIR/passages.json" "$OUTDIR/nlp-baseline.json" -o "$OUTDIR/gap-sweep.json"
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('gap_sweep')"
        echo ""
    fi

    if ! step_done "extract_structure"; then
        echo "--- Step 4: Structural Extraction ---"
        $VENV "$SCRIPT_DIR/extract_structure.py" "$OUTDIR/passages.json" "$OUTDIR/nlp-baseline.json" --gaps "$OUTDIR/gap-sweep.json" -o "$OUTDIR/structure.json" $LIMIT_FLAG
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('extract_structure')"
        echo ""
    fi

    if ! step_done "enrich_cognitive"; then
        echo "--- Step 5a: Passage-Level Enrichment ---"
        $VENV "$SCRIPT_DIR/enrich_cognitive.py" "$OUTDIR/passages.json" "$OUTDIR/structure.json" -o "$OUTDIR/enriched.json" --pass-num 1
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('enrich_cognitive')"
        echo ""
    fi

    if ! step_done "enrich_entity"; then
        echo "--- Step 5b: Entity-Level Synthesis ---"
        $VENV "$SCRIPT_DIR/enrich_entity.py" "$OUTDIR/enriched.json" "$OUTDIR/structure.json" --pass-num 1 --mode "$MODE" 2>&1 | tee /tmp/enrich_entity_output.txt
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('enrich_entity')"
        echo ""
    fi

    if ! step_done "backfill"; then
        echo "--- Step 5c: Backfill Enrichment ---"
        $VENV "$SCRIPT_DIR/enrich_cognitive.py" --backfill "$OUTDIR/enriched.json" --pass-num 1
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('backfill')"
        echo ""
    fi

    # Complete pass 1
    NEW_MEMS=$(grep -o 'NEW_MEMORIES=[0-9]*' /tmp/enrich_entity_output.txt 2>/dev/null | head -1 | cut -d= -f2 || echo "0")
    $VENV -c "
import sys; sys.path.insert(0, '$SCRIPT_DIR')
from pipeline_state import PipelineManifest
m = PipelineManifest('$MANIFEST')
m.complete_pass(new_memories=${NEW_MEMS:-0}, new_entities=0, new_edges=0)
"
    echo "Pass 1 complete. New memories: ${NEW_MEMS:-0}"
fi

# ===================== CONVERGENCE LOOP =====================
PASS=$(current_pass)
STATUS=$(last_pass_status)

# If pass 1 just completed, start at pass 2
if [ "$PASS" = "1" ] && [ "$STATUS" = "complete" ]; then
    PASS=2
fi

# If resuming an in-progress pass, stay on that pass
if [ "$STATUS" = "in_progress" ]; then
    true  # PASS is already correct from current_pass()
fi

while [ "$PASS" -le "$MAX_PASSES" ]; do
    if is_converged; then break; fi

    echo ""
    echo "=== Convergence Pass $PASS ==="

    # Start new pass if needed
    CURRENT=$(current_pass)
    STATUS=$(last_pass_status)
    if [ "$CURRENT" -lt "$PASS" ] || ([ "$CURRENT" = "$PASS" ] && [ "$STATUS" = "complete" ]); then
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.start_pass($PASS)"
    fi

    if ! step_done "convergence_extract"; then
        echo "--- Convergence Extraction ---"
        $VENV "$SCRIPT_DIR/extract_structure.py" --convergence "$OUTDIR/enriched.json" --structure-base "$OUTDIR/structure.json" -o "$OUTDIR/structure.json" --pass-num "$PASS"
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('convergence_extract')"
        echo ""
    fi

    if ! step_done "backfill_enrich"; then
        echo "--- Backfill Enrichment ---"
        $VENV "$SCRIPT_DIR/enrich_cognitive.py" --backfill "$OUTDIR/enriched.json" --pass-num "$PASS"
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('backfill_enrich')"
        echo ""
    fi

    if ! step_done "incremental_entity"; then
        echo "--- Incremental Entity Synthesis ---"
        $VENV "$SCRIPT_DIR/enrich_entity.py" "$OUTDIR/enriched.json" "$OUTDIR/structure.json" --incremental --pass-num "$PASS" --mode "$MODE" 2>&1 | tee /tmp/enrich_entity_output.txt
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('incremental_entity')"
        echo ""
    fi

    if ! step_done "backfill_new"; then
        echo "--- Backfill New Memories ---"
        $VENV "$SCRIPT_DIR/enrich_cognitive.py" --backfill "$OUTDIR/enriched.json" --pass-num "$PASS"
        $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.complete_step('backfill_new')"
        echo ""
    fi

    # Complete pass, check convergence
    NEW_MEMS=$(grep -o 'NEW_MEMORIES=[0-9]*' /tmp/enrich_entity_output.txt 2>/dev/null | head -1 | cut -d= -f2 || echo "0")

    $VENV -c "
import sys; sys.path.insert(0, '$SCRIPT_DIR')
from pipeline_state import PipelineManifest
m = PipelineManifest('$MANIFEST')
m.complete_pass(new_memories=${NEW_MEMS:-0}, new_entities=0, new_edges=0)
if ${NEW_MEMS:-0} == 0:
    m.mark_converged()
"

    echo "Pass $PASS complete. New memories: ${NEW_MEMS:-0}"

    if [ "${NEW_MEMS:-0}" = "0" ]; then
        echo "Converged — no new memories generated."
        break
    fi

    PASS=$((PASS + 1))
done

if [ "$PASS" -gt "$MAX_PASSES" ] && ! is_converged; then
    echo "Safety stop — max passes ($MAX_PASSES) reached."
    $VENV -c "import sys; sys.path.insert(0, '$SCRIPT_DIR'); from pipeline_state import PipelineManifest; m = PipelineManifest('$MANIFEST'); m.mark_converged()"
fi

# ===================== POST-CONVERGENCE =====================
echo ""
echo "--- Step 6: Reference Overlay ---"
$VENV "$SCRIPT_DIR/generate_overlay.py" "$OUTDIR/structure.json" "$OUTDIR/passages.json" -o "$OUTDIR/reference-overlay.json" --subject "$SUBJECT"
echo ""

echo "--- Step 7: Assemble Corpus ---"
$VENV "$SCRIPT_DIR/assemble_corpus.py" "$OUTDIR/enriched.json" "$OUTDIR/nlp-baseline.json" -o "$OUTDIR/corpus.json" -r "$OUTDIR/coverage-report.json" --llm-dedup
echo ""

echo "--- Step 7.5: Co-location Gap Closure ---"
$VENV "$SCRIPT_DIR/orphan_closure.py" "$OUTDIR/corpus.json" "$OUTDIR/passages.json" \
    --schema "$SCHEMA_PATH" \
    --report "orphan-closure-report.json"
echo ""

echo "--- Step 7.6: Dangling Edge Resolution ---"
$VENV "$SCRIPT_DIR/dangling_resolution.py" "$OUTDIR/corpus.json" "$OUTDIR/passages.json" \
    --report "dangling-resolution-report.json"
echo ""

echo "--- Step 8: Verify Import Readiness ---"
$VENV "$SCRIPT_DIR/verify_corpus.py" "$OUTDIR/corpus.json" \
    --schema "$SCHEMA_PATH" \
    --output "$OUTDIR"
VERIFY_EXIT=$?
if [ $VERIFY_EXIT -eq 1 ]; then
    echo ""
    echo "CRITICAL: Import readiness gate failed."
    echo "Run with --fix to attempt auto-repair:"
    echo "  python3 scripts/verify_corpus.py $OUTDIR/corpus.json --fix --output $OUTDIR"
fi
echo ""

# Print convergence summary
echo "=== Pipeline Complete ==="
$VENV -c "
import json
m = json.load(open('$MANIFEST'))
for p in m['passes']:
    print(f\"  Pass {p['pass']}: {p['new_memories']} new memories, {p.get('new_entities', 0)} new entities, {p.get('new_edges', 0)} new edges\")
print(f\"  Total passes: {len(m['passes'])}\")
print(f\"  Converged: {m['converged']}\")
"
echo ""
echo "Outputs:"
echo "  $OUTDIR/passages.json        — chunked passages"
echo "  $OUTDIR/nlp-baseline.json    — NLP entity concordance"
echo "  $OUTDIR/gap-sweep.json       — LLM gap findings"
echo "  $OUTDIR/structure.json       — extracted graph structure"
echo "  $OUTDIR/enriched.json        — cognitively enriched data"
echo "  $OUTDIR/corpus.json          — importable corpus"
echo "  $OUTDIR/manifest.json        — convergence state"
echo "  $OUTDIR/coverage-report.json — coverage audit"
echo "  $OUTDIR/orphan-closure-report.json — orphan closure audit"
echo "  $OUTDIR/dangling-resolution-report.json — dangling edge resolution"
echo "  $OUTDIR/verification-report.json — import readiness"
