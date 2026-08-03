#!/usr/bin/env bash
#
# Verify BGE-M3 ONNX model is exported and matches committed checksums.
#
# The model is produced by the export script (not downloaded).
# This script only verifies — it does not export or download.
#
# Usage:
#   ./scripts/download-models.sh
#
# First-time setup:
#   pip install -r scripts/requirements-export.txt
#   python scripts/export_bge_m3.py
#

set -euo pipefail

MODEL_DIR="${HOME}/.hortora/models/bge-m3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECKSUM_FILE="${SCRIPT_DIR}/bge-m3-checksums.sha256"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

errors=0

if [[ ! -f "${CHECKSUM_FILE}" ]]; then
    echo -e "${RED}No checksum file found at ${CHECKSUM_FILE}${NC}"
    echo "Run the export script first to generate checksums:"
    echo "  pip install -r scripts/requirements-export.txt"
    echo "  python scripts/export_bge_m3.py"
    exit 1
fi

while IFS= read -r line; do
    line=$(echo "${line}" | xargs)
    [[ -z "${line}" || "${line}" == \#* ]] && continue

    expected_hash=$(echo "${line}" | awk '{print $1}')
    filename=$(echo "${line}" | awk '{print $2}')
    filepath="${MODEL_DIR}/${filename}"

    if [[ ! -f "${filepath}" ]]; then
        echo -e "${RED}MISSING: ${filepath}${NC}"
        errors=$((errors + 1))
        continue
    fi

    actual_hash=$(shasum -a 256 "${filepath}" | awk '{print $1}')
    if [[ "${actual_hash}" != "${expected_hash}" ]]; then
        echo -e "${RED}MISMATCH: ${filename}${NC}"
        echo "  Expected: ${expected_hash}"
        echo "  Actual:   ${actual_hash}"
        errors=$((errors + 1))
    else
        echo -e "${GREEN}OK: ${filename}${NC}"
    fi
done < "${CHECKSUM_FILE}"

if [[ ${errors} -gt 0 ]]; then
    echo ""
    echo -e "${RED}${errors} file(s) missing or mismatched.${NC}"
    echo "Run the export script to (re)generate the model:"
    echo "  pip install -r scripts/requirements-export.txt"
    echo "  python scripts/export_bge_m3.py"
    exit 1
fi

echo ""
echo -e "${GREEN}All BGE-M3 model files verified.${NC}"
