#!/bin/bash
# Run a pipeline iteration and compare to baseline.
#
# Usage:
#   ./iterate.sh [--limit N] [--label name]
#
# Cleans output-test/, runs pipeline, compares to output/ baseline.
# Results appended to iteration-log.md.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKBENCH="$(dirname "$SCRIPT_DIR")"
SOURCE="$WORKBENCH/sources/frida-kahlo-biography.md"
BASELINE="$WORKBENCH/output/corpus.json"
OUTDIR="$WORKBENCH/output-test"
VENV="$SCRIPT_DIR/.venv/bin/python3"
LOG="$WORKBENCH/iteration-log.md"

LIMIT=3
LABEL="unnamed"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --limit) LIMIT="$2"; shift 2;;
        --label) LABEL="$2"; shift 2;;
        *) shift;;
    esac
done

echo "=== Iteration: $LABEL (limit=$LIMIT) ==="

# Clean and run
rm -rf "$OUTDIR"
mkdir -p "$OUTDIR"
bash "$SCRIPT_DIR/run_pipeline.sh" "$SOURCE" "$OUTDIR" --limit "$LIMIT" --max-passes 2

# Compare
if [ -f "$OUTDIR/corpus.json" ] && [ -f "$BASELINE" ]; then
    echo ""
    PASSAGES_FLAG=""
    if [ "$LIMIT" -gt 0 ]; then
        PASSAGES_FLAG="--passages $LIMIT"
    fi
    $VENV "$SCRIPT_DIR/compare_corpus.py" "$BASELINE" "$OUTDIR/corpus.json" $PASSAGES_FLAG

    # Append to log
    echo "" >> "$LOG"
    echo "### $LABEL ($(date '+%Y-%m-%d %H:%M'), limit=$LIMIT)" >> "$LOG"
    echo '```' >> "$LOG"
    $VENV "$SCRIPT_DIR/compare_corpus.py" "$BASELINE" "$OUTDIR/corpus.json" $PASSAGES_FLAG >> "$LOG"
    echo '```' >> "$LOG"
else
    echo "ERROR: corpus.json not produced"
    echo "" >> "$LOG"
    echo "### $LABEL ($(date '+%Y-%m-%d %H:%M'), limit=$LIMIT) — FAILED" >> "$LOG"
fi
