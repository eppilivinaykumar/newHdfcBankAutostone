#!/usr/bin/env bash
set -euo pipefail
# Static contract checks: BOXes are never sent to this wrapper; start requires 7 args after action.
# Run on a Linux host with bash, setsid, timeout and curl available.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER="$SCRIPT_DIR/wrapper.sh"
grep -Fq 'start  <run_id> <job_id> <execution_id> <callback_url> <timeout_seconds> <working_directory> <command> [callback_secret_file]' "$WRAPPER"
grep -q 'PID:' "$WRAPPER"
grep -q 'LOG:' "$WRAPPER"
grep -q 'exitCode' "$WRAPPER"
grep -q 'X-AutoStone-Callback-Secret' "$WRAPPER"
bash -n "$WRAPPER"
echo 'wrapper contract/static shell checks passed'
