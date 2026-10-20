#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARCHIVE_NAME="delivery-tracking-system-monitoring.zip"
OUTPUT_PATH="${ROOT_DIR}/${ARCHIVE_NAME}"

rm -f "${OUTPUT_PATH}"
cd "${ROOT_DIR}"

zip -r "${OUTPUT_PATH}" . \
  -x "*.git*" \
  -x "*.idea*" \
  -x "*target/*" \
  -x "*.class" \
  -x "*__pycache__*" \
  -x "*.pyc" \
  -x "*.DS_Store" \
  -x "*Thumbs.db" \
  -x "*.env" \
  -x "*secret.yml" \
  -x "*.log" \
  -x "*delivery-tracking-system-monitoring.zip"

echo "Archive created: ${OUTPUT_PATH}"
