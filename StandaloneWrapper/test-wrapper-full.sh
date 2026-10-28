#!/usr/bin/env bash
set -euo pipefail
W="$(cd "$(dirname "$0")" && pwd)/wrapper.sh"
D="$(mktemp -d)"; trap 'rm -rf "$D"' EXIT
mkdir -p "$D/bin" "$D/state"
export AUTOSTONE_STATE_DIR="$D/state"
cat > "$D/secret" <<'EOF'
full-test-secret
EOF
cat > "$D/bin/curl" <<'CURL'
#!/bin/sh
printf '%s\n' "$*" >> "$STATE_CAPTURE"
exit 0
CURL
chmod +x "$D/bin/curl"
export STATE_CAPTURE="$D/curl.args"
export PATH="$D/bin:$PATH"

run_start(){ "$W" start "$1" 1 "$2" http://callback.invalid/cb "$3" /tmp "$4" "$D/secret"; }

OUT="$(run_start success 101 10 'exit 0')"; PID="$(sed -n 's/^PID://p' <<<"$OUT")"; test -n "$PID"; sleep .2; grep -q 'status.*SUCCESS' "$D/curl.args"
OUT="$(run_start failure 102 10 'exit 7')"; PID="$(sed -n 's/^PID://p' <<<"$OUT")"; test -n "$PID"; sleep .2; grep -q 'status.*FAILED' "$D/curl.args"
OUT="$(run_start controls 103 20 'sleep 10')"; PID="$(sed -n 's/^PID://p' <<<"$OUT")"; test -n "$PID"; "$W" pause "$PID" >/dev/null; "$W" status "$PID" | grep -q 'STATUS:PAUSED'; "$W" resume "$PID" >/dev/null; "$W" stop "$PID" >/dev/null; sleep .2; grep -q 'status.*STOPPED' "$D/curl.args"
OUT="$(run_start kill 104 20 'sleep 10')"; PID="$(sed -n 's/^PID://p' <<<"$OUT")"; "$W" pause "$PID" >/dev/null; "$W" kill "$PID" >/dev/null; sleep .2; grep -q 'status.*KILLED' "$D/curl.args"
OUT="$(run_start timeout 105 1 'sleep 3')"; PID="$(sed -n 's/^PID://p' <<<"$OUT")"; test -n "$PID"; sleep 2; grep -q 'status.*TIMEOUT' "$D/curl.args"
echo 'WRAPPER FULL OUTCOME TEST: PASS (SUCCESS, FAILED, PAUSED, RESUMED, STOPPED, KILLED, TIMEOUT)'
