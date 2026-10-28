#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
W="$SCRIPT_DIR/wrapper.sh"
bash -n "$W"
D="$(mktemp -d)"; trap 'rm -rf "$D"' EXIT
mkdir -p "$D/bin" "$D/state"
export AUTOSTONE_STATE_DIR="$D/state"
echo secret > "$D/secret"
cat > "$D/bin/curl" <<'CURL'
#!/bin/sh
printf '%s\n' "$*" >> "$STATE_CAPTURE"
exit 0
CURL
chmod +x "$D/bin/curl"
export STATE_CAPTURE="$D/curl.args"
export PATH="$D/bin:$PATH"
OUT="$($W start r 1 1 http://callback.invalid/cb 20 /tmp 'sleep 10' "$D/secret")"
PID="$(printf '%s\n' "$OUT" | sed -n 's/^PID://p')"
test -n "$PID"
$W pause "$PID" >/dev/null
$W status "$PID" | grep -q 'STATUS:PAUSED'
$W resume "$PID" >/dev/null
$W stop "$PID" >/dev/null
sleep .2
grep -q 'status.*STOPPED' "$D/curl.args"
OUT="$($W start r 1 2 http://callback.invalid/cb 20 /tmp 'sleep 10' "$D/secret")"
PID="$(printf '%s\n' "$OUT" | sed -n 's/^PID://p')"
$W pause "$PID" >/dev/null
$W kill "$PID" >/dev/null
sleep .2
grep -q 'status.*KILLED' "$D/curl.args"
OUT="$($W start r 1 3 http://callback.invalid/cb 1 /tmp 'sleep 3' "$D/secret")"
sleep 2
grep -q 'status.*TIMEOUT' "$D/curl.args"
echo 'wrapper v4 runtime control tests passed'
